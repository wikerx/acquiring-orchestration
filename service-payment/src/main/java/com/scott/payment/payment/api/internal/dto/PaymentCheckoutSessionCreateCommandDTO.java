package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建 Hosted Checkout 会话内部命令。
 */
@Data
public class PaymentCheckoutSessionCreateCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 已通过内部签名边界传入的商户号。 */
    @NotBlank(message = "merchantId is required")
    private String merchantId;

    /** 商户订单号，与商户号共同限定订单归属。 */
    @NotBlank(message = "merchantOrderNo is required")
    private String merchantOrderNo;

    /** 商户本次会话创建请求号，用于数据库幂等。 */
    @NotBlank(message = "merchantRequestId is required")
    private String merchantRequestId;

    /** 商户加密请求体指纹，用于检测相同请求号下的报文冲突。 */
    @NotBlank(message = "requestFingerprint is required")
    private String requestFingerprint;

    /** 订单金额，单位为 {@link #currency} 的主币种单位。 */
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
    private BigDecimal amount;

    /** ISO 4217 三位币种代码。 */
    @NotBlank(message = "currency is required")
    private String currency;

    /** 币种小数位数，仅用于精确换算和展示，不改变原始金额。 */
    @NotNull(message = "currencyExponent is required")
    private Integer currencyExponent;

    /** 会话对应的支付动作，当前通常为一步支付。 */
    private String paymentAction;
    /** 付款页展示的订单主题。 */
    private String orderSubject;
    /** 付款页展示的订单说明。 */
    private String orderDescription;
    /** 订单商品快照 JSON，不得包含卡号、CVV 或密钥。 */
    private String orderItemsJson;

    /** 创建会话时固化的允许支付方式快照。 */
    @Valid
    private List<AllowedPaymentMethodDTO> allowedPaymentMethods;

    /** 平台收银台前端受控基础地址。 */
    @NotBlank(message = "checkoutDomain is required")
    private String checkoutDomain;

    /** 付款页语言或地区标识。 */
    private String locale;
    /** 付款页公开展示的商户名称。 */
    private String merchantDisplayName;
    /** 付款页公开展示的商户 Logo 地址。 */
    private String merchantLogoUrl;
    /** 支付完成后允许返回的商户地址。 */
    private String merchantReturnUrl;
    /** 付款取消后允许返回的商户地址。 */
    private String merchantCancelUrl;
    /** 商户通知地址摘要，不持久化完整地址。 */
    private String merchantNotifyUrlHash;
    /** 商户通知地址 AES-GCM 密文，不得进入日志或页面响应。 */
    private String merchantNotifyUrlCiphertext;
    /** 付款人预填信息 AES-GCM 密文。 */
    private String payerInfoCiphertext;
    /** 账单预填信息 AES-GCM 密文。 */
    private String billingInfoCiphertext;
    /** 付款人 ISO 3166 国家或地区代码。 */
    private String payerCountry;
    /** 已脱敏的付款人邮箱。 */
    private String payerEmailMasked;
    /** 付款人邮箱摘要，用于关联而不保存明文。 */
    private String payerEmailHash;
    /** 是否允许同一会话在失败后重试：0 否，1 是。 */
    private Integer retryAllowed;
    /** 同一会话允许创建的最大支付尝试次数。 */
    private Integer maxAttemptCount;
    /** 会话失效时间。 */
    private LocalDateTime expireTime;
    /** 已脱敏的会话创建来源摘要。 */
    private String requestSource;
    /** 创建会话调用链追踪号。 */
    private String traceId;

    /**
     * 会话创建时固化的单个支付方式配置。
     */
    @Data
    public static class AllowedPaymentMethodDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 平台支付方式编码。 */
        @NotBlank(message = "paymentMethod is required")
        private String paymentMethod;

        /** 可选指定渠道编码；为空时由支付路由决定。 */
        private String channelCode;
        /** 允许付款人选择的卡品牌或支付品牌。 */
        private List<String> brands;
        /** 当前支付方式的 3DS 执行模式。 */
        private String threeDsMode;
    }
}
