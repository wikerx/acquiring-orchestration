package com.scott.payment.admin.application.monitor;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTimeoutCloseJobMigrationContractTests
 * @date : 2026-08-21 10:20
 * @email : scott_x@163.com
 * @description : 校验支付超时关单正式任务初始化及历史定时演示任务停用的数据迁移契约
 * @status : create
 */
@Slf4j
class PaymentTimeoutCloseJobMigrationContractTests {

    /**
     * 校验环境迁移后仅保留正式关单任务参与 CRON 调度。
     *
     * @throws IOException 迁移脚本无法读取时抛出
     */
    @Test
    void shouldEnableProductionTaskAndDisableLegacyCronTasks() throws IOException {
        log.info("用例开始：校验支付超时关单正式任务启用且历史 CRON 演示任务停用");
        String migration = Files.readString(modulePath(
                "src/main/resources/sql/payment-checkout-24h-deadline-migration.sql"));

        assertThat(migration).contains(
                "'PAY_TIMEOUT_CLOSE'",
                "'paymentTimeoutClose'",
                "'JOB_DEMO_CRON_CLOSE_1M'",
                "'JOB_DEMO_CRON_CLOSE_5M'",
                "SET `status` = 'DISABLED'",
                "`next_trigger_time` = NULL");
        log.info("用例结果：正式任务和历史 CRON 演示任务状态迁移契约完整");
    }

    /**
     * 兼容从仓库根目录或 service-admin 模块目录执行测试。
     *
     * @param relativePath service-admin 模块内的相对路径
     * @return 可读取的迁移脚本路径
     */
    private Path modulePath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("service-admin").resolve(relativePath);
    }
}
