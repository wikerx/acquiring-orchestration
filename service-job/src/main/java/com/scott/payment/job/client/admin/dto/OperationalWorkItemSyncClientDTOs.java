package com.scott.payment.job.client.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** service-job 调用统一运营待办同步接口的请求和响应模型。 */
public final class OperationalWorkItemSyncClientDTOs {

    private OperationalWorkItemSyncClientDTOs() {
    }

    /** 同一次任务运行及重试共用稳定请求键。 */
    @Data
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestKey;
        private LocalDateTime syncTime;
    }

    /** 管理服务返回的真实同步状态变更统计。 */
    @Data
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestKey;
        private Integer scannedSourceCount;
        private Integer createdCount;
        private Integer reopenedCount;
        private Integer closedCount;
        private Integer refreshedCount;
        private LocalDateTime syncTime;
    }
}
