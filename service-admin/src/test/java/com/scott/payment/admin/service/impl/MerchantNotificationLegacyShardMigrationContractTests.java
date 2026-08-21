package com.scott.payment.admin.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationLegacyShardMigrationContractTests
 * @date : 2026-08-21 11:20
 * @email : scott_x@163.com
 * @description : 校验分片启用前遗留商户通知及其投递日志可原子迁移到已发布季度，并保留可回滚的数据身份。
 * @status : create
 */
class MerchantNotificationLegacyShardMigrationContractTests {

    /** 迁移必须按季度无损复制完整记录，并在同一事务内清理模板表源数据。 */
    @Test
    void migrationShouldMoveLegacyRowsIntoPublishedQuarterTablesAtomically() throws IOException {
        String migration = readSql("merchant-notification-legacy-shard-migration.sql");

        assertThat(migration)
                .contains("SET NAMES utf8mb4")
                .contains("START TRANSACTION")
                .contains("COMMIT")
                .contains("INSERT INTO `transaction_merchant_notification_202603`")
                .contains("INSERT INTO `transaction_merchant_notification_202604`")
                .contains("INSERT INTO `transaction_merchant_notification_log_202603`")
                .contains("INSERT INTO `transaction_merchant_notification_log_202604`")
                .contains("`id`, `notify_id`, `transaction_id`")
                .contains("`source`.`last_attempt_no`, `source`.`max_retry_count`")
                .contains("`source`.`transaction_date_time` >= '2026-07-01 00:00:00.000'")
                .contains("`source`.`transaction_date_time` < '2027-01-01 00:00:00.000'")
                .contains("DELETE FROM `transaction_merchant_notification_log`")
                .contains("DELETE FROM `transaction_merchant_notification`")
                .doesNotContain("LEAST(`source`.`max_retry_count`, 5)")
                .doesNotContain("automatic notification attempts exhausted")
                .doesNotContain("${");
    }

    /** 回滚必须只识别季度号段启用前的小 ID，并同步恢复关联投递日志。 */
    @Test
    void rollbackShouldRestoreOnlyLegacyIdentityRowsAndTheirLogs() throws IOException {
        String rollback = readSql("merchant-notification-legacy-shard-rollback.sql");

        assertThat(rollback)
                .contains("SET NAMES utf8mb4")
                .contains("START TRANSACTION")
                .contains("COMMIT")
                .contains("`notification`.`id` < 202600000000000000")
                .contains("`notification`.`notify_id` = `source`.`notify_id`")
                .contains("INSERT INTO `transaction_merchant_notification`")
                .contains("INSERT INTO `transaction_merchant_notification_log`")
                .contains("DELETE FROM `transaction_merchant_notification_log_202603`")
                .contains("DELETE FROM `transaction_merchant_notification_log_202604`")
                .contains("DELETE FROM `transaction_merchant_notification_202603`")
                .contains("DELETE FROM `transaction_merchant_notification_202604`")
                .doesNotContain("${");
    }

    /** 读取 service-admin SQL 资源。 */
    private String readSql(String fileName) throws IOException {
        return Files.readString(Path.of("src/main/resources/sql", fileName));
    }
}
