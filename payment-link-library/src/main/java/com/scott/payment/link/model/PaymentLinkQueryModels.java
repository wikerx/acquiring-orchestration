package com.scott.payment.link.model;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 共享管理查询模型；不承载匿名付款投影或服务端权限判定。 */
public final class PaymentLinkQueryModels {
    private PaymentLinkQueryModels() { }

    /** 链接列表与统计共用的精确筛选和有界分页参数，仍须叠加操作者数据范围。 */
    @Data
    public static class Query {
        @Size(max = 32)
        private String linkId;
        @Size(max = 64)
        private String merchantOrderNo;
        private String merchantId;
        /** 旧查询字段仅保留以明确拒绝，禁止静默忽略导致查询范围扩大。 */
        @Deprecated @Size(max = 120)
        private String keyword;
        @Pattern(regexp = "ACTIVE|DISABLED|EXPIRED|COMPLETED")
        private String status;
        @Pattern(regexp = "[A-Z]{3}")
        private String currency;
        @Size(max = 80)
        private String channelCode;
        private LocalDateTime createTimeFrom;
        private LocalDateTime createTimeTo;
        @Min(1)
        private int pageNo = 1;
        @Min(1) @Max(100)
        private int pageSize = 10;
    }

    /** 供已授权双端读取的链接详情；包含内部配置，不可直接返回匿名付款人。 */
    @Data
    public static class View {
        private String linkId;
        private String merchantId;
        private String merchantOrderNo;
        private String linkName;
        private String description;
        private String merchantDisplayName;
        private String currency;
        private String amount;
        private Integer currencyExponent;
        private String locale;
        private List<String> cardBrands = List.of();
        /** 创建时冻结的规范方式。旧链接继续以 cardBrands 表达。 */
        private List<String> paymentMethodKeys;
        /** 只有配置范围中所有方式均未实现付款交互时明确返回 false。 */
        private Boolean paymentAvailable;
        private String status;
        private String paymentStatus;
        private Integer attemptCount;
        private String transactionId;
        private LocalDateTime transactionDateTime;
        private LocalDateTime expireTime;
        private LocalDateTime createTime;
        private LocalDateTime paidTime;
        private String createdAppCode;
        private String createdBy;
        /** 当前账号展示名；历史审计 ID 不变，未找到账号时为空。 */
        private String createdByName;
        private String disableReason;
        private Integer version;
        @lombok.ToString.Exclude
        private String notifyUrl;
        @lombok.ToString.Exclude
        private String redirectUrl;
    }

    /** 链接状态计数和分币种成功金额，失败尝试与失败订单保持不同口径。 */
    @Data
    public static class Summary {
        private long total;
        private long active;
        private long completed;
        private long processing;
        private long failedAttempts;
        private Map<String, String> successfulAmounts = Map.of();
    }

    /** 链接对应的收款会话投影，保留提交截止时间及当前付款状态。 */
    @Data
    public static class Collection {
        private String collectionId;
        private String linkId;
        private String merchantOrderNo;
        private String checkoutSessionId;
        private String paymentStatus;
        private LocalDateTime submissionDeadline;
        private LocalDateTime createTime;
    }

    /** 一次收银台提交的付款尝试；只有实际生成核心交易时才关联系统订单。 */
    @Data
    public static class Attempt {
        private String checkoutAttemptId;
        private Integer attemptNo;
        private String transactionId;
        private LocalDateTime transactionDateTime;
        private String status;
        private String paymentMethod;
        private String cardBrand;
        private String currency;
        private String amount;
        private Integer currencyExponent;
        private LocalDateTime createTime;
        private LocalDateTime completeTime;
    }

    /** 链接生命周期的来源、操作者及发生时间，用于只读审计追溯。 */
    @Data
    public static class Event {
        private String eventId;
        private String eventType;
        private String actorAppCode;
        private String actorId;
        /** 受当前受众与商户范围限制的账号展示名。 */
        private String actorName;
        private String reason;
        private LocalDateTime createTime;
    }

    /** 邀请发送的脱敏投影，保留来源应用、操作者和邮件投递结果。 */
    @Data
    public static class Delivery {
        private String requestId;
        private String recipientMasked;
        private String locale;
        private Integer sendStatus;
        private Long emailRecordId;
        private String createdAppCode;
        private String createdBy;
        /** 当前账号展示名；历史审计 ID 不变，未找到账号时为空。 */
        private String createdByName;
        private LocalDateTime createTime;
    }
}
