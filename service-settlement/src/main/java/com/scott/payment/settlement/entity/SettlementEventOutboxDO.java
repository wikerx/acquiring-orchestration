package com.scott.payment.settlement.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 结算服务本地事件 Outbox；保存冻结 JSON，至少一次发布到交易级 FIFO Topic。 */
@Data
public class SettlementEventOutboxDO {
    private Long id;
    private String eventNo;
    private String settlementBatchNo;
    private Long candidateId;
    private String topic;
    private String tag;
    private String messageKey;
    private String messageGroup;
    private String payloadJson;
    private String eventStatus;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String processingOwner;
    private LocalDateTime processingDeadline;
    private LocalDateTime sentTime;
    private String lastFailureCode;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
