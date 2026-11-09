package com.scott.payment.admin.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统一运营待办表、权限、系统任务及回滚脚本合同测试。
 */
class OperationalWorkItemSchemaContractTest {

    /**
     * 第一批迁移必须包含两张运营表、完整权限和受保护系统任务。
     */
    @Test
    void shouldDefineOperationalTablesPermissionsAndSystemJob() throws IOException {
        String migration = read("operational-work-item-migration.sql");

        assertThat(migration).contains(
                "CREATE TABLE IF NOT EXISTS admin_operational_work_item",
                "UNIQUE KEY uk_admin_operational_work_item_source (source_type, source_key)",
                "CREATE TABLE IF NOT EXISTS admin_operational_work_item_history",
                "UNIQUE KEY uk_admin_operational_work_item_history_request (request_id)",
                "'operations:work-item:list'",
                "'operations:work-item:detail'",
                "'operations:work-item:claim'",
                "'operations:work-item:assign'",
                "'operations:work-item:transfer'",
                "'operations:work-item:complete'",
                "'operations:work-item:remark'",
                "'operations:work-item:export'",
                "menu_code = 'admin_home_catalog_v3'",
                "page.sort_no = 3",
                "menu.menu_code = 'admin_operations_catalog_v1'",
                "SET visible = 0, status = 0, deleted = 1",
                "'OPERATIONAL_WORK_ITEM_SYNC'",
                "'operationalWorkItemSync'",
                "'30 * * * * ?'",
                "allow_concurrent = 0");
        assertThat(migration).contains("'COMPLETE'");
    }

    /**
     * 验收和回滚必须覆盖表、权限、菜单及任务，避免只迁移不治理。
     */
    @Test
    void shouldProvidePostcheckAndRollbackForEveryArtifact() throws IOException {
        String postcheck = read("operational-work-item-postcheck.sql");
        String rollback = read("operational-work-item-rollback.sql");

        assertThat(postcheck).contains(
                "admin_operational_work_item",
                "admin_operational_work_item_history",
                "operations:work-item:%",
                "operations:work-item:complete",
                "OPERATIONAL_WORK_ITEM_SYNC");
        assertThat(rollback).contains(
                "DELETE relation\nFROM sys_role_permission relation",
                "DELETE relation\nFROM sys_role_menu relation",
                "DELETE FROM sys_permission",
                "DELETE FROM sys_menu",
                "DELETE FROM sys_job_task WHERE job_code = 'OPERATIONAL_WORK_ITEM_SYNC'",
                "DROP TABLE IF EXISTS admin_operational_work_item_history",
                "DROP TABLE IF EXISTS admin_operational_work_item");
    }

    /**
     * 第二批渠道异常迁移必须同时提供约束升级、发布检查和可执行回退。
     */
    @Test
    void shouldProvideChannelAbnormalMigrationPostcheckAndRollback() throws IOException {
        String migration = read("operational-work-item-channel-abnormal-migration.sql");
        String postcheck = read("operational-work-item-channel-abnormal-postcheck.sql");
        String rollback = read("operational-work-item-channel-abnormal-rollback.sql");

        assertThat(migration).contains(
                "CHANNEL_MATCH_ABNORMAL",
                "SOURCE_SYNC",
                "chk_admin_operational_work_item_state",
                "chk_admin_operational_work_item_history_action",
                "OPERATIONAL_WORK_ITEM_SYNC");
        assertThat(postcheck).contains(
                "CHANNEL_MATCH_ABNORMAL",
                "invalid_owner_projection_count",
                "OPERATIONAL_WORK_ITEM_SYNC");
        assertThat(rollback).contains(
                "DELETE history",
                "source_type = 'CHANNEL_MATCH_ABNORMAL'",
                "DROP CHECK chk_admin_operational_work_item_state",
                "DROP CHECK chk_admin_operational_work_item_history_action");
    }

    /**
     * 第二批清分异常积压迁移必须只扩展运营投影，并提供发布检查和可执行回退。
     */
    @Test
    void shouldProvideClearingBacklogMigrationPostcheckAndRollback() throws IOException {
        String migration = read("operational-work-item-clearing-backlog-migration.sql");
        String postcheck = read("operational-work-item-clearing-backlog-postcheck.sql");
        String rollback = read("operational-work-item-clearing-backlog-rollback.sql");

        assertThat(migration).contains(
                "CLEARING_BACKLOG",
                "chk_admin_operational_work_item_state",
                "OPERATIONAL_WORK_ITEM_SYNC");
        assertThat(migration).doesNotContain(
                "UPDATE transaction_finance_state",
                "DELETE FROM transaction_finance_state");
        assertThat(postcheck).contains(
                "CLEARING_BACKLOG",
                "invalid_clearing_projection_count",
                "OPERATIONAL_WORK_ITEM_SYNC");
        assertThat(rollback).contains(
                "DELETE history",
                "source_type = 'CLEARING_BACKLOG'",
                "CHANNEL_MATCH_ABNORMAL",
                "DROP CHECK chk_admin_operational_work_item_state");
    }

    /**
     * 第三批结算异常积压迁移只能扩展运营投影，并提供发布检查和可执行回退。
     */
    @Test
    void shouldProvideSettlementBacklogMigrationPostcheckAndRollback() throws IOException {
        String migration = read("operational-work-item-settlement-backlog-migration.sql");
        String postcheck = read("operational-work-item-settlement-backlog-postcheck.sql");
        String rollback = read("operational-work-item-settlement-backlog-rollback.sql");

        assertThat(migration).contains(
                "SETTLEMENT_BACKLOG",
                "chk_admin_operational_work_item_state",
                "OPERATIONAL_WORK_ITEM_SYNC");
        assertThat(migration).doesNotContain(
                "UPDATE settlement_batch",
                "DELETE FROM settlement_batch");
        assertThat(postcheck).contains(
                "SETTLEMENT_BACKLOG",
                "invalid_settlement_projection_count",
                "OPERATIONAL_WORK_ITEM_SYNC");
        assertThat(rollback).contains(
                "DELETE history",
                "source_type = 'SETTLEMENT_BACKLOG'",
                "CLEARING_BACKLOG",
                "DROP CHECK chk_admin_operational_work_item_state");
    }

    /**
     * 第四批高风险事件迁移只能扩展运营投影，并提供唯一性、活动来源和回滚检查。
     */
    @Test
    void shouldProvideHighRiskEventMigrationPostcheckAndRollback() throws IOException {
        String migration = read("operational-work-item-high-risk-event-migration.sql");
        String postcheck = read("operational-work-item-high-risk-event-postcheck.sql");
        String rollback = read("operational-work-item-high-risk-event-rollback.sql");

        assertThat(migration).contains(
                "HIGH_RISK_EVENT",
                "chk_admin_operational_work_item_state",
                "OPERATIONAL_WORK_ITEM_SYNC");
        assertThat(migration).doesNotContain(
                "UPDATE risk_evaluation_record",
                "UPDATE transaction_");
        assertThat(postcheck).contains(
                "HIGH_RISK_EVENT",
                "duplicate_count",
                "invalid_high_risk_projection_count",
                "invalid_high_risk_close_reason_count",
                "OPERATIONAL_WORK_ITEM_SYNC");
        assertThat(rollback).contains(
                "DELETE history",
                "source_type = 'HIGH_RISK_EVENT'",
                "SETTLEMENT_BACKLOG",
                "DROP CHECK chk_admin_operational_work_item_state");
    }

    private String read(String fileName) throws IOException {
        Path direct = Path.of("src/main/resources/sql", fileName);
        Path path = Files.exists(direct) ? direct : Path.of("service-admin").resolve(direct);
        return Files.readString(path);
    }
}
