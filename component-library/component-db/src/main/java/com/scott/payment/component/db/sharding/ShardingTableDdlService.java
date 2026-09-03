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
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表表ddl服务契约，位于 公共组件库，声明该业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
 */
@Service
public class ShardingTableDdlService {

    private final JdbcTemplate jdbcTemplate;
    private final ShardingPhysicalTableNameResolver tableNameResolver;
    private final ShardingAutoIncrementValueCalculator autoIncrementValueCalculator;
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
    @DS(DataSourceName.MASTER)
    public ShardingTableInspectionResult createPhysicalTableIfAbsent(TransactionShardingGovernanceProperties properties,
                                                                     TransactionShardingGovernanceProperties.TableRule rule,
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

    /**
     * 校验分表 DDL 的显式安全开关。
     * <p>
     * 自动建表只允许在 MASTER 数据源上从模板表创建新物理表，并禁止自动修改既有表；
     * 任一配置缺失或越界时立即拒绝执行，避免在只读库或错误环境运行 DDL。
     * </p>
     *
     * @param properties 当前环境的分表配置
     * @throws ServiceException 配置缺失、维护未启用或 DDL 边界不满足时抛出
     */
    private void validateDdlEnabled(TransactionShardingGovernanceProperties properties) {
        if (properties == null || properties.getTableMaintenance() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "sharding table maintenance config is required");
        }
        TransactionShardingGovernanceProperties.TableMaintenance maintenance = properties.getTableMaintenance();
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
