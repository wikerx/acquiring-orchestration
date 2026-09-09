package com.scott.payment.settlement.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementProcessingModeSqlContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户结算档案处理模式迁移、基础结构和只读后检合同。
 * @status : create
 */
class SettlementProcessingModeSqlContractTest {

    @Test
    void migrationShouldBeIdempotentAndKeepExistingProfilesOnAutoPost() throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/20260901_01_settlement_processing_mode_migration.sql");

        assertThat(migration).contains(
                "information_schema.columns",
                "ADD COLUMN processing_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO_POST'",
                "processing_mode IN ('AUTO_POST', 'AUTO_REVIEW', 'MANUAL')",
                "information_schema.table_constraints",
                "SIGNAL SQLSTATE '45000'");
        assertThat(migration.toLowerCase()).doesNotContain(
                "reconciliation_status", "nacos", " yml", "double", " float");
    }

    @Test
    void baseSchemaAndSeedShouldDeclareProcessingModeExplicitly() throws IOException {
        String schema = readRepositoryFile(
                "docs/sql/20260826_01_settlement_phase_a_schema_draft.sql");
        String seed = readRepositoryFile(
                "docs/sql/20260826_08_settlement_posting_migration.sql");

        assertThat(schema).contains(
                "processing_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO_POST'",
                "processing_mode IN ('AUTO_POST', 'AUTO_REVIEW', 'MANUAL')");
        assertThat(seed).contains(
                "target_currency_exponent, business_time_zone, daily_cutoff_time, processing_mode, profile_status",
                "'Asia/Shanghai', '00:00:00', 'AUTO_POST', 'ACTIVE'");
    }

    @Test
    void postcheckShouldCoverColumnConstraintAndInvalidValues() throws IOException {
        String postcheck = readRepositoryFile(
                "docs/sql/20260901_02_settlement_processing_mode_postcheck.sql");

        assertThat(postcheck).contains(
                "missing_or_invalid_processing_mode_column_count",
                "missing_processing_mode_check_count",
                "invalid_processing_mode_value_count",
                "column_default = 'AUTO_POST'",
                "processing_mode NOT IN ('AUTO_POST', 'AUTO_REVIEW', 'MANUAL')");
        assertThat(postcheck.toLowerCase()).doesNotContain(
                "reconciliation_status", "nacos", " yml", "double", " float");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
