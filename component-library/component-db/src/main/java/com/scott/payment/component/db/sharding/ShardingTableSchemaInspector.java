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
 * @description : Sharding Table Schema Inspector 协作组件，位于 公共组件库，封装 shardingtableschemainspector 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
public class ShardingTableSchemaInspector {

    /**
     * jdbc Template，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final JdbcTemplate jdbcTemplate;
    /**
     * table Name Resolver，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 整理inspectsingle表，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param tableName table Name 输入值，参与 表name 的查询、校验、转换、写入或日志摘要
     * @param idColumn ID Column 输入值，参与 IDcolumn 的查询、校验、转换、写入或日志摘要
     * @param shardingColumn sharding Column 输入值，参与 shardingcolumn 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 整理showcreate表，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param tableName table Name 输入值，参与 表name 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 规范化columnexists，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param tableName table Name 输入值，参与 表name 的查询、校验、转换、写入或日志摘要
     * @param columnName column Name 输入值，参与 columnname 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 判断 is bigint auto increment primary key 条件是否成立，用于控制 Sharding Table Schema Inspector 的后续分支。
     * <p>
     * 前置条件：调用方已准备 公共组件库 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param tableName table Name 输入值，参与 tablename 的查询、校验、转换、写入或日志摘要
     * @param idColumn ID Column 输入值，参与 IDcolumn 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
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
     * 解析normalizecreate表，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 公共组件库 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param createTableSql create Table Sql 输入值，参与 create表sql 的查询、校验、转换、写入或日志摘要
     * @param tableName table Name 输入值，参与 表name 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
