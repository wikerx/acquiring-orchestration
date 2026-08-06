package com.scott.payment.admin.application.monitor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户通知补偿任务初始化 SQL 合同测试。
 */
class MerchantNotificationRetryJobMigrationContractTests {

    @Test
    void shouldInitializeEnabledNonConcurrentRetryTask() throws IOException {
        String migration = Files.readString(modulePath(
                "src/main/resources/sql/merchant-notification-retry-job-migration.sql"
        ));

        assertThat(migration).contains(
                "'MERCHANT_NOTIFICATION_RETRY'",
                "'merchantNotificationRetry'",
                "'0 */1 * * * ?'",
                "'DISTRIBUTED', 'CRON', 'SYNC', 'LOCAL', 'FIRE_ONCE'",
                "300, 1, 60, 0, JSON_OBJECT('limit', 5), 'ENABLED'",
                "ON DUPLICATE KEY UPDATE",
                "next_trigger_time = CASE",
                "WHEN status = 'ENABLED' AND next_trigger_time IS NULL"
        );
    }

    private Path modulePath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("service-admin").resolve(relativePath);
    }
}
