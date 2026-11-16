package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 系统交易加黑表结构、迁移和权限合同测试。 */
class TradeBlackSchemaContractTest {

    /** 新环境表结构必须包含审计归属、强幂等摘要、查询索引和受控导出权限。 */
    @Test
    void shouldDefineAuditableSchemaAndExportPermission() throws IOException {
        String schema = read("risk-management-schema.sql");
        String workflow = read("risk-trade-black-workflow-migration.sql");
        String menu = read("risk-management-menu.sql");

        assertThat(schema + workflow).contains(
                "request_fingerprint CHAR(64) NOT NULL",
                "operator_account_id BIGINT NOT NULL",
                "UNIQUE KEY uk_trade_black_request_account (operator_account_id, request_id)",
                "batch_no VARCHAR(64)",
                "candidate_key VARCHAR(64)",
                "KEY idx_trade_black_update_time (update_time, id)",
                "KEY idx_trade_black_batch_candidate (batch_no, candidate_key)");
        assertThat(menu).contains(
                "risk_trade_black_system_export",
                "risk:tradeBlack:system:export");
    }

    /** 存量升级脚本必须与回滚脚本成对覆盖新增字段、索引和唯一约束。 */
    @Test
    void shouldProvideExplicitExistingEnvironmentMigrationAndRollback() throws IOException {
        String migration = read("risk-trade-black-completion-migration.sql");
        String rollback = read("risk-trade-black-completion-rollback.sql");

        assertThat(migration).contains(
                "仅适用于已经执行旧版工作流脚本",
                "ADD COLUMN request_fingerprint CHAR(64) NULL",
                "ADD COLUMN operator_account_id BIGINT NULL",
                "ADD COLUMN batch_no VARCHAR(64) NULL",
                "ADD COLUMN candidate_key VARCHAR(64) NULL",
                "ADD UNIQUE KEY uk_trade_black_request_account (operator_account_id, request_id)");
        assertThat(rollback).contains(
                "DROP INDEX uk_trade_black_request_account",
                "DROP COLUMN operator_account_id",
                "DROP COLUMN request_fingerprint",
                "DROP INDEX idx_trade_black_batch_candidate",
                "DROP INDEX idx_trade_black_update_time",
                "DROP COLUMN candidate_key",
                "DROP COLUMN batch_no");
    }

    /** 新增 SQL 及注释必须保持 UTF-8 可读文本。 */
    @Test
    void shouldNotContainMojibakeInTradeBlackSql() throws IOException {
        String sql = read("risk-management-schema.sql")
                + read("risk-trade-black-workflow-migration.sql")
                + read("risk-trade-black-completion-migration.sql")
                + read("risk-trade-black-completion-rollback.sql")
                + read("risk-management-menu.sql");

        assertThat(sql).doesNotContain("å•", "æˆ", "èµ", "ç»", "�");
    }

    private String read(String fileName) throws IOException {
        Path direct = Path.of("src/main/resources/sql", fileName);
        Path path = Files.exists(direct) ? direct : Path.of("service-admin").resolve(direct);
        return Files.readString(path);
    }
}
