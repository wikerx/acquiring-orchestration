package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionAbnormalEventDO
 * @date : 2026-08-06 00:00
 * @description : 渠道勾兑异常案件实体，位于支付持久化层，保存脱敏证据、资金差异快照、精确分片时间和案件处置状态。
 * @status : create
 */
@Data
@TableName("transaction_abnormal_event")
public class TransactionAbnormalEventDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
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
    private LocalDateTime resolvedTime;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private String deduplicationKey;
    private String merchantId;
    private String merchantOrderNo;
    private String sourceTransactionId;
    private LocalDateTime sourceTransactionDateTime;
    private LocalDateTime rootTransactionDateTime;
    private String transactionType;
    private String platformStatus;
    private String channelCode;
    private String channelOrderNo;
    private String channelTransactionId;
    private String channelStatus;
    private String channelMatchResult;
    private String detectSource;
    private String platformCurrency;
    private BigDecimal platformAmount;
    private String channelCurrency;
    private BigDecimal channelAmount;
    private BigDecimal amountDifference;
    private Integer currencyExponent;
    private LocalDateTime lastSeenTime;
    private Integer occurrenceCount;
    private String assignedToId;
    private String assignedToName;
    private LocalDateTime assignedTime;
    private String resolutionType;
    private String resolutionReferenceId;
    private Integer merchantNotifyRequired;
    private Integer version;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
