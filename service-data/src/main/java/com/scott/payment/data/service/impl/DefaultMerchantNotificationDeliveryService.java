package com.scott.payment.data.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionPrimaryRouteScope;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.entity.DataMerchantNotificationLogDO;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.mapper.DataMerchantNotificationMapper;
import com.scott.payment.data.model.MerchantCallbackAttemptResult;
import com.scott.payment.data.model.MerchantCallbackAuditHeaders;
import com.scott.payment.data.model.MerchantNotificationStatus;
import com.scott.payment.data.service.MerchantNotificationDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 编排商户通知任务抢占、HTTP 投递结果提交、重试计划和超时恢复。
 *
 * <p>HTTP 调用不持有数据库事务。任务通过交易分片时间和版本 CAS 抢占，尝试日志、最终状态和
 * 下一次重试 Outbox 由独立事务边界原子提交，因此多实例和 MQ 重投不会覆盖已提交终态。</p>
 */
@Slf4j
@Service
public class DefaultMerchantNotificationDeliveryService implements MerchantNotificationDeliveryService {

    /**
     * 商户通知任务逻辑表。
     */
    private static final String NOTIFICATION_TABLE = "transaction_merchant_notification";

    /**
     * 通知日志业务 ID 前缀。
     */
    private static final String NOTIFICATION_LOG_PREFIX = "TNL";

    /**
     * 交易时间默认按平台业务时区解释。
     */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * 仍可继续重试的失败状态。
     */
    private static final MerchantNotificationStatus STATUS_FAILED = MerchantNotificationStatus.FAILED;

    /**
     * 达到最大尝试次数后的关闭终态。
     */
    private static final MerchantNotificationStatus STATUS_CLOSED = MerchantNotificationStatus.CLOSED;

    /**
     * 自动商户回调协议允许的最大投递次数。
     */
    private static final int AUTOMATIC_DELIVERY_LIMIT = 5;

    /**
     * 数据库失败原因字段最大长度。
     */
    private static final int MAX_FAIL_REASON_LENGTH = 512;

    /**
     * 商户通知任务数据访问入口。
     */
    private final DataMerchantNotificationMapper notificationMapper;

    /**
     * 单次商户回调 HTTP 执行和外部结果分类边界。
     */
    private final MerchantCallbackHttpExecutor callbackHttpExecutor;

    /**
     * 商户通知执行和恢复参数。
     */
    private final DataMerchantNotificationProperties properties;

    /**
     * 尝试日志、任务结果与自动重试 Outbox 的原子事务提交边界。
     */
    private final MerchantNotificationAttemptCommitService attemptCommitService;

    /**
     * 创建商户通知投递服务。
     *
     * @param notificationMapper 通知任务 Mapper
     * @param callbackHttpExecutor 回调 HTTP 执行器
     * @param properties 商户通知执行参数
     * @param attemptCommitService 回调结果原子提交服务
     */
    public DefaultMerchantNotificationDeliveryService(
            DataMerchantNotificationMapper notificationMapper,
            MerchantCallbackHttpExecutor callbackHttpExecutor,
            DataMerchantNotificationProperties properties,
            MerchantNotificationAttemptCommitService attemptCommitService) {
        this.notificationMapper = notificationMapper;
        this.callbackHttpExecutor = callbackHttpExecutor;
        this.properties = properties;
        this.attemptCommitService = attemptCommitService;
    }

    /**
     * 扫描指定交易季度内已经到期的通知任务并逐条尝试投递。
     *
     * <p>扫描前先以版本 CAS 恢复超时的 PROCESSING 任务；批次内单条失败仍按其状态机提交，
     * 状态提交异常则向上抛出，避免调用方错误确认处理完成。</p>
     *
     * @param transactionDateTime 用于定位交易季度分片的任意时间
     * @param limit 本批最大任务数
     * @return 本批获得商户成功确认的任务数量
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
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
        log.info("商户到期通知批次处理完成, event: DATA_MERCHANT_NOTIFY_DUE_END, traceId: {}, routeTable: {}, taskCount: {}, successCount: {}, durationMs: {}",
                TraceContext.getTraceId(), NOTIFICATION_TABLE, tasks.size(), successCount, elapsedMillis(startNanos));
        return successCount;
    }

    /**
     * 按交易号和原始交易分片时间投递一条已到期自动通知。
     *
     * @param transactionDateTime 原始交易分片时间
     * @param transactionId 平台交易号
     * @return true 表示商户已确认且任务成功推进为 SUCCESS
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
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
        log.info("商户单笔通知处理完成, event: DATA_MERCHANT_NOTIFY_TRANSACTION_END, traceId: {}, transactionId: {}, taskFound: {}, notified: {}, routeTable: {}",
                TraceContext.getTraceId(), transactionId, task != null, notified, NOTIFICATION_TABLE);
        return notified;
    }

    /**
     * 执行一条后台人工重发命令，并使用命令事件号保持商户侧幂等。
     *
     * <p>已提交的相同人工事件直接返回历史结果；自动计划仍在执行时拒绝抢占，交由 MQ 重投，
     * 防止人工操作截断原有重试计划。</p>
     *
     * @param transactionDateTime 原始交易分片时间
     * @param transactionId 平台交易号
     * @param callbackEventId 人工重发命令的稳定事件号
     * @return true 表示该人工事件已经获得成功确认
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public boolean retryTransaction(LocalDateTime transactionDateTime,
                                    String transactionId,
                                    String callbackEventId) {
        return retryManual(
                transactionDateTime, transactionId, null, null, callbackEventId);
    }

    /**
     * 按商户、任务号、交易号和分片时间精确执行一次人工重发。
     *
     * <p>该兼容扩展专供商户端重发消息使用。任务归属在消费阶段再次校验，避免 API 校验完成后
     * 到消息消费之间选中同交易的其他通知任务；其余幂等、CAS 和尝试日志规则与管理端人工重发一致。</p>
     *
     * @param transactionDateTime 原交易分片时间
     * @param transactionId 平台交易号
     * @param notifyId 商户通知任务号
     * @param merchantId 通知任务所属商户号
     * @param callbackEventId 人工重发命令的稳定事件号
     * @return true 表示该人工事件已经获得成功确认
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public boolean retryNotification(LocalDateTime transactionDateTime,
                                     String transactionId,
                                     String notifyId,
                                     String merchantId,
                                     String callbackEventId) {
        if (!StringUtils.hasText(notifyId) || !StringUtils.hasText(merchantId)) {
            throw new ServiceException(
                    ApiResultEnum.PARAM_MISSING.getCode(),
                    "notify_id and merchant_id are required");
        }
        return retryManual(
                transactionDateTime,
                transactionId,
                notifyId.trim(),
                merchantId.trim(),
                callbackEventId);
    }

    /**
     * 执行管理端兼容命令或商户端精确命令的共同人工重发状态机。
     *
     * @param transactionDateTime 原交易分片时间
     * @param transactionId 平台交易号
     * @param notifyId 商户通知任务号；管理端兼容命令为空
     * @param merchantId 通知任务所属商户号；管理端兼容命令为空
     * @param callbackEventId 人工重发命令的稳定事件号
     * @return true 表示商户已经确认成功
     */
    private boolean retryManual(LocalDateTime transactionDateTime,
                                String transactionId,
                                String notifyId,
                                String merchantId,
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
            Integer committedOutcome = attemptCommitService.findManualOutcome(
                    callbackEventId, transactionDateTime);
            if (committedOutcome != null) {
                log.info("人工重发事件幂等命中, event: DATA_MERCHANT_NOTIFY_MANUAL_DUPLICATE, traceId: {}, callbackEventId: {}, transactionId: {}, success: {}",
                        TraceContext.getTraceId(), callbackEventId, transactionId, committedOutcome == 1);
                return committedOutcome == 1;
            }
            boolean exactMerchantTask = StringUtils.hasText(notifyId) && StringUtils.hasText(merchantId);
            task = exactMerchantTask
                    ? notificationMapper.selectRetryableNotification(
                            transactionId, notifyId, merchantId, transactionDateTime)
                    : notificationMapper.selectRetryableByTransactionId(
                            transactionId, transactionDateTime);
            if (task == null) {
                DataMerchantNotificationTaskDO current = exactMerchantTask
                        ? notificationMapper.selectCurrentStateByNotification(
                                transactionId, notifyId, merchantId, transactionDateTime)
                        : notificationMapper.selectCurrentStateByTransactionId(
                                transactionId, transactionDateTime);
                if (current != null && "PROCESSING".equals(current.getNotifyStatus())) {
                    throw new IllegalStateException("merchant notification manual retry is processing");
                }
                // 管理端校验到 MQ 消费之间可能发生状态变化；未落尝试日志的人工命令不能被静默确认。
                log.warn("人工重发任务已不具备执行资格, event: DATA_MERCHANT_NOTIFY_MANUAL_INELIGIBLE, traceId: {}, callbackEventId: {}, notifyId: {}, transactionId: {}, merchantId: {}, currentStatus: {}",
                        TraceContext.getTraceId(), callbackEventId, notifyId, transactionId, merchantId,
                        current == null ? null : current.getNotifyStatus());
                throw new IllegalStateException("merchant notification manual retry task is no longer eligible");
            }
        }
        boolean notified = notifySingle(task, callbackEventId, true);
        log.info("人工重发通知处理完成, event: DATA_MERCHANT_NOTIFY_MANUAL_RETRY_END, traceId: {}, callbackEventId: {}, notifyId: {}, transactionId: {}, merchantId: {}, taskFound: {}, notified: {}, routeTable: {}",
                TraceContext.getTraceId(), callbackEventId, task.getNotifyId(), transactionId,
                task.getMerchantId(), true, notified, NOTIFICATION_TABLE);
        return notified;
    }

    /**
     * 消费一条自动重试到期命令，只处理版本和尝试序号完全匹配的任务。
     *
     * @param transactionDateTime 原始交易分片时间
     * @param transactionId 平台交易号
     * @param notifyId 通知任务号
     * @param expectedVersion 创建重试事件时冻结的任务版本
     * @param attemptNo 本事件要求执行的尝试序号
     * @return true 表示本次重试获得商户成功确认
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
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
                    transactionId, notifyId, transactionDateTime, expectedVersion, attemptNo, LocalDateTime.now());
        }
        int expectedAttempt = task == null || task.getLastAttemptNo() == null
                ? 1
                : task.getLastAttemptNo() + 1;
        boolean notified = task != null && expectedAttempt == attemptNo && notifySingle(task);
        log.info("自动重试到期事件处理完成, event: DATA_MERCHANT_NOTIFY_RETRY_DUE_END, traceId: {}, messageVersion: {}, attemptNo: {}, notifyId: {}, transactionId: {}, taskFound: {}, notified: {}",
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
                        task.getId(), task.getTransactionDateTime(), task.getVersion(), callbackEventId, beginTime)
                : notificationMapper.markProcessing(
                        task.getId(), task.getTransactionDateTime(), task.getVersion(), beginTime);
        if (claimed != 1) {
            log.info("商户通知抢占未命中, event: DATA_MERCHANT_NOTIFY_SKIP, traceId: {}, notifyId: {}, transactionId: {}, manualRetry: {}, reason: processingLockMiss",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), manualRetry);
            if (manualRetry) {
                // 人工重发是一条独立业务指令；抢占冲突必须让 MQ 重投，不能静默丢失用户操作。
                throw new IllegalStateException("merchant notification manual retry claim conflict");
            }
            return false;
        }
        int processingVersion = task.getVersion() == null ? 1 : task.getVersion() + 1;
        int attemptNo = task.getLastAttemptNo() == null ? 1 : task.getLastAttemptNo() + 1;
        log.info("商户回调投递开始, event: DATA_MERCHANT_NOTIFY_ATTEMPT_START, traceId: {}, notifyId: {}, transactionId: {}, operationId: {}, merchantId: {}, merchantOrderNo: {}, targetUrlHash: {}, attemptNo: {}, maxRetryCount: {}, payloadLength: {}, signType: {}",
                TraceContext.getTraceId(),
                task.getNotifyId(),
                task.getTransactionId(),
                task.getOperationId(),
                task.getMerchantId(),
                task.getMerchantOrderNo(),
                task.getTargetUrlHash(),
                attemptNo,
                safeMaxRetry(task),
                task.getPayloadJsonMasked() == null ? 0 : task.getPayloadJsonMasked().length(),
                task.getSignType());

        MerchantCallbackAttemptResult result = callbackHttpExecutor.execute(task, attemptNo, callbackEventId);
        LocalDateTime finishedTime = LocalDateTime.now();
        DataMerchantNotificationLogDO attemptLog = buildNotifyLog(
                task, attemptNo, result, callbackEventId, manualRetry, beginTime, finishedTime);
        if (result.success()) {
            attemptCommitService.recordSuccess(task, processingVersion, attemptLog, finishedTime);
            log.info("商户回调投递成功, event: DATA_MERCHANT_NOTIFY_ATTEMPT_END, traceId: {}, notifyId: {}, transactionId: {}, merchantId: {}, attemptNo: {}, httpStatus: {}, success: true, responseSummary: {}, durationMs: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                    attemptNo, result.httpStatus(), result.responseAudit(),
                    durationMillis(beginTime, finishedTime));
            return true;
        }

        int effectiveMaxRetry = safeMaxRetry(task);
        boolean exhausted = attemptNo >= effectiveMaxRetry;
        boolean permanentlyRejected = !result.retryable();
        MerchantNotificationStatus nextStatus = manualRetry || exhausted || permanentlyRejected
                ? STATUS_CLOSED : STATUS_FAILED;
        LocalDateTime nextRetryTime = manualRetry || exhausted || permanentlyRejected
                ? null : nextRetryTime(finishedTime, attemptNo);
        attemptCommitService.recordFailure(
                task,
                processingVersion,
                nextStatus.name(),
                nextRetryTime,
                safeLength(result.errorMessage(), MAX_FAIL_REASON_LENGTH),
                finishedTime,
                attemptNo,
                attemptLog);
        log.warn("商户回调投递失败, event: DATA_MERCHANT_NOTIFY_ATTEMPT_END, traceId: {}, notifyId: {}, transactionId: {}, merchantId: {}, attemptNo: {}, httpStatus: {}, failureCode: {}, nextStatus: {}, exhausted: {}, nextRetryTime: {}, failureReason: {}, durationMs: {}",
                TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                attemptNo, result.httpStatus(), result.failureCode(), nextStatus, exhausted, nextRetryTime,
                safeLength(result.errorMessage(), 200), durationMillis(beginTime, finishedTime));
        return false;
    }

    /**
     * 构造一次脱敏通知尝试日志，由独立事务服务与任务状态和重试 Outbox 原子提交。
     *
     * @param task 当前通知任务快照
     * @param attemptNo 当前尝试序号
     * @param result HTTP 执行安全结果
     * @param callbackEventId 人工重试事件号；自动投递时为空
     * @param manualRetry 是否为人工重试
     * @param beginTime 本次尝试开始时间
     * @param finishedTime 本次尝试结束时间
     * @return 可持久化的脱敏尝试日志
     */
    private DataMerchantNotificationLogDO buildNotifyLog(DataMerchantNotificationTaskDO task,
                                                          int attemptNo,
                                                          MerchantCallbackAttemptResult result,
                                                          String callbackEventId,
                                                          boolean manualRetry,
                                                          LocalDateTime beginTime,
                                                          LocalDateTime finishedTime) {
        DataMerchantNotificationLogDO logDO = new DataMerchantNotificationLogDO();
        logDO.setNotifyLogId(notificationLogId(task.getNotifyId(), attemptNo, manualRetry, callbackEventId));
        logDO.setNotifyId(task.getNotifyId());
        logDO.setCallbackEventId(manualRetry ? callbackEventId : null);
        logDO.setDeliveryMode(manualRetry ? "MANUAL" : "AUTO");
        logDO.setTransactionId(task.getTransactionId());
        logDO.setOperationId(task.getOperationId());
        logDO.setMerchantId(task.getMerchantId());
        logDO.setAttemptNo(attemptNo);
        logDO.setTargetUrlHash(task.getTargetUrlHash());
        logDO.setHttpStatus(result.httpStatus());
        logDO.setRequestHeaderJsonMasked(JsonUtils.toJsonString(
                auditHeaders(task, attemptNo,
                        StringUtils.hasText(callbackEventId) ? callbackEventId : result.eventId())));
        logDO.setRequestBodyJsonMasked(result.requestAuditBody());
        logDO.setResponseBodyJsonMasked(result.responseAudit());
        logDO.setSuccess(result.success() ? 1 : 0);
        logDO.setErrorMessage(safeLength(result.errorMessage(), 1_024));
        logDO.setNotifyTime(beginTime);
        logDO.setDurationMillis(durationMillis(beginTime, finishedTime));
        fillTransactionTime(logDO, task.getTransactionDateTime());
        logDO.setCreateTime(finishedTime);
        return logDO;
    }

    /**
     * 使用通知业务身份生成稳定日志 ID，避免多实例或进程重启后的本地序列碰撞。
     *
     * @param notifyId 通知任务唯一标识
     * @param attemptNo 当前自动或人工投递次数
     * @param manualRetry 是否为人工重试
     * @param callbackEventId 人工重试事件标识；自动投递时允许为空
     * @return 带通知日志前缀的稳定 UUID 文本
     */
    static String notificationLogId(String notifyId,
                                    int attemptNo,
                                    boolean manualRetry,
                                    String callbackEventId) {
        String deliveryMode = manualRetry ? "MANUAL" : "AUTO";
        String source = notifyId + ":" + attemptNo + ":" + deliveryMode + ":"
                + (callbackEventId == null ? "" : callbackEventId);
        return NOTIFICATION_LOG_PREFIX + "-"
                + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构造允许写入审计表的强类型请求头摘要。
     *
     * @param task 当前通知任务
     * @param attemptNo 当前尝试序号
     * @param eventId 本次回调稳定事件号
     * @return 不含真实认证令牌的请求头投影
     */
    private MerchantCallbackAuditHeaders auditHeaders(DataMerchantNotificationTaskDO task,
                                                       int attemptNo,
                                                       String eventId) {
        return new MerchantCallbackAuditHeaders(
                "Bearer ***",
                "application/json; charset=UTF-8",
                MerchantCallbackRequestFactory.CALLBACK_VERSION,
                String.valueOf(attemptNo),
                eventId,
                task.getNotifyId(),
                task.getTransactionId());
    }

    /**
     * 先查询单季度有界候选，再逐条 CAS 回收超时 PROCESSING 任务。
     *
     * <p>恢复只撤销本地抢占，不把结果解释为商户未收到。自动投递使用稳定 notifyId，
     * 后续至少一次重发仍由商户按事件号去重。</p>
     *
     * @param beginTime 交易季度起点
     * @param now 当前恢复时间
     */
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
            log.warn("商户通知超时抢占已恢复, event: DATA_MERCHANT_NOTIFY_PROCESSING_RECOVERED, traceId: {}, quarterBegin: {}, staleBefore: {}, recoveredCount: {}",
                    TraceContext.getTraceId(), beginTime, staleBefore, recovered);
        }
    }

    /**
     * 按协议计算当前自动尝试失败后的下一次通知时间。
     *
     * @param baseTime 当前尝试结束时间
     * @param attemptNo 当前尝试序号
     * @return 下一次到期时间
     */
    private LocalDateTime nextRetryTime(LocalDateTime baseTime, int attemptNo) {
        return baseTime.plus(retryDelayAfterAttempt(attemptNo));
    }

    /**
     * 返回任务有效最大自动尝试次数，历史大值也强制收敛到协议上限。
     *
     * @param task 通知任务
     * @return 1 至 5 之间的最大尝试次数
     */
    private int safeMaxRetry(DataMerchantNotificationTaskDO task) {
        return task.getMaxRetryCount() == null || task.getMaxRetryCount() <= 0
                ? 1
                : Math.min(task.getMaxRetryCount(), AUTOMATIC_DELIVERY_LIMIT);
    }

    /**
     * 返回第 1 至第 4 次失败后的固定等待时长，第 5 次失败不再调用此方法。
     *
     * @param attemptNo 已完成的尝试序号
     * @return 下一次重试前的等待时长
     */
    static Duration retryDelayAfterAttempt(int attemptNo) {
        return switch (attemptNo) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            case 3 -> Duration.ofMinutes(10);
            case 4 -> Duration.ofMinutes(30);
            default -> throw new IllegalArgumentException("merchant notification attempt has no next retry");
        };
    }

    /**
     * 填充通知日志的业务时间、UTC 时间和时区。
     *
     * @param target 通知尝试日志
     * @param transactionDateTime 原始交易时间
     */
    private void fillTransactionTime(DataMerchantNotificationLogDO target, LocalDateTime transactionDateTime) {
        target.setTransactionDateTime(transactionDateTime);
        target.setTransactionUtcTime(transactionDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime());
        target.setTransactionTimeZone(DEFAULT_TIME_ZONE);
    }

    /**
     * 返回交易时间所在季度的半开区间起点。
     *
     * @param transactionDateTime 原始交易分片时间
     * @return 所在季度第一天零点
     */
    private LocalDateTime quarterBegin(LocalDateTime transactionDateTime) {
        int firstMonth = ((transactionDateTime.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDateTime.of(transactionDateTime.getYear(), firstMonth, 1, 0, 0);
    }

    /**
     * 校验交易分表时间。
     *
     * @param transactionDateTime 调用方提供的分片时间
     */
    private void validateTransactionDateTime(LocalDateTime transactionDateTime) {
        if (transactionDateTime == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
    }

    /**
     * 校验数据库任务携带可精确路由的交易分片时间，并在任何状态更新前失败。
     *
     * @param task 数据库任务快照
     */
    private void validateTaskTransactionDateTime(DataMerchantNotificationTaskDO task) {
        if (task == null || task.getTransactionDateTime() == null) {
            throw new IllegalStateException("merchant notification task transaction_date_time is required");
        }
        if (task.getId() == null || task.getVersion() == null) {
            throw new IllegalStateException("merchant notification task id and version are required for CAS");
        }
    }

    /**
     * 截断数据库和日志文本，空值保持为空。
     *
     * @param value 原始非敏感文本
     * @param maxLength 最大字符数
     * @return 截断后的文本
     */
    private String safeLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 计算本次 HTTP 尝试耗时并限制为数据库整数范围。
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 非负毫秒数
     */
    private int durationMillis(LocalDateTime startTime, LocalDateTime endTime) {
        long millis = Duration.between(startTime, endTime).toMillis();
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) millis);
    }

    /**
     * 计算消费或批量处理耗时。
     *
     * @param startNanos 单调时钟起点
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

}
