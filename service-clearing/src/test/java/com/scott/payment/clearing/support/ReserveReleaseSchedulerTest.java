package com.scott.payment.clearing.support;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReserveReleaseSchedulerTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证固定代码调度入口无需业务开关即可触发保证金释放扫描。
 * @status : create
 */
class ReserveReleaseSchedulerTest {

    @Test
    void runShouldInvokeReserveReleaseScan() {
        ReserveReleaseScanService scanService = mock(ReserveReleaseScanService.class);
        ReserveReleaseScheduler scheduler = new ReserveReleaseScheduler(scanService);

        scheduler.run();

        verify(scanService).scan();
    }
}
