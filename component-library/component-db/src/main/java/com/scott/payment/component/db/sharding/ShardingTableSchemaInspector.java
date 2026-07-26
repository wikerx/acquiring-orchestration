package com.scott.payment.component.db.sharding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableSchemaInspector
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingTableSchemaInspector Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class ShardingTableSchemaInspector {

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
     * 创建分表结构检查器。
     *
     * @param jdbcTemplate       JDBC 查询入口
     * @param tableNameResolver  表名安全校验器
     */
    public ShardingTableSchemaInspector(JdbcTemplate jdbcTemplate,
                                        ShardingPhysicalTableNameResolver tableNameResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableNameResolver = tableNameResolver;
    }

    /**
     * 检查模板表结构。
     *
     * @param rule 单表分表规则
     * @return 检查结果
     */
    public ShardingTableInspectionResult inspectTemplate(PaymentQuarterShardingProperties.TableRule rule) {
        String templateTable = tableNameResolver.templateTableName(rule);
        String idColumn = tableNameResolver.idColumnName(rule);
        String shardingColumn = tableNameResolver.requireSafeIdentifier(rule.getShardingColumn(), "sharding column");
        return inspectSingleTable(templateTable, idColumn, shardingColumn);
    }

    /**
     * 检查目标表并与模板表对比。
     *
     * @param rule          单表分表规则
     * @param physicalTable 目标物理表
     * @return 检查结果
     */
    public ShardingTableInspectionResult inspectPhysicalTable(PaymentQuarterShardingProperties.TableRule rule,
                                                              String physicalTable) {
        String safePhysicalTable = tableNameResolver.requireSafeIdentifier(physicalTable, "physical table");
        String templateTable = tableNameResolver.templateTableName(rule);
        String idColumn = tableNameResolver.idColumnName(rule);
        String shardingColumn = tableNameResolver.requireSafeIdentifier(rule.getShardingColumn(), "sharding column");
        ShardingTableInspectionResult result = inspectSingleTable(safePhysicalTable, idColumn, shardingColumn);
        if (!result.isExists()) {
            result.setSchemaCheckStatus("SKIPPED");
            return result;
        }
        if (!tableExists(templateTable)) {
            result.setSchemaMatched(false);
            result.setSchemaCheckStatus("FAILED");
            result.setMessage("template table does not exist");
            return result;
        }
        boolean matched = normalizeCreateTable(result.getCreateTableSql(), safePhysicalTable)
                .equals(normalizeCreateTable(showCreateTable(templateTable), templateTable));
        result.setSchemaMatched(matched);
        result.setSchemaCheckStatus(matched ? "MATCHED" : "MISMATCHED");
        if (!matched) {
            result.setMessage("physical table schema is different from template table");
        }
        return result;
    }

    /**
     * 判断表是否存在。
     *
     * @param tableName 表名
     * @return true 表示存在
     */
    public boolean tableExists(String tableName) {
        String safeTableName = tableNameResolver.requireSafeIdentifier(tableName, "table");
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Integer.class, safeTableName);
        return count != null && count > 0;
    }

    /**
     * 查询表当前 AUTO_INCREMENT 值。
     *
     * @param tableName 表名
     * @return 当前 AUTO_INCREMENT 值，不存在时返回 null
     */
    public Long autoIncrementCurrent(String tableName) {
        String safeTableName = tableNameResolver.requireSafeIdentifier(tableName, "table");
        return jdbcTemplate.queryForObject("""
                SELECT AUTO_INCREMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Long.class, safeTableName);
    }

    /**
     * 完成 inspect Single Table 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 ShardingTableSchemaInspector 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param tableName table Name 输入值，含义由调用方法名称和所属业务对象限定
     * @param idColumn id Column 输入值，含义由调用方法名称和所属业务对象限定
     * @param shardingColumn sharding Column 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private ShardingTableInspectionResult inspectSingleTable(String tableName, String idColumn, String shardingColumn) {
        ShardingTableInspectionResult result = new ShardingTableInspectionResult();
        result.setTableName(tableName);
        if (!tableExists(tableName)) {
            result.setExists(false);
            result.setIdColumnMatched(false);
            result.setShardingColumnExists(false);
            result.setSchemaMatched(false);
            result.setSchemaCheckStatus("SKIPPED");
            result.setMessage("table does not exist");
            return result;
        }
        result.setExists(true);
        result.setCreateTableSql(showCreateTable(tableName));
        result.setAutoIncrementCurrent(autoIncrementCurrent(tableName));
        result.setIdColumnMatched(isBigintAutoIncrementPrimaryKey(tableName, idColumn));
        result.setShardingColumnExists(columnExists(tableName, shardingColumn));
        result.setSchemaMatched(result.isIdColumnMatched() && result.isShardingColumnExists());
        result.setSchemaCheckStatus(result.isSchemaMatched() ? "MATCHED" : "MISMATCHED");
        if (!result.isIdColumnMatched()) {
            result.setMessage("id column is not BIGINT AUTO_INCREMENT PRIMARY KEY");
        } else if (!result.isShardingColumnExists()) {
            result.setMessage("sharding column does not exist");
        }
        return result;
    }

    /**
     * 完成 show Create Table 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 ShardingTableSchemaInspector 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param tableName table Name 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String showCreateTable(String tableName) {
        String safeTableName = tableNameResolver.requireSafeIdentifier(tableName, "table");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW CREATE TABLE `" + safeTableName + "`");
        if (rows.isEmpty()) {
            return "";
        }
        Object value = rows.get(0).get("Create Table");
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 完成 column Exists 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 ShardingTableSchemaInspector 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param tableName table Name 输入值，含义由调用方法名称和所属业务对象限定
     * @param columnName column Name 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    /**
     * 判断 is Bigint Auto Increment Primary Key 条件是否成立，用于控制后续业务分支。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 ShardingTableSchemaInspector 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param tableName table Name 输入值，含义由调用方法名称和所属业务对象限定
     * @param idColumn id Column 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isBigintAutoIncrementPrimaryKey(String tableName, String idColumn) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT DATA_TYPE, EXTRA, COLUMN_KEY
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, tableName, idColumn);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> row = rows.get(0);
        String dataType = String.valueOf(row.get("DATA_TYPE")).toLowerCase(Locale.ROOT);
        String extra = String.valueOf(row.get("EXTRA")).toLowerCase(Locale.ROOT);
        String columnKey = String.valueOf(row.get("COLUMN_KEY")).toUpperCase(Locale.ROOT);
        return "bigint".equals(dataType) && extra.contains("auto_increment") && "PRI".equals(columnKey);
    }

    /**
     * 标准化 normalize Create Table 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 ShardingTableSchemaInspector 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param createTableSql create Table Sql 输入值，含义由调用方法名称和所属业务对象限定
     * @param tableName table Name 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private String normalizeCreateTable(String createTableSql, String tableName) {
        if (createTableSql == null) {
            return "";
        }
        String safeTableName = tableNameResolver.requireSafeIdentifier(tableName, "table");
        return createTableSql
                .replaceFirst("(?i)CREATE TABLE `" + safeTableName + "`", "CREATE TABLE `__TABLE__`")
                .replaceAll("(?i) AUTO_INCREMENT=\\d+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
