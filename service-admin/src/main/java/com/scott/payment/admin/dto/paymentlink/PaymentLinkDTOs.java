package com.scott.payment.admin.dto.paymentlink;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 支付链接内部契约；公开创建对象不接收操作者、可变金额模式或多次成功参数。 */
public final class PaymentLinkDTOs {
    private PaymentLinkDTOs() { }

    /** 共享管理规则，保留当前服务 DTO 的序列化契约。 */
    public static class Create extends com.scott.payment.link.model.PaymentLinkManagementModels.Create { }

    /** 仅由已鉴权的 Admin/Merchant 服务生成，不直接反序列化公开请求。 */
    public record Disable(@NotNull Integer version, @NotBlank @Size(max = 500) String reason,
                          @NotBlank @Size(max = 80) String requestId) { }

    public record SendEmail(@NotBlank @Email @Size(max = 254) String email,
                            @NotBlank @Pattern(regexp = "en-US|zh-CN") String locale,
                            @NotBlank @Size(max = 80) String requestId) { }

    /** 复用管理查询字段，保留本服务公开类型及序列化契约。 */
    public static class Delivery extends com.scott.payment.link.model.PaymentLinkQueryModels.Delivery { }

    /** 共享管理规则，保留当前服务 DTO 的序列化契约。 */
    public static class Actor extends com.scott.payment.link.model.PaymentLinkManagementModels.Actor { }

    /** 复用管理查询字段，保留本服务公开类型及序列化契约。 */
    public static class Query extends com.scott.payment.link.model.PaymentLinkQueryModels.Query { }

    /** 可信服务之间传递的链接操作命令；身份与商户范围由鉴权上下文生成。 */
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

    /** 复用管理查询字段，保留本服务公开类型及序列化契约。 */
    public static class View extends com.scott.payment.link.model.PaymentLinkQueryModels.View { }

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

    /** 经授权生成的持有人访问地址，含付款凭据，不得写入普通日志。 */
    @Data
    public static class Share {
        @lombok.ToString.Exclude
        private String url;
    }

    /** 进入既有收银台的会话标识及地址；创建会话不代表已生成系统交易。 */
    @Data
    public static class Started {
        @lombok.ToString.Exclude
        private String checkoutUrl;
        private String checkoutSessionId;
    }

    /** 共享管理规则，保留当前服务 DTO 的序列化契约。 */
    public static class Options extends com.scott.payment.link.model.PaymentLinkManagementModels.Options { }

    /** 复用管理查询字段，保留本服务公开类型及序列化契约。 */
    public static class Summary extends com.scott.payment.link.model.PaymentLinkQueryModels.Summary { }

    /** 复用管理查询字段，保留本服务公开类型及序列化契约。 */
    public static class Collection extends com.scott.payment.link.model.PaymentLinkQueryModels.Collection { }

    /** 复用管理查询字段，保留本服务公开类型及序列化契约。 */
    public static class Attempt extends com.scott.payment.link.model.PaymentLinkQueryModels.Attempt { }

    /** 复用管理查询字段，保留本服务公开类型及序列化契约。 */
    public static class Event extends com.scott.payment.link.model.PaymentLinkQueryModels.Event { }
}
