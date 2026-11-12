package com.scott.payment.admin.dto.exporttask;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理端交易域异步导出源内部契约，仅承载冻结查询、冻结商户范围和安全行投影。
 */
public final class AdminTransactionExportInternalDTOs {

    private AdminTransactionExportInternalDTOs() {
    }

    /**
     * 任务创建时冻结的交易主单查询条件。
     */
    @Data
    public static class TransactionOrderQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String merchantOrderNo;
        private String transactionId;
        private String sourceTransactionId;
        private String transactionType;
        private String transactionStatus;
        private String channelCode;
        private String paymentMethod;
        private String paymentBrand;
        private String cardBin;
        private String channelOrderNo;
        private String merchantResponseCode;
        private String channelResponseCode;
        private String authCode;
        private String acquirerReferenceNo;
        private String channelMatchStatus;
        private String reconciliationStatus;
        private String settlementStatus;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private String queryTimeZone;
    }

    /**
     * 任务创建时冻结的商户通知查询条件。
     */
    @Data
    public static class MerchantNotificationQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String transactionId;
        private String notifyStatus;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private String queryTimeZone;
    }

    /**
     * 任务创建时冻结的退款查询条件。
     */
    @Data
    public static class RefundQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String refundTransactionId;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private String merchantOperationNo;
        private String transactionType;
        private String refundScope;
        private String approvalStatus;
        private String transactionStatus;
        private String requestSource;
        private String channelCode;
        private String channelOrderNo;
        private String acquirerReferenceNo;
        private String paymentMethod;
        private String paymentBrand;
        private String labelCurrency;
        private String transactionCurrency;
        private BigDecimal minimumTransactionAmount;
        private BigDecimal maximumTransactionAmount;
        private String applicantId;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime completeBeginTime;
        private LocalDateTime completeEndTime;
        private String queryTimeZone;
    }

    /**
     * service-data 分页读取交易主单导出源的请求。
     */
    @Data
    public static class OrderPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private TransactionOrderQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /**
     * service-data 分页读取退款导出源的请求。
     */
    @Data
    public static class RefundPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private RefundQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /**
     * service-data 分页读取交易操作流水导出源的请求。
     */
    @Data
    public static class OperationPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private TransactionOrderQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /**
     * service-data 分页读取商户通知记录导出源的请求。
     */
    @Data
    public static class MerchantNotificationPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private MerchantNotificationQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /**
     * 一页交易主单安全投影和相同条件下总行数。
     */
    @Data
    public static class OrderPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<OrderRow> records = new ArrayList<>();
    }

    /**
     * 一页退款安全投影和相同条件下总行数。
     */
    @Data
    public static class RefundPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<RefundRow> records = new ArrayList<>();
    }

    /**
     * 一页交易操作流水安全投影和相同条件下总行数。
     */
    @Data
    public static class OperationPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<OperationRow> records = new ArrayList<>();
    }

    /**
     * 一页商户通知记录安全投影和相同条件下总行数。
     */
    @Data
    public static class MerchantNotificationPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<MerchantNotificationRow> records = new ArrayList<>();
    }

    /**
     * 交易主单导出安全行，不包含原始渠道报文和支付敏感信息。
     */
    @Data
    public static class OrderRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rootTransactionId;
        private String latestTransactionId;
        private String merchantId;
        private String merchantOrderNo;
        private String merchantOrderId;
        private String transactionType;
        private String transactionStatus;
        private String lifecycleStatus;
        private BigDecimal currentAmount;
        private String currentCurrency;
        private BigDecimal authorizedAmount;
        private BigDecimal capturedAmount;
        private BigDecimal refundedAmount;
        private BigDecimal transactionRate;
        private String channelCode;
        private String channelOrderNo;
        private String merchantResponseCode;
        private String merchantResponseMessage;
        private String channelMatchStatus;
        private Integer threeDsEnabled;
        private Integer dccEnabled;
        private Integer edcEnabled;
        private String reconciliationStatus;
        private String settlementStatus;
        private LocalDateTime transactionDateTime;
    }

    /**
     * 退款导出安全行，不包含渠道原文和支付敏感信息。
     */
    @Data
    public static class RefundRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String refundTransactionId;
        private String sourceTransactionId;
        private String merchantId;
        private String merchantOrderNo;
        private String transactionType;
        private String refundScope;
        private String requestSource;
        private BigDecimal transactionAmount;
        private String transactionCurrency;
        private String transactionStatus;
        private String approvalStatus;
        private String applicantName;
        private String channelCode;
        private String channelOrderNo;
        private LocalDateTime transactionDateTime;
        private LocalDateTime completeTime;
    }

    /**
     * 交易操作流水导出安全行，不包含原始渠道报文和支付敏感信息。
     */
    @Data
    public static class OperationRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String transactionId;
        private String sourceTransactionId;
        private String merchantId;
        private String merchantOrderNo;
        private String merchantOrderId;
        private String transactionType;
        private String transactionStatus;
        private BigDecimal transactionAmount;
        private String transactionCurrency;
        private BigDecimal transactionRate;
        private String merchantResponseCode;
        private String merchantResponseMessage;
        private String merchantNotificationStatus;
        private String paymentMethod;
        private String paymentBrand;
        private String cardBin;
        private String cardNumberMasked;
        private String channelCode;
        private String channelOrderNo;
        private String channelTransactionId;
        private String channelResponseCode;
        private String channelResponseMessage;
        private String authCode;
        private String acquirerReferenceNo;
        private String channelMatchStatus;
        private Integer threeDsEnabled;
        private Integer dccEnabled;
        private Integer edcEnabled;
        private String reconciliationStatus;
        private String settlementStatus;
        private LocalDateTime transactionDateTime;
        private LocalDateTime operationTime;
    }

    /**
     * 商户通知记录导出安全行，不包含回调地址、签名材料、通知载荷或响应正文。
     */
    @Data
    public static class MerchantNotificationRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String notifyId;
        private String transactionId;
        private String operationId;
        private String merchantId;
        private String merchantOrderNo;
        private String notifyType;
        private String eventType;
        private String notifyStatus;
        private Integer lastAttemptNo;
        private Integer maxRetryCount;
        private LocalDateTime nextRetryTime;
        private LocalDateTime successTime;
        private String failReason;
        private LocalDateTime transactionDateTime;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 任务创建时冻结的渠道勾兑异常查询条件。 */
    @Data
    public static class ChannelMatchAbnormalQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String eventId;
        private String transactionId;
        private String merchantId;
        private String merchantOrderNo;
        private String abnormalType;
        private String abnormalLevel;
        private String eventStatus;
        private String transactionType;
        private String platformStatus;
        private String channelCode;
        private String channelOrderNo;
        private String assignedToId;
        private String detectSource;
        private Integer minimumOccurrenceCount;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private String queryTimeZone;
    }

    /** 渠道勾兑异常导出源分页请求，商户范围来自任务快照。 */
    @Data
    public static class ChannelMatchAbnormalPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private ChannelMatchAbnormalQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /** 一页渠道勾兑异常安全投影。 */
    @Data
    public static class ChannelMatchAbnormalPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<ChannelMatchAbnormalRow> records = new ArrayList<>();
    }

    /** 渠道勾兑异常导出安全行，不包含渠道原始报文。 */
    @Data
    public static class ChannelMatchAbnormalRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String abnormalEventId;
        private String transactionId;
        private String merchantId;
        private String merchantOrderNo;
        private String abnormalType;
        private String abnormalLevel;
        private String eventStatus;
        private String platformStatus;
        private String channelCode;
        private String channelStatus;
        private BigDecimal platformAmount;
        private String platformCurrency;
        private Integer occurrenceCount;
        private String assignedToName;
        private LocalDateTime firstSeenTime;
        private LocalDateTime lastSeenTime;
    }
}
