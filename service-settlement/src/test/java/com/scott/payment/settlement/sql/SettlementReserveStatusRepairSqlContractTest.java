package com.scott.payment.settlement.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 全额释放保证金状态修复和只读后检合同。 */
class SettlementReserveStatusRepairSqlContractTest {

    @Test
    void migrationShouldOnlyRepairFullyReleasedRowsBackedByReleaseAction() throws IOException {
        String migration = readRepositoryFile("docs/sql/20260912_01_reserve_status_repair.sql");

        assertThat(migration).contains(
                "START TRANSACTION", "COMMIT",
                "UPDATE merchant_reserve_item reserve_item",
                "reserve_item.reserve_status = 'RELEASED'",
                "reserve_item.reserve_status = 'HELD'",
                "reserve_item.release_batch_no IS NOT NULL",
                "reserve_item.released_amount > 0",
                "reserve_item.version = reserve_item.version + 1",
                "FROM merchant_reserve_action reserve_action",
                "reserve_action.reserve_item_id = reserve_item.id",
                "reserve_action.settlement_batch_no = reserve_item.release_batch_no",
                "reserve_action.action_type = 'RELEASE'",
                "- reserve_item.credit_adjustment_amount - reserve_item.reversed_amount = 0");
        assertThat(migration.toUpperCase()).doesNotContain(
                "DELETE FROM", "TRUNCATE TABLE", "DROP TABLE");
    }

    @Test
    void postcheckShouldDetectBothStaleHeldAndInvalidReleasedRows() throws IOException {
        String postcheck = readRepositoryFile("docs/sql/20260912_02_reserve_status_repair_postcheck.sql");

        assertThat(postcheck).contains(
                "fully_released_still_held_count",
                "released_with_nonzero_responsibility_count",
                "reserve_action.action_type = 'RELEASE'",
                "reserve_item.reserve_status = 'RELEASED'");
        assertThat(postcheck.toUpperCase()).doesNotContain(
                "UPDATE ", "DELETE FROM", "TRUNCATE TABLE", "DROP TABLE");
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        return Files.readString(path);
    }
}
