package com.scott.payment.admin.application.monitor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 渠道交易查询勾兑任务初始化 SQL 合同测试。 */
class ChannelTransactionMatchJobMigrationContractTests {

    @Test
    void shouldInitializeEnabledNonConcurrentCronTaskWithoutInMemoryRetry() throws IOException {
        String migration = Files.readString(modulePath(
                "src/main/resources/sql/transaction-channel-match-job-migration.sql"
        ));

        assertThat(migration).contains(
                "'CHANNEL_TRANSACTION_MATCH'",
                "'channelTransactionMatch'",
                "'0 */1 * * * ?'",
                "'DISTRIBUTED', 'CRON', 'SYNC', 'LOCAL', 'FIRE_ONCE'",
                "300, 0, 60, 0, JSON_OBJECT('lookbackQuarters', 4, 'limit', 100), 'ENABLED'",
                "ON DUPLICATE KEY UPDATE",
                "retry_count = VALUES(retry_count)"
        );
    }

    private Path modulePath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("service-admin").resolve(relativePath);
    }
}
