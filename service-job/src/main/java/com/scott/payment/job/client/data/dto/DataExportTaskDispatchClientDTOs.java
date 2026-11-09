package com.scott.payment.job.client.data.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Job 触发 Data 异步导出任务领取和过期清理的内部客户端契约。
 */
public final class DataExportTaskDispatchClientDTOs {

    private DataExportTaskDispatchClientDTOs() {
    }

    /**
     * 单轮有界调度请求，runId 派生请求键用于审计和任务幂等追踪。
     */
    @Data
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestId;
        private Integer batchSize;
        private LocalDateTime dispatchTime;
    }

    /**
     * 单轮领取、成功、失败和过期清理数量汇总。
     */
    @Data
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestId;
        private Integer claimedCount;
        private Integer successCount;
        private Integer failedCount;
        private Integer expiredCount;
    }
}
