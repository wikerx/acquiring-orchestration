package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 2027-Q1 分表结构修复包的空表门禁、增量字段、最终核验和受控回滚边界。
 */
class FutureQuarterShardingRepairMigrationBundleContractTest {

    private static final List<String> REPAIRED_COLUMNS = List.of(
            "label_currency_exponent",
            "label_authorized_amount",
            "label_authorized_cancel_amount",
            "label_captured_amount",
            "label_refunded_amount",
            "label_chargeback_amount",
            "label_available_capture_amount",
            "label_available_refund_amount",
            "label_amount_currency",
            "label_change_amount",
            "label_authorized_before",
            "label_authorized_after",
            "label_captured_before",
            "label_captured_after",
            "label_refunded_before",
            "label_refunded_after",
            "label_available_capture_before",
            "label_available_capture_after",
            "label_available_refund_before",
            "label_available_refund_after");

    /**
     * 前检必须拒绝非空表、重复迁移和已存在的风控 Q1 双表。
     */
    @Test
    void precheckShouldGuardEmptyUnrepairedTablesAndRiskPairAbsence() throws IOException {
        String sql = executableSql(readSql("20261108_05_future_quarter_schema_repair_precheck.sql"));

        assertThat(sql).contains(
                "TARGET_ROW_COUNT <> 0",
                "EXISTING_TARGET_COLUMN_COUNT <> 0",
                "EXPECTED_TEMPLATE_COLUMN_COUNT <> 21",
                "EXISTING_RISK_Q1_TABLE_COUNT <> 0",
                "SIGNAL SQLSTATE '45000'");
        assertThat(sql).doesNotContain(
                "ALTER TABLE", "CREATE TABLE", "INSERT ", "UPDATE ", "DELETE ", "TRUNCATE ", "RENAME ");
    }

    /**
     * 迁移只能向三张空的交易 Q1 表增加模板中已有的 21 个字段。
     */
    @Test
    void migrationShouldOnlyAddAllTwentyOneColumns() throws IOException {
        String sql = executableSql(readSql("20261108_06_future_quarter_schema_repair_migration.sql"));

        assertThat(sql).contains(
                "ALTER TABLE `TRANSACTION_ORDER_202701`",
                "ALTER TABLE `TRANSACTION_OPERATION_202701`",
                "ALTER TABLE `TRANSACTION_AMOUNT_CHANGE_LOG_202701`");
        assertThat(countOccurrences(sql, "ADD COLUMN")).isEqualTo(21);
        REPAIRED_COLUMNS.forEach(column -> assertThat(sql).contains("`" + column.toUpperCase() + "`"));
        assertThat(sql).doesNotContain(
                "DROP COLUMN", "DROP TABLE", "CREATE TABLE", "INSERT ", "UPDATE ", "DELETE ",
                "TRUNCATE ", "RENAME ");
    }

    /**
     * 后检必须比对五组模板，验证号段、60 张计划表、建表日志和唯一任务成功状态。
     */
    @Test
    void postcheckShouldRequireFiveMatchedTablesAndSuccessfulJob() throws IOException {
        String sql = executableSql(readSql("20261108_07_future_quarter_schema_repair_postcheck.sql"));

        assertThat(sql).contains(
                "REQUIRED_TABLE_COUNT <> 5",
                "TARGET_ROW_COUNT <> 0",
                "COLUMN_MISMATCH_COUNT <> 0",
                "INDEX_MISMATCH_COUNT <> 0",
                "TABLE_OPTION_MISMATCH_COUNT <> 0",
                "AUTO_INCREMENT IS NULL",
                "PLANNED_COUNT <> 60",
                "LATEST_CREATE_LOG_COUNT = 0",
                "WHERE DRY_RUN = 0",
                "SHARDING_JOB_COUNT <> 1",
                "LAST_RUN_STATUS <> 'SUCCESS'");
        assertThat(sql).doesNotContain("WHERE DRY_RUN <> 0");
        for (String table : List.of(
                "transaction_order_202701",
                "transaction_operation_202701",
                "transaction_amount_change_log_202701",
                "risk_evaluation_record_202701",
                "risk_evaluation_hit_detail_202701")) {
            assertThat(sql).contains(table.toUpperCase());
        }
    }

    /**
     * 回滚必须先拒绝非空表，再删除本批风控表和 21 个新增字段。
     */
    @Test
    void rollbackShouldRefuseBusinessDataBeforeDroppingRepairArtifacts() throws IOException {
        String sql = executableSql(readSql("20261108_08_future_quarter_schema_repair_rollback.sql"));

        assertThat(sql).contains(
                "REQUIRED_TABLE_COUNT <> 5",
                "TARGET_ROW_COUNT <> 0",
                "SIGNAL SQLSTATE '45000'",
                "DROP TABLE `RISK_EVALUATION_HIT_DETAIL_202701`",
                "DROP TABLE `RISK_EVALUATION_RECORD_202701`");
        assertThat(countOccurrences(sql, "DROP COLUMN")).isEqualTo(21);
    }

    /**
     * 去除注释和空行，避免说明文字影响 DDL 安全断言。
     */
    private String executableSql(String sql) {
        return sql.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                .reduce("", (left, right) -> left + right + "\n")
                .toUpperCase();
    }

    /**
     * 统计迁移关键字出现次数，确保字段清单没有漏项或重复项。
     */
    private int countOccurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    /**
     * 从模块目录向上查找仓库级 SQL 文件，兼容 IDE 与 Maven Reactor 工作目录。
     */
    private String readSql(String fileName) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("docs/sql").resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IllegalStateException(fileName + " is missing");
    }
}
