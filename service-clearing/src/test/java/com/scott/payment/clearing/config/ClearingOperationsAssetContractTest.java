package com.scott.payment.clearing.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingOperationsAssetContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证清分监控和影子验收资产可解析、只读且引用真实指标。
 * @status : create
 */
class ClearingOperationsAssetContractTest {

    private static final Pattern SQL_COMMENT = Pattern.compile("(?m)^\\s*--.*$");
    private static final Pattern FORBIDDEN_SQL = Pattern.compile(
            "(?i)\\b(INSERT|UPDATE|DELETE|ALTER|DROP|CREATE|TRUNCATE|REPLACE|CALL|EXECUTE|PREPARE|LOCK)\\b");

    @Test
    void prometheusRulesShouldParseAndReferenceClearingMetrics() throws IOException {
        String rules = read("docs/deployment/prometheus/clearing-alert-rules.yml");
        Object parsed = new Yaml().load(rules);

        assertThat(parsed).isInstanceOf(Map.class);
        assertThat(rules).contains(
                "clearing_amount_imbalance_total",
                "clearing_pending_count",
                "clearing_oldest_pending_seconds",
                "clearing_fee_cache_hit_total",
                "clearing_compensation_batch_total",
                "clearing_tier_lock_seconds_bucket",
                "failure_code=\"RESERVE_RETURN_EXCEEDED\"",
                "scan_type",
                "rule_type",
                "priority: P0",
                "priority: P1",
                "priority: P2");
        assertThat(rules).doesNotContain("failureCode", "scanType", "ruleType");
    }

    @Test
    void grafanaDashboardShouldBeValidJsonAndKeepCurrencyDimension() throws IOException {
        JsonNode dashboard = new ObjectMapper().readTree(
                read("docs/deployment/grafana/clearing-dashboard.json"));

        assertThat(dashboard.path("uid").asText()).isEqualTo("acquiring-clearing-v1");
        assertThat(dashboard.path("panels").size()).isGreaterThanOrEqualTo(10);
        assertThat(dashboard.toString()).contains(
                "clearing_reserve_remaining_amount", "max by (currency)", "clearing_metrics_refresh_total",
                "transaction_type", "failure_code", "rule_type", "duplicate_type", "scan_type");
        assertThat(dashboard.toString()).doesNotContain(
                "transactionType", "failureCode", "ruleType", "duplicateType", "scanType");
    }

    @Test
    void shadowAcceptanceSqlShouldContainOnlySetAndSelectStatements() throws IOException {
        String sql = read("docs/sql/20260826_06_transaction_clearing_shadow_acceptance_draft.sql");
        String executableSql = SQL_COMMENT.matcher(sql).replaceAll("");
        List<String> statements = Arrays.stream(executableSql.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();

        assertThat(statements).isNotEmpty().allMatch(statement ->
                statement.regionMatches(true, 0, "SET", 0, 3)
                        || statement.regionMatches(true, 0, "SELECT", 0, 6));
        assertThat(FORBIDDEN_SQL.matcher(executableSql).find()).isFalse();
        assertThat(executableSql).contains(
                "transaction_date_time >= @begin_time",
                "transaction_date_time < @end_time",
                "GROUP BY reserve_currency",
                "UNION ALL",
                "candidate_status = 'POSTED'",
                "source_revision < f.clearing_revision",
                "settlement_rate IS NOT NULL",
                "record_status = 'ACTIVE'");
    }

    private String read(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct);
        }
        return Files.readString(Path.of("..").resolve(relativePath).normalize());
    }
}
