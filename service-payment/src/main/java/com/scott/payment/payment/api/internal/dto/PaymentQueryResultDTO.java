package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentQueryResultDTO
 * @date : 2026-07-17 21:10
 * @email : scott_x@163.com
 * @description : service-payment 交易查询内部响应，位于内部 DTO 层，按商户订单聚合同一生命周期下的交易动作列表。
 * @status : create
 */
@Data
public class PaymentQueryResultDTO implements Serializable {

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
     * 商户本次查询请求唯一标识，来自 orderInfo.orderId。
     */
    private String merchantOrderId;

    /** 首次交易保存的子商户快照；不存在子商户时为空。 */
    private PaymentCreateCommandDTO.SubMerchantInfoDTO subMerchantInfo;

    /** 首次交易保存的商品或服务明细；未上送时为空列表。 */
    private List<PaymentCreateCommandDTO.GoodsInfoDTO> goodsInfo = new ArrayList<>();

    /** 首次交易保存的持卡人账单信息。 */
    private PaymentCreateCommandDTO.BillingCardHolderInfoDTO billingCardHolderInfo;

    /** 首次交易保存的付款人信息。 */
    private PaymentCreateCommandDTO.PayerInfoDTO payerInfo;

    /** 首次交易保存的收货人信息。 */
    private PaymentCreateCommandDTO.ShippingInfoDTO shippingInfo;

    /** 查询目标动作可向商户返回的 3DS 安全字段子集。 */
    private PaymentCreateResultDTO.ThreeDsInfoDTO threeDSInfo;

    /**
     * 商户上送订单金额或生命周期标签金额。
     */
    private BigDecimal orderAmount;

    /**
     * 商户上送订单币种或生命周期标签币种。
     */
    private String orderCurrency;

    /**
     * 当前生命周期累计授权成功金额。
     */
    private BigDecimal totalAuthorizedAmount;

    /**
     * 当前生命周期累计请款成功金额。
     */
    private BigDecimal totalCapturedAmount;

    /**
     * 当前生命周期累计退款成功金额。
     */
    private BigDecimal totalRefundAmount;

    /**
     * 当前生命周期累计授权取消、预授权取消或未请款金额释放成功金额。
     */
    private BigDecimal totalAuthorizedCancelAmount;

    /**
     * 当前生命周期累计拒付成立或确认成功金额。
     */
    private BigDecimal totalRefuseAmount;

    /**
     * 商户上送或页面标签展示的原始金额。
     */
    private BigDecimal labelAmount;

    /**
     * 商户上送或页面标签展示的原始币种。
     */
    private String labelCurrency;

    /**
     * 平台上送渠道的交易金额。
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
     * 商户结算币种或交易结算币种。
     */
    private String settlementCurrency;

    /** 已形成的结算换汇汇率；没有真实财务记录时为空。 */
    private BigDecimal settlementRate;

    /** 已形成的结算费用金额；没有真实财务记录时为空。 */
    private BigDecimal settlementFeeAmount;

    /** 已形成的费用明细；没有真实财务记录时为空列表。 */
    private List<PaymentCreateResultDTO.FeeItemDTO> feeItems = new ArrayList<>();

    /**
     * 交易发生时区。
     */
    private String transactionTimeZone;

    /**
     * 同一商户订单下的交易动作列表。
     */
    private List<TransactionInfoDTO> transactionInfo = new ArrayList<>();

    /**
     * 查询返回的单笔交易动作摘要。
     */
    @Data
    public static class TransactionInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台当前交易唯一标识。
         */
        private String transactionId;

        /**
         * 原平台交易唯一标识，首次类交易为空。
         */
        private String sourceTransactionId;

        /** 后续动作源交易发生时间；首次交易为空。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime sourceTransactionDateTime;

        /**
         * 当前动作商户响应码，例如 T200、T202、T203、F210。
         */
        private String code;

        /**
         * 当前动作商户响应描述，面向商户展示。
         */
        private String message;

        /**
         * 交易类型，对齐字典 transaction_type。
         */
        private String transactionType;

        /** 当前动作交易状态。 */
        private String transactionStatus;

        /**
         * 交易发生时间。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;

        /** 生命周期根主单的分片时间，供查询结果继续发起后续动作。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;

        /**
         * 支付方式，如 BANK_CARD。
         */
        private String paymentMethod;

        /**
         * 卡品牌或支付品牌。
         */
        private String cardBrand;

        /**
         * 脱敏卡 BIN，格式为前六位 + **** + 后四位。
         */
        private String cardBin;

        /**
         * 授权码，渠道成功返回时填写。
         */
        private String authCode;

        /**
         * ARN 或收单机构参考号。
         */
        private String arn;

        /**
         * 商户订单备注或描述。
         */
        private String description;

        /**
         * 商户通知回调地址。
         */
        private String callbackUrl;

        /**
         * 生命周期首次交易保存的商户网站原始 URL。
         */
        private String merchantWebsite;

        /** Hosted Checkout 结果页返回地址。 */
        private String redirectUrl;

        /** Hosted Checkout 创建会话语言。 */
        private String language;
    }
}
