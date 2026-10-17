package com.scott.payment.payment.schedule;

import com.scott.payment.payment.service.TransactionCallbackInboxWorker;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionCallbackInboxSchedulerTests
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox 调度器测试，验证数据库到期扫描使用统一交易业务时区且批量上限受控。
 * @status : create
 */
class TransactionCallbackInboxSchedulerTests {

    /** 调度器必须用注入时钟生成业务时间，并把配置批量值限制为最多 500。 */
    @Test
    void shouldUseBusinessClockAndClampBatchSize() {
        TransactionCallbackInboxWorker worker = mock(TransactionCallbackInboxWorker.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-17T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        TransactionCallbackInboxScheduler scheduler =
                new TransactionCallbackInboxScheduler(worker, 800, clock);

        scheduler.processDueCallbacks();

        verify(worker).processDue(LocalDateTime.of(2026, 10, 17, 12, 0), 500);
    }
}
