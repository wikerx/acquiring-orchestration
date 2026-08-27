package com.scott.payment.job.client.clearing.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** service-job 与 service-clearing 的补偿扫描 JSON 契约。 */
public final class ClearingCompensationClientDTOs {
    private ClearingCompensationClientDTOs() {
    }

    @Data
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;
        private String mode;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime cursorTransactionDateTime;
        private Long cursorId;
        private Integer limit;
    }

    @Data
    public static class Record implements Serializable {
        private static final long serialVersionUID = 1L;
        private String transactionId;
        private String reason;
        private String result;
        private LocalDateTime transactionDateTime;
    }

    @Data
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;
        private String mode;
        private int scannedCount;
        private int writeCount;
        private int skippedCount;
        private boolean hasMore;
        private LocalDateTime nextCursorTransactionDateTime;
        private Long nextCursorId;
        private List<Record> records = Collections.emptyList();
    }
}
