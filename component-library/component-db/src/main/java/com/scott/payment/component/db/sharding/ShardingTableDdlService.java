package com.scott.payment.component.db.sharding;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableDdlService
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingTableDdlService 服务契约，用于声明业务能力、调用边界和返回结果约束，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class ShardingTableDdlService {

    /**
     * jdbc Template 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JdbcTemplate jdbcTemplate;
    /**
     * table Name Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ShardingPhysicalTableNameResolver tableNameResolver;
    /**
     * auto Increment Value Calculator 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ShardingAutoIncrementValueCalculator autoIncrementValueCalculator;
    /**
     * schema Inspector 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
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
