package com.scott.payment.payment.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarter;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingSupport
 * @date : 2026-07-14 22:10
 * @email : scott_x@163.com
 * @description : 交易分表路由支撑组件，位于 service-payment 支撑层，统一按 transaction_date_time 和平台 transaction_id 解析交易物理表，避免查询、回调和状态推进各自拼接表名。
 * @status : create
 */
@Component
public class TransactionShardingSupport {

    /**
     * 历史平台交易 ID 前缀。新生成的 transactionId 不再携带业务前缀，但历史数据仍需兼容查询。
     */
    private static final String LEGACY_TRANSACTION_ID_PREFIX = "TX";

    /**
     * 平台内部生命周期关联 ID 前缀，operation_id 中包含原始交易业务时间片段。
     */
    private static final String OPERATION_ID_PREFIX = "OP";

    /**
     * 交易号中的业务时间片段格式，与 PaymentOrderNoGenerator 保持一致。
     */
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS", Locale.ROOT);

    /**
     * 交易号业务时间片段长度。
     */
    private static final int ORDER_TIME_PART_LENGTH = 17;

    /**
     * 季度分表配置。
     */
    private final PaymentQuarterShardingProperties shardingProperties;

    /**
     * 季度解析器。
     */
    private final ShardingQuarterResolver quarterResolver;

    /**
     * 物理表名解析器。
     */
    private final ShardingPhysicalTableNameResolver tableNameResolver;

    /**
     * 创建交易分表支撑组件。
     *
     * @param shardingProperties 季度分表配置
     * @param quarterResolver 季度解析器
     * @param tableNameResolver 物理表名解析器
     */
    public TransactionShardingSupport(PaymentQuarterShardingProperties shardingProperties,
                                      ShardingQuarterResolver quarterResolver,
                                      ShardingPhysicalTableNameResolver tableNameResolver) {
        this.shardingProperties = shardingProperties;
        this.quarterResolver = quarterResolver;
        this.tableNameResolver = tableNameResolver;
    }

    /**
     * 按逻辑表和交易时间解析物理表名。
     *
     * @param logicalTable 交易逻辑表名
     * @param transactionDateTime 交易业务时间
     * @return 已校验的物理表名
     */
    public String physicalTable(String logicalTable, LocalDateTime transactionDateTime) {
        PaymentQuarterShardingProperties.TableRule rule = resolveRule(logicalTable);
        ShardingQuarter quarter = quarterResolver.fromDateTime(transactionDateTime);
        if (!quarterResolver.inRange(rule, quarter)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "transaction_date_time is outside sharding table range");
        }
        return tableNameResolver.physicalTableName(rule, quarter);
    }

    /**
     * 按时间范围计算需要查询的物理表。
     * <p>
     * 起始时间早于分表规则起始季度时自动裁剪到起始季度；结束时间晚于当前时间时自动裁剪到当前时间，避免后台查询扫未来表。
     *
     * @param logicalTable 交易逻辑表名
     * @param beginTime 查询开始时间
     * @param endTime 查询结束时间
     * @return 查询涉及的物理表名列表，按季度倒序排列
     */
    public List<String> physicalTablesInRange(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime) {
        PaymentQuarterShardingProperties.TableRule rule = resolveRule(logicalTable);
        LocalDateTime now = LocalDateTime.now(quarterResolver.zoneId(shardingProperties));
        LocalDateTime safeEnd = endTime == null || endTime.isAfter(now) ? now : endTime;
        LocalDateTime safeBegin = beginTime == null ? quarterStart(quarterResolver.startQuarter(rule)) : beginTime;
        LocalDateTime minBegin = quarterStart(quarterResolver.startQuarter(rule));
        if (safeBegin.isBefore(minBegin)) {
            safeBegin = minBegin;
        }
        if (safeBegin.isAfter(safeEnd)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "query begin time must not be after end time");
        }
        ShardingQuarter beginQuarter = quarterResolver.fromDateTime(safeBegin);
        ShardingQuarter endQuarter = quarterResolver.fromDateTime(safeEnd);
        List<String> tables = new ArrayList<>();
        ShardingQuarter cursor = endQuarter;
        while (cursor.compareTo(beginQuarter) >= 0) {
            if (quarterResolver.inRange(rule, cursor)) {
                tables.add(tableNameResolver.physicalTableName(rule, cursor));
            }
            cursor = previous(cursor);
        }
        return tables;
    }

    /**
     * 从平台交易 ID 中解析交易业务时间。
     *
     * @param transactionId 平台当前交易 ID
     * @return 交易业务时间，无法解析时返回 null
     */
    public LocalDateTime parseTransactionDateTime(String transactionId) {
        LocalDateTime dateTime = parseBusinessDateTime(transactionId, "");
        return dateTime == null ? parseBusinessDateTime(transactionId, LEGACY_TRANSACTION_ID_PREFIX) : dateTime;
    }

    /**
     * 从平台内部生命周期关联 ID 中解析原始交易业务时间。
     *
     * @param operationId 平台内部生命周期关联标识
     * @return 原始交易业务时间，无法解析时返回 null
     */
    public LocalDateTime parseOperationDateTime(String operationId) {
        return parseBusinessDateTime(operationId, OPERATION_ID_PREFIX);
    }

    private LocalDateTime parseBusinessDateTime(String value, String prefix) {
        if (!StringUtils.hasText(value) || value.length() < prefix.length() + ORDER_TIME_PART_LENGTH) {
            return null;
        }
        if (StringUtils.hasText(prefix) && !value.startsWith(prefix)) {
            return null;
        }
        String timePart = value.substring(prefix.length(), prefix.length() + ORDER_TIME_PART_LENGTH);
        try {
            return LocalDateTime.parse(timePart, ORDER_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /**
     * 读取并校验交易分表规则。
     *
     * @param logicalTable 交易逻辑表名
     * @return 分表规则
     */
    public PaymentQuarterShardingProperties.TableRule resolveRule(String logicalTable) {
        if (shardingProperties == null || shardingProperties.getTables() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding tables config is required");
        }
        PaymentQuarterShardingProperties.TableRule rule = shardingProperties.getTables().get(logicalTable);
        if (rule == null) {
            rule = shardingProperties.getTables().values().stream()
                    .filter(item -> logicalTable.equals(item.getLogicalTable()))
                    .findFirst()
                    .orElse(null);
        }
        if (rule == null || Boolean.FALSE.equals(rule.getEnabled())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), logicalTable + " sharding rule is not enabled");
        }
        if (!StringUtils.hasText(rule.getLogicalTable())) {
            rule.setLogicalTable(logicalTable);
        }
        if (!StringUtils.hasText(rule.getTemplateTable())) {
            rule.setTemplateTable(logicalTable);
        }
        if (!StringUtils.hasText(rule.getShardingColumn())) {
            rule.setShardingColumn("transaction_date_time");
        }
        return rule;
    }

    private LocalDateTime quarterStart(ShardingQuarter quarter) {
        int month = (quarter.quarter() - 1) * 3 + 1;
        return LocalDateTime.of(quarter.year(), month, 1, 0, 0, 0, 0);
    }

    private ShardingQuarter previous(ShardingQuarter quarter) {
        if (quarter.quarter() == 1) {
            return new ShardingQuarter(quarter.year() - 1, 4);
        }
        return new ShardingQuarter(quarter.year(), quarter.quarter() - 1);
    }
}
