package com.scott.payment.payment.schedule;

import com.scott.payment.payment.service.TransactionEventOutboxRelayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 扫描当前及历史季度分表并持续投递交易 Outbox。
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "payment.transaction.outbox",
        name = "relay-enabled",
        havingValue = "true")
public class TransactionEventOutboxRelayScheduler {

    /** 交易 Outbox 到期事件投递服务。 */
    private final TransactionEventOutboxRelayService relayService;

    /** 每个季度分表单次扫描的最大事件数。 */
    private final int batchSize;

    /** 包含当前季度在内向前扫描的季度数量。 */
    private final int lookbackQuarters;

    /** 可替换时钟，用于确定当前及历史季度。 */
    private final Clock clock;

    /**
     * 创建生产环境使用的 Outbox 调度器，批量大小和历史季度范围由部署配置注入。
     *
     * @param relayService 交易 Outbox 到期事件投递服务
     * @param batchSize 每个季度分表单次扫描的最大事件数
     * @param lookbackQuarters 包含当前季度在内向前扫描的季度数量
     */
    @Autowired
    public TransactionEventOutboxRelayScheduler(
            TransactionEventOutboxRelayService relayService,
            @Value("${payment.transaction.outbox.batch-size:100}") int batchSize,
            @Value("${payment.transaction.outbox.lookback-quarters:8}") int lookbackQuarters) {
        this(relayService, batchSize, lookbackQuarters, Clock.systemDefaultZone());
    }

    TransactionEventOutboxRelayScheduler(TransactionEventOutboxRelayService relayService,
                                         int batchSize,
                                         int lookbackQuarters,
                                         Clock clock) {
        this.relayService = relayService;
        this.batchSize = Math.max(1, batchSize);
        this.lookbackQuarters = Math.max(1, lookbackQuarters);
        this.clock = clock;
    }

    /**
     * 逐季度扫描到期 Outbox 并尝试投递。
     *
     * <p>事件投递结果和重试次数由 Outbox 服务持久化；单次调度不以 Redis 或内存状态判断消息已发送。</p>
     */
    @Scheduled(
            initialDelayString = "${payment.transaction.outbox.initial-delay-ms:10000}",
            fixedDelayString = "${payment.transaction.outbox.fixed-delay-ms:5000}")
    public void relay() {
        LocalDateTime now = LocalDateTime.now(clock);
        int published = 0;
        for (int quarter = 0; quarter < lookbackQuarters; quarter++) {
            published += relayService.publishDueEvents(
                    now.minusMonths(quarter * 3L),
                    batchSize);
        }
        if (published > 0) {
            log.info("event: TRANSACTION_OUTBOX_RELAY_BATCH published: {} lookbackQuarters: {} batchSize: {}",
                    published, lookbackQuarters, batchSize);
        }
    }
}
