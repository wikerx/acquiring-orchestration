package com.scott.payment.settlement.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalProjectionSqlContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 冲正 Maker-Checker 和交易三层结算投影迁移合同。
 * @status : create
 */
class SettlementReversalProjectionSqlContractTest {

    @Test
    void migrationShouldProtectReversalIdempotencyAndProjectionPrecision() throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/20260831_05_settlement_reversal_projection_migration.sql");

        assertThat(migration).contains(
                "CREATE TABLE settlement_reversal_daily_sequence",
                "CREATE TABLE settlement_reversal_order",
                "reversal_order_no VARCHAR(20)",
                "UNIQUE KEY uk_settlement_reversal_create_request (create_request_key)",
                "UNIQUE KEY uk_settlement_reversal_decision_request (decision_request_key)",
                "UNIQUE KEY uk_settlement_reversal_batch (reversal_batch_no)",
                "UNIQUE KEY uk_settlement_reversal_active_original (active_original_batch_no)",
                "submitted_by_account_id <> decided_by_account_id",
                "ALTER TABLE transaction_operation_202603",
                "ALTER TABLE transaction_operation_202604",
                "ALTER TABLE transaction_order_202603",
                "ALTER TABLE transaction_order_202604",
                "ALTER TABLE transaction_finance_state_202603",
                "ALTER TABLE transaction_finance_state_202604",
                "settlement_amount DECIMAL(24,8)",
                "settlement_rate DECIMAL(24,12)",
                "settlement_transaction_id VARCHAR(64)",
                "settlement_transaction_date_time DATETIME(3)",
                "ALTER TABLE settlement_projection_task",
                "REVERSE不得替换为冲正日期");
        assertThat(migration.toLowerCase()).doesNotContain(
                "double", " float", "reconciliation_status", "nacos", "yml");
    }

    @Test
    void postcheckShouldCoverAuditRealCandidatesAndAllProjectionFields() throws IOException {
        String postcheck = readRepositoryFile(
                "docs/sql/20260831_06_settlement_reversal_projection_postcheck.sql");

        assertThat(postcheck).contains(
                "missing_reversal_table_count",
                "invalid_settlement_decimal_shape_count",
                "duplicate_active_original_batch_count",
                "invalid_approved_reversal_batch_count",
                "invalid_reversal_fund_ledger_audit_count",
                "ledger.operator_id <=> reversal_order.submitted_by_account_id",
                "ledger.reviewer_id <=> reversal_order.decided_by_account_id",
                "non_transaction_candidate_projection_count",
                "relation.source_type <> 'CLEARING_REVISION'",
                "invalid_finance_operation_settlement_projection_count",
                "invalid_order_latest_action_settlement_projection_count",
                "invalid_settlement_rate_source_count",
                "finance.settlement_status = 'REVERSED' THEN projected_batch.original_batch_no",
                "operation.settlement_amount <=> finance.settlement_amount",
                "operation.settlement_rate <=> finance.settlement_rate",
                "operation.settlement_date <=> finance.settlement_date",
                "operation.settlement_batch_no <=> finance.settlement_batch_no");
        assertThat(postcheck).doesNotContain(
                "ledger.maker_account_id", "ledger.checker_account_id", "SELECT * FROM transaction_");
    }

    @Test
    void baselineShouldKeepTemplateAndQuarterlySettlementProjectionColumnsAligned() throws IOException {
        String baseline = readRepositoryFile("docs/sql/payment_acquiring_表结构.sql");

        for (String table : List.of(
                "transaction_finance_state", "transaction_finance_state_202603",
                "transaction_finance_state_202604", "transaction_operation",
                "transaction_operation_202603", "transaction_operation_202604",
                "transaction_order", "transaction_order_202603", "transaction_order_202604")) {
            String definition = tableDefinition(baseline, table);
            assertThat(definition).contains(
                    "`settlement_status` varchar(32)",
                    "`settlement_currency` char(3)",
                    "`settlement_amount` decimal(24,8)",
                    "`settlement_rate` decimal(24,12)",
                    "`settlement_date` date",
                    "`settlement_batch_no` varchar(19)");
            if (table.startsWith("transaction_order")) {
                assertThat(definition).contains(
                        "`settlement_transaction_id` varchar(64)",
                        "`settlement_transaction_date_time` datetime(3)");
            }
        }
    }

    private String tableDefinition(String schema, String table) {
        String marker = "CREATE TABLE `" + table + "` (";
        int start = schema.indexOf(marker);
        assertThat(start).as("table %s must exist", table).isGreaterThanOrEqualTo(0);
        int end = schema.indexOf("\n) ENGINE=", start);
        assertThat(end).as("table %s definition must terminate", table).isGreaterThan(start);
        return schema.substring(start, end);
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
