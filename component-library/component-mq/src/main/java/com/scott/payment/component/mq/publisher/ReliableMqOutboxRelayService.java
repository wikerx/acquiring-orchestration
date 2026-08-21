package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.db.outbox.service.ReliableMqOutboxStore;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.component.mq.properties.ReliableMqOutboxProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqOutboxRelayService
 * @date : 2026-08-02 22:20
 * @email : scott_x@163.com
 * @description : 非交易 Outbox Relay，通过数据库 CAS 抢占、发送冻结快照并推进成功或重试终态
 * @status : create
 */
@Slf4j
@Service
public class ReliableMqOutboxRelayService {

    /** 重试等待上限，避免指数退避溢出。 */
    private static final long MAX_RETRY_DELAY_SECONDS = 3600L;
    /** Outbox 持久化服务。 */
    private final ReliableMqOutboxStore outboxStore;
    /** MQ 生产者。 */
    private final MqProducer mqProducer;
    /** Outbox 配置。 */
    private final ReliableMqOutboxProperties properties;

    /** 创建 Outbox Relay。 */
    public ReliableMqOutboxRelayService(ReliableMqOutboxStore outboxStore,
                                        MqProducer mqProducer,
                                        ReliableMqOutboxProperties properties) {
        this.outboxStore = outboxStore;
        this.mqProducer = mqProducer;
        this.properties = properties;
    }

    /**
     * 按事件号投递一条消息。
     *
     * @param eventId 消息唯一编号
     * @return 已投递或此前已成功时返回 true
     */
    public boolean relayEvent(String eventId) {
        ReliableMqOutboxDO event = outboxStore.findByEventId(eventId);
        if (event == null || "CLOSED".equals(event.getEventStatus())) {
            return false;
        }
        if ("SENT".equals(event.getEventStatus())) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        if (outboxStore.claim(event.getId(), event.getVersion(), now) != 1) {
            return alreadySent(eventId);
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
            boolean sent = outboxStore.markSent(event.getId(), claimedVersion, LocalDateTime.now()) == 1
                    || alreadySent(eventId);
            if (!sent) {
                log.error("event: RELIABLE_MQ_MARK_SENT_CAS_FAILED eventId: {} topic: {} expectedVersion: {}",
                        event.getEventId(), event.getTopic(), claimedVersion);
            }
            return sent;
        } catch (RuntimeException exception) {
            recordFailure(event, claimedVersion, exception);
            return false;
        }
    }

    /** 扫描并投递一批已到期消息。 */
    public int relayDue() {
        LocalDateTime now = LocalDateTime.now();
        int successCount = 0;
        for (ReliableMqOutboxDO event : outboxStore.findDue(now, Math.max(properties.getBatchSize(), 1))) {
            if (relayEvent(event.getEventId())) {
                successCount++;
            }
        }
        return successCount;
    }

    /** 恢复超时 PROCESSING 消息。 */
    public int recoverStale() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusSeconds(Math.max(properties.getProcessingTimeoutSeconds(), 1L));
        return outboxStore.recoverStale(staleBefore, now);
    }

    /** 根据当前失败次数推进 RETRY_WAIT 或 CLOSED。 */
    private void recordFailure(ReliableMqOutboxDO event,
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
            log.error("event: RELIABLE_MQ_MARK_FAILED_CAS_FAILED eventId: {} topic: {} expectedVersion: {} exceptionType: {}",
                    event.getEventId(), event.getTopic(), claimedVersion,
                    exception.getClass().getSimpleName());
        }
        log.warn("event: RELIABLE_MQ_RELAY_FAILED eventId: {} topic: {} retryCount: {} closed: {} exceptionType: {} stateRecorded: {}",
                event.getEventId(), event.getTopic(), nextRetryCount, exhausted,
                exception.getClass().getSimpleName(), affectedRows == 1);
    }

    /** 计算受限指数退避秒数。 */
    private long retryDelaySeconds(int retryCount) {
        long base = Math.max(properties.getRetryDelaySeconds(), 1L);
        int shift = Math.min(Math.max(retryCount - 1, 0), 8);
        return Math.min(base * (1L << shift), MAX_RETRY_DELAY_SECONDS);
    }

    /** CAS 冲突后确认消息是否已由其他实例投递成功。 */
    private boolean alreadySent(String eventId) {
        ReliableMqOutboxDO latest = outboxStore.findByEventId(eventId);
        return latest != null && "SENT".equals(latest.getEventStatus());
    }
}
