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
 * @description : 收单支付实时风控评估请求 DTO，位于 service-risk 内部接口 DTO 层，只接收必要风控上下文，不接收完整卡号和 CVV。
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
     * 交易类型，对齐字典 transaction_type，例如 AUTHORIZATION。
     */
    private String transactionType;

    /**
     * 支付方式，例如 BANK_CARD，用于选择风控规则集。
     */
    private String paymentMethod;

    /**
     * 上游请求唯一标识，用于串联 OpenAPI、payment 和 risk 调用链路。
     */
    private String requestId;

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
     * 卡品牌，例如 VISA、MASTERCARD；允许为空。
     */
    private String cardBrand;

    /**
     * 卡 BIN 前六位，仅用于风控识别，不接收完整 PAN。
     */
    private String cardBin;

    /**
     * 卡号后四位，仅用于排查和风险辅助，不接收完整 PAN。
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
