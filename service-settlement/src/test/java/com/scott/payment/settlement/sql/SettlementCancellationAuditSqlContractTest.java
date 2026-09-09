package com.scott.payment.settlement.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCancellationAuditSqlContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 正式批次取消的不可变审计迁移和只读后检合同。
 * @status : create
 */
class SettlementCancellationAuditSqlContractTest {

    @Test
    void migrationShouldProtectCancellationIdempotencyAndTrustedAudit() throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/20260831_07_settlement_cancellation_audit_migration.sql");

        assertThat(migration).contains(
                "CREATE TABLE settlement_batch_cancellation_audit",
                "UNIQUE KEY uk_settlement_batch_cancellation_batch (settlement_batch_no)",
                "UNIQUE KEY uk_settlement_batch_cancellation_request (request_key)",
                "operator_account_id BIGINT NOT NULL",
                "operator_role_snapshot VARCHAR(1000) NOT NULL",
                "client_ip VARCHAR(64) NOT NULL",
                "user_agent VARCHAR(500) NOT NULL",
                "operation_time DATETIME(3) NOT NULL",
                "cancelled_time DATETIME(3) NOT NULL",
                "released_candidate_count >= 0",
                "operator_account_id = 0",
                "operator_account_name = 'service-settlement'",
                "operator_role_snapshot = 'SYSTEM'");
    }

    @Test
    void postcheckShouldCoverShapeStateAndReleasedCandidates() throws IOException {
        String postcheck = readRepositoryFile(
                "docs/sql/20260831_08_settlement_cancellation_audit_postcheck.sql");

        assertThat(postcheck).contains(
                "missing_cancellation_audit_table_count",
                "missing_or_invalid_cancellation_audit_column_count",
                "missing_cancellation_audit_unique_index_count",
                "invalid_cancellation_audit_value_count",
                "invalid_cancellation_batch_state_count",
                "batch.version <> audit.expected_version + 1",
                "invalid_cancellation_release_count",
                "relation_status = 'RELEASED'",
                "audit.operator_account_id < 0",
                "audit.operator_account_id = 0");
        assertThat(postcheck.toLowerCase()).doesNotContain(
                "double", " float", "reconciliation_status", "nacos", "yml");
    }

    @Test
    void followUpMigrationShouldConstrainAccountZeroToTrustedScheduler() throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/20260901_05_settlement_system_operator_cancellation_migration.sql");
        String postcheck = readRepositoryFile(
                "docs/sql/20260901_06_settlement_system_operator_cancellation_postcheck.sql");

        assertThat(migration).contains(
                "DROP CHECK chk_settlement_batch_cancellation_value",
                "ADD CONSTRAINT chk_settlement_batch_cancellation_value CHECK",
                "operator_account_id > 0",
                "operator_account_id = 0",
                "operator_account_name = 'service-settlement'",
                "operator_role_snapshot = 'SYSTEM'");
        assertThat(postcheck).contains(
                "missing_system_cancellation_check_count",
                "invalid_system_cancellation_operator_count",
                "operator_account_id = 0");
        assertThat(migration.toUpperCase()).doesNotContain(
                "DELETE FROM", "TRUNCATE TABLE");
    }

    @Test
    void recoveryMigrationShouldAddImmutableAuditAndAllowManualReviewCancellation() throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/20260905_03_settlement_batch_recovery_migration.sql");
        String postcheck = readRepositoryFile(
                "docs/sql/20260905_04_settlement_batch_recovery_postcheck.sql");

        assertThat(migration).contains(
                "CREATE TABLE settlement_batch_recovery_audit",
                "UNIQUE KEY uk_settlement_batch_recovery_request (request_key)",
                "recovery_action = 'RETRY_RATE_LOCKING'",
                "batch_status_before = 'MANUAL_REVIEW'",
                "failure_stage_before = 'RATE_LOCKING'",
                "failure_code_before = 'SETTLEMENT_RETRY_EXHAUSTED'",
                "DROP CHECK chk_settlement_batch_cancellation_value",
                "'FAILED_RETRYABLE', 'MANUAL_REVIEW'");
        assertThat(postcheck).contains(
                "missing_recovery_audit_table_count",
                "missing_or_invalid_recovery_audit_column_count",
                "missing_recovery_audit_unique_index_count",
                "invalid_recovery_audit_value_count",
                "invalid_recovery_batch_reference_count",
                "invalid_recovery_relation_count",
                "missing_manual_review_cancellation_check_count");
        assertThat(migration.toUpperCase()).doesNotContain(
                "DELETE FROM", "TRUNCATE TABLE", "UPDATE SETTLEMENT_BATCH SET");
        assertThat(postcheck.toUpperCase()).doesNotContain(
                "DELETE FROM", "TRUNCATE TABLE", "UPDATE SETTLEMENT_BATCH SET");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
