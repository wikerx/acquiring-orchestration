package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateCommandDTO
 * @date : 2026-05-31 21:00
 * @email : scott_x@163.com
 * @description : service-openapi 调用 service-payment 创建收单交易的内部请求参数，承载渠道调用、风控执行和生命周期关联所需上下文；卡号和安全码只允许内存传递，禁止日志、MQ 和落库明文保存。
 * @status : create
 */
@Data
public class PaymentCreateCommandDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 支付平台颁发的商户号，用于定位商户、通道、风控和费率配置。
     */
    @NotBlank(message = "merchantId is required")
    private String merchantId;

    /**
     * 商户订单号，商户侧保持唯一，用于幂等和交易查询。
     */
    @NotBlank(message = "merchantOrderNo is required")
    private String merchantOrderNo;

    /**
     * 交易类型，对齐字典 transaction_type，例如 AUTHORIZATION、PAYMENT、CAPTURE、REFUND。
     */
    private String transactionType;

    /**
     * 支付方式，例如 BANK_CARD、PAYPAL、APPLE_PAY。
     */
    private String paymentMethod;

    /**
     * 商户请求唯一标识，通常来自 JWT jti 或交易 transactionId，用于链路追踪和幂等。
     */
    private String requestId;

    /**
     * 订单金额，主币种单位，例如 123.45 USD。
     */
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
    private BigDecimal amount;

    /**
     * 订单币种，使用 ISO 4217 三位大写币种代码。
     */
    @NotBlank(message = "currency is required")
    private String currency;

    /**
     * 交易请求时间，按 UTC+8 业务时区写入。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 请求体安全摘要，OpenAPI 层传入用于排查，但不保存完整密文和敏感卡信息。
     */
    private String requestFingerprint;

    /**
     * 商户侧子商户信息，用于风控、渠道资料补充和 MID 路由。
     */
    private SubMerchantInfoDTO subMerchantInfo;

    /**
     * 持卡人账单信息，用于 AVS、风控和渠道请求。
     */
    private BillingCardHolderInfoDTO billingCardHolderInfo;

    /**
     * 卡信息，仅允许在 Payment 到渠道调用的内存链路中使用。
     */
    private CardInfoDTO cardInfo;

    /**
     * 3DS 认证信息，用于渠道授权和责任转移判断。
     */
    private ThreeDsInfoDTO threeDsInfo;

    /**
     * 交易扩展信息，包含商户交易 ID、原交易引用和回调地址。
     */
    private TransactionInfoDTO transactionInfo;

    /**
     * 原交易参考号，请款、退款、撤销等后续动作可用于关联原始交易。
     */
    private String sourceReference;

    /**
     * 商户通知回调地址，交易状态变化后系统可按该地址推送异步通知。
     */
    private String callbackUrl;

    /**
     * 请求来源站点，优先来自 Origin 或 Referer，用于来源网址风控。
     */
    private String sourceUrl;

    /**
     * 付款人 IP，来自网关转发头或请求远端地址，用于风控识别。
     */
    private String payerIp;

    /**
     * 付款人浏览器 User-Agent，用于风控、3DS 和排查。
     */
    private String userAgent;

    @Data
    public static class SubMerchantInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String subName;

        private String subCompanyName;

        private String subId;

        private String subStreet;

        private String subCity;

        private String subState;

        private String subCountryCode;

        private String subEmail;

        private String subPhone;

        private String subPostal;

        private String subTaxId;

        private String merchantCategory;

        private String intesCode;

        private String chargeType;
    }

    @Data
    public static class BillingCardHolderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String firstName;

        private String lastName;

        private String phone;

        private String email;

        private String country;

        private String state;

        private String city;

        private String street;

        private String postal;
    }

    @Data
    public static class CardInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * PAN 卡号，只允许用于内存渠道调用，不允许明文日志、MQ 或落库。
         */
        private String cardNo;

        private String expirationMonth;

        private String expirationYear;

        /**
         * CVV/CVC 安全码，只允许用于内存渠道调用，不允许明文日志、MQ 或落库。
         */
        private String securityCode;
    }

    @Data
    public static class ThreeDsInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String eci;

        /**
         * CAVV 属于认证敏感值，日志必须脱敏。
         */
        private String cavv;

        private String dsTransactionId;

        private String threeDsVersion;
    }

    @Data
    public static class TransactionInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String transactionId;

        private String sourceTransactionId;

        private String description;

        private String callbackUrl;

        private String cardBrand;
    }
}
