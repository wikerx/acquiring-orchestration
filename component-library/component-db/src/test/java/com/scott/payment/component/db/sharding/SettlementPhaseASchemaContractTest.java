package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementPhaseASchemaContractTest
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 校验结算 Phase A 独立 SQL 与完整设计基线一致，并锁定只读前后检及禁止余额入账的阶段边界。
 * @status : create
 */
class SettlementPhaseASchemaContractTest {

    private static final List<String> PHASE_A_TABLES = List.of(
            "merchant_settlement_profile",
            "settlement_batch_daily_sequence",
            "settlement_batch",
            "settlement_candidate_dependency",
            "settlement_batch_candidate",
            "settlement_batch_rate",
            "settlement_result_item",
            "settlement_result_summary");

    /** Phase A 建表草案只增加结算档案及批次固定表，不重复创建候选或修改余额和保证金资金表。 */
    @Test
    void schemaShouldContainOnlyApprovedPhaseATables() throws IOException {
        String sql = readSql("20260826_01_settlement_phase_a_schema_draft.sql");

        PHASE_A_TABLES.forEach(table -> assertThat(sql).contains("CREATE TABLE " + table + " ("));
        assertThat(sql)
                .doesNotContain("CREATE TABLE settlement_candidate (")
                .doesNotContain("ALTER TABLE merchant_fund_account")
                .doesNotContain("ALTER TABLE merchant_fund_ledger")
                .doesNotContain("ALTER TABLE merchant_reserve_item")
                .doesNotContain("INSERT INTO merchant_fund_ledger")
                .doesNotContain("UPDATE merchant_fund_account");
    }

    /** 独立草案必须逐表复用已评审基线定义，避免字段、索引、CHECK 或金额精度漂移。 */
    @Test
    void schemaShouldMatchReviewedBaselineDefinitions() throws IOException {
        String baseline = readSql("20260825_01_transaction_clearing_schema_draft.sql");
        String phaseA = readSql("20260826_01_settlement_phase_a_schema_draft.sql");

        PHASE_A_TABLES.forEach(table -> assertThat(tableDefinition(phaseA, table))
                .as(table + " definition")
                .isEqualTo(tableDefinition(baseline, table)));
    }

    /** 前后检只能读取数据库，不得隐藏 DDL 或数据修复语句。 */
    @Test
    void precheckAndPostcheckShouldRemainReadOnly() throws IOException {
        for (String fileName : List.of(
                "20260826_02_settlement_phase_a_precheck_draft.sql",
                "20260826_03_settlement_phase_a_postcheck_draft.sql")) {
            String sql = executableSql(readSql(fileName));
            assertThat(sql).contains("SELECT ").doesNotContain(
                    "CREATE ", "ALTER ", "DROP ", "TRUNCATE ", "INSERT ", "UPDATE ", "DELETE ", "SET ");
        }
    }

    /** 数据库必须同时兜底批次创建幂等、候选归属、汇率唯一和资金流水幂等。 */
    @Test
    void schemaShouldContainFinancialIdempotencyConstraints() throws IOException {
        String sql = readSql("20260826_01_settlement_phase_a_schema_draft.sql");

        assertThat(sql).contains(
                "UNIQUE KEY uk_settlement_batch_no",
                "UNIQUE KEY uk_settlement_profile_active",
                "UNIQUE KEY uk_settlement_create_request",
                "UNIQUE KEY uk_settlement_business_sequence",
                "UNIQUE KEY uk_settlement_candidate_dependency",
                "UNIQUE KEY uk_settlement_batch_candidate_pair",
                "UNIQUE KEY uk_settlement_batch_currency_rate",
                "UNIQUE KEY uk_settlement_result_ledger_idempotency",
                "direct_rate DECIMAL(24,12)",
                "unrounded_target_amount DECIMAL(48,20)",
                "minimum_target_amount DECIMAL(48,20)",
                "maximum_target_amount DECIMAL(48,20)",
                "target_amount DECIMAL(24,8)");
    }

    /** 前检必须覆盖认领日期，资金基线只允许使用既有交易结算和保证金结算业务类型。 */
    @Test
    void checksShouldUseCandidateEligibilityAndExistingLedgerBusinessTypes() throws IOException {
        String precheck = readSql("20260826_02_settlement_phase_a_precheck_draft.sql");
        String postcheck = readSql("20260826_03_settlement_phase_a_postcheck_draft.sql");

        assertThat(precheck).contains(
                "13 - COUNT(*) AS missing_candidate_column_count",
                "'settlement_eligible_date'");
        assertThat(postcheck)
                .contains(
                        "9 - COUNT(*) AS missing_phase_a_table_count",
                        "'merchant_settlement_profile'",
                        "'settlement_batch_daily_sequence'",
                        "'settlement_batch'",
                        "'settlement_candidate'",
                        "'settlement_candidate_dependency'",
                        "'settlement_batch_candidate'",
                        "'settlement_batch_rate'",
                        "'settlement_result_item'",
                        "'settlement_result_summary'")
                .contains("business_type IN ('TRANSACTION_SETTLEMENT', 'RESERVE_SETTLEMENT')")
                .doesNotContain("business_type = 'SETTLEMENT'");
    }

    /** Phase B 复用的批次结构必须容纳封批、计算完成和失败补偿，且不把计算完成误写成资金入账。 */
    @Test
    void schemaShouldContainPhaseBProcessingStatesWithoutPostingResults() throws IOException {
        String sql = readSql("20260826_01_settlement_phase_a_schema_draft.sql");

        assertThat(sql).contains(
                "'CLAIMED', 'RATE_LOCKED', 'CALCULATING', 'CALCULATED'",
                "'FAILED_RETRYABLE', 'MANUAL_REVIEW'",
                "calculated_time DATETIME(3) NULL COMMENT '结果和汇总计算完成时间，不代表余额入账'",
                "result_role IN ('TRACE', 'FINANCIAL_COMPONENT', 'LEDGER_POSTING')")
                .doesNotContain("INSERT INTO merchant_fund_ledger", "UPDATE merchant_fund_account");
    }

    private String tableDefinition(String sql, String tableName) {
        int start = sql.indexOf("CREATE TABLE " + tableName + " (");
        int end = start < 0 ? -1 : sql.indexOf(") ENGINE=InnoDB", start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException(tableName + " definition is missing or incomplete");
        }
        return sql.substring(start, end).strip();
    }

    private String executableSql(String sql) {
        return sql.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                .reduce("", (left, right) -> left + right + "\n")
                .toUpperCase();
    }

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
