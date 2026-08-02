package com.scott.payment.component.db.sharding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableSchemaInspector
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表治理结构检查器；通过治理直连数据源校验物理表主键、自增号段、分片时间精度、字符集及模板一致性，不执行 DDL。
 * @status : create
 */
@Component
public class ShardingTableSchemaInspector {

    /** 从 SHOW CREATE TABLE 的实时元数据中提取自增计数器，避免 information_schema 统计缓存误报。 */
    private static final Pattern AUTO_INCREMENT_PATTERN = Pattern.compile(
            "\\bAUTO_INCREMENT\\s*=\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE);

    /** 治理直连数据源的只读元数据查询入口。 */
    private final JdbcTemplate jdbcTemplate;
    /** 将规则解析为受控模板/物理表名，并阻断非法标识符进入元数据 SQL。 */
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
    public ShardingTableInspectionResult inspectTemplate(TransactionShardingGovernanceProperties.TableRule rule) {
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
    public ShardingTableInspectionResult inspectPhysicalTable(TransactionShardingGovernanceProperties.TableRule rule,
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
        boolean matched = result.isSchemaMatched()
                && normalizeCreateTable(result.getCreateTableSql(), safePhysicalTable)
                .equals(normalizeCreateTable(showCreateTable(templateTable), templateTable));
        result.setSchemaMatched(matched);
        result.setSchemaCheckStatus(matched ? "MATCHED" : "MISMATCHED");
        if (!matched && (result.getMessage() == null || result.getMessage().isBlank())) {
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
        return parseAutoIncrementCurrent(showCreateTable(safeTableName));
    }

    /**
     * 汇总单张模板表或物理表的结构门禁结果。
     *
     * @param tableName 待检查的安全表名
     * @param idColumn 主键列名
     * @param shardingColumn 分片时间列名
     * @return 包含存在性、主键、时间精度、字符集和实时自增计数器的检查结果
     */
    private ShardingTableInspectionResult inspectSingleTable(String tableName, String idColumn, String shardingColumn) {
        ShardingTableInspectionResult result = new ShardingTableInspectionResult();
        result.setTableName(tableName);
        if (!tableExists(tableName)) {
            result.setExists(false);
            result.setIdColumnMatched(false);
            result.setShardingColumnExists(false);
            result.setShardingColumnPrecisionMatched(false);
            result.setCharsetMatched(false);
            result.setSchemaMatched(false);
            result.setSchemaCheckStatus("SKIPPED");
            result.setMessage("table does not exist");
            return result;
        }
        result.setExists(true);
        String createTableSql = showCreateTable(tableName);
        result.setCreateTableSql(createTableSql);
        result.setAutoIncrementCurrent(parseAutoIncrementCurrent(createTableSql));
        result.setIdColumnMatched(isBigintAutoIncrementPrimaryKey(tableName, idColumn));
        result.setShardingColumnExists(columnExists(tableName, shardingColumn));
        result.setShardingColumnPrecisionMatched(isDatetime3Column(tableName, shardingColumn));
        result.setCharsetMatched(isUtf8mb4Table(tableName));
        result.setSchemaMatched(result.isIdColumnMatched()
                && result.isShardingColumnExists()
                && result.isShardingColumnPrecisionMatched()
                && result.isCharsetMatched());
        result.setSchemaCheckStatus(result.isSchemaMatched() ? "MATCHED" : "MISMATCHED");
        if (!result.isIdColumnMatched()) {
            result.setMessage("id column is not BIGINT AUTO_INCREMENT PRIMARY KEY");
        } else if (!result.isShardingColumnExists()) {
            result.setMessage("sharding column does not exist");
        } else if (!result.isShardingColumnPrecisionMatched()) {
            result.setMessage("sharding column must be DATETIME(3)");
        } else if (!result.isCharsetMatched()) {
            result.setMessage("table charset must be utf8mb4");
        }
        return result;
    }

    /**
     * 校验分片列使用 DATETIME(3)，保证季度边界和毫秒级交易时间不被截断。
     *
     * @param tableName 安全表名
     * @param columnName 分片时间列名
     * @return 列存在且为 DATETIME(3) 时返回 true
     */
    private boolean isDatetime3Column(String tableName, String columnName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT DATA_TYPE, DATETIME_PRECISION
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, tableName, columnName);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> row = rows.get(0);
        return "datetime".equalsIgnoreCase(String.valueOf(row.get("DATA_TYPE")))
                && "3".equals(String.valueOf(row.get("DATETIME_PRECISION")));
    }

    /**
     * 校验物理表字符集为 utf8mb4，避免 Binding 查询发生字符集语义漂移。
     *
     * @param tableName 安全表名
     * @return 表排序规则属于 utf8mb4 时返回 true
     */
    private boolean isUtf8mb4Table(String tableName) {
        String collation = jdbcTemplate.queryForObject("""
                SELECT TABLE_COLLATION
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, String.class, tableName);
        return collation != null && collation.toLowerCase(Locale.ROOT).startsWith("utf8mb4_");
    }

    /**
     * 读取不受 information_schema 统计缓存影响的实时建表元数据。
     *
     * @param tableName 待查询的安全表名
     * @return SHOW CREATE TABLE 返回的建表语句；无结果时返回空串
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
     * 解析 MySQL 实时建表元数据中的 AUTO_INCREMENT 计数器。
     *
     * @param createTableSql SHOW CREATE TABLE 返回的完整建表语句
     * @return 当前自增计数器；表没有自增计数器时返回 null
     * @throws IllegalStateException 自增计数器超过 Java signed BIGINT 支持范围时抛出
     */
    private Long parseAutoIncrementCurrent(String createTableSql) {
        if (createTableSql == null || createTableSql.isBlank()) {
            return null;
        }
        Matcher matcher = AUTO_INCREMENT_PATTERN.matcher(createTableSql);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("table AUTO_INCREMENT exceeds supported signed BIGINT range", exception);
        }
    }

    /**
     * 判断指定列是否存在。
     *
     * @param tableName 安全表名
     * @param columnName 安全列名
     * @return 列存在时返回 true
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
     * 校验主键列使用 MySQL BIGINT AUTO_INCREMENT PRIMARY KEY 契约。
     *
     * @param tableName 安全表名
     * @param idColumn 主键列名
     * @return 主键契约完整匹配时返回 true
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
     * 去除表名、自增计数器和无意义空白差异，用于模板与物理表结构比对。
     *
     * @param createTableSql SHOW CREATE TABLE 返回值
     * @param tableName SQL 中的实际表名
     * @return 可稳定比较的建表语句
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
