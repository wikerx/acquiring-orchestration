package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
