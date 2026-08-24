package com.scott.payment.admin.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 费用试算逐项快照结构契约测试，防止初始化、迁移和回滚脚本发生漂移。 */
class FeeSimulationDetailSnapshotSchemaContractTests {

    @Test
    void shouldKeepFoundationSchemaAlignedWithSimulationSnapshotModel() throws IOException {
        String foundation = Files.readString(Path.of(
                "src/main/resources/sql/fee-account-foundation-schema.sql"));

        assertThat(foundation).contains(
                "label_amount_usd DECIMAL(24,8)",
                "reserve_rate DECIMAL(12,8)",
                "net_settlement_formula_snapshot VARCHAR(1000)",
                "CREATE TABLE IF NOT EXISTS fee_simulation_record_detail",
                "UNIQUE KEY uk_fee_simulation_detail_line (simulation_record_id, line_no)",
                "KEY idx_fee_simulation_detail_rule (matched_rule_id, matched_tier_id)",
                "create_time DATETIME(3)",
                "费用分类：交易、退款、风控、拒付、结算处理、结算换汇");
    }

    @Test
    void shouldKeepSimulationSnapshotMigrationReversible() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/sql/fee-simulation-detail-snapshot-migration.sql"));
        String rollback = Files.readString(Path.of(
                "src/main/resources/sql/fee-simulation-detail-snapshot-rollback.sql"));

        assertThat(migration).contains(
                "FROM information_schema.columns",
                "ADD COLUMN label_amount_usd DECIMAL(24,8)",
                "ADD COLUMN reserve_rate DECIMAL(12,8)",
                "ADD COLUMN net_settlement_formula_snapshot VARCHAR(1000)",
                "CREATE TABLE IF NOT EXISTS fee_simulation_record_detail",
                "UNIQUE KEY uk_fee_simulation_detail_line (simulation_record_id, line_no)")
                .doesNotContain("ADD COLUMN IF NOT EXISTS");
        assertThat(rollback).contains(
                "DROP TABLE IF EXISTS fee_simulation_record_detail",
                "FROM information_schema.columns",
                "DROP COLUMN net_settlement_formula_snapshot",
                "DROP COLUMN reserve_rate",
                "DROP COLUMN label_amount_usd")
                .doesNotContain("DROP COLUMN IF EXISTS");
    }
}
