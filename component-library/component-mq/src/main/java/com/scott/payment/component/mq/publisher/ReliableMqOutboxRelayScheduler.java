package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.mq.properties.ReliableMqOutboxProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqOutboxRelayScheduler
 * @date : 2026-08-02 22:20
 * @email : scott_x@163.com
 * @description : 非交易可靠 MQ Outbox 补偿调度器，独立调度到期投递、超时恢复和指标刷新
 * @status : create
 */
@Slf4j
@Component
public class ReliableMqOutboxRelayScheduler {

    /** Relay 服务。 */
    private final ReliableMqOutboxRelayService relayService;
    /** Outbox 配置。 */
    private final ReliableMqOutboxProperties properties;

    /** 创建补偿调度器。 */
    public ReliableMqOutboxRelayScheduler(ReliableMqOutboxRelayService relayService,
                                          ReliableMqOutboxProperties properties) {
        this.relayService = relayService;
        this.properties = properties;
    }

    /**
     * 批量投递已到期消息。
     *
     * <p>正常事件在事务提交后会触发即时投递，本任务只负责进程退出、线程池拒绝或 MQ
     * 短暂故障后的补偿，因此无需每秒持续查询主库。</p>
     */
    @Scheduled(
            initialDelayString = "${acquiring.mq.outbox.relay-initial-delay-ms:5000}",
            fixedDelayString = "${acquiring.mq.outbox.relay-delay-ms:5000}")
    public void relayDue() {
        if (!isRelayEnabled()) {
            return;
        }
        try {
            relayService.relayDue();
        } catch (RuntimeException exception) {
            log.warn("event: RELIABLE_MQ_RELAY_SCAN_FAILED exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 恢复超过处理时限仍停留在 PROCESSING 的消息。
     *
     * <p>恢复判断基于分钟级超时阈值，与到期消息投递拆开后可以低频执行，避免每个服务实例
     * 每秒向主库发送一条通常不更新任何记录的 UPDATE。</p>
     */
    @Scheduled(
            initialDelayString = "${acquiring.mq.outbox.recovery-initial-delay-ms:30000}",
            fixedDelayString = "${acquiring.mq.outbox.recovery-delay-ms:30000}")
    public void recoverStale() {
        if (!isRelayEnabled()) {
            return;
        }
        try {
            relayService.recoverStale();
        } catch (RuntimeException exception) {
            log.warn("event: RELIABLE_MQ_STALE_RECOVERY_FAILED exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }

    /** 低频刷新通用 Outbox 数据库状态 Gauge，避免每秒执行聚合查询。 */
    @Scheduled(
            initialDelayString = "${acquiring.mq.outbox.metrics-initial-delay-ms:30000}",
            fixedDelayString = "${acquiring.mq.outbox.metrics-delay-ms:60000}")
    public void refreshMetrics() {
        if (!isRelayEnabled()) {
            return;
        }
        try {
            relayService.refreshMetrics();
        } catch (RuntimeException exception) {
            log.warn("event: RELIABLE_MQ_OUTBOX_METRICS_REFRESH_FAILED exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }

    /** 全局开关和服务职责开关均开启时，当前实例才执行数据库补偿任务。 */
    private boolean isRelayEnabled() {
        return properties.isRelayEnabled() && properties.isServiceRelayEnabled();
    }
}
