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
     * 判断分表时间是否属于逻辑表已启用的季度范围。
     * <p>
     * 后台扫描和补偿任务应在访问物理表前调用该方法，避免对尚未创建或已退出治理范围的
     * 季度执行 SQL；单笔业务路由仍使用 {@link #physicalTable(String, LocalDateTime)} 的强校验。
     *
     * @param logicalTable 逻辑表名
     * @param shardingTime 分表时间
     * @return true 表示该季度允许访问，false 表示超出配置范围
     */
    public boolean isWithinConfiguredRange(String logicalTable, LocalDateTime shardingTime) {
        PaymentQuarterShardingProperties.TableRule rule = resolveRule(logicalTable);
        ShardingQuarter quarter = quarterResolver.fromDateTime(shardingTime);
        return quarterResolver.inRange(rule, quarter);
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
     * 规范化quarterstart，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param quarter quarter 输入值，参与 quarter 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private LocalDateTime quarterStart(ShardingQuarter quarter) {
        int month = (quarter.quarter() - 1) * 3 + 1;
        return LocalDateTime.of(quarter.year(), month, 1, 0, 0, 0, 0);
    }

    /**
     * 规范化previous，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param quarter quarter 输入值，参与 quarter 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ShardingQuarter previous(ShardingQuarter quarter) {
        if (quarter.quarter() == 1) {
            return new ShardingQuarter(quarter.year() - 1, 4);
        }
        return new ShardingQuarter(quarter.year(), quarter.quarter() - 1);
    }
}
