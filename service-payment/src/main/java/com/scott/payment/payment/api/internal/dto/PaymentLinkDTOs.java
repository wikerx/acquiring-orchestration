package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 支付链接内部契约；公开创建对象不接收操作者、可变金额模式或多次成功参数。 */
public final class PaymentLinkDTOs {
    private PaymentLinkDTOs() { }

    @Data
    public static class Create {
        @NotBlank @Size(max = 80)
        private String requestId;
        @Size(max = 120)
        private String linkName;
        @Size(max = 64)
        private String merchantOrderNo;
        @Size(max = 500)
        private String description;
        @NotBlank @Pattern(regexp = "[A-Z]{3}")
        private String currency;
        @NotBlank @Pattern(regexp = "[0-9]{1,6}(\\.[0-9]{1,2})?")
        private String amount;
        @Min(15) @Max(43200)
        private Integer expireMinutes;
        /** 自选截止时刻采用既有上海时间契约，与 expireMinutes 互斥。 */
        private LocalDateTime expireTime;
        @NotBlank @Pattern(regexp = "en-US|zh-CN|AUTO")
        private String locale = "en-US";
        @Size(max = 16)
        private List<@Pattern(regexp = "[A-Z_]{2,40}") String> cardBrands;
        @Size(max = 512)
        @lombok.ToString.Exclude
        private String notifyUrl;
        @Size(max = 512)
        @lombok.ToString.Exclude
        private String redirectUrl;

        @JsonAnySetter
        public void rejectUnsupported(String name, Object value) {
            throw new IllegalArgumentException("Unsupported payment link field");
        }
    }

    /** 仅由已鉴权的 Admin/Merchant 服务生成，不直接反序列化公开请求。 */
    @Data
    public static class Actor {
        private String appCode;
        private String actorId;
        private boolean allMerchants;
        private Set<String> merchantIds = Set.of();
    }

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

    @Data
    public static class Command {
        private Actor actor;
        private String merchantId;
        private String linkId;
        private Create create;
        private Query query;
        private Integer version;
        private String reason;
        private String requestId;
        @lombok.ToString.Exclude
        private String opaqueToken;
    }

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
        private String disableReason;
        private Integer version;
        @lombok.ToString.Exclude
        private String notifyUrl;
        @lombok.ToString.Exclude
        private String redirectUrl;
    }

    /** 匿名投影不得复用包含订单结果/联系人/回调地址的内部详情对象。 */
    @Data
    public static class PublicView {
        private String linkName;
        private String description;
        private String merchantDisplayName;
        private String currency;
        private String amount;
        private Integer currencyExponent;
        private String locale;
        private String status;
        private boolean payable;
        private boolean processing;
        /** 仅最新尝试已确认失败时返回，不含其他付款人的身份或渠道原文。 */
        private LocalDateTime lastFailedTime;
        private LocalDateTime expireTime;
        private LocalDateTime serverTime;
    }

    @Data
    public static class Share {
        @lombok.ToString.Exclude
        private String url;
    }

    @Data
    public static class Started {
        @lombok.ToString.Exclude
        private String checkoutUrl;
        private String checkoutSessionId;
    }

    @Data
    public static class Options {
        /** 默认展示商户结算币种，实际收单能力仍独立校验。 */
        private String defaultCurrency;
        private Map<String, Integer> currencyExponents = Map.of();
        private String merchantId;
        private String merchantDisplayName;
        private List<String> currencies = List.of();
        /** 商户当前币种的完整支付方式，仅作展示；首批链接仍仅授权银行卡。 */
        private Map<String, List<String>> paymentMethodsByCurrency = Map.of();
        private Map<String, List<String>> cardBrandsByCurrency = Map.of();
        private List<String> cardBrands = List.of();
        private List<String> locales = List.of("en-US", "zh-CN", "AUTO");
        private int minExpireMinutes = 15;
        private int maxExpireMinutes = 43200;
    }

    /** 数据库按币种聚合的内部强类型行，不暴露给浏览器。 */
    @Data
    public static class Aggregate {
        private String currency;
        private long total;
        private long active;
        private long completed;
        private long processing;
        private long failedAttempts;
        private java.math.BigDecimal amount;
    }

    @Data
    public static class Summary {
        private long total;
        private long active;
        private long completed;
        private long processing;
        private long failedAttempts;
        private Map<String, String> successfulAmounts = Map.of();
    }

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

    @Data
    public static class Event {
        private String eventId;
        private String eventType;
        private String actorAppCode;
        private String actorId;
        private String reason;
        private LocalDateTime createTime;
    }
}
