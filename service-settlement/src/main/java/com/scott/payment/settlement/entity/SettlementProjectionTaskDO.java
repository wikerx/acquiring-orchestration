package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 结算提交后异步投影交易状态并发布缓存联动消息的可靠任务。 */
@Data
public class SettlementProjectionTaskDO {
    private Long id;
    private String taskNo;
    private String settlementBatchNo;
    /** SETTLE 或 REVERSE。 */
    private String projectionAction;
    private String originalBatchNo;
    private Long candidateId;
    private String transactionId;
    private LocalDateTime transactionDateTime;
    private Integer clearingRevision;
    private String operationId;
    private String merchantId;
    private String settlementCurrency;
    private BigDecimal settlementAmount;
    private LocalDate settlementDate;
    private String taskStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastFailureCode;
    private LocalDateTime completedTime;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
