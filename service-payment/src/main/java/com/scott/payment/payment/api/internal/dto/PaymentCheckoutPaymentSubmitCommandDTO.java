package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    /** 敏感卡数据，仅允许在当前支付调用链内存中过境。 */
    @Valid
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
