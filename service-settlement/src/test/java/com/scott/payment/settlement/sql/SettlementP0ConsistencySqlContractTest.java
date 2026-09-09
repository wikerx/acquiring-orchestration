package com.scott.payment.settlement.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementP0ConsistencySqlContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 结算 P0 数据库迁移合同，防止资金守恒、冲正唯一性和真实交易投影约束漂移。
 * @status : create
 */
class SettlementP0ConsistencySqlContractTest {

    @Test
    void shouldMigrateReserveAdjustmentsAndBatchReferences() throws IOException {
        String migration = readRepositoryFile(
                "docs/sql/20260831_01_settlement_p0_consistency_migration.sql");

        assertThat(migration).contains(
                "debit_adjustment_amount DECIMAL(24,8) NOT NULL DEFAULT 0",
                "credit_adjustment_amount DECIMAL(24,8) NOT NULL DEFAULT 0",
                "returned_amount + released_amount + credit_adjustment_amount + reversed_amount",
                "<= retained_amount + debit_adjustment_amount",
                "ADD COLUMN direction VARCHAR(8)",
                "ADD COLUMN reversal_of_action_id BIGINT",
                "'ADJUSTMENT'",
                "'REVERSAL_ADJUSTMENT'",
                "ADD UNIQUE KEY uk_reserve_action_reversal_ref (reversal_of_action_id)",
                "action_type IN (\n          'REVERSAL_HOLD', 'REVERSAL_RETURN', 'REVERSAL_RELEASE', 'REVERSAL_ADJUSTMENT'",
                "batch_type = 'REVERSAL' AND original_batch_no IS NOT NULL",
                "batch_type IN ('REGULAR', 'RESERVE_RELEASE', 'ADJUSTMENT')"
        );
        assertThat(migration).doesNotContain("LIKE 'REVERSAL\\_%' ESCAPE");
    }

    @Test
    void shouldPostcheckRealTransactionProjectionAndFundingConstraints() throws IOException {
        String postcheck = readRepositoryFile(
                "docs/sql/20260831_02_settlement_p0_consistency_postcheck.sql");

        assertThat(postcheck).contains(
                "invalid_reserve_responsibility_count",
                "duplicate_reserve_action_reversal_count",
                "invalid_reserve_reversal_reference_count",
                "invalid_settlement_batch_reference_count",
                "non_transaction_projection_task_count",
                "non_unique = 0",
                "column_name = 'reversal_of_action_id'",
                "reversal_action.action_type <> CONCAT('REVERSAL_', original_action.action_type)",
                "reversal_action.direction = 'DEBIT' AND original_action.direction = 'CREDIT'",
                "reversal_action.direction = 'CREDIT' AND original_action.direction = 'DEBIT'",
                "COALESCE(task.original_batch_no, task.settlement_batch_no)",
                "relation_row.source_type <> 'CLEARING_REVISION'"
        );
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
