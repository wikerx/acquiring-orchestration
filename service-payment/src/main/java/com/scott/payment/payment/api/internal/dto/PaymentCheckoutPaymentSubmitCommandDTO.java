package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
/**
 * Hosted Checkout 提交支付内部命令。
 */
@Getter
@Setter
public class PaymentCheckoutPaymentSubmitCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 不透明访问令牌摘要，禁止通过内部接口传递令牌明文。 */
    @NotBlank(message = "tokenHash is required")
    private String tokenHash;

    /** Hosted Checkout 会话号，必须与令牌摘要绑定。 */
    @NotBlank(message = "checkoutSessionId is required")
    private String checkoutSessionId;

    /** 本次支付尝试请求号，用于数据库幂等，重试时必须保持稳定。 */
    @NotBlank(message = "attemptRequestId is required")
    private String attemptRequestId;

    /** 付款人选择的支付方式编码。 */
    @NotBlank(message = "paymentMethod is required")
    private String paymentMethod;

    /** 不包含原始卡数据的请求指纹，用于识别幂等冲突。 */
    private String requestFingerprint;
    /** 当前调用链追踪号。 */
    private String traceId;
    /** 客户端 IP 摘要。 */
    private String clientIpHash;
    /** 3DS 渠道调用需要的真实 IP，仅允许在当前请求调用栈使用。 */
    private String payerIp;
    /** User-Agent 摘要。 */
    private String userAgentHash;
    /** Origin 摘要。 */
    private String originHash;
    /** Referer 摘要。 */
    private String refererHash;
    /** 3DS 所需浏览器环境 JSON，持久化前必须控制字段和长度。 */
    private String browserInfoJson;
    /** 已摘要化的设备环境 JSON，不能作为唯一身份凭据。 */
    private String deviceInfoJson;

    /** 浏览器生成的卡数据密文信封，OpenAPI 只能原样转发。 */
    @Valid
    @NotNull(message = "cardDataEnvelope is required")
    private CardDataEnvelopeDTO cardDataEnvelope;

    /** 解密后的卡数据仅供 service-payment 当前调用栈使用，禁止从 JSON 接口绑定。 */
    @JsonIgnore
    private CardInfoDTO cardInfo;

    /** 渠道及 3DS 所需账单资料，日志中必须脱敏。 */
    @Valid
    private BillingCardHolderInfoDTO billingCardHolderInfo;

    /**
     * 付款人提交的敏感银行卡资料。
     */
    @Getter
    @Setter
    public static class CardInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 完整 PAN，严禁写入缓存、数据库、日志或响应。 */
        private String cardNo;
        /** 卡片到期月份，格式 {@code MM}。 */
        private String expirationMonth;
        /** 卡片到期年份，格式 {@code yyyy}。 */
        private String expirationYear;
        /** CVV/CVC，严禁写入缓存、数据库、日志或响应。 */
        private String securityCode;
        /** 卡面持卡人姓名，属于个人信息，日志中必须脱敏。 */
        private String cardholderName;
    }

    /** 浏览器混合加密信封；密文字段不得写入普通日志或持久化。 */
    @Getter
    @Setter
    public static class CardDataEnvelopeDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank(message = "cardDataEnvelope.algorithm is required")
        private String algorithm;
        @NotBlank(message = "cardDataEnvelope.keyId is required")
        @Size(max = 64, message = "cardDataEnvelope.keyId is too long")
        private String keyId;
        @NotBlank(message = "cardDataEnvelope.encryptedKey is required")
        @Size(max = 1024, message = "cardDataEnvelope.encryptedKey is too long")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "cardDataEnvelope.encryptedKey format does not match")
        private String encryptedKey;
        @NotBlank(message = "cardDataEnvelope.iv is required")
        @Size(max = 32, message = "cardDataEnvelope.iv is too long")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "cardDataEnvelope.iv format does not match")
        private String iv;
        @NotBlank(message = "cardDataEnvelope.ciphertext is required")
        @Size(max = 8192, message = "cardDataEnvelope.ciphertext is too long")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "cardDataEnvelope.ciphertext format does not match")
        private String ciphertext;
        @NotBlank(message = "cardDataEnvelope.nonce is required")
        @Size(max = 128, message = "cardDataEnvelope.nonce is too long")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "cardDataEnvelope.nonce format does not match")
        private String nonce;
    }

    /**
     * 支付渠道和 3DS 校验所需的账单持卡人资料。
     */
    @Getter
    @Setter
    public static class BillingCardHolderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 持卡人名字，属于个人信息。 */
        private String firstName;
        /** 持卡人姓氏，属于个人信息。 */
        private String lastName;
        /** 付款人邮箱，仅按渠道需要传递，日志中必须脱敏。 */
        private String email;
        /** 付款人联系电话，仅按渠道需要传递。 */
        private String phone;
        /** ISO 3166-1 alpha-3 账单国家或地区代码。 */
        private String country;
        /** 账单州或省。 */
        private String state;
        /** 账单城市。 */
        private String city;
        /** 账单街道地址，禁止写入普通日志。 */
        private String street;
        /** 账单邮编。 */
        private String postal;
    }
}
