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
 * @classname : TransactionClearingMigrationBundleContractTest
 * @date : 2026-08-25 23:40
 * @email : scott_x@163.com
 * @description : 校验清分第三阶段 SQL 拆分包的只读边界、兼容扩展范围、28 表拓扑和影子候选固定表完整性，不连接或修改数据库。
 * @status : create
 */
class TransactionClearingMigrationBundleContractTest {

    /** 前检只允许 SELECT，不能混入结构或数据写操作。 */
    @Test
    void precheckShouldRemainReadOnly() throws IOException {
        String sql = executableSql(readSql("20260825_02_transaction_clearing_precheck_draft.sql"));

        assertThat(sql).contains("SELECT ");
        assertThat(sql).contains(
                "SETTLEMENT_CANDIDATE",
                "CLEARING_RESERVE_ADJUSTMENT",
                "CLEARING_TIER_PERIOD_REPLAY",
                "CLEARING_TIER_PERIOD_REPLAY_ITEM");
        assertThat(sql).doesNotContain(
                "ALTER TABLE", "CREATE TABLE", "UPDATE ", "DELETE ", "DROP ", "TRUNCATE ", "SET ",
                "SETTLEMENT_BATCH", "MERCHANT_FUND_ACCOUNT", "MERCHANT_FUND_LEDGER");
    }

    /** 兼容迁移必须同时覆盖模板、Q3 和 Q4，且在应用引用新列之前独立执行。 */
    @Test
    void compatibilityMigrationShouldCoverPublishedQuarterTables() throws IOException {
        String sql = readSql("20260825_03_transaction_clearing_compatibility_draft.sql");

        for (String table : List.of(
                "transaction_event_outbox",
                "transaction_operation",
                "transaction_order",
                "transaction_merchant_snapshot",
                "transaction_finance_state")) {
            assertThat(sql)
                    .contains("ALTER TABLE " + table + "\n")
                    .contains("ALTER TABLE " + table + "_202603")
                    .contains("ALTER TABLE " + table + "_202604");
        }
        assertThat(sql).contains("uk_merchant_snapshot_transaction", "delivery_mode", "fee_snapshot_hash");
    }

    /** 拓扑迁移只创建本阶段所需清分表和影子候选，不提前落地真实结算资金表。 */
    @Test
    void topologyMigrationShouldContainExactlyClearingScope() throws IOException {
        String sql = readSql("20260825_04_transaction_clearing_topology_draft.sql");
        String reserveStateDefinition = tableDefinition(sql, "transaction_reserve_clearing_state");
        String candidateDefinition = tableDefinition(sql, "settlement_candidate");

        assertThat(sql)
                .contains("CREATE TABLE fee_tier_accumulator")
                .contains("CREATE TABLE transaction_clearing_detail")
                .contains("CREATE TABLE transaction_reserve_clearing_detail")
                .contains("CREATE TABLE transaction_reserve_clearing_state")
                .contains("CREATE TABLE clearing_reserve_adjustment")
                .contains("CREATE TABLE clearing_tier_period_replay")
                .contains("CREATE TABLE clearing_tier_period_replay_item")
                .contains("CREATE TABLE settlement_candidate")
                .contains("transaction_clearing_detail_202603", "transaction_clearing_detail_202604")
                .contains("transaction_reserve_clearing_detail_202603", "transaction_reserve_clearing_detail_202604")
                .contains("transaction_reserve_clearing_state_202603", "transaction_reserve_clearing_state_202604")
                .doesNotContain(
                        "CREATE TABLE settlement_batch",
                        "CREATE TABLE settlement_candidate_dependency",
                        "CREATE TABLE settlement_result_item",
                        "CREATE TABLE settlement_result_summary",
                        "ALTER TABLE merchant_reserve_item");
        assertThat(reserveStateDefinition)
                .contains("transaction_date_time DATETIME(3) NOT NULL")
                .contains("debit_adjustment_amount DECIMAL(24,8) NOT NULL DEFAULT 0")
                .contains("credit_adjustment_amount DECIMAL(24,8) NOT NULL DEFAULT 0")
                .contains("KEY idx_reserve_state_metrics\n"
                        + "        (reserve_currency, transaction_date_time, remaining_amount)")
                .doesNotContain("original_transaction_date_time");
        assertThat(candidateDefinition)
                .contains("shadow_mode TINYINT NOT NULL DEFAULT 1")
                .contains("UNIQUE KEY uk_settlement_candidate_no")
                .contains("UNIQUE KEY uk_settlement_candidate_source")
                .contains("REPLAY_HOLD")
                .contains("CONSTRAINT chk_settlement_candidate_value CHECK")
                .contains("CONSTRAINT chk_settlement_candidate_state CHECK");
    }

    /** 拆分拓扑必须与完整评审基线中的清分表定义完全一致，避免正式执行草案字段或约束漂移。 */
    @Test
    void topologyMigrationShouldMatchReviewBaselineClearingDefinitions() throws IOException {
        String baseline = readSql("20260825_01_transaction_clearing_schema_draft.sql");
        String topology = readSql("20260825_04_transaction_clearing_topology_draft.sql");

        assertThat(clearingTopology(topology)).isEqualTo(clearingTopology(baseline));
        assertThat(tableDefinition(topology, "settlement_candidate"))
                .isEqualTo(tableDefinition(baseline, "settlement_candidate"));
        for (String table : List.of(
                "clearing_reserve_adjustment",
                "clearing_tier_period_replay",
                "clearing_tier_period_replay_item")) {
            assertThat(tableDefinition(topology, table))
                    .as(table + " definition")
                    .isEqualTo(tableDefinition(baseline, table));
        }
    }

    /** 发布后核验必须覆盖缺表、结构、分片时间精度、字符集、号段和唯一索引。 */
    @Test
    void postcheckShouldCoverAllPublicationGates() throws IOException {
        String sql = readSql("20260825_05_transaction_clearing_postcheck_draft.sql");

        assertThat(sql).contains(
                "SELECT 14 - COUNT(*) AS missing_table_count",
                "missing_table_count",
                "column_count",
                "index_count",
                "check_count",
                "datetime_precision",
                "invalid_collation_count",
                "auto_increment_check_status",
                "missing_required_unique_index_count",
                "missing_settlement_candidate_check_count",
                "missing_workflow_check_count",
                "invalid_candidate_replay_hold_check_count",
                "invalid_reserve_metrics_index_count",
                "missing_reserve_adjustment_column_count",
                "invalid_reserve_conservation_check_count",
                "idx_reserve_state_metrics",
                "reserve_currency,transaction_date_time,remaining_amount",
                "uk_settlement_candidate_no",
                "uk_settlement_candidate_source",
                "uk_reserve_adjustment_request",
                "uk_tier_replay_request",
                "uk_tier_replay_item_sequence",
                "chk_settlement_candidate_value",
                "chk_settlement_candidate_state",
                "chk_reserve_adjustment_state",
                "chk_tier_replay_state",
                "chk_tier_replay_item_state");
        assertThat(sql)
                .contains("LEFT JOIN information_schema.columns c")
                .contains("c.column_name IS NULL")
                .doesNotContain("SET NAMES");
    }

    /** 截取指定 CREATE TABLE 定义，避免其它清分表同名字段掩盖分片键漂移。 */
    private String tableDefinition(String sql, String tableName) {
        int start = sql.indexOf("CREATE TABLE " + tableName + " (");
        if (start < 0) {
            throw new IllegalStateException(tableName + " definition is missing");
        }
        int end = sql.indexOf(") ENGINE=InnoDB", start);
        if (end < 0) {
            throw new IllegalStateException(tableName + " definition is incomplete");
        }
        return sql.substring(start, end);
    }

    /** 截取固定累计表至最后一张季度保证金状态表的完整清分拓扑定义。 */
    private String clearingTopology(String sql) {
        int start = sql.indexOf("CREATE TABLE fee_tier_accumulator (");
        int lastAlter = sql.indexOf("ALTER TABLE transaction_reserve_clearing_state_202604", start);
        int end = lastAlter < 0 ? -1 : sql.indexOf(";", lastAlter);
        if (start < 0 || lastAlter < 0 || end < 0) {
            throw new IllegalStateException("clearing topology definition is incomplete");
        }
        return sql.substring(start, end + 1).strip();
    }

    /** 去除空行和 SQL 行注释，避免注释中的禁止词影响只读合同断言。 */
    private String executableSql(String sql) {
        return sql.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                .reduce("", (left, right) -> left + right + "\n")
                .toUpperCase();
    }

    /** 读取仓库级 SQL 草案，兼容 IDE 和 Maven Reactor 工作目录。 */
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
