package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 汇率波动预警的初始化结构、迁移脚本和主结构快照一致性测试。
 */
class ExchangeRateVolatilitySchemaContractTest {

    /**
     * 两份结构源必须包含相同的三张业务表和跨来源趋势索引。
     */
    @Test
    void schemaSourcesShouldContainAlertTablesAndVolatilityIndex() throws IOException {
        String moduleSchema = Files.readString(modulePath(
                "src/main/resources/sql/exchange-rate-management-schema.sql"));
        String masterSchema = Files.readString(repositoryPath("docs/sql/payment_acquiring_表结构.sql"));

        for (String contract : new String[]{
                "exchange_rate_alert_rule",
                "exchange_rate_alert_event",
                "exchange_rate_alert_evaluation",
                "idx_exchange_business_rate_volatility",
                "uk_exchange_rate_alert_active_key"
        }) {
            assertThat(moduleSchema).contains(contract);
            assertThat(masterSchema).contains(contract);
        }
    }

    /**
     * 初始化脚本必须同时提供系统规则、受保护任务、菜单和全部细粒度权限。
     */
    @Test
    void moduleSchemaShouldSeedSystemTaskMenusAndPermissions() throws IOException {
        String schema = Files.readString(modulePath(
                "src/main/resources/sql/exchange-rate-management-schema.sql"));

        assertThat(schema).contains(
                "SYSTEM_TRANSACTION_72H_3PCT",
                "SYSTEM_SETTLEMENT_72H_3PCT",
                "EXCHANGE_RATE_VOLATILITY_MONITOR",
                "exchangeRateVolatilityMonitorJob",
                "exchange_rate_volatility",
                "exchange_rate_alert_event",
                "exchange_rate_alert_rule",
                "exchange:volatility:query",
                "exchange:alert-event:ack",
                "exchange:alert-rule:import",
                "exchange:alert-rule:export"
        );
    }

    /**
     * 上线脚本必须同时具备迁移前检查、迁移后检查和保留审计事实的止损脚本。
     */
    @Test
    void migrationSetShouldKeepPrecheckPostcheckAndContainment() throws IOException {
        String precheck = Files.readString(repositoryPath(
                "docs/sql/20261102_02_exchange_rate_volatility_alert_precheck.sql"));
        String migration = Files.readString(repositoryPath(
                "docs/sql/20261102_03_exchange_rate_volatility_alert_migration.sql"));
        String postcheck = Files.readString(repositoryPath(
                "docs/sql/20261102_04_exchange_rate_volatility_alert_postcheck.sql"));
        String rollback = Files.readString(repositoryPath(
                "docs/sql/20261102_05_exchange_rate_volatility_alert_rollback.sql"));

        assertThat(precheck).contains("1 - COUNT(*) AS missing_exchange_business_rate");
        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS exchange_rate_alert_event")
                .contains("Cron、超时、重试和启停状态保留现有运维配置");
        assertThat(postcheck).contains("missing_system_job_count", "missing_permission");
        assertThat(rollback).contains("默认只停用入口和任务并保留审计事实")
                .contains("IF event_count = 0 AND evaluation_count = 0 AND custom_rule_count = 0");
    }

    private Path modulePath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("service-admin").resolve(relativePath);
    }

    private Path repositoryPath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
    }
}
