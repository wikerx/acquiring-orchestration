package com.scott.payment.payment.schedule;

import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.payment.service.TransactionEventOutboxRelayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * 按已发布季度节点持续投递交易 Outbox，不使用固定历史季度窗口截断待补偿事件。
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

    /** 已发布规则中的物理节点，用于阻止调度任务访问未创建季度。 */
    private final TransactionShardingProperties shardingProperties;

    /** 每个季度分表单次扫描的最大事件数。 */
    private final int batchSize;

    /** 可替换时钟；生产固定使用交易路由时区，测试可注入。 */
    private final Clock clock;

    /**
     * 创建生产环境使用的 Outbox 调度器。
     *
     * @param relayService 交易 Outbox 到期事件投递服务
     * @param shardingProperties 已发布的交易分片规则
     * @param batchSize 每个季度分表单次扫描的最大事件数
     */
    @Autowired
    public TransactionEventOutboxRelayScheduler(
            TransactionEventOutboxRelayService relayService,
            TransactionShardingProperties shardingProperties,
            @Value("${payment.transaction.outbox.batch-size:100}") int batchSize) {
        this(relayService, shardingProperties, batchSize,
                Clock.system(ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID)));
    }

    TransactionEventOutboxRelayScheduler(TransactionEventOutboxRelayService relayService,
                                          TransactionShardingProperties shardingProperties,
                                          int batchSize,
                                          Clock clock) {
        this.relayService = relayService;
        this.shardingProperties = shardingProperties;
        this.batchSize = Math.max(1, batchSize);
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
        List<LocalDateTime> publishedQuarters = publishedQuartersNotAfter(now);
        int published = 0;
        for (LocalDateTime quarter : publishedQuarters) {
            published += relayService.publishDueEvents(quarter, batchSize);
        }
        if (published > 0) {
            log.info("event: TRANSACTION_OUTBOX_RELAY_BATCH published: {} scannedQuarterCount: {} batchSize: {}",
                    published, publishedQuarters.size(), batchSize);
        }
    }

    /**
     * 将不晚于当前季度的全部已验证节点转换为季度锚点，避免固定回看窗口漏掉长期失败事件。
     *
     * @param now 当前交易路由时间
     * @return 按季度倒序排列的扫描锚点
     */
    private List<LocalDateTime> publishedQuartersNotAfter(LocalDateTime now) {
        LocalDateTime currentQuarter = quarterAnchor(now);
        return shardingProperties.getPhysicalNodes().stream()
                .map(this::quarterAnchor)
                .filter(quarter -> !quarter.isAfter(currentQuarter))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /** 将 yyyy0Q 节点后缀转换为对应季度第一天零点。 */
    private LocalDateTime quarterAnchor(String suffix) {
        if (suffix == null || !suffix.matches("\\d{4}0[1-4]")) {
            throw new IllegalStateException("transaction sharding physical node suffix must use yyyyQQ");
        }
        int year = Integer.parseInt(suffix.substring(0, 4));
        int quarter = Integer.parseInt(suffix.substring(5, 6));
        return LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
    }

    /** 将任意时间归一为所在季度第一天零点。 */
    private LocalDateTime quarterAnchor(LocalDateTime value) {
        int firstMonth = ((value.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDateTime.of(value.getYear(), firstMonth, 1, 0, 0);
    }
}
