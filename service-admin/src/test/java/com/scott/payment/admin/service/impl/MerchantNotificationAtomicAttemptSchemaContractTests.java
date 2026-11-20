package com.scott.payment.admin.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户回调原子尝试、人工 MQ 幂等和 Outbox 超时恢复数据库结构契约测试
 */
class MerchantNotificationAtomicAttemptSchemaContractTests {

    /**
     * 迁移必须同步覆盖逻辑表和现有季度物理表，并提供可审计回滚路径。
     */
    @Test
    void migrationShouldCoverLogicalAndPhysicalTables() throws IOException {
        String migration = readSql("merchant-notification-atomic-attempt-migration.sql");
        String rollback = readSql("merchant-notification-atomic-attempt-rollback.sql");

        assertThat(migration).contains("SET NAMES utf8mb4");
        for (String table : notificationTables()) {
            assertThat(migration)
                    .contains("ALTER TABLE `" + table + "`")
                    .contains("`processing_mode` varchar(16)")
                    .contains("`processing_event_id` varchar(128)")
                    .contains("ADD KEY `idx_processing_event` (`processing_event_id`, `transaction_date_time`)");
            assertThat(rollback)
                    .contains("ALTER TABLE `" + table + "`")
                    .contains("DROP INDEX `idx_processing_event`")
                    .contains("DROP COLUMN `processing_event_id`")
                    .contains("DROP COLUMN `processing_mode`");
        }
        for (String table : notificationLogTables()) {
            assertThat(migration)
                    .contains("ALTER TABLE `" + table + "`")
                    .contains("`callback_event_id` varchar(128)")
                    .contains("`delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO'")
                    .contains("ADD UNIQUE KEY `uk_callback_event` (`callback_event_id`)");
            assertThat(rollback)
                    .contains("ALTER TABLE `" + table + "`")
                    .contains("DROP INDEX `uk_callback_event`")
                    .contains("DROP COLUMN `delivery_mode`")
                    .contains("DROP COLUMN `callback_event_id`");
        }
        for (String table : outboxTables()) {
            assertThat(migration)
                    .contains("ALTER TABLE `" + table + "`")
                    .contains("ADD KEY `idx_event_status_update` (`event_status`, `update_time`)");
            assertThat(rollback)
                    .contains("ALTER TABLE `" + table + "` DROP INDEX `idx_event_status_update`");
        }
    }

    /**
     * 全量结构脚本必须和已执行迁移保持一致，避免新环境缺少人工回调幂等约束。
     */
    @Test
    void foundationSchemaShouldContainAtomicAttemptContract() throws IOException {
        String schema = Files.readString(Path.of("../docs/sql/payment_acquiring_表结构.sql"));

        // 全量备份会增加季度表，且其他业务表可拥有同名字段；必须逐表验证真实契约。
        Map<String, String> notifications = tableDefinitions(schema, "transaction_merchant_notification");
        assertThat(notifications.keySet()).containsAll(notificationTables());
        notifications.forEach((table, definition) -> assertThat(definition).as(table)
                .contains("`processing_mode` varchar(16)", "`processing_event_id` varchar(128)",
                        "KEY `idx_processing_event` (`processing_event_id`,`transaction_date_time`)",
                        "DEFAULT CHARSET=utf8mb4"));

        Map<String, String> logs = tableDefinitions(schema, "transaction_merchant_notification_log");
        assertThat(logs.keySet()).containsAll(notificationLogTables());
        logs.forEach((table, definition) -> assertThat(definition).as(table)
                .contains("`callback_event_id` varchar(128)",
                        "`delivery_mode` varchar(16) NOT NULL DEFAULT 'AUTO'",
                        "UNIQUE KEY `uk_callback_event` (`callback_event_id`)",
                        "DEFAULT CHARSET=utf8mb4"));

        Map<String, String> outboxes = tableDefinitions(schema, "transaction_event_outbox");
        assertThat(outboxes.keySet()).containsAll(outboxTables());
        outboxes.forEach((table, definition) -> assertThat(definition).as(table)
                .contains("KEY `idx_event_status_update` (`event_status`,`update_time`)",
                        "DEFAULT CHARSET=utf8mb4"));
    }

    /**
     * 自动通知历史日志修复必须覆盖所有现有表，且不得改写人工重发事件号。
     */
    @Test
    void automaticCallbackEventRepairShouldOnlyClearAutomaticDeliveryKeys() throws IOException {
        String repair = readSql("merchant-notification-auto-callback-event-repair.sql");

        assertThat(repair)
                .contains("SET NAMES utf8mb4")
                .contains("START TRANSACTION")
                .contains("COMMIT")
                .doesNotContain("${");
        for (String table : notificationLogTables()) {
            assertThat(repair)
                    .contains("UPDATE `" + table + "`")
                    .contains("SET `callback_event_id` = NULL")
                    .contains("WHERE `delivery_mode` = 'AUTO'")
                    .contains("AND `callback_event_id` IS NOT NULL");
        }
        assertThat(repair).doesNotContain("WHERE `delivery_mode` = 'MANUAL'");
    }

    /**
     * 读取当前模块的 SQL 资源。
     */
    private String readSql(String fileName) throws IOException {
        return Files.readString(Path.of("src/main/resources/sql", fileName));
    }

    /**
     * 提取指定逻辑表及全部六位季度后缀物理表，避免其他业务的同名字段污染契约计数。
     */
    private Map<String, String> tableDefinitions(String schema, String logicalTable) {
        Pattern pattern = Pattern.compile("CREATE TABLE `(" + Pattern.quote(logicalTable)
                + "(?:_[0-9]{6})?)`\\s*\\(.*?\\) ENGINE=[^;]+;", Pattern.DOTALL);
        var matcher = pattern.matcher(schema);
        Map<String, String> definitions = new LinkedHashMap<>();
        while (matcher.find()) {
            assertThat(definitions.put(matcher.group(1), matcher.group())).isNull();
        }
        return definitions;
    }

    /**
     * 返回通知任务逻辑表和已建季度物理表。
     */
    private List<String> notificationTables() {
        return List.of(
                "transaction_merchant_notification",
                "transaction_merchant_notification_202603",
                "transaction_merchant_notification_202604");
    }

    /**
     * 返回通知日志逻辑表和已建季度物理表。
     */
    private List<String> notificationLogTables() {
        return List.of(
                "transaction_merchant_notification_log",
                "transaction_merchant_notification_log_202603",
                "transaction_merchant_notification_log_202604");
    }

    /**
     * 返回 Outbox 逻辑表和已建季度物理表。
     */
    private List<String> outboxTables() {
        return List.of(
                "transaction_event_outbox",
                "transaction_event_outbox_202603",
                "transaction_event_outbox_202604");
    }
}
