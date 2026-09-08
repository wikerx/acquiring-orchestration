package com.scott.payment.settlement.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证保证金手动结算扩展只放宽专用任务字段并保留交易任务约束。 */
class SettlementManualReserveReviewSqlContractTest {

    @Test
    void migrationShouldAllowReserveReleaseWithoutTransactionCycleFields() throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/20260908_01_manual_reserve_settlement_review_migration.sql");

        assertThat(migration).contains(
                "ALTER TABLE settlement_manual_review_task",
                "DROP CHECK chk_manual_review_task_value",
                "MODIFY COLUMN initial_delay_unit CHAR(1) NULL",
                "MODIFY COLUMN initial_delay_days INT NULL",
                "MODIFY COLUMN regular_delay_days INT NULL",
                "MODIFY COLUMN settlement_frequency VARCHAR(16) NULL",
                "review_type IN ('REGULAR', 'RESERVE_RELEASE')",
                "review_type = 'REGULAR'",
                "initial_delay_unit IN ('T', 'D')",
                "review_type = 'RESERVE_RELEASE'",
                "initial_delay_unit IS NULL",
                "initial_delay_days IS NULL",
                "regular_delay_days IS NULL",
                "settlement_frequency IS NULL",
                "frequency_day IS NULL");
        assertThat(migration.toUpperCase()).doesNotContain(
                "DELETE FROM", "TRUNCATE TABLE", "DROP TABLE", "UPDATE SETTLEMENT_MANUAL_REVIEW_TASK");
    }

    @Test
    void postcheckShouldVerifyNullableCycleFieldsAndReserveTaskValues() throws IOException {
        String postcheck = readRepositoryFile(
                "docs/sql/20260908_02_manual_reserve_settlement_review_postcheck.sql");

        assertThat(postcheck).contains(
                "information_schema.columns",
                "information_schema.check_constraints",
                "chk_manual_review_task_value",
                "invalid_reserve_manual_tasks",
                "invalid_manual_task_review_types",
                "review_type = 'RESERVE_RELEASE'",
                "review_type NOT IN ('REGULAR', 'RESERVE_RELEASE')");
        assertThat(postcheck.toUpperCase()).doesNotContain(
                "DELETE FROM", "TRUNCATE TABLE", "DROP TABLE", "UPDATE SETTLEMENT_MANUAL_REVIEW_TASK");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
