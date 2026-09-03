package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementPostingSchemaContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 锁定结算资金提交、保证金动作、投影和Outbox的数据库最终幂等合同。
 * @status : create
 */
class SettlementPostingSchemaContractTest {

    @Test
    void migrationShouldContainAllPostingBoundaries() throws IOException {
        String sql = read("20260826_08_settlement_posting_migration.sql");

        assertThat(sql).contains(
                "uk_settlement_one_ledger_posting",
                "uk_settlement_single_reversal",
                "CREATE TABLE merchant_reserve_action",
                "UNIQUE KEY uk_reserve_action_no",
                "CREATE TABLE settlement_projection_task",
                "UNIQUE KEY uk_settlement_projection_batch_candidate",
                "CREATE TABLE settlement_event_outbox",
                "UNIQUE KEY uk_settlement_event_no");
        assertThat(read("20260826_09_settlement_posting_postcheck.sql"))
                .contains("business_type IN ('TRANSACTION_SETTLEMENT', 'RESERVE_SETTLEMENT')");
        assertThat(sql).doesNotContain("DOUBLE", "FLOAT");
    }

    @Test
    void checksShouldRemainReadOnly() throws IOException {
        for (String file : new String[]{
                "20260826_07_settlement_posting_precheck.sql",
                "20260826_09_settlement_posting_postcheck.sql"}) {
            String executable = read(file).lines().map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                    .reduce("", (left, right) -> left + right + "\n").toUpperCase();
            assertThat(executable).contains("SELECT ").doesNotContain(
                    "CREATE ", "ALTER ", "DROP ", "TRUNCATE ",
                    "INSERT ", "UPDATE ", "DELETE ", "CALL ", "SET ");
        }
    }

    private String read(String name) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("docs/sql").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IllegalStateException(name + " is missing");
    }
}
