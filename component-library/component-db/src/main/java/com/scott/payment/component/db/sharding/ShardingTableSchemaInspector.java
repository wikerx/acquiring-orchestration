package com.scott.payment.component.db.sharding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 分表物理表结构检查器。
 *
 * <p>该组件只执行只读元数据查询，用于确认模板表、目标表、主键自增字段、
 * 分表字段和 AUTO_INCREMENT 当前值。DDL 操作由 {@link ShardingTableDdlService} 负责。</p>
 */
@Component
public class ShardingTableSchemaInspector {

    private final JdbcTemplate jdbcTemplate;
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

    private String showCreateTable(String tableName) {
        String safeTableName = tableNameResolver.requireSafeIdentifier(tableName, "table");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW CREATE TABLE `" + safeTableName + "`");
        if (rows.isEmpty()) {
            return "";
        }
        Object value = rows.get(0).get("Create Table");
        return value == null ? "" : String.valueOf(value);
    }

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
