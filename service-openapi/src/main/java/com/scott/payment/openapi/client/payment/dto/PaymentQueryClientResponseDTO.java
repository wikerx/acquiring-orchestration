package com.scott.payment.openapi.client.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI 调用 service-payment 查询交易的内部响应，按商户订单聚合返回交易动作列表。
 */
@Data
public class PaymentQueryClientResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;

    /**
     * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
     */
    private String merchantOrderNo;

    /**
     * 商户请求订单标识，用于区分同一商户订单下的一次接口提交或后续交易动作。
     */
    private String merchantOrderId;

    /**
     * 首次交易保存的子商户快照。
     */
    private PaymentCreateClientRequestDTO.SubMerchantInfoDTO subMerchantInfo;

    /**
     * 首次交易保存的商品或服务明细。
     */
    private List<PaymentCreateClientRequestDTO.GoodsInfoDTO> goodsInfo = new ArrayList<>();

    /**
     * 首次交易保存的持卡人账单信息。
     */
    private PaymentCreateClientRequestDTO.BillingCardHolderInfoDTO billingCardHolderInfo;

    /**
     * 首次交易保存的付款人信息。
     */
    private PaymentCreateClientRequestDTO.PayerInfoDTO payerInfo;

    /**
     * 首次交易保存的收货人信息。
     */
    private PaymentCreateClientRequestDTO.ShippingInfoDTO shippingInfo;

    /**
     * 查询目标动作可向商户返回的 3DS 安全字段子集。
     */
    private PaymentCreateClientResponseDTO.ThreeDsInfoDTO threeDSInfo;


    private BigDecimal orderAmount;


    private String orderCurrency;


    private BigDecimal totalAuthorizedAmount;


    private BigDecimal totalCapturedAmount;


    private BigDecimal totalRefundAmount;


    private BigDecimal totalAuthorizedCancelAmount;


    private BigDecimal totalRefuseAmount;


    private BigDecimal labelAmount;


    private String labelCurrency;


    private BigDecimal transactionAmount;


    private String transactionCurrency;


    private BigDecimal transactionRate;


    private String rateSource;


    private LocalDateTime rateTime;


    private BigDecimal settlementAmount;


    private String settlementCurrency;

    /**
     * 已形成的结算换汇汇率。
     */
    private BigDecimal settlementRate;

    /**
     * 已形成的结算费用金额。
     */
    private BigDecimal settlementFeeAmount;

    /**
     * 已形成的费用明细。
     */
    private List<PaymentCreateClientResponseDTO.FeeItemDTO> feeItems = new ArrayList<>();

    /**
     * 交易业务时区，使用 IANA 时区标识解释本地交易时间。
     */
    private String transactionTimeZone;

    /**
     * 交易信息集合，承载 {@code PaymentQueryClientResponseDTO} 当前请求或响应中的多值数据。
     */
    private List<TransactionInfoDTO> transactionInfo = new ArrayList<>();

    /**
     * 查询返回的单笔交易动作摘要。
     */
    @Data
    public static class TransactionInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String transactionId;

        /**
         * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
         */
        private String sourceTransactionId;

        /**
         * 后续动作源交易发生时间；首次交易为空。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime sourceTransactionDateTime;


        private String code;


        private String message;

        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private String transactionType;

        /**
         * 当前动作交易状态。
         */
        private String transactionStatus;

        /**
         * 当前动作内部处理阶段，仅用于 OpenAPI 结果投影。
         */
        private String processStage;

        /**
         * 支付核心受控失败原因码，不包含渠道原始码。
         */
        private String failReasonCode;

        /**
         * 支付核心内部失败摘要；商户响应不得直接透传。
         */
        private String failReasonMessage;

        /**
         * 支付核心受控等待原因码，用于识别渠道结果未确认。
         */
        private String pendingReasonCode;

        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;

        /**
         * 生命周期根主单的分片时间，供商户从查询结果继续发起动作。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;


        private String paymentMethod;

        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         */
        private String cardBrand;

        /**
         * 卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。
         */
        private String cardBin;


        private String authCode;


        private String arn;


        private String description;


        private String callbackUrl;

        /**
         * 生命周期首次交易保存的商户网站原始 URL。
         */
        private String merchantWebsite;

        /**
         * Hosted Checkout 结果页返回地址。
         */
        private String redirectUrl;

        /**
         * Hosted Checkout 创建会话语言。
         */
        private String language;
    }
}
