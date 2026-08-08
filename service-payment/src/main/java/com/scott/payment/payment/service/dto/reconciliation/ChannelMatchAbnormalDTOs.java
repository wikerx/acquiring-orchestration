package com.scott.payment.payment.service.dto.reconciliation;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalDTOs
 * @date : 2026-08-06 00:00
 * @description : Payment 勾兑异常内部 DTO 集合，承载案件记录、领取、重查和非资金终态处置命令；管理端查询模型由 service-admin 维护。
 * @status : create
 */
public final class ChannelMatchAbnormalDTOs {

    private ChannelMatchAbnormalDTOs() {
    }

    /** 勾兑异常列表与详情记录，金额使用币种主单位 BigDecimal。 */
    @Data
    public static class AbnormalRecord implements Serializable {
        private static final long serialVersionUID = 1L;
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
        private LocalDateTime lastSeenTime;
        private LocalDateTime resolvedTime;
        private LocalDateTime transactionDateTime;
        private LocalDateTime sourceTransactionDateTime;
        private LocalDateTime rootTransactionDateTime;
        private String merchantId;
        private String merchantOrderNo;
        private String sourceTransactionId;
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
        private Integer occurrenceCount;
        private String assignedToId;
        private String assignedToName;
        private LocalDateTime assignedTime;
        private String resolutionType;
        private String resolutionReferenceId;
        private Integer merchantNotifyRequired;
        private Integer version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 领取或转派命令。 */
    @Data
    public static class AssignCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private String operatorId;
        private String operatorName;
    }

    /** 关闭或忽略命令；不允许携带目标交易状态。 */
    @Data
    public static class ResolveCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private String resolutionType;
        private String reason;
        private String referenceId;
    }

    /** 单笔重新勾兑命令。 */
    @Data
    public static class RequeryCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
    }

    /** 批量重新勾兑案件引用，最多 100 条。 */
    @Data
    public static class BatchRequeryCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<CaseReference> cases = new ArrayList<>();
    }

    /** 批量案件号及精确分片时间。 */
    @Data
    public static class CaseReference implements Serializable {
        private static final long serialVersionUID = 1L;
        private String eventId;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
    }

    /** 批量重查结果；单笔失败不阻断其余案件。 */
    @Data
    public static class BatchRequeryResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private int requestedCount;
        private int acceptedCount;
        private int failedCount;
        private List<String> failedEventIds = new ArrayList<>();
    }
}
