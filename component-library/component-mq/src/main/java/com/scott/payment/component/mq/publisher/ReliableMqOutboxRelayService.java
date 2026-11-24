package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.db.outbox.model.ReliableMqOutboxMetricsSnapshot;
import com.scott.payment.component.db.outbox.service.ReliableMqOutboxStore;
import com.scott.payment.component.mq.observability.MqOutboxOperationalMetrics;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.component.mq.properties.ReliableMqOutboxProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 非交易 Outbox Relay，通过数据库 CAS 抢占、发送冻结快照并推进成功或重试终态
 */
@Slf4j
@Service
public class ReliableMqOutboxRelayService {

    /**
     * 重试等待上限，避免指数退避溢出。
     */
    private static final long MAX_RETRY_DELAY_SECONDS = 3600L;
    /**
     * Outbox 持久化服务。
     */
    private final ReliableMqOutboxStore outboxStore;
    /**
     * MQ 生产者。
     */
    private final MqProducer mqProducer;
    /**
     * Outbox 配置。
     */
    private final ReliableMqOutboxProperties properties;
    /**
     * Outbox 低基数运维指标。
     */
    private final MqOutboxOperationalMetrics metrics;
    /**
     * 当前生产服务名，用于共享 Outbox 表的数据隔离。
     */
    private final String producerService;

    /**
     * 创建 Outbox Relay。
     */
    @Autowired
    public ReliableMqOutboxRelayService(ReliableMqOutboxStore outboxStore,
                                        MqProducer mqProducer,
                                        ReliableMqOutboxProperties properties,
                                        MqOutboxOperationalMetrics metrics,
                                        @Value("${spring.application.name}") String producerService) {
        this.outboxStore = outboxStore;
        this.mqProducer = mqProducer;
        this.properties = properties;
        this.metrics = metrics;
        if (!StringUtils.hasText(producerService)) {
            throw new IllegalArgumentException("spring.application.name can not be blank");
        }
        this.producerService = producerService.trim();
    }

    /**
     * 创建不注册指标的 Relay，供纯单元测试直接构造。
     */
    public ReliableMqOutboxRelayService(ReliableMqOutboxStore outboxStore,
                                        MqProducer mqProducer,
                                        ReliableMqOutboxProperties properties,
                                        String producerService) {
        this(outboxStore, mqProducer, properties, MqOutboxOperationalMetrics.noop(), producerService);
    }

    /**
     * 按事件号投递一条消息。
     *
     * @param eventId 消息唯一编号
     * @return 已投递或此前已成功时返回 true
     */
    public boolean relayEvent(String eventId) {
        ReliableMqOutboxDO event = outboxStore.findByEventId(eventId);
        return event != null && producerService.equals(event.getProducerService())
                && dispatch(event) == DispatchOutcome.RELAYED;
    }

    private DispatchOutcome dispatch(ReliableMqOutboxDO event) {
        if ("CLOSED".equals(event.getEventStatus())) {
            return DispatchOutcome.FAILED;
        }
        String eventId = event.getEventId();
        if ("SENT".equals(event.getEventStatus())) {
            return DispatchOutcome.RELAYED;
        }
        LocalDateTime now = LocalDateTime.now();
        if (outboxStore.claim(event.getId(), event.getVersion(), now) != 1) {
            return alreadySent(eventId, event.getProducerService())
                    ? DispatchOutcome.RELAYED : DispatchOutcome.DEFERRED;
        }
        int claimedVersion = event.getVersion() + 1;
        try {
            mqProducer.sendSerialized(
                    event.getTopic(),
                    event.getTag(),
                    event.getEventId(),
                    event.getTraceId(),
                    event.getRetryCount(),
                    event.getPayloadJson());
        } catch (RuntimeException exception) {
            return recordFailure(event, claimedVersion, exception);
        }
        try {
            boolean sent = outboxStore.markSent(event.getId(), claimedVersion, LocalDateTime.now()) == 1
                    || alreadySent(eventId, event.getProducerService());
            if (!sent) {
                log.error("可靠消息发送成功状态 CAS 更新失败, event: RELIABLE_MQ_MARK_SENT_CAS_FAILED, eventId: {}, topic: {}, expectedVersion: {}",
                        event.getEventId(), event.getTopic(), claimedVersion);
            }
            return sent ? DispatchOutcome.RELAYED : DispatchOutcome.DEFERRED;
        } catch (RuntimeException exception) {
            log.error("可靠消息成功回执持久化待确认, event: RELIABLE_MQ_MARK_SENT_UNCERTAIN, eventId: {}, topic: {}, errorType: {}",
                    eventId, event.getTopic(), exception.getClass().getSimpleName());
            return DispatchOutcome.DEFERRED;
        }
    }

    /**
     * 扫描并投递一批已到期消息。
     */
    public int relayDue() {
        long startNanos = System.nanoTime();
        String outcome = "failure";
        LocalDateTime now = LocalDateTime.now();
        int batchSize = Math.max(properties.getBatchSize(), 1);
        try {
            List<ReliableMqOutboxDO> events = outboxStore.findDue(producerService, now, batchSize);
            metrics.recordBatchSize(MqOutboxOperationalMetrics.RELIABLE_OUTBOX, events.size(), batchSize);
            int successCount = 0;
            for (ReliableMqOutboxDO event : events) {
                if (relayEvent(event.getEventId())) {
                    successCount++;
                }
            }
            outcome = "success";
            return successCount;
        } finally {
            metrics.recordRelayDuration(
                    MqOutboxOperationalMetrics.RELIABLE_OUTBOX,
                    outcome,
                    System.nanoTime() - startNanos);
        }
    }

    /**
     * 恢复超时 PROCESSING 消息。
     */
    public int recoverStale() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusSeconds(Math.max(properties.getProcessingTimeoutSeconds(), 1L));
        return outboxStore.recoverStale(producerService, staleBefore, now);
    }

    /** 专用 Job 与常规中继共用消息行的状态和版本抢占。 */
    public CapabilityRecovery recoverPaymentCapabilityDelivery(int requestedLimit, long deadlineEpochMillis) {
        if (!"service-admin".equals(producerService)) {
            throw new IllegalStateException("payment capability relay is owned by service-admin");
        }
        int limit = Math.max(1, Math.min(100, requestedLimit));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusSeconds(Math.max(properties.getProcessingTimeoutSeconds(), 1L));
        if (System.currentTimeMillis() >= deadlineEpochMillis) {
            return new CapabilityRecovery(0, 0, 0, 0, true);
        }
        outboxStore.recoverPaymentCapabilityStale(staleBefore, now, limit);
        if (System.currentTimeMillis() >= deadlineEpochMillis) {
            return new CapabilityRecovery(0, 0, 0, 0, true);
        }
        List<ReliableMqOutboxDO> due = outboxStore.findPaymentCapabilityDue(now, limit);
        int relayed = 0;
        int failed = 0;
        for (ReliableMqOutboxDO event : due) {
            if (System.currentTimeMillis() >= deadlineEpochMillis) {
                break;
            }
            switch (dispatch(event)) {
                case RELAYED -> relayed++;
                case FAILED -> failed++;
                case DEFERRED -> { }
            }
        }
        return new CapabilityRecovery(due.size(), relayed, failed, due.size() - relayed - failed,
                System.currentTimeMillis() >= deadlineEpochMillis);
    }

    public record CapabilityRecovery(int selected, int relayed, int failed, int deferred, boolean budgetExhausted) {
    }

    private enum DispatchOutcome {
        RELAYED, FAILED, DEFERRED
    }

    /**
     * 从数据库刷新 pending、CLOSED 和最老积压 Gauge。
     */
    public void refreshMetrics() {
        try {
            ReliableMqOutboxMetricsSnapshot snapshot = outboxStore.metricsSnapshot(producerService);
            if (snapshot != null) {
                metrics.updateReliable(
                        count(snapshot.getInitCount()),
                        count(snapshot.getProcessingCount()),
                        count(snapshot.getRetryWaitCount()),
                        count(snapshot.getClosedCount()),
                        snapshot.getOldestPendingTime(),
                        LocalDateTime.now());
            }
            metrics.recordSnapshotRefresh(MqOutboxOperationalMetrics.RELIABLE_OUTBOX, "success");
        } catch (RuntimeException exception) {
            metrics.recordSnapshotRefresh(MqOutboxOperationalMetrics.RELIABLE_OUTBOX, "failed");
            throw exception;
        }
    }

    /**
     * 将数据库聚合结果中的空数量转换为零。
     *
     * @param value 数据库聚合数量
     * @return 非空且不改变符号的数量值
     */
    private long count(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 根据当前失败次数推进 RETRY_WAIT 或 CLOSED。
     */
    private DispatchOutcome recordFailure(ReliableMqOutboxDO event,
                               int claimedVersion,
                               RuntimeException exception) {
        int nextRetryCount = event.getRetryCount() + 1;
        boolean exhausted = nextRetryCount >= event.getMaxRetryCount();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRetryTime = exhausted ? null : now.plusSeconds(retryDelaySeconds(nextRetryCount));
        int affectedRows = outboxStore.markFailed(
                event.getId(),
                claimedVersion,
                exhausted ? "CLOSED" : "RETRY_WAIT",
                nextRetryTime,
                exception.getClass().getSimpleName(),
                now);
        if (affectedRows != 1) {
            log.error("可靠消息发送失败状态 CAS 更新失败, event: RELIABLE_MQ_MARK_FAILED_CAS_FAILED, eventId: {}, topic: {}, expectedVersion: {}, exceptionType: {}",
                    event.getEventId(), event.getTopic(), claimedVersion,
                    exception.getClass().getSimpleName());
        }
        log.warn("可靠消息投递失败, event: RELIABLE_MQ_RELAY_FAILED, eventId: {}, topic: {}, retryCount: {}, closed: {}, exceptionType: {}, stateRecorded: {}",
                event.getEventId(), event.getTopic(), nextRetryCount, exhausted,
                exception.getClass().getSimpleName(), affectedRows == 1);
        return affectedRows == 1 ? DispatchOutcome.FAILED : DispatchOutcome.DEFERRED;
    }

    /**
     * 计算受限指数退避秒数。
     */
    private long retryDelaySeconds(int retryCount) {
        long base = Math.max(properties.getRetryDelaySeconds(), 1L);
        int shift = Math.min(Math.max(retryCount - 1, 0), 8);
        return Math.min(base * (1L << shift), MAX_RETRY_DELAY_SECONDS);
    }

    /**
     * CAS 冲突后确认消息是否已由其他实例投递成功。
     */
    private boolean alreadySent(String eventId, String expectedProducer) {
        ReliableMqOutboxDO latest = outboxStore.findByEventId(eventId);
        return latest != null
                && expectedProducer.equals(latest.getProducerService())
                && "SENT".equals(latest.getEventStatus());
    }
}
