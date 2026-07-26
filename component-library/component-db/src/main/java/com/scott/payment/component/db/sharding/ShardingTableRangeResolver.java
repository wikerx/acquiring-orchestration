package com.scott.payment.component.db.sharding;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableRangeResolver
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 季度分表物理表范围解析组件，位于 component-db 分表基础层，统一按逻辑表和分表时间计算安全物理表名。
 * @status : create
 */
@Component
public class ShardingTableRangeResolver {

    /**
     * 季度分表配置。
     */
    private final PaymentQuarterShardingProperties shardingProperties;

    /**
     * 季度解析器。
     */
    private final ShardingQuarterResolver quarterResolver;

    /**
     * 物理表名安全解析器。
     */
    private final ShardingPhysicalTableNameResolver tableNameResolver;

    /**
     * 创建季度分表物理表范围解析组件。
     *
     * @param shardingProperties 季度分表配置
     * @param quarterResolver 季度解析器
     * @param tableNameResolver 物理表名安全解析器
     */
    public ShardingTableRangeResolver(PaymentQuarterShardingProperties shardingProperties,
                                      ShardingQuarterResolver quarterResolver,
                                      ShardingPhysicalTableNameResolver tableNameResolver) {
        this.shardingProperties = shardingProperties;
        this.quarterResolver = quarterResolver;
        this.tableNameResolver = tableNameResolver;
    }

    /**
     * 按逻辑表和分表时间解析单张物理表。
     *
     * @param logicalTable 逻辑表名
     * @param shardingTime 分表时间，通常为 transaction_date_time
     * @return 安全物理表名
     */
    public String physicalTable(String logicalTable, LocalDateTime shardingTime) {
        PaymentQuarterShardingProperties.TableRule rule = resolveRule(logicalTable);
        ShardingQuarter quarter = quarterResolver.fromDateTime(shardingTime);
        if (!quarterResolver.inRange(rule, quarter)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "sharding time is outside table range");
        }
        return tableNameResolver.physicalTableName(rule, quarter);
    }

    /**
     * 按逻辑表和时间范围解析需要访问的物理表。
     * <p>
     * 返回顺序为季度倒序，便于交易查询按时间倒序跨表分页；开始时间早于配置起始季度时会裁剪到起始季度。
     *
     * @param logicalTable 逻辑表名
     * @param beginTime 查询开始时间
     * @param endTime 查询结束时间
     * @return 安全物理表名列表
     */
    public List<String> physicalTablesInRange(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime) {
        PaymentQuarterShardingProperties.TableRule rule = resolveRule(logicalTable);
        LocalDateTime now = LocalDateTime.now(quarterResolver.zoneId(shardingProperties));
        LocalDateTime safeEnd = endTime == null || endTime.isAfter(now) ? now : endTime;
        LocalDateTime minBegin = quarterStart(quarterResolver.startQuarter(rule));
        LocalDateTime safeBegin = beginTime == null || beginTime.isBefore(minBegin) ? minBegin : beginTime;
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
     * 读取并校验逻辑表分表规则。
     *
     * @param logicalTable 逻辑表名
     * @return 已启用的分表规则
     */
    public PaymentQuarterShardingProperties.TableRule resolveRule(String logicalTable) {
        if (!StringUtils.hasText(logicalTable)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "logical table is required");
        }
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
            rule.setTemplateTable(rule.getLogicalTable());
        }
        if (!StringUtils.hasText(rule.getShardingColumn())) {
            rule.setShardingColumn(shardingProperties.getShardingColumn());
        }
        return rule;
    }

    /**
     * 完成 quarter Start 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 ShardingTableRangeResolver 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param quarter quarter 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private LocalDateTime quarterStart(ShardingQuarter quarter) {
        int month = (quarter.quarter() - 1) * 3 + 1;
        return LocalDateTime.of(quarter.year(), month, 1, 0, 0, 0, 0);
    }

    /**
     * 完成 previous 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 ShardingTableRangeResolver 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param quarter quarter 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private ShardingQuarter previous(ShardingQuarter quarter) {
        if (quarter.quarter() == 1) {
            return new ShardingQuarter(quarter.year() - 1, 4);
        }
        return new ShardingQuarter(quarter.year(), quarter.quarter() - 1);
    }
}
