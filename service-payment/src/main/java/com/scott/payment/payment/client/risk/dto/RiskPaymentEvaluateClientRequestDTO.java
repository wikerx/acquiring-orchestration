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
 * @description : service-payment 调用 service-risk 的支付风控评估请求 DTO，仅传递风控必要上下文，不传递完整卡号和 CVV。
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
     * 卡品牌。
     */
    private String cardBrand;

    /**
     * 卡 BIN 前六位，不包含完整 PAN。
     */
    private String cardBin;

    /**
     * 卡号后四位，不包含完整 PAN。
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
