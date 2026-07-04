package com.scott.payment.component.db.sharding;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableDdlService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sharding Table Ddl 服务契约，位于 component-library/component-db 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class ShardingTableDdlService {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JdbcTemplate jdbcTemplate;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final ShardingPhysicalTableNameResolver tableNameResolver;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final ShardingAutoIncrementValueCalculator autoIncrementValueCalculator;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final ShardingTableSchemaInspector schemaInspector;

    /**
     * 创建分表 DDL 服务。
     *
     * @param jdbcTemplate                 JDBC 执行入口
     * @param tableNameResolver            表名安全校验器
     * @param autoIncrementValueCalculator AUTO_INCREMENT 计算器
     * @param schemaInspector              表结构检查器
     */
    public ShardingTableDdlService(JdbcTemplate jdbcTemplate,
                                   ShardingPhysicalTableNameResolver tableNameResolver,
                                   ShardingAutoIncrementValueCalculator autoIncrementValueCalculator,
                                   ShardingTableSchemaInspector schemaInspector) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableNameResolver = tableNameResolver;
        this.autoIncrementValueCalculator = autoIncrementValueCalculator;
        this.schemaInspector = schemaInspector;
    }

    /**
     * 从模板表创建目标物理表。
     *
     * <p>如果目标表已存在，则不会重复创建；调用方可继续根据检查结果登记为 skipped。</p>
     *
     * @param properties 分表配置
     * @param rule       单表分表规则
     * @param quarter    目标季度
     * @return 创建后或已存在目标表的检查结果
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param properties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param rule 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param quarter 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DS(DataSourceName.MASTER)
    public ShardingTableInspectionResult createPhysicalTableIfAbsent(PaymentQuarterShardingProperties properties,
                                                                     PaymentQuarterShardingProperties.TableRule rule,
                                                                     ShardingQuarter quarter) {
        validateDdlEnabled(properties);
        String templateTable = tableNameResolver.templateTableName(rule);
        String physicalTable = tableNameResolver.physicalTableName(rule, quarter);
        if (schemaInspector.tableExists(physicalTable)) {
            return schemaInspector.inspectPhysicalTable(rule, physicalTable);
        }
        ShardingTableInspectionResult templateInspection = schemaInspector.inspectTemplate(rule);
        if (!templateInspection.isExists()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "template table does not exist");
        }
        if (!templateInspection.isIdColumnMatched() || !templateInspection.isShardingColumnExists()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "template table schema is not valid");
        }
        jdbcTemplate.execute("CREATE TABLE `" + physicalTable + "` LIKE `" + templateTable + "`");
        if (Boolean.TRUE.equals(properties.getTableMaintenance().getSetAutoIncrementStartValue())) {
            ShardingAutoIncrementRange range = autoIncrementValueCalculator.calculate(properties, quarter);
            jdbcTemplate.execute("ALTER TABLE `" + physicalTable + "` AUTO_INCREMENT = " + range.startValue());
        }
        return schemaInspector.inspectPhysicalTable(rule, physicalTable);
    }

    private void validateDdlEnabled(PaymentQuarterShardingProperties properties) {
        if (properties == null || properties.getTableMaintenance() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding table maintenance config is required");
        }
        PaymentQuarterShardingProperties.TableMaintenance maintenance = properties.getTableMaintenance();
        if (!Boolean.TRUE.equals(maintenance.getEnabled())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "sharding table maintenance is disabled");
        }
        if (!DataSourceName.MASTER.equals(maintenance.getDdlDataSource())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "sharding DDL data source must be master");
        }
        if (!Boolean.TRUE.equals(maintenance.getAllowCreateFromTemplateTable())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "create table from template is disabled");
        }
        if (Boolean.TRUE.equals(maintenance.getAllowAlterExistingTable())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "alter existing sharding table is not allowed");
        }
    }
}
