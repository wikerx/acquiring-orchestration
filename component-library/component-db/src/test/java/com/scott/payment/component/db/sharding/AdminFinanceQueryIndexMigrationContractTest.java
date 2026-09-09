package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFinanceQueryIndexMigrationContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 锁定 Admin 清分与结算分页索引迁移的幂等边界和季度模板覆盖。
 * @status : create
 */
class AdminFinanceQueryIndexMigrationContractTest {

    @Test
    void migrationShouldBeIdempotentAndCoverTemplatesAndActiveQuarters() throws IOException {
        String sql = readSql();

        assertThat(sql).contains(
                "CREATE PROCEDURE add_admin_query_index_if_absent",
                "information_schema.statistics",
                "AND index_name = p_index_name",
                "'transaction_finance_state'",
                "'transaction_finance_state_202603'",
                "'transaction_finance_state_202604'",
                "'settlement_batch'",
                "'idx_finance_admin_time'",
                "'idx_finance_admin_merchant_time'",
                "'idx_finance_admin_status_time'",
                "'idx_settlement_admin_date'",
                "'idx_settlement_admin_merchant_date'",
                "'idx_settlement_admin_status_date'",
                "ALGORITHM=INPLACE, LOCK=NONE");
        assertThat(sql.toUpperCase()).doesNotContain(
                "DROP TABLE", "TRUNCATE TABLE", "DELETE FROM", "UPDATE ");
    }

    private String readSql() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("docs/sql")
                    .resolve("20260827_01_admin_clearing_settlement_query_index_migration.sql");
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Admin clearing and settlement query index migration is missing");
    }
}
