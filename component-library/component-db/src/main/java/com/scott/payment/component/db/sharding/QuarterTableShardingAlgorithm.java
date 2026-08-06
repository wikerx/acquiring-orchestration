package com.scott.payment.component.db.sharding;

import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.scott.payment.component.core.exception.TransactionDataUnavailableException;
import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : QuarterTableShardingAlgorithm
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : ShardingSphere 标准表分片算法，按 Asia/Shanghai 下的 transaction_date_time 精确或范围路由到已登记季度节点。
 * @status : create
 */
public class QuarterTableShardingAlgorithm implements StandardShardingAlgorithm<Comparable<?>> {

    /** 将带时区时间统一换算到数据库路由时区，禁止各服务按 JVM 默认时区分片。 */
    private ZoneId zoneId = ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID);
    /** 当前发布规则版本，仅用于缺失物理节点时提供可追踪的失败信息。 */
    private String ruleVersion = "unpublished";

    /**
     * 初始化不可变路由约束；配置非 Asia/Shanghai 时拒绝装载规则。
     *
     * @param properties ShardingSphere 算法属性，不得包含连接凭证
     */
    @Override
    public void init(Properties properties) {
        String configuredZone = properties.getProperty("database-zone-id", TransactionShardingProperties.REQUIRED_ZONE_ID);
        if (!TransactionShardingProperties.REQUIRED_ZONE_ID.equals(configuredZone)) {
            throw new IllegalArgumentException("quarter sharding zone must be Asia/Shanghai");
        }
        zoneId = ZoneId.of(configuredZone);
        ruleVersion = properties.getProperty("rule-version", "unpublished");
    }

    /**
     * 将单个交易时间精确路由到一个已登记季度节点，节点不存在时拒绝执行 SQL。
     *
     * @param availableTargetNames 已通过治理校验并发布的物理表节点
     * @param shardingValue transaction_date_time 精确值
     * @return 唯一物理表名
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames,
                             PreciseShardingValue<Comparable<?>> shardingValue) {
        LocalDateTime dateTime = toDatabaseDateTime(shardingValue.getValue());
        String expected = shardingValue.getLogicTableName() + "_" + suffix(dateTime);
        return availableTargetNames.stream()
                .filter(expected::equals)
                .findFirst()
                .orElseThrow(() -> new TransactionDataUnavailableException(
                        shardingValue.getLogicTableName(), quarterLabel(dateTime), ruleVersion));
    }

    /**
     * 将时间范围路由到连续季度节点；发布节点中间有缺口时失败，避免返回不完整结果。
     *
     * @param availableTargetNames 已通过治理校验并发布的物理表节点
     * @param shardingValue transaction_date_time 范围
     * @return 按季度升序排列的物理表名
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         RangeShardingValue<Comparable<?>> shardingValue) {
        Range<Comparable<?>> range = shardingValue.getValueRange();
        List<NodeQuarter> candidates = availableTargetNames.stream()
                .map(target -> new NodeQuarter(target, parseQuarter(target, shardingValue.getLogicTableName())))
                .sorted(Comparator.comparing(NodeQuarter::quarter))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("no verified sharding nodes for " + shardingValue.getLogicTableName()
                    + " rule " + ruleVersion);
        }
        ShardingQuarter lower = range.hasLowerBound()
                ? quarter(adjustLower(toDatabaseDateTime(range.lowerEndpoint()), range.lowerBoundType()))
                : candidates.get(0).quarter();
        ShardingQuarter upper = range.hasUpperBound()
                ? quarter(adjustUpper(toDatabaseDateTime(range.upperEndpoint()), range.upperBoundType()))
                : candidates.get(candidates.size() - 1).quarter();
        if (lower.compareTo(upper) > 0) {
            return List.of();
        }
        Set<ShardingQuarter> expected = quartersBetween(lower, upper);
        Set<ShardingQuarter> registered = candidates.stream().map(NodeQuarter::quarter)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!registered.containsAll(expected)) {
            ShardingQuarter missing = expected.stream().filter(item -> !registered.contains(item)).findFirst().orElseThrow();
            throw new TransactionDataUnavailableException(
                    shardingValue.getLogicTableName(), missing.year() + "-Q" + missing.quarter(), ruleVersion);
        }
        return candidates.stream()
                .filter(candidate -> expected.contains(candidate.quarter()))
                .map(NodeQuarter::targetName)
                .toList();
    }

    /**
     * 返回配置中引用的稳定算法类型名。
     *
     * @return 季度时间分片算法类型
     */
    @Override
    public String getType() {
        return "QUARTER_BY_TRANSACTION_TIME";
    }

    /** 下界为开区间时推进一个纳秒，确保季度边界不会误包含前一节点。 */
    private LocalDateTime adjustLower(LocalDateTime value, BoundType boundType) {
        return boundType == BoundType.OPEN ? value.plusNanos(1) : value;
    }

    /** 上界为开区间时回退一个纳秒，确保季度边界不会误包含后一节点。 */
    private LocalDateTime adjustUpper(LocalDateTime value, BoundType boundType) {
        return boundType == BoundType.OPEN ? value.minusNanos(1) : value;
    }

    /**
     * 将 JDBC 和 Java 时间类型统一为数据库本地时间；不接受无法确定时间线的其他类型。
     */
    private LocalDateTime toDatabaseDateTime(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("transaction_date_time must not be null");
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof LocalDate localDate) {
            return localDate.atStartOfDay();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return LocalDateTime.ofInstant(offsetDateTime.toInstant(), zoneId);
        }
        if (value instanceof ZonedDateTime zonedDateTime) {
            return LocalDateTime.ofInstant(zonedDateTime.toInstant(), zoneId);
        }
        if (value instanceof Instant instant) {
            return LocalDateTime.ofInstant(instant, zoneId);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), zoneId);
        }
        if (value instanceof Temporal) {
            throw new IllegalArgumentException("unsupported transaction_date_time temporal type: "
                    + value.getClass().getSimpleName());
        }
        throw new IllegalArgumentException("unsupported transaction_date_time type: " + value.getClass().getSimpleName());
    }

    /** 将交易时间转换为 YYYY0Q 物理表后缀。 */
    private String suffix(LocalDateTime dateTime) {
        return "%04d%02d".formatted(dateTime.getYear(), quarter(dateTime).quarter());
    }

    /** 生成不含物理表名的季度诊断标签。 */
    private String quarterLabel(LocalDateTime dateTime) {
        ShardingQuarter quarter = quarter(dateTime);
        return quarter.year() + "-Q" + quarter.quarter();
    }

    /** 根据数据库本地时间计算所属自然季度。 */
    private ShardingQuarter quarter(LocalDateTime dateTime) {
        return new ShardingQuarter(dateTime.getYear(), (dateTime.getMonthValue() - 1) / 3 + 1);
    }

    /**
     * 从已发布物理节点解析季度，并校验节点属于当前逻辑表且后缀格式合法。
     */
    private ShardingQuarter parseQuarter(String targetName, String logicTable) {
        String prefix = logicTable + "_";
        if (!targetName.startsWith(prefix)) {
            throw new IllegalArgumentException("unexpected target for logic table " + logicTable);
        }
        String value = targetName.substring(prefix.length());
        if (!value.matches("\\d{4}0[1-4]")) {
            throw new IllegalArgumentException("invalid quarterly target suffix for " + logicTable);
        }
        return new ShardingQuarter(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(5, 6)));
    }

    /** 枚举闭区间内的连续季度，用于检测 actualDataNodes 中间缺口。 */
    private Set<ShardingQuarter> quartersBetween(ShardingQuarter lower, ShardingQuarter upper) {
        Set<ShardingQuarter> result = new LinkedHashSet<>();
        ShardingQuarter current = lower;
        while (current.compareTo(upper) <= 0) {
            result.add(current);
            current = current.quarter() == 4
                    ? new ShardingQuarter(current.year() + 1, 1)
                    : new ShardingQuarter(current.year(), current.quarter() + 1);
        }
        return result;
    }

    private record NodeQuarter(String targetName, ShardingQuarter quarter) {
    }
}
