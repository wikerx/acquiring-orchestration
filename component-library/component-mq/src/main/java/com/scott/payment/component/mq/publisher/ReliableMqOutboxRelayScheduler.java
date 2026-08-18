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
 * @description : 非交易可靠 MQ Outbox 补偿调度器，恢复超时抢占并批量投递到期消息
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

    /** 周期恢复超时占用并投递已到期消息；失败不终止后续调度。 */
    @Scheduled(fixedDelayString = "${acquiring.mq.outbox.relay-delay-ms:1000}")
    public void relay() {
        if (!properties.isRelayEnabled()) {
            return;
        }
        try {
            relayService.recoverStale();
            relayService.relayDue();
        } catch (RuntimeException exception) {
            log.warn("event: RELIABLE_MQ_RELAY_SCAN_FAILED exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
