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
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionEventOutboxRelayScheduler
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 按已发布季度节点双频投递交易 Outbox：最近季度高频、历史季度低频，并独立刷新全量运维指标。
 * @status : create
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

    /** 高频扫描覆盖的最近季度数量，默认当前和上一季度。 */
    private final int recentQuarterCount;

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
            @Value("${payment.transaction.outbox.batch-size:100}") int batchSize,
            @Value("${payment.transaction.outbox.recent-quarter-count:2}") int recentQuarterCount) {
        this(relayService, shardingProperties, batchSize, recentQuarterCount,
                Clock.system(ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID)));
    }

    TransactionEventOutboxRelayScheduler(TransactionEventOutboxRelayService relayService,
                                          TransactionShardingProperties shardingProperties,
                                          int batchSize,
                                          Clock clock) {
        this(relayService, shardingProperties, batchSize, 2, clock);
    }

    TransactionEventOutboxRelayScheduler(TransactionEventOutboxRelayService relayService,
                                          TransactionShardingProperties shardingProperties,
                                          int batchSize,
                                          int recentQuarterCount,
                                          Clock clock) {
        this.relayService = relayService;
        this.shardingProperties = shardingProperties;
        this.batchSize = Math.max(1, batchSize);
        this.recentQuarterCount = Math.max(1, recentQuarterCount);
        this.clock = clock;
    }

    /**
     * 逐季度扫描到期 Outbox 并尝试投递。
     *
     * <p>事件投递结果和重试次数由 Outbox 服务持久化；单次调度不以 Redis 或内存状态判断消息已发送。</p>
     */
    @Scheduled(
            initialDelayString = "${payment.transaction.outbox.initial-delay-ms:10000}",
            fixedDelayString = "${payment.transaction.outbox.recent-fixed-delay-ms:${payment.transaction.outbox.fixed-delay-ms:5000}}")
    public void relayRecent() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalDateTime> publishedQuarters = publishedQuartersNotAfter(now);
        relayQuarters(publishedQuarters.stream().limit(recentQuarterCount).toList(), "recent");
    }

    /** 低频扫描最近窗口之外的全部已发布历史季度，保留长期失败事件补偿能力。 */
    @Scheduled(
            initialDelayString = "${payment.transaction.outbox.historical-initial-delay-ms:60000}",
            fixedDelayString = "${payment.transaction.outbox.historical-fixed-delay-ms:300000}")
    public void relayHistorical() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<LocalDateTime> publishedQuarters = publishedQuartersNotAfter(now);
        relayQuarters(publishedQuarters.stream().skip(recentQuarterCount).toList(), "historical");
    }

    /** 每分钟汇总全部已发布季度的 pending、CLOSED 和最老积压指标。 */
    @Scheduled(
            initialDelayString = "${payment.transaction.outbox.metrics-initial-delay-ms:30000}",
            fixedDelayString = "${payment.transaction.outbox.metrics-fixed-delay-ms:60000}")
    public void refreshMetrics() {
        relayService.refreshMetrics(publishedQuartersNotAfter(LocalDateTime.now(clock)));
    }

    private void relayQuarters(List<LocalDateTime> quarters, String scanType) {
        int published = 0;
        for (LocalDateTime quarter : quarters) {
            published += relayService.publishDueEvents(quarter, batchSize);
        }
        if (published > 0) {
            log.info("event: TRANSACTION_OUTBOX_RELAY_BATCH scanType: {} published: {} scannedQuarterCount: {} batchSize: {}",
                    scanType, published, quarters.size(), batchSize);
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
