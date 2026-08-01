package com.scott.payment.data.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingSingleTableContext;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.entity.DataMerchantNotificationLogDO;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.mapper.DataMerchantNotificationLogMapper;
import com.scott.payment.data.mapper.DataMerchantNotificationMapper;
import com.scott.payment.data.service.MerchantNotificationDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantNotificationDeliveryService
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知默认实现，以主库版本 CAS 保证多实例抢占，执行有界 HTTP 回调并持久化每次尝试和重试状态
 * @status : create
 */
@Slf4j
@Service
@DS(DataSourceName.MASTER)
public class DefaultMerchantNotificationDeliveryService implements MerchantNotificationDeliveryService {

    /** 商户通知任务逻辑表。 */
    private static final String NOTIFICATION_TABLE = "transaction_merchant_notification";

    /** 商户通知尝试日志逻辑表。 */
    private static final String NOTIFICATION_LOG_TABLE = "transaction_merchant_notification_log";

    /** 通知日志业务 ID 前缀。 */
    private static final String NOTIFICATION_LOG_PREFIX = "TNL";

    /** 交易时间默认按平台业务时区解释。 */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /** 仍可继续重试的失败状态。 */
    private static final String STATUS_FAILED = "FAILED";

    /** 达到最大尝试次数后的关闭终态。 */
    private static final String STATUS_CLOSED = "CLOSED";

    /** 线性重试退避的基础秒数。 */
    private static final long BASE_RETRY_DELAY_SECONDS = 60L;

    /** 数据库失败原因字段最大长度。 */
    private static final int MAX_FAIL_REASON_LENGTH = 512;

    /** 审计响应摘要最大长度，防止异常商户响应形成大字段。 */
    private static final int MAX_RESPONSE_SUMMARY_LENGTH = 1_200;

    /** 商户通知任务数据访问入口。 */
    private final DataMerchantNotificationMapper notificationMapper;

    /** 商户通知尝试日志数据访问入口。 */
    private final DataMerchantNotificationLogMapper notificationLogMapper;

    /** 物理分表解析入口。 */
    private final ShardingDataTemplate shardingDataTemplate;

    /** 具备有界超时的商户通知 HTTP 客户端。 */
    private final RestTemplate restTemplate;

    /** 商户通知执行和恢复参数。 */
    private final DataMerchantNotificationProperties properties;

    /**
     * 创建商户通知投递服务。
     *
     * @param notificationMapper 通知任务 Mapper
     * @param notificationLogMapper 通知日志 Mapper
     * @param shardingDataTemplate 分表解析入口
     * @param restTemplate 商户通知 HTTP 客户端
     * @param properties 商户通知执行参数
     */
    public DefaultMerchantNotificationDeliveryService(
            DataMerchantNotificationMapper notificationMapper,
            DataMerchantNotificationLogMapper notificationLogMapper,
            ShardingDataTemplate shardingDataTemplate,
            @Qualifier("dataMerchantNotificationRestTemplate") RestTemplate restTemplate,
            DataMerchantNotificationProperties properties) {
        this.notificationMapper = notificationMapper;
        this.notificationLogMapper = notificationLogMapper;
        this.shardingDataTemplate = shardingDataTemplate;
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int notifyDue(LocalDateTime transactionDateTime, int limit) {
        validateTransactionDateTime(transactionDateTime);
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        long startNanos = System.nanoTime();
        String table = physicalTable(NOTIFICATION_TABLE, transactionDateTime);
        LocalDateTime now = LocalDateTime.now();
        recoverStaleProcessing(table, now);
        List<DataMerchantNotificationTaskDO> tasks = notificationMapper.selectDueForNotify(table, now, limit);
        int successCount = 0;
        for (DataMerchantNotificationTaskDO task : tasks) {
            if (notifySingle(table, task)) {
                successCount++;
            }
        }
        log.info("event: DATA_MERCHANT_NOTIFY_DUE_END traceId: {} physicalTable: {} taskCount: {} successCount: {} durationMs: {}",
                TraceContext.getTraceId(), table, tasks.size(), successCount, elapsedMillis(startNanos));
        return successCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean notifyTransaction(LocalDateTime transactionDateTime, String transactionId) {
        validateTransactionDateTime(transactionDateTime);
        if (!StringUtils.hasText(transactionId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_id is required");
        }
        String table = physicalTable(NOTIFICATION_TABLE, transactionDateTime);
        LocalDateTime now = LocalDateTime.now();
        recoverStaleProcessing(table, now);
        DataMerchantNotificationTaskDO task = notificationMapper.selectReadyByTransactionId(table, transactionId, now);
        boolean notified = task != null && notifySingle(table, task);
        log.info("event: DATA_MERCHANT_NOTIFY_TRANSACTION_END traceId: {} transactionId: {} taskFound: {} notified: {} physicalTable: {}",
                TraceContext.getTraceId(), transactionId, task != null, notified, table);
        return notified;
    }

    /**
     * 通过数据库版本 CAS 抢占任务，执行一次 HTTP 投递，并把结果推进为 SUCCESS、FAILED 或 CLOSED。
     *
     * <p>HTTP 调用不持有数据库事务。进程在抢占后异常退出时，由过期 PROCESSING 回收机制恢复；
     * 因而投递语义是至少一次，商户必须使用固定 notifyId 去重。</p>
     *
     * @param notificationTable 通知任务物理分表
     * @param task 待执行任务
     * @return true 表示 HTTP 2xx 且任务成功推进为 SUCCESS
     */
    private boolean notifySingle(String notificationTable, DataMerchantNotificationTaskDO task) {
        LocalDateTime beginTime = LocalDateTime.now();
        if (notificationMapper.markProcessing(notificationTable, task.getId(), task.getVersion(), beginTime) != 1) {
            log.info("event: DATA_MERCHANT_NOTIFY_SKIP traceId: {} notifyId: {} transactionId: {} reason=processingLockMiss",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId());
            return false;
        }
        int processingVersion = task.getVersion() == null ? 1 : task.getVersion() + 1;
        int attemptNo = task.getLastAttemptNo() == null ? 1 : task.getLastAttemptNo() + 1;
        log.info("event: DATA_MERCHANT_NOTIFY_ATTEMPT_START traceId: {} notifyId: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} callbackUrl: {} targetUrlHash: {} attemptNo: {} maxRetryCount: {} payloadLength: {} signType: {}",
                TraceContext.getTraceId(),
                task.getNotifyId(),
                task.getTransactionId(),
                task.getOperationId(),
                task.getMerchantId(),
                task.getMerchantOrderNo(),
                safeCallbackUrl(task.getTargetUrlMasked(), resolveTargetUrl(task)),
                task.getTargetUrlHash(),
                attemptNo,
                safeMaxRetry(task),
                task.getPayloadJsonMasked() == null ? 0 : task.getPayloadJsonMasked().length(),
                task.getSignType());

        NotifyAttemptResult result = executeHttpNotify(task, beginTime);
        LocalDateTime finishedTime = LocalDateTime.now();
        insertNotifyLog(task, attemptNo, result, beginTime, finishedTime);
        if (result.success()) {
            requireSingleStateUpdate(
                    notificationMapper.markSuccess(notificationTable, task.getId(), processingVersion, finishedTime),
                    task,
                    "SUCCESS");
            log.info("event: DATA_MERCHANT_NOTIFY_ATTEMPT_END traceId: {} notifyId: {} transactionId: {} merchantId: {} attemptNo: {} httpStatus: {} success: true responseSummary: {} durationMs: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                    attemptNo, result.httpStatus(), responseSummary(result.responseBody()),
                    durationMillis(beginTime, finishedTime));
            return true;
        }

        boolean exhausted = attemptNo >= safeMaxRetry(task);
        String nextStatus = exhausted ? STATUS_CLOSED : STATUS_FAILED;
        LocalDateTime nextRetryTime = exhausted ? null : nextRetryTime(finishedTime, attemptNo);
        requireSingleStateUpdate(
                notificationMapper.markFailed(
                        notificationTable,
                        task.getId(),
                        processingVersion,
                        nextStatus,
                        nextRetryTime,
                        safeLength(result.errorMessage(), MAX_FAIL_REASON_LENGTH),
                        finishedTime),
                task,
                nextStatus);
        log.warn("event: DATA_MERCHANT_NOTIFY_ATTEMPT_END traceId: {} notifyId: {} transactionId: {} merchantId: {} attemptNo: {} httpStatus: {} success: false nextStatus: {} exhausted: {} nextRetryTime: {} failureReason: {} durationMs: {}",
                TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                attemptNo, result.httpStatus(), nextStatus, exhausted, nextRetryTime,
                safeLength(result.errorMessage(), 200), durationMillis(beginTime, finishedTime));
        return false;
    }

    /**
     * 执行单次 HTTP 回调；非 2xx、网络异常和超时都转换为可记录的失败结果。
     *
     * @param task 已抢占通知任务
     * @param beginTime 本次尝试开始时间
     * @return 不包含原始 URL 和敏感异常文本的投递结果
     */
    private NotifyAttemptResult executeHttpNotify(DataMerchantNotificationTaskDO task, LocalDateTime beginTime) {
        String targetUrl = resolveTargetUrl(task);
        if (!StringUtils.hasText(targetUrl)) {
            return new NotifyAttemptResult(false, null, null, "merchant callback url is empty");
        }
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    targetUrl,
                    new HttpEntity<>(task.getPayloadJsonMasked(), requestHeaders(task)),
                    String.class);
            return new NotifyAttemptResult(
                    response.getStatusCode().is2xxSuccessful(),
                    response.getStatusCode().value(),
                    safeMaskedResponse(response.getBody()),
                    response.getStatusCode().is2xxSuccessful()
                            ? null
                            : "merchant callback http status " + response.getStatusCode().value());
        } catch (HttpStatusCodeException exception) {
            return new NotifyAttemptResult(
                    false,
                    exception.getStatusCode().value(),
                    safeMaskedResponse(exception.getResponseBodyAsString()),
                    "merchant callback http status " + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            log.warn("event: DATA_MERCHANT_NOTIFY_HTTP_FAILED traceId: {} notifyId: {} transactionId: {} merchantId: {} callbackUrl: {} exceptionType: {} durationMs: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                    safeCallbackUrl(task.getTargetUrlMasked(), targetUrl), exception.getClass().getSimpleName(),
                    durationMillis(beginTime, LocalDateTime.now()));
            return new NotifyAttemptResult(false, null, null,
                    "merchant callback transport error: " + exception.getClass().getSimpleName());
        }
    }

    /**
     * 构造商户回调请求头；固定 notifyId 是商户侧去重依据，不包含平台密钥或认证原文。
     *
     * @param task 商户通知任务
     * @return HTTP 请求头
     */
    private HttpHeaders requestHeaders(DataMerchantNotificationTaskDO task) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-OPGS-Notify-Id", task.getNotifyId());
        headers.set("X-OPGS-Transaction-Id", task.getTransactionId());
        return headers;
    }

    /**
     * 写入一次脱敏通知尝试日志；写库异常必须上抛，防止消息被错误确认。
     */
    private void insertNotifyLog(DataMerchantNotificationTaskDO task,
                                 int attemptNo,
                                 NotifyAttemptResult result,
                                 LocalDateTime beginTime,
                                 LocalDateTime finishedTime) {
        DataMerchantNotificationLogDO logDO = new DataMerchantNotificationLogDO();
        logDO.setNotifyLogId(PaymentOrderNoGenerator.nextOrderNo(NOTIFICATION_LOG_PREFIX, task.getTransactionDateTime()));
        logDO.setNotifyId(task.getNotifyId());
        logDO.setTransactionId(task.getTransactionId());
        logDO.setOperationId(task.getOperationId());
        logDO.setMerchantId(task.getMerchantId());
        logDO.setAttemptNo(attemptNo);
        logDO.setTargetUrlHash(task.getTargetUrlHash());
        logDO.setHttpStatus(result.httpStatus());
        logDO.setRequestHeaderJsonMasked(JsonUtils.toJsonString(auditHeaders(task)));
        logDO.setRequestBodyJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(task.getPayloadJsonMasked()));
        logDO.setResponseBodyJsonMasked(result.responseBody());
        logDO.setSuccess(result.success() ? 1 : 0);
        logDO.setErrorMessage(safeLength(result.errorMessage(), 1_024));
        logDO.setNotifyTime(beginTime);
        logDO.setDurationMillis(durationMillis(beginTime, finishedTime));
        fillTransactionTime(logDO, task.getTransactionDateTime());
        logDO.setCreateTime(finishedTime);
        String physicalTable = physicalTable(NOTIFICATION_LOG_TABLE, task.getTransactionDateTime());
        int affectedRows = notificationLogMapper.insertPhysical(physicalTable, logDO);
        if (affectedRows != 1) {
            throw new IllegalStateException("merchant notification attempt log was not inserted");
        }
    }

    /**
     * 构造允许写入审计表的请求头摘要。
     */
    private Map<String, String> auditHeaders(DataMerchantNotificationTaskDO task) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        headers.put("X-OPGS-Notify-Id", task.getNotifyId());
        headers.put("X-OPGS-Transaction-Id", task.getTransactionId());
        return headers;
    }

    /**
     * 回收进程中断留下的超时 PROCESSING 任务，使定时补偿能够再次抢占。
     */
    private void recoverStaleProcessing(String physicalTable, LocalDateTime now) {
        LocalDateTime staleBefore = now.minusSeconds(properties.getProcessingTimeoutSeconds());
        int recovered = notificationMapper.recoverStaleProcessing(physicalTable, staleBefore, now);
        if (recovered > 0) {
            log.warn("event: DATA_MERCHANT_NOTIFY_PROCESSING_RECOVERED traceId: {} physicalTable: {} staleBefore: {} recoveredCount: {}",
                    TraceContext.getTraceId(), physicalTable, staleBefore, recovered);
        }
    }

    /**
     * 要求状态 CAS 恰好更新一行；HTTP 已执行但状态推进失败时上抛，交由补偿机制恢复。
     */
    private void requireSingleStateUpdate(int affectedRows,
                                          DataMerchantNotificationTaskDO task,
                                          String targetStatus) {
        if (affectedRows != 1) {
            log.error("event: DATA_MERCHANT_NOTIFY_STATE_CONFLICT traceId: {} notifyId: {} transactionId: {} merchantId: {} targetStatus: {} affectedRows: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                    targetStatus, affectedRows);
            throw new IllegalStateException("merchant notification state transition conflict");
        }
    }

    /**
     * 从数据库配置快照读取实际回调 URL；解析失败不记录快照内容，并按普通通知失败处理。
     */
    private String resolveTargetUrl(DataMerchantNotificationTaskDO task) {
        if (!StringUtils.hasText(task.getNotifyConfigSnapshotJson())) {
            return null;
        }
        try {
            JSONObject snapshot = JsonUtils.parseObject(task.getNotifyConfigSnapshotJson(), JSONObject.class);
            return snapshot == null ? null : snapshot.getString("callbackUrl");
        } catch (RuntimeException exception) {
            log.warn("event: DATA_MERCHANT_NOTIFY_CONFIG_INVALID traceId: {} notifyId: {} transactionId: {} exceptionType: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(),
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    /** 计算线性退避后的下一次通知时间。 */
    private LocalDateTime nextRetryTime(LocalDateTime baseTime, int attemptNo) {
        return baseTime.plusSeconds(BASE_RETRY_DELAY_SECONDS * Math.max(1, Math.min(attemptNo, 30)));
    }

    /** 返回任务有效最大尝试次数，脏数据按一次处理。 */
    private int safeMaxRetry(DataMerchantNotificationTaskDO task) {
        return task.getMaxRetryCount() == null || task.getMaxRetryCount() <= 0 ? 1 : task.getMaxRetryCount();
    }

    /** 对响应执行二次脱敏和长度限制。 */
    private String safeMaskedResponse(String responseBody) {
        return safeLength(SensitiveDataMaskUtils.maskJsonSafely(responseBody), MAX_RESPONSE_SUMMARY_LENGTH);
    }

    /** 返回适合结构化日志的商户响应摘要。 */
    private String responseSummary(String responseBody) {
        return safeMaskedResponse(responseBody);
    }

    /** 返回候选值中的第一个非空白文本。 */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /** 移除 URL 查询参数值，防止商户令牌进入日志。 */
    private String safeCallbackUrl(String... values) {
        String url = firstText(values);
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int queryIndex = url.indexOf('?');
        return queryIndex < 0 ? url : url.substring(0, queryIndex) + "?...";
    }

    /** 填充通知日志的业务时间、UTC 时间和时区。 */
    private void fillTransactionTime(DataMerchantNotificationLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(transactionDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime());
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /** 解析并校验交易时间对应的通知物理分表。 */
    private String physicalTable(String logicalTable, LocalDateTime transactionDateTime) {
        return shardingDataTemplate.resolvePhysicalTable(
                ShardingSingleTableContext.of(logicalTable, transactionDateTime, DataSourceName.MASTER));
    }

    /** 校验交易分表时间。 */
    private void validateTransactionDateTime(LocalDateTime transactionDateTime) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
    }

    /** 截断数据库和日志文本，空值保持为空。 */
    private String safeLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /** 计算本次 HTTP 尝试耗时并限制为数据库整数范围。 */
    private int durationMillis(LocalDateTime startTime, LocalDateTime endTime) {
        long millis = Duration.between(startTime, endTime).toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) millis);
    }

    /** 计算消费或批量处理耗时。 */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** 单次商户 HTTP 回调的安全结果摘要。 */
    private record NotifyAttemptResult(boolean success,
                                       Integer httpStatus,
                                       String responseBody,
                                       String errorMessage) {
    }
}
