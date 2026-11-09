package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * service-payment 创建收单交易的内部响应参数，返回交易受理后的商户可见结果摘要和内部生命周期关联 ID；operationId 仅内部链路使用，不暴露给商户。
 */
@Data
public class PaymentCreateResultDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台当前交易唯一标识，对应 transaction_operation.transaction_id；每一笔授权、请款、退款、撤销都不同。
     */
    private String transactionId;

    /**
     * 原平台交易唯一标识，后续动作返回商户请求传入的 sourceTransactionId。
     */
    private String sourceTransactionId;

    /**
     * 源交易发生时间，由支付核心按 sourceTransactionId 自动定位；仅用于后续动作响应。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime sourceTransactionDateTime;

    /**
     * 平台内部生命周期关联标识，对应 transaction_order.operation_id；不返回商户、不直接上送渠道。
     */
    private String operationId;

    /**
     * 商户订单号，原样返回给 OpenAPI 和商户。
     */
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识，来自 orderInfo.orderId。
     */
    private String merchantOrderId;

    /**
     * 支付平台颁发的商户号。
     */
    private String merchantId;

    /**
     * 首次交易保存的商品或服务明细；未上送时为空列表。
     */
    private List<PaymentCreateCommandDTO.GoodsInfoDTO> goodsInfo;

    /**
     * 首次交易保存的持卡人账单信息；未上送时为空。
     */
    private PaymentCreateCommandDTO.BillingCardHolderInfoDTO billingCardHolderInfo;

    /**
     * 首次交易保存的付款人信息，返回商户前由支付核心从密文快照恢复。
     */
    private PaymentCreateCommandDTO.PayerInfoDTO payerInfo;

    /**
     * 首次交易保存的收货人信息；未上送时为空。
     */
    private PaymentCreateCommandDTO.ShippingInfoDTO shippingInfo;

    /**
     * 商户侧子商户信息，用于 OpenAPI 响应回显允许展示的子商户摘要。
     */
    private SubMerchantInfoDTO subMerchantInfo;

    /**
     * 交易类型，对齐字典 transaction_type。
     */
    private String transactionType;

    /**
     * 交易状态，对齐字典 transaction_status。
     */
    private String status;

    /**
     * 当前动作商户响应码，例如 T0000、T0001、T0002、F1000、F2000 或 F3000。
     */
    private String merchantResponseCode;

    /**
     * 当前动作商户响应描述，面向商户展示。
     */
    private String merchantResponseMessage;

    /**
     * 内部处理阶段，用于说明当前交易处于风控、路由、渠道请求或等待回调等节点。
     */
    private String processStage;

    /**
     * 失败原因码，仅当 status=FAILED 时返回。
     */
    private String failReasonCode;

    /**
     * 商户可见失败原因描述，避免暴露过细渠道规则。
     */
    private String failReasonMessage;

    /**
     * 挂起原因码，仅当 status=PENDING 时返回。
     */
    private String pendingReasonCode;

    /**
     * 商户标签金额，单位为标签币种最小单位，例如 USD 分；用于兼容既有内部调用方和对外响应转换。
     */
    private Long amount;

    /**
     * 商户标签币种，使用 ISO 4217 三位大写币种代码；用于兼容既有内部调用方和对外响应转换。
     */
    private String currency;

    /**
     * 商户上送订单金额，主币种单位。
     */
    private BigDecimal orderAmount;

    /**
     * 商户上送订单币种。
     */
    private String orderCurrency;

    /**
     * 当前生命周期累计授权成功金额，商户标签币种单位。
     */
    private BigDecimal totalAuthorizedAmount;

    /**
     * 当前生命周期累计请款成功金额，商户标签币种单位。
     */
    private BigDecimal totalCapturedAmount;

    /**
     * 当前生命周期累计退款成功金额，商户标签币种单位。
     */
    private BigDecimal totalRefundAmount;

    /**
     * 当前生命周期累计授权取消、预授权取消或未请款金额释放成功金额，商户标签币种单位。
     */
    private BigDecimal totalAuthorizedCancelAmount;

    /**
     * 当前生命周期累计拒付成立或确认成功金额，商户标签币种单位。
     */
    private BigDecimal totalRefuseAmount;

    /**
     * 商户上送或页面标签展示的原始金额，主币种单位。
     */
    private BigDecimal labelAmount;

    /**
     * 商户上送或页面标签展示的原始币种。
     */
    private String labelCurrency;

    /**
     * 平台上送渠道的交易金额，主币种单位。
     */
    private BigDecimal transactionAmount;

    /**
     * 平台上送渠道的交易币种。
     */
    private String transactionCurrency;

    /**
     * 标签金额转平台交易金额使用的汇率。
     */
    private BigDecimal transactionRate;

    /**
     * 汇率来源编码。
     */
    private String rateSource;

    /**
     * 汇率生效或报价时间。
     */
    private LocalDateTime rateTime;

    /**
     * 预计或最终结算金额。
     */
    private BigDecimal settlementAmount;

    /**
     * 预计或最终结算币种。
     */
    private String settlementCurrency;

    /**
     * 已形成的结算换汇汇率；没有真实财务记录时为空。
     */
    private BigDecimal settlementRate;

    /**
     * 已形成的结算费用金额；没有真实财务记录时为空。
     */
    private BigDecimal settlementFeeAmount;

    /**
     * 已形成的费用明细；没有真实财务记录时为空列表。
     */
    private List<FeeItemDTO> feeItems;

    /**
     * 当前动作可向商户返回的 3DS 安全字段子集。
     */
    private ThreeDsInfoDTO threeDSInfo;

    /**
     * 交易发生时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime transactionDateTime;

    /**
     * 生命周期根主单的分片时间。首次交易与 transactionDateTime 相同，后续动作沿用原主单时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime rootTransactionDateTime;

    /**
     * 交易发生时区。
     */
    private String transactionTimeZone;

    /**
     * 支付方式，如 BANK_CARD。
     */
    private String paymentMethod;

    /**
     * 卡品牌或支付品牌，如 MASTERCARD、VISA。
     */
    private String paymentBrand;

    /**
     * 脱敏卡 BIN，格式为前六位 + **** + 后四位。
     */
    private String cardBin;

    /**
     * 渠道返回的脱敏卡号，禁止完整 PAN。
     */
    private String cardNumberMasked;

    /**
     * 卡有效期月份，来自商户请求或渠道摘要。
     */
    private String expiryMonth;

    /**
     * 卡有效期年份，来自商户请求或渠道摘要。
     */
    private String expiryYear;

    /**
     * 发卡国家或地区代码，来自渠道或卡 BIN 识别。
     */
    private String issuerCountry;

    /**
     * 资金类型，例如 CREDIT、DEBIT。
     */
    private String fundingMethod;

    /**
     * CSC/CVV 校验结果，来自渠道响应摘要。
     */
    private String cscResult;

    /**
     * 授权码，渠道成功返回时填写。
     */
    private String authCode;

    /**
     * ARN 或收单机构参考号。
     */
    private String acquirerReferenceNo;

    /**
     * 订单备注或描述，商户上送后原样返回。
     */
    private String description;

    /**
     * 商户通知回调地址，商户上送或配置存在时返回。
     */
    private String callbackUrl;

    /**
     * 首次交易保存的商户网站原始 URL，创建和幂等响应必须返回同一值。
     */
    private String merchantWebsite;

    /**
     * Hosted Checkout 交易结果页返回地址；按生命周期恢复。
     */
    private String redirectUrl;

    /**
     * Hosted Checkout 创建会话时选择的语言；按生命周期恢复。
     */
    private String language;

    /**
     * 允许商户接收的 3DS 安全结果，不包含 CAVV 和协议原文。
     */
    @Data
    public static class ThreeDsInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String eci;
        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String dsTransactionId;

        private String threeDsVersion;

        private String status;

        private Boolean liabilityShifted;
    }

    /**
     * 已形成的费用明细。
     */
    @Data
    public static class FeeItemDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String categories;

        private BigDecimal amount;

        private String currency;

        private BigDecimal rate;
    }

    /**
     * 支付结果中的子商户冻结快照，保证查询和通知不受后续商户资料变更影响。
     */
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

        /**
         * {@code subEmail}，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         */
        private String subEmail;

        /**
         * {@code subPhone}，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String subPhone;


        private String subPostal;


        private String subTaxId;


        private String merchantCategory;


        private String intesCode;

        /**
         * {@code chargeType}，用于区分 {@code SubMerchantInfoDTO} 记录的处理类别、配置维度或外部协议枚举。
         */
        private String chargeType;
    }

}
