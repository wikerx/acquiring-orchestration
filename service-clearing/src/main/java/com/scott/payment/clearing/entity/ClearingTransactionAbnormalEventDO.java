package com.scott.payment.clearing.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 清分服务对既有 transaction_abnormal_event 逻辑表的最小持久化视图。 */
@Data
public class ClearingTransactionAbnormalEventDO {
    private String abnormalEventId;
    private String transactionId;
    private String operationId;
    private String abnormalType;
    private String abnormalLevel;
    private String eventStatus;
    private String sourceRecordType;
    private String sourceRecordId;
    private String abnormalDescription;
    private String rawReferenceJson;
    private LocalDateTime firstSeenTime;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private String deduplicationKey;
    private String merchantId;
    private String merchantOrderNo;
    private String sourceTransactionId;
    private String transactionType;
    private String platformStatus;
    private String channelMatchResult;
    private String detectSource;
    private LocalDateTime lastSeenTime;
    private Integer occurrenceCount;
    private Integer merchantNotifyRequired;
    private Integer version;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
