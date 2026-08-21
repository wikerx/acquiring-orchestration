package com.scott.payment.data.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionPrimaryRouteScope;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.mapper.DataMerchantNotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationRetryReconciliationService
 * @date : 2026-08-06 12:42
 * @email : scott_x@163.com
 * @description : 低频扫描全部已发布季度的到期通知，仅补发可靠 MQ 事件，不直接访问商户端点
 * @status : create
 */
@Slf4j
@Service
public class MerchantNotificationRetryReconciliationService {

    private static final ZoneId PLATFORM_ZONE_ID = ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID);

    /** 通知任务 Mapper。 */
    private final DataMerchantNotificationMapper notificationMapper;
    /** 主库可靠 Outbox 发布器，用于低频补偿事件。 */
    private final ReliableMqPublisher reliableMqPublisher;
    /** 已验证季度节点。 */
    private final TransactionShardingProperties shardingProperties;
    /** 平台时钟。 */
    private final Clock clock;
    /** PROCESSING 超时秒数。 */
    private final long processingTimeoutSeconds;
    /** 每季度最大恢复候选数。 */
    private final int recoveryBatchLimit;

    /** 创建生产环境对账服务。 */
    @Autowired
    public MerchantNotificationRetryReconciliationService(
            DataMerchantNotificationMapper notificationMapper,
            ReliableMqPublisher reliableMqPublisher,
            TransactionShardingProperties shardingProperties,
            DataMerchantNotificationProperties properties) {
        this(notificationMapper, reliableMqPublisher, shardingProperties,
                Clock.system(PLATFORM_ZONE_ID),
                properties.getProcessingTimeoutSeconds(),
                properties.getRecoveryBatchLimit());
    }

    MerchantNotificationRetryReconciliationService(
            DataMerchantNotificationMapper notificationMapper,
            ReliableMqPublisher reliableMqPublisher,
            TransactionShardingProperties shardingProperties,
            Clock clock,
            long processingTimeoutSeconds,
            int recoveryBatchLimit) {
        this.notificationMapper = notificationMapper;
        this.reliableMqPublisher = reliableMqPublisher;
        this.shardingProperties = shardingProperties;
        this.clock = clock;
        this.processingTimeoutSeconds = Math.max(processingTimeoutSeconds, 1L);
        this.recoveryBatchLimit = Math.max(recoveryBatchLimit, 1);
    }

    /**
     * 恢复超时任务并把仍到期的失败任务重新可靠入 MQ。
     *
     * @param limitPerQuarter 每季度最大补发数量
     * @param requestedTimes 可选季度定位时间；为空时覆盖全部已发布且不晚于当前季度的节点
     * @return 可靠入队事件数量
     */
    @DS(DataSourceName.TRANSACTION)
    public int reconcile(int limitPerQuarter, List<LocalDateTime> requestedTimes) {
        if (limitPerQuarter <= 0) {
            throw new IllegalArgumentException("merchant notification reconcile limit must be positive");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalDateTime> quarters = resolveQuarters(requestedTimes, now);
        int queued = 0;
        for (LocalDateTime quarter : quarters) {
            List<DataMerchantNotificationTaskDO> tasks;
            try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
                recoverStale(quarter, now);
                tasks = notificationMapper.selectDueForNotify(
                        quarter, quarter.plusMonths(3), now, limitPerQuarter);
            }
            for (DataMerchantNotificationTaskDO task : tasks) {
                reliableMqPublisher.publish(
                        MqTopic.PAYMENT_EVENT,
                        MqTag.MERCHANT_NOTIFICATION_RETRY_DUE,
                        retryMessage(task, now));
                queued++;
            }
        }
        log.info("event: DATA_MERCHANT_NOTIFY_RECONCILE_END traceId: {} quarterCount: {} queuedCount: {} limitPerQuarter: {}",
                TraceContext.getTraceId(), quarters.size(), queued, limitPerQuarter);
        return queued;
    }

    /**
     * 精确补发一条已经到期的通知 MQ 命令，不在内部接口线程访问商户端点。
     *
     * @param transactionId 平台交易 ID
     * @param transactionDateTime 交易分片时间
     * @return true 表示找到到期任务并可靠入队，false 表示任务不存在或尚未到期
     */
    @DS(DataSourceName.TRANSACTION)
    public boolean reconcileTransaction(String transactionId, LocalDateTime transactionDateTime) {
        LocalDateTime now = LocalDateTime.now(clock);
        DataMerchantNotificationTaskDO task;
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            task = notificationMapper.selectReadyByTransactionId(transactionId, transactionDateTime, now);
        }
        if (task == null) {
            return false;
        }
        reliableMqPublisher.publish(
                MqTopic.PAYMENT_EVENT,
                MqTag.MERCHANT_NOTIFICATION_RETRY_DUE,
                retryMessage(task, now));
        log.info("event: DATA_MERCHANT_NOTIFY_RECONCILE_TRANSACTION_END traceId: {} transactionId: {} notifyId: {} expectedVersion: {} attemptNo: {}",
                TraceContext.getTraceId(), task.getTransactionId(), task.getNotifyId(), task.getVersion(),
                task.getLastAttemptNo() == null ? 1 : task.getLastAttemptNo() + 1);
        return true;
    }

    /** 逐条 CAS 恢复当前季度超时 PROCESSING 任务。 */
    private void recoverStale(LocalDateTime quarter, LocalDateTime now) {
        LocalDateTime staleBefore = now.minusSeconds(processingTimeoutSeconds);
        List<DataMerchantNotificationTaskDO> candidates = notificationMapper.selectStaleProcessing(
                quarter, quarter.plusMonths(3), staleBefore, recoveryBatchLimit);
        for (DataMerchantNotificationTaskDO candidate : candidates) {
            notificationMapper.recoverStaleProcessingCas(
                    candidate.getId(), candidate.getTransactionDateTime(), candidate.getVersion(),
                    staleBefore, now);
        }
    }

    /** 构造立即到期、由数据库版本最终判定是否执行的补偿事件。 */
    private MerchantNotificationRetryDueMessage retryMessage(DataMerchantNotificationTaskDO task,
                                                              LocalDateTime now) {
        MerchantNotificationRetryDueMessage message = new MerchantNotificationRetryDueMessage();
        message.setMessageId("MNR-JOB-" + UUID.randomUUID());
        message.setCreatedAt(now);
        message.setTraceId(TraceContext.getOrCreateTraceId());
        message.setRetryCount(0);
        message.setNotifyId(task.getNotifyId());
        message.setTransactionId(task.getTransactionId());
        message.setTransactionDateTime(task.getTransactionDateTime());
        message.setExpectedVersion(task.getVersion());
        message.setAttemptNo(task.getLastAttemptNo() == null ? 1 : task.getLastAttemptNo() + 1);
        message.setDeliverAt(now);
        message.setEventType(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);
        return message;
    }

    /** 解析显式季度或全部已发布季度，未来节点不参与扫描。 */
    private List<LocalDateTime> resolveQuarters(List<LocalDateTime> requestedTimes, LocalDateTime now) {
        LocalDateTime currentQuarter = quarterAnchor(now);
        if (requestedTimes != null && !requestedTimes.isEmpty()) {
            return requestedTimes.stream()
                    .map(this::quarterAnchor)
                    .distinct()
                    .filter(quarter -> !quarter.isAfter(currentQuarter))
                    .sorted(Comparator.reverseOrder())
                    .toList();
        }
        return shardingProperties.getPhysicalNodes().stream()
                .map(this::quarterAnchor)
                .filter(quarter -> !quarter.isAfter(currentQuarter))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /** 将 yyyy0Q 节点转换为季度锚点。 */
    private LocalDateTime quarterAnchor(String suffix) {
        if (suffix == null || !suffix.matches("\\d{4}0[1-4]")) {
            throw new IllegalStateException("transaction sharding physical node suffix must use yyyyQQ");
        }
        int year = Integer.parseInt(suffix.substring(0, 4));
        int quarter = Integer.parseInt(suffix.substring(5, 6));
        return LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
    }

    /** 将业务时间归一为季度锚点。 */
    private LocalDateTime quarterAnchor(LocalDateTime value) {
        if (value == null) {
            throw new IllegalArgumentException("transaction date time can not be null");
        }
        int firstMonth = ((value.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDateTime.of(value.getYear(), firstMonth, 1, 0, 0);
    }
}
