package com.scott.payment.risk.api.internal.dto;

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
 * @classname : RiskPaymentEvaluateRequestDTO
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单支付实时风控评估请求 DTO，位于 service-risk 内部接口 DTO 层；完整卡号只允许在内存中用于名单和频控匹配，禁止写日志、MQ 和交易库。
 * @status : create
 */
@Data
public class RiskPaymentEvaluateRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 支付平台商户号，用于定位商户风控策略，不能为空。
     */
    @NotBlank(message = "merchantId is required")
    private String merchantId;

    /**
     * 商户订单号，用于风控审计和同商户重复风险识别，不能为空。
     */
    @NotBlank(message = "merchantOrderNo is required")
    private String merchantOrderNo;

    /**
     * 支付核心生成的平台当前交易号，用于串联 OpenAPI、payment、risk 和渠道日志。
     */
    private String transactionId;

    /**
     * 交易类型，对齐字典 transaction_type，例如 AUTHORIZATION。
     */
    private String transactionType;

    /**
     * 支付方式，例如 BANK_CARD，用于选择风控规则集。
     */
    private String paymentMethod;

    /**
     * 已完成路由的渠道编码；路由前评估允许为空，渠道专属 3DS 策略评估必须提供。
     */
    private String channelCode;

    /**
     * 上游请求唯一标识，用于串联 OpenAPI、payment 和 risk 调用链路。
     */
    private String requestId;

    /**
     * 支付核心确认的可信请求来源，例如 OPENAPI 或 HOSTED_CHECKOUT。
     */
    private String requestSource;

    /**
     * 交易金额，主币种单位，不能为空且必须大于 0。
     */
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
    private BigDecimal amount;

    /**
     * 交易币种，使用 ISO 4217 三位大写币种代码，不能为空。
     */
    @NotBlank(message = "currency is required")
    private String currency;

    /**
     * 商户侧交易发起时间，允许为空，空值由风控服务按接收时间审计。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 请求体指纹，用于排查重复请求，不包含完整明文请求。
     */
    private String requestFingerprint;

    /**
     * 请求来源网址，用于识别恶意来源或异常跳转来源。
     */
    private String sourceUrl;

    /**
     * 付款人 IP，用于地域、频率和阻断名单判断。
     */
    private String payerIp;

    /**
     * 付款人浏览器 User-Agent，用于设备侧风险识别。
     */
    private String userAgent;

    /**
     * 子商户 ID，用于平台商户下多子商户风险隔离。
     */
    private String subMerchantId;

    /**
     * MCC 或商户行业分类，用于行业风险策略。
     */
    private String merchantCategory;

    /**
     * 子商户国家或地区代码，用于跨境风险判断。
     */
    private String subMerchantCountryCode;

    /**
     * 持卡人姓名，仅用于内存名单匹配，禁止日志明文输出。
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
     * 卡品牌，例如 VISA、MASTERCARD；允许为空。
     */
    private String cardBrand;

    /**
     * 完整 PAN，仅限内部风控内存匹配使用；禁止日志明文输出、禁止落库、禁止写 MQ。
     */
    private String cardNo;

    /**
     * 卡 BIN 前缀，最多 11 位，用于 BIN 区间和发卡国家识别。
     */
    private String cardBin;

    /**
     * 卡号后四位，仅用于排查和风险辅助。
     */
    private String cardLast4;

    /**
     * 账单国家或地区代码，用于 AVS 和跨境风险判断。
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

    /** 商户体系内付款人 ID。 */
    private String payerId;
    /** 付款人姓名，仅用于内存名单匹配。 */
    private String payerName;
    /** 付款人邮箱，禁止日志明文输出。 */
    private String payerEmail;
    /** 付款人手机号，禁止日志明文输出。 */
    private String payerPhone;
    /** 付款人国家或地区代码。 */
    private String payerCountry;
    /** 付款人街道地址。 */
    private String payerAddress;
    /** 付款人邮编。 */
    private String payerZip;
    /** 付款人州、省或区域。 */
    private String payerRegion;
    /** 付款人城市。 */
    private String payerCity;
    /** 付款会话 ID，仅用于当前风控调用。 */
    private String payerSessionId;

    /**
     * 商户体系内客户标识，用于白名单和频率规则。
     */
    private String customerId;

    /**
     * 商户生成的稳定设备指纹，用于黑白名单和频率规则。
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

    /** 收货人姓名。 */
    private String shippingName;
    /** 收货人邮箱。 */
    private String shippingEmail;
    /** 收货人手机号。 */
    private String shippingPhone;
    /** 收货州、省或区域。 */
    private String shippingRegion;
    /** 收货城市。 */
    private String shippingCity;

    /**
     * 3DS ECI 值，用于判断认证责任转移状态。
     */
    private String threeDsEci;

    /**
     * 3DS 协议版本，用于识别认证链路能力。
     */
    private String threeDsVersion;

    /**
     * 3DS DS 交易 ID，用于证明已完成认证链路。
     */
    private String threeDsTransactionId;
}
