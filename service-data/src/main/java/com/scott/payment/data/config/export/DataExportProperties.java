package com.scott.payment.data.config.export;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 异步导出执行边界配置，限制分页、租约、重试和文件保留时间。
 */
@Data
@ConfigurationProperties(prefix = "data.export")
public class DataExportProperties {
    private int pageSize = 100;
    private int platformPageSize = 500;
    private int dispatchBatchSize = 5;
    private int dispatchConcurrency = 2;
    private int maxRetryCount = 3;
    private Duration processingLease = Duration.ofMinutes(10);
    private Duration retryDelay = Duration.ofMinutes(2);
    private Duration fileRetention = Duration.ofDays(7);
    private long maxFileSizeBytes = 200L * 1024 * 1024;

    /**
     * 校验导出任务容量参数，防止错误配置造成无界查询或零长度租约。
     */
    public void validate() {
        if (pageSize < 50 || pageSize > 100) {
            throw new IllegalStateException("data.export.page-size must be between 50 and 100");
        }
        if (platformPageSize < 100 || platformPageSize > 500) {
            throw new IllegalStateException("data.export.platform-page-size must be between 100 and 500");
        }
        if (dispatchBatchSize < 1 || dispatchBatchSize > 50
                || dispatchConcurrency < 1 || dispatchConcurrency > 4
                || maxRetryCount < 1 || maxRetryCount > 10) {
            throw new IllegalStateException("data.export batch or retry configuration is invalid");
        }
        if (processingLease == null || processingLease.isNegative() || processingLease.isZero()
                || retryDelay == null || retryDelay.isNegative()
                || fileRetention == null || fileRetention.isNegative() || fileRetention.isZero()
                || maxFileSizeBytes <= 0) {
            throw new IllegalStateException("data.export duration or file size configuration is invalid");
        }
    }
}
