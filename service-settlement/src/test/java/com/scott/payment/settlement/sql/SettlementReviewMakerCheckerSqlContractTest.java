package com.scott.payment.settlement.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewMakerCheckerSqlContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 结算预审 Maker-Checker 迁移合同，保护既有候选状态、唯一幂等和审计快照。
 * @status : create
 */
class SettlementReviewMakerCheckerSqlContractTest {

    @Test
    void migrationShouldKeepReviewSnapshotsAndExistingReplayStateCompatible() throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/20260831_03_settlement_review_maker_checker_migration.sql");

        assertThat(migration).contains(
                "SET NAMES utf8mb4",
                "CREATE TABLE settlement_review_daily_sequence",
                "CREATE TABLE settlement_review_order",
                "CREATE TABLE settlement_review_candidate",
                "CREATE TABLE settlement_review_rate",
                "CREATE TABLE settlement_review_summary",
                "UNIQUE KEY uk_settlement_review_create_request (create_request_key)",
                "UNIQUE KEY uk_settlement_review_decision_request (decision_request_key)",
                "UNIQUE KEY uk_settlement_batch_review (review_order_no)",
                "ADD COLUMN review_rate_id BIGINT",
                "ADD UNIQUE KEY uk_settlement_batch_review_rate (review_rate_id)",
                "information_schema.table_constraints",
                "CALL drop_settlement_review_check('settlement_candidate', 'chk_settlement_candidate_state')",
                "'READY', 'REPLAY_HOLD', 'REVIEW_LOCKED', 'SUPERSEDED'",
                "candidate_status IN ('READY', 'REPLAY_HOLD', 'SUPERSEDED')",
                "invalid_existing_batch_projection_count",
                "UPDATE settlement_batch batch",
                "SUM(relation.source_type = 'CLEARING_REVISION')",
                "projectable_candidate_count BETWEEN 0 AND candidate_count",
                "review_status = 'REJECTED'",
                "decision_action = 'REJECT'",
                "review_status = 'CANCELLED'",
                "decision_action = 'CANCEL'",
                "submitted_by_account_id = decided_by_account_id",
                "maker_role_snapshot IS NOT NULL",
                "checker_role_snapshot IS NOT NULL",
                "maker_account_id <> checker_account_id",
                "DATETIME(3)",
                "DECIMAL(24,12)",
                "DECIMAL(24,8)");
        assertThat(migration.toLowerCase()).doesNotContain(
                "double", " float", "reconciliation_status", "nacos", "yml");
    }

    @Test
    void postcheckShouldCoverOwnershipRatesAuditAndProjectionCounts() throws IOException {
        String postcheck = readRepositoryFile(
                "docs/sql/20260831_04_settlement_review_maker_checker_postcheck.sql");

        assertThat(postcheck).contains(
                "invalid_replay_hold_ownership",
                "invalid_review_candidate_ownership",
                "invalid_pending_review_snapshot",
                "invalid_approved_review_batch",
                "invalid_review_rate_count",
                "invalid_review_rate_inheritance",
                "invalid_manual_fund_ledger_audit",
                "invalid_settlement_projection_count",
                "invalid_review_projection_count",
                "SELECT COUNT(*) FROM settlement_review_rate",
                "SELECT COUNT(*) FROM settlement_batch_rate",
                "relation.source_type = 'CLEARING_REVISION'");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
