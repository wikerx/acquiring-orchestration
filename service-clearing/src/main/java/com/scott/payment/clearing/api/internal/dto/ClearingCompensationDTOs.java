package com.scott.payment.clearing.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** 清分补偿扫描接口模型。 */
public final class ClearingCompensationDTOs {

    private ClearingCompensationDTOs() {
    }

    /** 单季度补偿扫描请求。 */
    @Data
    public static class CompensationScanRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String mode;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime cursorTransactionDateTime;
        private Long cursorId;
        private Integer limit;
    }

    /** 单个补偿候选及处置结果。 */
    @Data
    public static class CompensationRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private String transactionId;
        private String operationId;
        private String merchantId;
        private String clearingStatus;
        private String reason;
        private String result;
        private LocalDateTime transactionDateTime;
    }

    /** 补偿扫描响应。 */
    @Data
    public static class CompensationScanResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private String mode;
        private int scannedCount;
        private int writeCount;
        private int skippedCount;
        private boolean hasMore;
        private LocalDateTime nextCursorTransactionDateTime;
        private Long nextCursorId;
        private List<CompensationRecord> records = Collections.emptyList();
    }
}
