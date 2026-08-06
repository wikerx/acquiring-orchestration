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
import com.scott.payment.component.db.sharding.TransactionPrimaryRouteScope;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.entity.DataMerchantNotificationLogDO;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.mapper.DataMerchantNotificationLogMapper;
import com.scott.payment.data.mapper.DataMerchantNotificationMapper;
import com.scott.payment.data.model.MerchantCallbackHttpRequest;
import com.scott.payment.data.service.MerchantNotificationDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
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
 * @description : service-data 商户通知默认实现，以交易逻辑数据源和带分片时间的 CAS 保证多实例抢占。
 * @status : create
 */
@Slf4j
@Service
@DS(DataSourceName.TRANSACTION)
public class DefaultMerchantNotificationDeliveryService implements MerchantNotificationDeliveryService {

    /** 商户通知任务逻辑表。 */
    private static final String NOTIFICATION_TABLE = "transaction_merchant_notification";

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

    /** 具备有界超时的商户通知 HTTP 客户端。 */
    private final RestTemplate restTemplate;

    /** 商户通知执行和恢复参数。 */
    private final DataMerchantNotificationProperties properties;

    /** v1 回调 JWT、Header 和加密正文构造器。 */
    private final MerchantCallbackRequestFactory requestFactory;

    /** 回调出站协议和网络边界校验器。 */
    private final MerchantCallbackTargetValidator targetValidator;

    /** 失败状态与自动重试 Outbox 的同事务持久化边界。 */
    private final MerchantNotificationRetryStateService retryStateService;

    /**
     * 创建商户通知投递服务。
     *
     * @param notificationMapper 通知任务 Mapper
     * @param notificationLogMapper 通知日志 Mapper
     * @param restTemplate 商户通知 HTTP 客户端
     * @param properties 商户通知执行参数
     */
    @Autowired
    public DefaultMerchantNotificationDeliveryService(
            DataMerchantNotificationMapper notificationMapper,
            DataMerchantNotificationLogMapper notificationLogMapper,
            @Qualifier("dataMerchantNotificationRestTemplate") RestTemplate restTemplate,
            DataMerchantNotificationProperties properties,
            MerchantCallbackRequestFactory requestFactory,
            MerchantCallbackTargetValidator targetValidator,
            MerchantNotificationRetryStateService retryStateService) {
        this.notificationMapper = notificationMapper;
        this.notificationLogMapper = notificationLogMapper;
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.requestFactory = requestFactory;
        this.targetValidator = targetValidator;
        this.retryStateService = retryStateService;
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
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime beginTime = quarterBegin(transactionDateTime);
        List<DataMerchantNotificationTaskDO> tasks;
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            recoverStaleProcessing(beginTime, now);
            tasks = notificationMapper.selectDueForNotify(beginTime, beginTime.plusMonths(3), now, limit);
        }
        int successCount = 0;
        for (DataMerchantNotificationTaskDO task : tasks) {
            if (notifySingle(task)) {
                successCount++;
            }
        }
        log.info("event: DATA_MERCHANT_NOTIFY_DUE_END traceId: {} routeTable: {} taskCount: {} successCount: {} durationMs: {}",
                TraceContext.getTraceId(), NOTIFICATION_TABLE, tasks.size(), successCount, elapsedMillis(startNanos));
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
        LocalDateTime now = LocalDateTime.now();
        DataMerchantNotificationTaskDO task;
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            task = notificationMapper.selectReadyByTransactionId(transactionId, transactionDateTime, now);
        }
        boolean notified = task != null && notifySingle(task);
        log.info("event: DATA_MERCHANT_NOTIFY_TRANSACTION_END traceId: {} transactionId: {} taskFound: {} notified: {} routeTable: {}",
                TraceContext.getTraceId(), transactionId, task != null, notified, NOTIFICATION_TABLE);
        return notified;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean retryTransaction(LocalDateTime transactionDateTime,
                                    String transactionId,
                                    String callbackEventId) {
        validateTransactionDateTime(transactionDateTime);
        if (!StringUtils.hasText(transactionId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_id is required");
        }
        if (!StringUtils.hasText(callbackEventId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "callback_event_id is required");
        }
        DataMerchantNotificationTaskDO task;
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            task = notificationMapper.selectRetryableByTransactionId(transactionId, transactionDateTime);
        }
        boolean notified = task != null && notifySingle(task, callbackEventId, true);
        log.info("event: DATA_MERCHANT_NOTIFY_MANUAL_RETRY_END traceId: {} callbackEventId: {} transactionId: {} taskFound: {} notified: {} routeTable: {}",
                TraceContext.getTraceId(), callbackEventId, transactionId, task != null, notified, NOTIFICATION_TABLE);
        return notified;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean retryDue(LocalDateTime transactionDateTime,
                            String transactionId,
                            String notifyId,
                            int expectedVersion,
                            int attemptNo) {
        validateTransactionDateTime(transactionDateTime);
        if (!StringUtils.hasText(transactionId) || !StringUtils.hasText(notifyId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(),
                    "transaction_id and notify_id are required");
        }
        if (expectedVersion < 0 || attemptNo <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "retry version and attempt number are invalid");
        }
        DataMerchantNotificationTaskDO task;
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            task = notificationMapper.selectReadyByRetryEvent(
                    transactionId, notifyId, transactionDateTime, expectedVersion, LocalDateTime.now());
        }
        int expectedAttempt = task == null || task.getLastAttemptNo() == null
                ? 1
                : task.getLastAttemptNo() + 1;
        boolean notified = task != null && expectedAttempt == attemptNo && notifySingle(task);
        log.info("event: DATA_MERCHANT_NOTIFY_RETRY_DUE_END traceId: {} messageVersion: {} attemptNo: {} notifyId: {} transactionId: {} taskFound: {} notified: {}",
                TraceContext.getTraceId(), expectedVersion, attemptNo, notifyId, transactionId,
                task != null, notified);
        return notified;
    }

    /**
     * 通过数据库版本 CAS 抢占任务，执行一次 HTTP 投递，并把结果推进为 SUCCESS、FAILED 或 CLOSED。
     *
     * <p>HTTP 调用不持有数据库事务。进程在抢占后异常退出时，由过期 PROCESSING 回收机制恢复；
     * 因而投递语义是至少一次，商户必须使用固定 notifyId 去重。</p>
     *
     * @param task 待执行任务
     * @return true 表示 HTTP 200、正文为 succeed 且任务成功推进为 SUCCESS
     */
    private boolean notifySingle(DataMerchantNotificationTaskDO task) {
        return notifySingle(task, null, false);
    }

    /**
     * 执行普通通知或后台人工重发；人工重发使用独立 CAS 和稳定事件号。
     *
     * @param task 待执行通知任务
     * @param callbackEventId 人工重发的固定回调事件号；普通通知为空
     * @param manualRetry 是否为后台人工重发
     * @return true 表示商户确认成功且通知状态推进为 SUCCESS
     */
    private boolean notifySingle(DataMerchantNotificationTaskDO task,
                                 String callbackEventId,
                                 boolean manualRetry) {
        validateTaskTransactionDateTime(task);
        LocalDateTime beginTime = LocalDateTime.now();
        int claimed = manualRetry
                ? notificationMapper.markProcessingForManualRetry(
                        task.getId(), task.getTransactionDateTime(), task.getVersion(), beginTime)
                : notificationMapper.markProcessing(
                        task.getId(), task.getTransactionDateTime(), task.getVersion(), beginTime);
        if (claimed != 1) {
            log.info("event: DATA_MERCHANT_NOTIFY_SKIP traceId: {} notifyId: {} transactionId: {} manualRetry: {} reason=processingLockMiss",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), manualRetry);
            if (manualRetry) {
                // 人工重发是一条独立业务指令；抢占冲突必须让 MQ 重投，不能静默丢失用户操作。
                throw new IllegalStateException("merchant notification manual retry claim conflict");
            }
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

        NotifyAttemptResult result = executeHttpNotify(task, attemptNo, callbackEventId, beginTime);
        LocalDateTime finishedTime = LocalDateTime.now();
        insertNotifyLog(task, attemptNo, result, beginTime, finishedTime);
        if (result.success()) {
            int affectedRows = notificationMapper.markSuccess(
                    task.getId(), task.getTransactionDateTime(), processingVersion, finishedTime);
            requireSingleStateUpdate(
                    affectedRows,
                    task,
                    "SUCCESS");
            log.info("event: DATA_MERCHANT_NOTIFY_ATTEMPT_END traceId: {} notifyId: {} transactionId: {} merchantId: {} attemptNo: {} httpStatus: {} success: true responseSummary: {} durationMs: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                    attemptNo, result.httpStatus(), responseSummary(result.responseBody()),
                    durationMillis(beginTime, finishedTime));
            return true;
        }

        int effectiveMaxRetry = manualRetry
                ? Math.max(safeMaxRetry(task), attemptNo + 1)
                : safeMaxRetry(task);
        boolean exhausted = attemptNo >= effectiveMaxRetry;
        String nextStatus = exhausted ? STATUS_CLOSED : STATUS_FAILED;
        LocalDateTime nextRetryTime = exhausted ? null : nextRetryTime(finishedTime, attemptNo);
        retryStateService.recordFailure(
                task,
                processingVersion,
                nextStatus,
                nextRetryTime,
                safeLength(result.errorMessage(), MAX_FAIL_REASON_LENGTH),
                finishedTime,
                attemptNo);
        log.warn("event: DATA_MERCHANT_NOTIFY_ATTEMPT_END traceId: {} notifyId: {} transactionId: {} merchantId: {} attemptNo: {} httpStatus: {} success: false nextStatus: {} exhausted: {} nextRetryTime: {} failureReason: {} durationMs: {}",
                TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                attemptNo, result.httpStatus(), nextStatus, exhausted, nextRetryTime,
                safeLength(result.errorMessage(), 200), durationMillis(beginTime, finishedTime));
        return false;
    }

    /**
     * 执行单次 HTTP 回调；仅 HTTP 200 且正文精确为 succeed（忽略首尾空白）才成功。
     */
    private NotifyAttemptResult executeHttpNotify(DataMerchantNotificationTaskDO task,
                                                   int attemptNo,
                                                   String callbackEventId,
                                                   LocalDateTime beginTime) {
        String targetUrl = resolveTargetUrl(task);
        if (!StringUtils.hasText(targetUrl)) {
            return new NotifyAttemptResult(false, null, null, "merchant callback url is empty", null, null);
        }
        MerchantCallbackHttpRequest request = null;
        try {
            targetValidator.validate(targetUrl);
            request = StringUtils.hasText(callbackEventId)
                    ? requestFactory.create(task, attemptNo, callbackEventId)
                    : requestFactory.create(task, attemptNo);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    targetUrl,
                    new HttpEntity<>(request.encryptedBody(), request.headers()),
                    String.class);
            boolean success = response.getStatusCode().value() == 200
                    && "succeed".equals(response.getBody() == null ? null : response.getBody().trim());
            return new NotifyAttemptResult(
                    success,
                    response.getStatusCode().value(),
                    safeMaskedResponse(response.getBody()),
                    callbackFailureReason(response, success),
                    request.eventId(),
                    request.auditBody());
        } catch (HttpStatusCodeException exception) {
            return new NotifyAttemptResult(
                    false,
                    exception.getStatusCode().value(),
                    safeMaskedResponse(exception.getResponseBodyAsString()),
                    "merchant callback http status " + exception.getStatusCode().value(),
                    eventId(request),
                    auditBody(request));
        } catch (RestClientException exception) {
            log.warn("event: DATA_MERCHANT_NOTIFY_HTTP_FAILED traceId: {} notifyId: {} transactionId: {} merchantId: {} callbackUrl: {} exceptionType: {} durationMs: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                    safeCallbackUrl(task.getTargetUrlMasked(), targetUrl), exception.getClass().getSimpleName(),
                    durationMillis(beginTime, LocalDateTime.now()));
            return new NotifyAttemptResult(false, null, null,
                    "merchant callback transport error: " + exception.getClass().getSimpleName(),
                    eventId(request),
                    auditBody(request));
        } catch (RuntimeException exception) {
            log.warn("event: DATA_MERCHANT_NOTIFY_PROTOCOL_FAILED traceId: {} notifyId: {} transactionId: {} merchantId: {} exceptionType: {} durationMs: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                    exception.getClass().getSimpleName(), durationMillis(beginTime, LocalDateTime.now()));
            return new NotifyAttemptResult(false, null, null,
                    "merchant callback protocol error: " + exception.getClass().getSimpleName(), null, null);
        }
    }

    /** 返回已构造请求的事件号；协议构造前失败时不伪造审计标识。 */
    private String eventId(MerchantCallbackHttpRequest request) {
        return request == null ? null : request.eventId();
    }

    /** 返回允许持久化的脱敏请求摘要，禁止把实际密文或认证信息写入日志表。 */
    private String auditBody(MerchantCallbackHttpRequest request) {
        return request == null ? null : request.auditBody();
    }

    /** 区分 HTTP 状态失败和 200 响应确认词不匹配。 */
    private String callbackFailureReason(ResponseEntity<String> response, boolean success) {
        if (success) {
            return null;
        }
        if (response.getStatusCode().value() != 200) {
            return "merchant callback http status " + response.getStatusCode().value();
        }
        return "merchant callback acknowledgement must be succeed";
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
        logDO.setRequestHeaderJsonMasked(JsonUtils.toJsonString(auditHeaders(task, attemptNo, result.eventId())));
        logDO.setRequestBodyJsonMasked(result.auditRequestBody());
        logDO.setResponseBodyJsonMasked(result.responseBody());
        logDO.setSuccess(result.success() ? 1 : 0);
        logDO.setErrorMessage(safeLength(result.errorMessage(), 1_024));
        logDO.setNotifyTime(beginTime);
        logDO.setDurationMillis(durationMillis(beginTime, finishedTime));
        fillTransactionTime(logDO, task.getTransactionDateTime());
        logDO.setCreateTime(finishedTime);
        int affectedRows = notificationLogMapper.insert(logDO);
        if (affectedRows != 1) {
            throw new IllegalStateException("merchant notification attempt log was not inserted");
        }
    }

    /**
     * 构造允许写入审计表的请求头摘要。
     */
    private Map<String, String> auditHeaders(DataMerchantNotificationTaskDO task,
                                             int attemptNo,
                                             String eventId) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer ***");
        headers.put("Content-Type", "application/json; charset=UTF-8");
        headers.put(MerchantCallbackRequestFactory.HEADER_CALLBACK_VERSION,
                MerchantCallbackRequestFactory.CALLBACK_VERSION);
        headers.put(MerchantCallbackRequestFactory.HEADER_CALLBACK_TIMES, String.valueOf(attemptNo));
        headers.put(MerchantCallbackRequestFactory.HEADER_CALLBACK_EVENT_ID, eventId);
        headers.put(MerchantCallbackRequestFactory.HEADER_NOTIFY_ID, task.getNotifyId());
        headers.put(MerchantCallbackRequestFactory.HEADER_TRANSACTION_ID, task.getTransactionId());
        return headers;
    }

    /** 先查询单季度有界候选，再逐条 CAS 回收超时 PROCESSING 任务。 */
    private void recoverStaleProcessing(LocalDateTime beginTime, LocalDateTime now) {
        LocalDateTime staleBefore = now.minusSeconds(properties.getProcessingTimeoutSeconds());
        List<DataMerchantNotificationTaskDO> candidates = notificationMapper.selectStaleProcessing(
                beginTime,
                beginTime.plusMonths(3),
                staleBefore,
                properties.getRecoveryBatchLimit());
        int recovered = 0;
        for (DataMerchantNotificationTaskDO candidate : candidates) {
            validateTaskTransactionDateTime(candidate);
            recovered += notificationMapper.recoverStaleProcessingCas(
                    candidate.getId(),
                    candidate.getTransactionDateTime(),
                    candidate.getVersion(),
                    staleBefore,
                    now);
        }
        if (recovered > 0) {
            log.warn("event: DATA_MERCHANT_NOTIFY_PROCESSING_RECOVERED traceId: {} quarterBegin: {} staleBefore: {} recoveredCount: {}",
                    TraceContext.getTraceId(), beginTime, staleBefore, recovered);
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

    /** 返回交易时间所在季度的半开区间起点。 */
    private LocalDateTime quarterBegin(LocalDateTime transactionDateTime) {
        int firstMonth = ((transactionDateTime.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDateTime.of(transactionDateTime.getYear(), firstMonth, 1, 0, 0);
    }

    /** 校验交易分表时间。 */
    private void validateTransactionDateTime(LocalDateTime transactionDateTime) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
    }

    /** 校验数据库任务携带可精确路由的交易分片时间，并在任何状态更新前失败。 */
    private void validateTaskTransactionDateTime(DataMerchantNotificationTaskDO task) {
        if (task == null || task.getTransactionDateTime() == null) {
            throw new IllegalStateException("merchant notification task transaction_date_time is required");
        }
        if (task.getId() == null || task.getVersion() == null) {
            throw new IllegalStateException("merchant notification task id and version are required for CAS");
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
                                       String errorMessage,
                                       String eventId,
                                       String auditRequestBody) {
    }
}
