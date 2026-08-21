package com.scott.payment.admin.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationAtomicAttemptSchemaContractTests
 * @date : 2026-08-21 19:00
 * @email : scott_x@163.com
 * @description : 商户回调原子尝试、人工 MQ 幂等和 Outbox 超时恢复数据库结构契约测试
 * @status : create
 */
class MerchantNotificationAtomicAttemptSchemaContractTests {

    /** 迁移必须同步覆盖逻辑表和现有季度物理表，并提供可审计回滚路径。 */
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

    /** 全量结构脚本必须和已执行迁移保持一致，避免新环境缺少人工回调幂等约束。 */
    @Test
    void foundationSchemaShouldContainAtomicAttemptContract() throws IOException {
        String schema = Files.readString(Path.of("../docs/sql/payment_acquiring_表结构.sql"));

        assertThat(count(schema, "`processing_mode` varchar(16)")).isEqualTo(notificationTables().size());
        assertThat(count(schema, "KEY `idx_processing_event` (`processing_event_id`,`transaction_date_time`)"))
                .isEqualTo(notificationTables().size());
        assertThat(count(schema, "`callback_event_id` varchar(128)")).isEqualTo(notificationLogTables().size());
        assertThat(count(schema, "UNIQUE KEY `uk_callback_event` (`callback_event_id`)"))
                .isEqualTo(notificationLogTables().size());
        assertThat(count(schema, "KEY `idx_event_status_update` (`event_status`,`update_time`)"))
                .isEqualTo(outboxTables().size());
        assertThat(schema).contains("DEFAULT CHARSET=utf8mb4");
    }

    /** 自动通知历史日志修复必须覆盖所有现有表，且不得改写人工重发事件号。 */
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

    /** 读取当前模块的 SQL 资源。 */
    private String readSql(String fileName) throws IOException {
        return Files.readString(Path.of("src/main/resources/sql", fileName));
    }

    /** 统计全量结构中精确契约片段的出现次数。 */
    private int count(String source, String fragment) {
        return source.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    /** 返回通知任务逻辑表和已建季度物理表。 */
    private List<String> notificationTables() {
        return List.of(
                "transaction_merchant_notification",
                "transaction_merchant_notification_202603",
                "transaction_merchant_notification_202604");
    }

    /** 返回通知日志逻辑表和已建季度物理表。 */
    private List<String> notificationLogTables() {
        return List.of(
                "transaction_merchant_notification_log",
                "transaction_merchant_notification_log_202603",
                "transaction_merchant_notification_log_202604");
    }

    /** 返回 Outbox 逻辑表和已建季度物理表。 */
    private List<String> outboxTables() {
        return List.of(
                "transaction_event_outbox",
                "transaction_event_outbox_202603",
                "transaction_event_outbox_202604");
    }
}
