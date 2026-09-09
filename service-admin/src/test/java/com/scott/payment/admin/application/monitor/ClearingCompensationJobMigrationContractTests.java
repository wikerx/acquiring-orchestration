package com.scott.payment.admin.application.monitor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCompensationJobMigrationContractTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 清分漏单滚动补偿任务初始化 SQL 合同测试。
 * @status : create
 */
class ClearingCompensationJobMigrationContractTests {

    @Test
    void shouldInitializeEnabledNonConcurrentShadowWriteTask() throws IOException {
        String migration = Files.readString(modulePath(
                "src/main/resources/sql/clearing-compensation-job-migration.sql"
        ));

        assertThat(migration).contains(
                "'CLEARING_COMPENSATION'",
                "'clearingCompensation'",
                "'0 */5 * * * ?'",
                "'DISTRIBUTED', 'CRON', 'SYNC', 'LOCAL', 'FIRE_ONCE'",
                "300, 0, 60, 0",
                "JSON_OBJECT('mode', 'SHADOW_WRITE', 'limit', 200, 'maxPages', 20)",
                "'ENABLED'",
                "DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 5 MINUTE)",
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
