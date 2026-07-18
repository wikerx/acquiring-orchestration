package com.scott.payment.openapi.client.payment.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateClientRequestDTO
 * @date : 2026-05-31 21:10
 * @email : scott_x@163.com
 * @description : OpenAPI 调用 service-payment 创建收单交易的内部请求参数，承载渠道调用、风控执行和生命周期关联所需上下文；卡号和安全码只允许内存传递，禁止日志、MQ 和落库明文保存。
 * @status : create
 */
@Data
public class PaymentCreateClientRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 支付平台颁发的商户号。
     */
    private String merchantId;

    /**
     * 商户订单号，来自 orderInfo.orderNo。
     */
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识，来自 orderInfo.orderId，用作资金类幂等键。
     */
    private String merchantOrderId;

    /**
     * 交易类型，对齐字典 transaction_type，例如 AUTHORIZATION、PAYMENT、CAPTURE、REFUND。
     */
    private String transactionType;

    /**
     * 支付方式，例如 BANK_CARD、PAYPAL、APPLE_PAY。
     */
    private String paymentMethod;

    /**
     * 请求唯一号，当前与 merchantOrderId 一致，用于链路排查。
     */
    private String requestId;

    /**
     * 订单金额，主币种单位。
     */
    private BigDecimal amount;

    /**
     * 交易币种，ISO 4217 三位大写字母。
     */
    private String currency;

    /**
     * 交易业务时间，数据库与分表均按 UTC+8 处理。
     */
    private LocalDateTime transactionDateTime;

    /**
     * OpenAPI 收到的密文请求体指纹，仅用于排查链路，不包含原始密文或卡号。
     */
    private String requestFingerprint;

    /**
     * OpenAPI 请求路径，用于后台商户请求日志排查。
     */
    private String openApiRequestPath;

    /**
     * OpenAPI 请求进入服务层的时间。
     */
    private LocalDateTime openApiRequestTime;

    /**
     * 商户请求密文掩码，只保留首尾短片段，禁止传递完整密文。
     */
    private String merchantRequestCipherMasked;

    /**
     * 商户请求脱敏明文 JSON，卡号、CVV、JWT、密钥等敏感字段必须脱敏。
     */
    private String merchantRequestPlainJsonMasked;

    /**
     * 商户侧子商户信息，用于风控、渠道资料补充和 MID 路由。
     */
    private SubMerchantInfoDTO subMerchantInfo;

    /**
     * 持卡人账单信息，用于 AVS、风控和渠道请求。
     */
    private BillingCardHolderInfoDTO billingCardHolderInfo;

    /**
     * 卡信息，仅允许在 OpenAPI 到 Payment 再到渠道调用的内存链路中使用。
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

        /**
         * 查询接口可选平台当前交易 ID；传入时 service-payment 只返回该商户订单下命中的单笔交易动作。
         */
        private String transactionId;

        private String sourceTransactionId;

        /**
         * 原交易业务时间，用于 service-payment 按 transaction_date_time + transaction_id 定位原交易分表。
         * <p>
         * 商户 OpenAPI 不要求上送该字段；内部调用方如已知原交易时间可传入，否则支付核心会先按 transaction_id 解析原交易时间。
         */
        private LocalDateTime sourceTransactionDateTime;

        private String description;

        private String callbackUrl;

        private String cardBrand;
    }
}
