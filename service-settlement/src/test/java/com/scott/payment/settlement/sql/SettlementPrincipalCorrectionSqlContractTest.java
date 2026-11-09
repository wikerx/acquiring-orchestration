package com.scott.payment.settlement.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 已入账历史标签本金差额调整迁移、后检和受控回滚合同。
 */
class SettlementPrincipalCorrectionSqlContractTest {

    @Test
    void migrationShouldProtectMoneyIdempotencyAndImmutableSources() throws IOException {
        String migration = read("docs/sql/20261106_05_settlement_principal_correction_migration.sql");

        assertThat(migration).contains(
                "CREATE TABLE settlement_principal_correction_order",
                "CREATE TABLE settlement_principal_correction_item",
                "UNIQUE KEY uk_settlement_principal_correction_submit",
                "UNIQUE KEY uk_settlement_principal_correction_batch",
                "UNIQUE KEY uk_settlement_principal_correction_decision",
                "UNIQUE KEY uk_settlement_principal_correction_ledger",
                "UNIQUE KEY uk_settlement_principal_correction_result",
                "submitted_by_account_id <> decided_by_account_id",
                "REGEXP_LIKE(target_currency, '^[A-Z]{3}$', 'c')",
                "REGEXP_LIKE(original_source_currency, '^[A-Z]{3}$', 'c')",
                "REGEXP_LIKE(label_currency, '^[A-Z]{3}$', 'c')",
                "original_locked_rate_id BIGINT NOT NULL",
                "original_locked_direct_rate DECIMAL(24,12)",
                "label_locked_rate_id BIGINT NOT NULL",
                "label_locked_direct_rate DECIMAL(24,12)",
                "signed_delta_amount DECIMAL(24,8)",
                "projection_status IN (");
        assertThat(migration.toLowerCase()).doesNotContain(
                "update transaction_clearing_detail",
                "update settlement_result_item",
                "update settlement_batch_rate",
                "update merchant_fund_ledger",
                "delete from transaction_",
                "double", " float");
    }

    @Test
    void postcheckShouldCoverSourceLedgerCalculationAndProjectionState() throws IOException {
        String postcheck = read("docs/sql/20261106_06_settlement_principal_correction_postcheck.sql");

        assertThat(postcheck).contains(
                "missing_principal_correction_table_count",
                "invalid_principal_correction_order_state_count",
                "invalid_principal_correction_item_count",
                "invalid_principal_correction_source_identity_count",
                "original_ledger.business_type <> 'TRANSACTION_SETTLEMENT'",
                "net_result.ledger_idempotency_key <> original_ledger.idempotency_key",
                "result_item.settlement_batch_rate_id <> item.original_locked_rate_id",
                "original_rate.source_currency <> item.original_source_currency",
                "label_rate.source_currency <> item.label_currency",
                "projection.projection_action <> 'SETTLE'",
                "invalid_principal_correction_ledger_count",
                "SETTLEMENT_PRINCIPAL_CORRECTION",
                "invalid_principal_correction_calculation_count",
                "REGEXP_LIKE(item.original_source_currency, '^[A-Z]{3}$', 'c')",
                "REGEXP_LIKE(item.label_currency, '^[A-Z]{3}$', 'c')",
                "REGEXP_LIKE(item.target_currency, '^[A-Z]{3}$', 'c')",
                "TRUNCATE(item.label_amount * item.label_locked_direct_rate",
                "item.signed_delta_amount <>",
                "invalid_principal_correction_projection_state_count");
        assertThat(postcheck.toLowerCase()).doesNotContain(" update ", " delete ", " insert ");
    }

    @Test
    void rollbackShouldRefuseToDeleteFinancialAuditFacts() throws IOException {
        String rollback = read("docs/sql/20261106_07_settlement_principal_correction_rollback.sql");

        assertThat(rollback).contains(
                "order_row_count > 0 OR item_row_count > 0",
                "SIGNAL SQLSTATE '45000'",
                "use a reviewed forward compensation");
    }

    private String read(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
