package com.scott.payment.clearing.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证历史标签本金修复表具备资金幂等、状态一致性、后检和保守回滚契约。
 */
class LabelPrincipalRepairSchemaContractTest {

    @Test
    void migrationShouldDefineDurableIdempotencyMoneyPrecisionAndStateChecks() throws IOException {
        String migration = read("docs/sql/20261106_02_label_principal_repair_migration.sql");

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS clearing_label_principal_repair")
                .contains("CREATE TABLE IF NOT EXISTS clearing_label_principal_repair_dependency_audit")
                .contains("CREATE TABLE IF NOT EXISTS clearing_label_principal_repair_reserve_state_audit")
                .contains("principal_amount_before DECIMAL(24,8) NOT NULL")
                .contains("label_amount DECIMAL(24,8) NOT NULL")
                .contains("reserve_fact_count TINYINT NOT NULL")
                .contains("reserve_snapshot_hash CHAR(64) NOT NULL")
                .contains("reserve_state_count TINYINT NOT NULL")
                .contains("reserve_state_snapshot_hash CHAR(64) NOT NULL")
                .contains("dependency_count TINYINT NOT NULL")
                .contains("dependency_snapshot_hash CHAR(64) NOT NULL")
                .contains("new_candidate_id BIGINT NULL")
                .contains("transaction_date_time DATETIME(3) NOT NULL")
                .contains("UNIQUE KEY uk_label_principal_repair_no (repair_no)")
                .contains("UNIQUE KEY uk_label_principal_repair_request (request_key)")
                .contains("idx_label_principal_repair_transaction")
                .contains("idx_label_principal_repair_status")
                .contains("CONSTRAINT chk_label_principal_repair_value CHECK")
                .contains("CONSTRAINT chk_label_principal_repair_state CHECK")
                .contains("UNIQUE KEY uk_label_principal_dependency_repair (repair_no, dependency_id)")
                .contains("CONSTRAINT chk_label_principal_dependency_audit CHECK")
                .contains("UNIQUE KEY uk_label_principal_reserve_state_repair")
                .contains("CONSTRAINT chk_label_principal_reserve_state_audit CHECK")
                .contains("dependency_type = 'RESERVE_HOLD_POSTED'")
                .contains("review_operator <> submit_operator")
                .contains("new_clearing_revision = expected_clearing_revision + 1")
                .doesNotContain("UPDATE transaction_clearing_detail")
                .doesNotContain("UPDATE settlement_candidate");
    }

    @Test
    void operationalSqlShouldProvideReadOnlyPostcheckAndEmptyTableOnlyRollback() throws IOException {
        String postcheck = read("docs/sql/20261106_03_label_principal_repair_postcheck.sql");
        String rollback = read("docs/sql/20261106_04_label_principal_repair_rollback.sql");

        assertThat(postcheck)
                .contains("missing_table_count")
                .contains("invalid_column_count")
                .contains("missing_contract_count")
                .contains("invalid_repair_row_count")
                .contains("invalid_dependency_audit_row_count")
                .contains("missing_or_unexpected_dependency_audit_count")
                .contains("invalid_reserve_state_audit_row_count")
                .contains("missing_or_unexpected_reserve_state_audit_count")
                .doesNotContain("DELETE FROM", "UPDATE clearing_label_principal_repair");
        assertThat(rollback)
                .contains("SELECT COUNT(*) INTO repair_row_count")
                .contains("SELECT COUNT(*) INTO dependency_audit_row_count")
                .contains("SELECT COUNT(*) INTO reserve_state_audit_row_count")
                .contains("SIGNAL SQLSTATE '45000'")
                .contains("use a reviewed forward-fix")
                .contains("DROP TABLE clearing_label_principal_repair_dependency_audit")
                .contains("DROP TABLE clearing_label_principal_repair_reserve_state_audit")
                .contains("DROP TABLE clearing_label_principal_repair");
        assertThat(rollback.indexOf("DROP TABLE clearing_label_principal_repair_dependency_audit"))
                .isLessThan(rollback.indexOf("DROP TABLE clearing_label_principal_repair;"));
        assertThat(rollback.indexOf("DROP TABLE clearing_label_principal_repair_reserve_state_audit"))
                .isLessThan(rollback.indexOf("DROP TABLE clearing_label_principal_repair;"));
    }

    private String read(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct);
        }
        return Files.readString(Path.of("..").resolve(relativePath).normalize());
    }
}
