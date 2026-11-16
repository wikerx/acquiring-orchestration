package com.scott.payment.data.client.admin.dto;

import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.RefundQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantNotificationQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TransactionOrderQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ChannelMatchAbnormalQuery;
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
 * Data 调用 Admin 交易域导出源的强类型内部模型，不包含浏览器身份或原始渠道报文。
 */
public final class AdminTransactionExportSourceDTOs {

    private AdminTransactionExportSourceDTOs() {
    }

    /**
     * 交易主单分页请求，商户范围来自任务创建时冻结的可信快照。
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
     * 退款分页请求，商户范围来自任务创建时冻结的可信快照。
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
     * 交易操作流水分页请求，商户范围来自任务创建时冻结的可信快照。
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
     * 商户通知记录分页请求，商户范围来自任务创建时冻结的可信快照。
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
     * 交易主单导出源分页响应。
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
     * 退款导出源分页响应。
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
     * 交易操作流水导出源分页响应。
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
     * 商户通知记录导出源分页响应。
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
     * 管理端交易主单安全导出投影。
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
     * 管理端退款安全导出投影。
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
     * 管理端交易操作流水安全导出投影。
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
     * 管理端商户通知记录安全导出投影，不包含地址、载荷和响应正文。
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

    /** 一页渠道勾兑异常导出来源请求。 */
    @Data
    public static class ChannelMatchAbnormalPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private ChannelMatchAbnormalQuery query;
        private Integer pageNo;
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

    /** 渠道勾兑异常安全导出行，不包含渠道原始报文。 */
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
