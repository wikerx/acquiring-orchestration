package com.scott.payment.payment.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.entity.TransactionMerchantNotificationLogDO;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationLogMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import com.scott.payment.payment.service.TransactionMerchantNotificationService;
import com.scott.payment.payment.support.TransactionShardingSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionMerchantNotificationService
 * @date : 2026-07-14 21:37
 * @email : scott_x@163.com
 * @description : 商户交易结果通知默认实现，位于 service-payment 服务实现层，按 transaction_date_time 分表扫描到期任务并记录每一次通知尝试。
 * @status : create
 */
@Slf4j
@Service
public class DefaultTransactionMerchantNotificationService implements TransactionMerchantNotificationService {

    private static final String TRANSACTION_MERCHANT_NOTIFICATION_TABLE = "transaction_merchant_notification";

    private static final String TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE = "transaction_merchant_notification_log";

    private static final String NOTIFY_LOG_PREFIX = "TNL";

    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    private static final String STATUS_FAILED = "FAILED";

    private static final String STATUS_CLOSED = "CLOSED";

    private static final long BASE_RETRY_DELAY_SECONDS = 60L;

    private static final int MAX_FAIL_REASON_LENGTH = 512;

    private final TransactionMerchantNotificationMapper notificationMapper;

    private final TransactionMerchantNotificationLogMapper notificationLogMapper;

    private final TransactionShardingSupport shardingSupport;

    private final RestTemplate restTemplate;

    /**
     * 创建商户通知服务。
     *
     * @param notificationMapper 商户通知任务 Mapper
     * @param notificationLogMapper 商户通知日志 Mapper
     * @param shardingSupport 交易分表支撑组件
     * @param restTemplate 商户通知 HTTP 客户端
     */
    public DefaultTransactionMerchantNotificationService(TransactionMerchantNotificationMapper notificationMapper,
                                                         TransactionMerchantNotificationLogMapper notificationLogMapper,
                                                         TransactionShardingSupport shardingSupport,
                                                         @Qualifier("merchantNotificationRestTemplate") RestTemplate restTemplate) {
        this.notificationMapper = notificationMapper;
        this.notificationLogMapper = notificationLogMapper;
        this.shardingSupport = shardingSupport;
        this.restTemplate = restTemplate;
    }

    /**
     * 执行指定交易时间所在分表的到期商户通知。
     *
     * @param transactionDateTime 交易业务时间
     * @param limit 最大处理数量
     * @return 成功通知数量
     */
    @Override
    public int notifyDue(LocalDateTime transactionDateTime, int limit) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        String table = shardingSupport.physicalTable(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, transactionDateTime);
        List<TransactionMerchantNotificationDO> dueTasks = notificationMapper.selectDueForNotify(table, LocalDateTime.now(), limit);
        int successCount = 0;
        for (TransactionMerchantNotificationDO task : dueTasks) {
            if (notifySingle(table, task)) {
                successCount++;
            }
        }
        return successCount;
    }

    /**
     * 按平台交易 ID 精准触发商户通知。
     *
     * @param transactionDateTime 交易业务时间
     * @param transactionId 平台当前交易 ID
     * @return true 表示商户通知成功
     */
    @Override
    public boolean notifyTransaction(LocalDateTime transactionDateTime, String transactionId) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
        if (!StringUtils.hasText(transactionId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_id is required");
        }
        String table = shardingSupport.physicalTable(TRANSACTION_MERCHANT_NOTIFICATION_TABLE, transactionDateTime);
        TransactionMerchantNotificationDO task = notificationMapper.selectReadyByTransactionId(
                table, transactionId, LocalDateTime.now());
        return task != null && notifySingle(table, task);
    }

    private boolean notifySingle(String notificationTable, TransactionMerchantNotificationDO task) {
        LocalDateTime beginTime = LocalDateTime.now();
        if (notificationMapper.markProcessing(notificationTable, task.getId(), task.getVersion(), beginTime) != 1) {
            return false;
        }
        int processingVersion = task.getVersion() == null ? 1 : task.getVersion() + 1;
        int attemptNo = task.getLastAttemptNo() == null ? 1 : task.getLastAttemptNo() + 1;
        NotifyAttemptResult result = executeHttpNotify(task, beginTime);
        LocalDateTime finishedTime = LocalDateTime.now();
        insertNotifyLog(task, attemptNo, result, beginTime, finishedTime);
        if (result.success()) {
            notificationMapper.markSuccess(notificationTable, task.getId(), processingVersion, finishedTime);
            return true;
        }
        boolean exhausted = attemptNo >= safeMaxRetry(task);
        notificationMapper.markFailed(
                notificationTable,
                task.getId(),
                processingVersion,
                exhausted ? STATUS_CLOSED : STATUS_FAILED,
                exhausted ? null : nextRetryTime(finishedTime, attemptNo),
                safeLength(result.errorMessage(), MAX_FAIL_REASON_LENGTH),
                finishedTime);
        return false;
    }

    private NotifyAttemptResult executeHttpNotify(TransactionMerchantNotificationDO task, LocalDateTime beginTime) {
        String targetUrl = resolveTargetUrl(task);
        if (!StringUtils.hasText(targetUrl)) {
            return new NotifyAttemptResult(false, null, null, "merchant callback url is empty");
        }
        String requestBody = task.getPayloadJsonMasked();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-OPGS-Notify-Id", task.getNotifyId());
            headers.set("X-OPGS-Transaction-Id", task.getTransactionId());
            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, new HttpEntity<>(requestBody, headers), String.class);
            boolean success = response.getStatusCode().is2xxSuccessful();
            String responseBody = response.getBody();
            return new NotifyAttemptResult(
                    success,
                    response.getStatusCode().value(),
                    SensitiveDataMaskUtils.maskJson(responseBody),
                    success ? null : "merchant callback http status " + response.getStatusCode().value());
        } catch (RestClientException exception) {
            log.warn("商户通知请求失败，notifyId：{}，transactionId：{}，原因：{}",
                    task.getNotifyId(), task.getTransactionId(), exception.getMessage());
            return new NotifyAttemptResult(false, null, null, exception.getMessage());
        }
    }

    private void insertNotifyLog(TransactionMerchantNotificationDO task,
                                 int attemptNo,
                                 NotifyAttemptResult result,
                                 LocalDateTime beginTime,
                                 LocalDateTime finishedTime) {
        TransactionMerchantNotificationLogDO logDO = new TransactionMerchantNotificationLogDO();
        logDO.setNotifyLogId(PaymentOrderNoGenerator.nextOrderNo(NOTIFY_LOG_PREFIX, task.getTransactionDateTime()));
        logDO.setNotifyId(task.getNotifyId());
        logDO.setTransactionId(task.getTransactionId());
        logDO.setOperationId(task.getOperationId());
        logDO.setMerchantId(task.getMerchantId());
        logDO.setAttemptNo(attemptNo);
        logDO.setTargetUrlHash(task.getTargetUrlHash());
        logDO.setHttpStatus(result.httpStatus());
        logDO.setRequestHeaderJsonMasked(JsonUtils.toJsonString(Map.of(
                "Content-Type", MediaType.APPLICATION_JSON_VALUE,
                "X-OPGS-Notify-Id", task.getNotifyId(),
                "X-OPGS-Transaction-Id", task.getTransactionId())));
        logDO.setRequestBodyJsonMasked(SensitiveDataMaskUtils.maskJson(task.getPayloadJsonMasked()));
        logDO.setResponseBodyJsonMasked(SensitiveDataMaskUtils.maskJson(result.responseBody()));
        logDO.setSuccess(result.success() ? 1 : 0);
        logDO.setErrorMessage(safeLength(result.errorMessage(), 1024));
        logDO.setNotifyTime(beginTime);
        logDO.setDurationMillis(durationMillis(beginTime, finishedTime));
        fillTransactionTime(logDO, task.getTransactionDateTime());
        logDO.setCreateTime(finishedTime);
        notificationLogMapper.insertPhysical(
                shardingSupport.physicalTable(TRANSACTION_MERCHANT_NOTIFICATION_LOG_TABLE, task.getTransactionDateTime()),
                logDO);
    }

    private String resolveTargetUrl(TransactionMerchantNotificationDO task) {
        if (!StringUtils.hasText(task.getNotifyConfigSnapshotJson())) {
            return null;
        }
        JSONObject snapshot = JsonUtils.parseObject(task.getNotifyConfigSnapshotJson(), JSONObject.class);
        return snapshot == null ? null : snapshot.getString("callbackUrl");
    }

    private LocalDateTime nextRetryTime(LocalDateTime baseTime, int attemptNo) {
        long delaySeconds = BASE_RETRY_DELAY_SECONDS * Math.max(1, Math.min(attemptNo, 30));
        return baseTime.plusSeconds(delaySeconds);
    }

    private int safeMaxRetry(TransactionMerchantNotificationDO task) {
        return task.getMaxRetryCount() == null || task.getMaxRetryCount() <= 0 ? 1 : task.getMaxRetryCount();
    }

    private int durationMillis(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            return 0;
        }
        long millis = Duration.between(startTime, endTime).toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    private void fillTransactionTime(TransactionMerchantNotificationLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(transactionDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime());
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    private String safeLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record NotifyAttemptResult(boolean success,
                                       Integer httpStatus,
                                       String responseBody,
                                       String errorMessage) {
    }
}
