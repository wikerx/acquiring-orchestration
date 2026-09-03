package com.scott.payment.clearing.support;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.entity.ClearingPendingMetricsDO;
import com.scott.payment.clearing.entity.ClearingReserveRemainingMetricsDO;
import com.scott.payment.clearing.mapper.ClearingOperationalMetricsMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingOperationalMetricsRefreshService
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 逐个已发布季度读取清分积压和保证金负债，并在全部查询成功后原子刷新 Gauge。 Redis 不参与该链路，任一季度失败时保留上一轮完整指标。
 * @status : update
 */
@Service
public class ClearingOperationalMetricsRefreshService {

    /**
     * 物理节点常量，统一 {@code ClearingOperationalMetricsRefreshService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final Pattern PHYSICAL_NODE = Pattern.compile("\\d{4}0[1-4]");
    /**
     * ISO币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；不允许为空；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private static final Pattern ISO_CURRENCY = Pattern.compile("[A-Z]{3}");

    private final ClearingOperationalMetricsMapper mapper;
    private final TransactionShardingProperties shardingProperties;
    private final ClearingOperationalMetrics metrics;
    private final Clock clock;

    /** 创建使用 UTC 运维时间和固定交易路由时区的生产刷新服务。 */
    @Autowired
    public ClearingOperationalMetricsRefreshService(ClearingOperationalMetricsMapper mapper,
                                                     TransactionShardingProperties shardingProperties,
                                                     ClearingOperationalMetrics metrics) {
        this(mapper, shardingProperties, metrics, Clock.systemUTC());
    }

    /** 包级构造器允许测试固定时钟，不改变生产时区。 */
    ClearingOperationalMetricsRefreshService(ClearingOperationalMetricsMapper mapper,
                                              TransactionShardingProperties shardingProperties,
                                              ClearingOperationalMetrics metrics,
                                              Clock clock) {
        this.mapper = mapper;
        this.shardingProperties = shardingProperties;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** 遍历不晚于当前季度的全部已发布节点，成功后一次性发布聚合结果。 */
    @DS(DataSourceName.TRANSACTION)
    public void refresh() {
        try {
            Instant currentInstant = clock.instant();
            LocalDateTime nowUtc = LocalDateTime.ofInstant(currentInstant, ZoneOffset.UTC);
            LocalDateTime routeNow = LocalDateTime.ofInstant(currentInstant,
                    ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID));
            LocalDateTime currentQuarter = quarterAnchor(routeNow);
            Map<String, Long> pendingCounts = new HashMap<>();
            Map<String, Long> oldestPendingSeconds = new HashMap<>();
            Map<String, BigDecimal> reserveRemaining = new HashMap<>();

            for (LocalDateTime begin : publishedQuarters()) {
                if (begin.isAfter(currentQuarter)) {
                    continue;
                }
                LocalDateTime end = begin.plusMonths(3);
                aggregatePending(mapper.selectPendingByStatus(begin, end, nowUtc),
                        pendingCounts, oldestPendingSeconds);
                aggregateReserve(mapper.selectReserveRemainingByCurrency(begin, end), reserveRemaining);
            }

            metrics.updatePending(pendingCounts, oldestPendingSeconds);
            metrics.updateReserveRemaining(reserveRemaining);
            metrics.recordMetricsRefresh(true);
        } catch (RuntimeException exception) {
            metrics.recordMetricsRefresh(false);
            throw exception;
        }
    }

    /** 只遍历数据库治理表声明已发布的季度，不猜测或广播未来分片。 */
    private List<LocalDateTime> publishedQuarters() {
        return shardingProperties.getPhysicalNodes().stream()
                .map(this::quarterAnchor)
                .distinct()
                .sorted()
                .toList();
    }

    private void aggregatePending(List<ClearingPendingMetricsDO> rows,
                                  Map<String, Long> counts,
                                  Map<String, Long> oldestSeconds) {
        if (rows == null) {
            return;
        }
        for (ClearingPendingMetricsDO row : rows) {
            if (row == null || row.getClearingStatus() == null) {
                throw new IllegalStateException("clearing pending metrics row is invalid");
            }
            long count = nonNegative(row.getPendingCount());
            long oldest = nonNegative(row.getOldestPendingSeconds());
            counts.merge(row.getClearingStatus(), count, Long::sum);
            oldestSeconds.merge(row.getClearingStatus(), oldest, Math::max);
        }
    }

    /** 按 ISO 币种累加剩余保证金，禁止跨币种合并成单一金额。 */
    private void aggregateReserve(List<ClearingReserveRemainingMetricsDO> rows,
                                  Map<String, BigDecimal> amounts) {
        if (rows == null) {
            return;
        }
        for (ClearingReserveRemainingMetricsDO row : rows) {
            String currency = row == null ? null : row.getReserveCurrency();
            BigDecimal amount = row == null ? null : row.getRemainingAmount();
            if (currency == null || !ISO_CURRENCY.matcher(currency).matches()
                    || amount == null || amount.signum() < 0) {
                metrics.recordAmountImbalance(currency);
                throw new IllegalStateException("clearing reserve remaining metrics row is invalid");
            }
            amounts.merge(currency, amount, BigDecimal::add);
        }
    }

    private LocalDateTime quarterAnchor(String suffix) {
        if (suffix == null || !PHYSICAL_NODE.matcher(suffix).matches()) {
            throw new IllegalStateException("transaction sharding physical node suffix must use yyyyQQ");
        }
        int year = Integer.parseInt(suffix.substring(0, 4));
        int quarter = Integer.parseInt(suffix.substring(5, 6));
        return LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
    }

    private LocalDateTime quarterAnchor(LocalDateTime value) {
        int firstMonth = ((value.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDateTime.of(value.getYear(), firstMonth, 1, 0, 0);
    }

    private long nonNegative(Long value) {
        if (value == null || value < 0) {
            throw new IllegalStateException("clearing pending metrics value must be non-negative");
        }
        return value;
    }
}
