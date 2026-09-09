package com.scott.payment.settlement.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 失败的分段预审决策任务恢复审计迁移和只读后检合同。 */
class SettlementReviewDecisionRecoverySqlContractTest {

    @Test
    void migrationShouldAddImmutableRecoveryAuditWithoutChangingDecisionTaskShape()
            throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/20260908_03_settlement_review_decision_recovery_migration.sql");

        assertThat(migration).contains(
                "CREATE TABLE settlement_review_decision_recovery_audit",
                "task_no VARCHAR(40) NOT NULL",
                "review_order_no VARCHAR(19) NOT NULL",
                "request_key VARCHAR(64) NOT NULL",
                "expected_version BIGINT NOT NULL",
                "task_status_before VARCHAR(24) NOT NULL",
                "failure_code_before VARCHAR(64) NOT NULL",
                "started_time_before DATETIME(3) NULL",
                "completed_time_before DATETIME(3) NULL",
                "UNIQUE KEY uk_settlement_review_decision_recovery_request (request_key)",
                "KEY idx_settlement_review_decision_recovery_task_time",
                "task_status_before = 'FAILED'",
                "operator_account_id > 0");
        assertThat(migration.toUpperCase()).doesNotContain(
                "ALTER TABLE SETTLEMENT_REVIEW_DECISION_TASK",
                "UPDATE SETTLEMENT_REVIEW_DECISION_TASK",
                "DELETE FROM", "TRUNCATE TABLE", "DROP TABLE");
    }

    @Test
    void postcheckShouldVerifyShapeValuesAndTaskReference() throws IOException {
        String postcheck = readRepositoryFile(
                "docs/sql/20260908_04_settlement_review_decision_recovery_postcheck.sql");

        assertThat(postcheck).contains(
                "missing_decision_recovery_audit_table_count",
                "missing_or_invalid_decision_recovery_audit_column_count",
                "missing_decision_recovery_audit_unique_index_count",
                "missing_decision_recovery_audit_task_index_count",
                "invalid_decision_recovery_audit_value_count",
                "invalid_decision_recovery_task_reference_count",
                "task.version < audit.expected_version + 1",
                "task.review_order_no <> audit.review_order_no");
        assertThat(postcheck).doesNotContain("audit.recovered_time < audit.operation_time");
        assertThat(postcheck.toUpperCase()).doesNotContain(
                "UPDATE ", "DELETE FROM", "TRUNCATE TABLE", "DROP TABLE");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
