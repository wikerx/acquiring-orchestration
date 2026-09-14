package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableSchemaInspectorTest
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 验证分表治理从实时建表元数据读取大号段 AUTO_INCREMENT，避免 MySQL information_schema 缓存造成误判。
 * @status : create
 */
class ShardingTableSchemaInspectorTest {

    /** 用于验证治理表名白名单和 SHOW CREATE TABLE 查询的季度物理表名。 */
    private static final String TABLE_NAME = "transaction_order_202603";
    /** 模板表与季度物理表允许使用不同的 CHECK 约束名。 */
    private static final String TEMPLATE_TABLE = "transaction_clearing_detail";
    /** 季度物理表名，用于验证治理结构等价比较。 */
    private static final String PHYSICAL_TABLE = "transaction_clearing_detail_202603";

    @Test
    @DisplayName("季度约束名和表注释不同但结构等价时应通过")
    void shouldIgnoreQuarterSpecificCheckNamesAndTableComments() {
        String templateCreateTable = createTableSql(
                TEMPLATE_TABLE,
                "chk_clearing_amount_tpl",
                "(`amount` >= 0)",
                "动作级不可变交易清分明细");
        String physicalCreateTable = createTableSql(
                PHYSICAL_TABLE,
                "chk_clearing_amount_202603",
                "(`amount` >= 0)",
                "2026年第3季度动作级交易清分明细");

        ShardingTableInspectionResult result = inspectPhysicalTable(templateCreateTable, physicalCreateTable);

        assertThat(result.isSchemaMatched()).isTrue();
        assertThat(result.getSchemaCheckStatus()).isEqualTo("MATCHED");
    }

    @Test
    @DisplayName("CHECK 条件正文不同时仍应拦截")
    void shouldRejectDifferentCheckConstraintBody() {
        String templateCreateTable = createTableSql(
                TEMPLATE_TABLE,
                "chk_clearing_amount_tpl",
                "(`amount` >= 0)",
                "动作级不可变交易清分明细");
        String physicalCreateTable = createTableSql(
                PHYSICAL_TABLE,
                "chk_clearing_amount_202603",
                "(`amount` > 0)",
                "2026年第3季度动作级交易清分明细");

        ShardingTableInspectionResult result = inspectPhysicalTable(templateCreateTable, physicalCreateTable);

        assertThat(result.isSchemaMatched()).isFalse();
        assertThat(result.getSchemaCheckStatus()).isEqualTo("MISMATCHED");
    }

    @Test
    @DisplayName("索引结构不同时仍应拦截")
    void shouldRejectDifferentIndexDefinition() {
        String templateCreateTable = createTableSql(
                TEMPLATE_TABLE,
                "chk_clearing_amount_tpl",
                "(`amount` >= 0)",
                "动作级不可变交易清分明细");
        String physicalCreateTable = createTableSql(
                PHYSICAL_TABLE,
                "chk_clearing_amount_202603",
                "(`amount` >= 0)",
                "2026年第3季度动作级交易清分明细")
                .replace("KEY `idx_amount` (`amount`)", "KEY `idx_amount` (`transaction_date_time`)");

        ShardingTableInspectionResult result = inspectPhysicalTable(templateCreateTable, physicalCreateTable);

        assertThat(result.isSchemaMatched()).isFalse();
        assertThat(result.getSchemaCheckStatus()).isEqualTo("MISMATCHED");
    }

    @Test
    @DisplayName("字段结构不同时仍应拦截")
    void shouldRejectDifferentColumnDefinition() {
        String templateCreateTable = createTableSql(
                TEMPLATE_TABLE,
                "chk_clearing_amount_tpl",
                "(`amount` >= 0)",
                "动作级不可变交易清分明细");
        String physicalCreateTable = createTableSql(
                PHYSICAL_TABLE,
                "chk_clearing_amount_202603",
                "(`amount` >= 0)",
                "2026年第3季度动作级交易清分明细")
                .replace("`amount` decimal(24,8) NOT NULL", "`amount` decimal(24,6) NOT NULL");

        ShardingTableInspectionResult result = inspectPhysicalTable(templateCreateTable, physicalCreateTable);

        assertThat(result.isSchemaMatched()).isFalse();
        assertThat(result.getSchemaCheckStatus()).isEqualTo("MISMATCHED");
    }

    @Test
    @DisplayName("应从实时建表元数据读取十八位季度号段")
    void shouldReadLargeAutoIncrementFromShowCreateTable() {
        ShardingTableSchemaInspector inspector = inspectorWithCreateTable("""
                CREATE TABLE `transaction_order_202603` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4
                """);

        assertThat(inspector.autoIncrementCurrent(TABLE_NAME)).isEqualTo(202603000000000001L);
    }

    @Test
    @DisplayName("没有自增计数器时应返回空值")
    void shouldReturnNullWhenShowCreateTableHasNoAutoIncrementCounter() {
        ShardingTableSchemaInspector inspector = inspectorWithCreateTable("""
                CREATE TABLE `transaction_order_202603` (
                  `id` bigint NOT NULL,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        assertThat(inspector.autoIncrementCurrent(TABLE_NAME)).isNull();
    }

    @Test
    @DisplayName("超过 signed BIGINT 范围时应拒绝继续治理")
    void shouldRejectAutoIncrementOutsideSignedBigintRange() {
        ShardingTableSchemaInspector inspector = inspectorWithCreateTable("""
                CREATE TABLE `transaction_order_202603` (
                  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
                  PRIMARY KEY (`id`)
                ) ENGINE=InnoDB AUTO_INCREMENT=18446744073709551615 DEFAULT CHARSET=utf8mb4
                """);

        assertThatThrownBy(() -> inspector.autoIncrementCurrent(TABLE_NAME))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("table AUTO_INCREMENT exceeds supported signed BIGINT range");
    }

    private ShardingTableSchemaInspector inspectorWithCreateTable(String createTableSql) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ShardingPhysicalTableNameResolver tableNameResolver = mock(ShardingPhysicalTableNameResolver.class);
        when(tableNameResolver.requireSafeIdentifier(TABLE_NAME, "table")).thenReturn(TABLE_NAME);
        when(jdbcTemplate.queryForList("SHOW CREATE TABLE `" + TABLE_NAME + "`"))
                .thenReturn(List.of(Map.of("Create Table", createTableSql)));
        return new ShardingTableSchemaInspector(jdbcTemplate, tableNameResolver);
    }

    private ShardingTableInspectionResult inspectPhysicalTable(String templateCreateTable,
                                                                String physicalCreateTable) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ShardingPhysicalTableNameResolver tableNameResolver = new ShardingPhysicalTableNameResolver();
        when(jdbcTemplate.queryForObject(contains("information_schema.TABLES"), eq(Integer.class), eq(PHYSICAL_TABLE)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("information_schema.TABLES"), eq(Integer.class), eq(TEMPLATE_TABLE)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("information_schema.COLUMNS"), eq(Integer.class),
                eq(PHYSICAL_TABLE), eq("transaction_date_time"))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("TABLE_COLLATION"), eq(String.class), eq(PHYSICAL_TABLE)))
                .thenReturn("utf8mb4_0900_ai_ci");
        when(jdbcTemplate.queryForList("SHOW CREATE TABLE `" + PHYSICAL_TABLE + "`"))
                .thenReturn(List.of(Map.of("Create Table", physicalCreateTable)));
        when(jdbcTemplate.queryForList(contains("SELECT DATA_TYPE, EXTRA, COLUMN_KEY"),
                eq(PHYSICAL_TABLE), eq("id")))
                .thenReturn(List.of(Map.of(
                        "DATA_TYPE", "bigint",
                        "EXTRA", "auto_increment",
                        "COLUMN_KEY", "PRI")));
        when(jdbcTemplate.queryForList(contains("SELECT DATA_TYPE, DATETIME_PRECISION"),
                eq(PHYSICAL_TABLE), eq("transaction_date_time")))
                .thenReturn(List.of(Map.of(
                        "DATA_TYPE", "datetime",
                        "DATETIME_PRECISION", 3)));
        when(jdbcTemplate.queryForList("SHOW CREATE TABLE `" + TEMPLATE_TABLE + "`"))
                .thenReturn(List.of(Map.of("Create Table", templateCreateTable)));

        TransactionShardingGovernanceProperties.TableRule rule = new TransactionShardingGovernanceProperties.TableRule();
        rule.setLogicalTable(TEMPLATE_TABLE);
        rule.setTemplateTable(TEMPLATE_TABLE);
        rule.setIdColumn("id");
        rule.setShardingColumn("transaction_date_time");
        return new ShardingTableSchemaInspector(jdbcTemplate, tableNameResolver)
                .inspectPhysicalTable(rule, PHYSICAL_TABLE);
    }

    private String createTableSql(String tableName,
                                  String constraintName,
                                  String checkBody,
                                  String tableComment) {
        return """
                CREATE TABLE `%s` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `amount` decimal(24,8) NOT NULL,
                  `transaction_date_time` datetime(3) NOT NULL,
                  PRIMARY KEY (`id`),
                  KEY `idx_amount` (`amount`),
                  CONSTRAINT `%s` CHECK (%s)
                ) ENGINE=InnoDB AUTO_INCREMENT=202603000000000001 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='%s'
                """.formatted(tableName, constraintName, checkBody, tableComment);
    }
}
