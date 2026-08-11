package com.scott.payment.payment.client.risk.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskPaymentEvaluateClientRequestDTO
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : service-payment 调用 service-risk 的支付风控评估请求 DTO，仅在内部链路传递风控必要上下文；完整卡号只用于 service-risk 内存匹配，禁止写日志、MQ 和交易库。
 * @status : create
 */
@Data
public class RiskPaymentEvaluateClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 支付平台商户号。
     */
    private String merchantId;

    /**
     * 商户订单号。
     */
    private String merchantOrderNo;

    /**
     * 支付核心生成的平台当前交易号，用于串联 payment、risk 和渠道调用日志。
     */
    private String transactionId;

    /**
     * 交易类型，对齐字典 transaction_type。
     */
    private String transactionType;

    /**
     * 支付方式。
     */
    private String paymentMethod;

    /**
     * 请求唯一标识。
     */
    private String requestId;

    /**
     * 支付核心确认的可信请求来源，例如 OPENAPI 或 HOSTED_CHECKOUT。
     */
    private String requestSource;

    /**
     * 交易金额，主币种单位。
     */
    private BigDecimal amount;

    /**
     * 交易币种，ISO 4217 三位大写代码。
     */
    private String currency;

    /**
     * 交易请求时间。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 请求体指纹，不包含完整明文。
     */
    private String requestFingerprint;

    /**
     * 请求来源网址。
     */
    private String sourceUrl;

    /**
     * 付款人 IP。
     */
    private String payerIp;

    /**
     * 付款人浏览器 User-Agent。
     */
    private String userAgent;

    /**
     * 子商户 ID。
     */
    private String subMerchantId;

    /**
     * 商户行业分类或 MCC。
     */
    private String merchantCategory;

    /**
     * 子商户国家或地区代码。
     */
    private String subMerchantCountryCode;

    /**
     * 持卡人姓名，由账单名字和姓氏在支付核心内存中组合。
     */
    private String cardholderName;

    /**
     * 个人子商户经营者姓名，用于 AML 法人规则匹配。
     */
    private String legalPerson;

    /**
     * 子商户企业名称，用于 AML 企业规则匹配。
     */
    private String enterprise;

    /**
     * 子商户账单地址，用于 AML 商户账单地址规则匹配。
     */
    private String merchantBillingAddress;

    /**
     * 卡品牌。
     */
    private String cardBrand;

    /**
     * 完整 PAN，仅限 service-payment 到 service-risk 内部风控调用使用；禁止日志明文输出、禁止落库、禁止写 MQ。
     */
    private String cardNo;

    /**
     * 卡 BIN 前缀，按内部风控区间规则最多传前 11 位。
     */
    private String cardBin;

    /**
     * 卡号后四位。
     */
    private String cardLast4;

    /**
     * 账单国家或地区代码。
     */
    private String billingCountry;

    /**
     * 账单邮箱，属于个人信息，禁止日志明文输出。
     */
    private String billingEmail;

    /**
     * 账单手机号，属于个人信息，禁止日志明文输出。
     */
    private String billingPhone;

    /**
     * 账单街道地址，属于个人信息，禁止日志明文输出。
     */
    private String billingAddress;

    /**
     * 账单邮编。
     */
    private String billingZip;

    /**
     * 账单州、省或区域代码。
     */
    private String billingRegion;

    /**
     * 账单城市，与国家、州省共同用于分层区域名单匹配。
     */
    private String billingCity;

    /**
     * 商户体系内客户标识。
     */
    private String customerId;

    /**
     * 商户生成的稳定设备指纹。
     */
    private String deviceFingerprint;

    /**
     * 收货街道地址。
     */
    private String shippingAddress;

    /**
     * 收货邮编。
     */
    private String shippingZip;

    /**
     * 收货国家或地区三字码。
     */
    private String shippingCountry;

    /**
     * 3DS ECI 值。
     */
    private String threeDsEci;

    /**
     * 3DS 协议版本。
     */
    private String threeDsVersion;

    /**
     * 3DS DS 交易 ID。
     */
    private String threeDsTransactionId;
}
