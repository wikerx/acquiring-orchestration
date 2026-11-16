package com.scott.payment.data.config.export;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 异步导出执行器配置测试，验证内部调度请求不会在容量不足时同步执行文件生成。
 */
class DataExportConfigurationTest {

    /**
     * 唯一工作线程被占用后必须拒绝第二个任务，不能让提交线程执行或进入等待队列。
     */
    @Test
    void shouldRejectWhenAllExportExecutionSlotsAreBusy() throws Exception {
        DataExportProperties properties = new DataExportProperties();
        properties.setDispatchConcurrency(1);
        ThreadPoolTaskExecutor executor = new DataExportConfiguration().dataExportTaskExecutor(properties);
        executor.initialize();
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                taskStarted.countDown();
                try {
                    releaseTask.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(taskStarted.await(500, TimeUnit.MILLISECONDS)).isTrue();

            assertThatThrownBy(() -> executor.execute(() -> { }))
                    .isInstanceOf(RejectedExecutionException.class);
        } finally {
            releaseTask.countDown();
            executor.shutdown();
        }
    }
}
