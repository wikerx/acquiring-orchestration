package com.scott.payment.payment.model.notification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商户交易结果通知协议。
 *
 * <p>该对象只承载商户可见字段，不包含平台动作号、渠道请求标识、渠道原始报文或支付凭据。
 * Fastjson2 默认省略空字段，保持既有通知 JSON 的兼容性。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MerchantNotificationPayload {

    /**
     * 商户及子商户信息。
     */
    private MerchantInfo merchantInfo;

    /**
     * 商户订单与累计金额信息。
     */
    private OrderInfo orderInfo;

    /**
     * 当前交易动作结果。
     */
    private TransactionInfo transactionInfo;

    /**
     * 标签金额、交易金额、汇率和结算金额信息。
     */
    private BillingInfo billingInfo;

    /**
     * 收银台会话号，仅收银台超时通知携带。
     */
    private String checkoutSessionId;

    /**
     * 商户身份信息。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MerchantInfo {

        /**
         * 平台商户号。
         */
        private String merchantId;

        /**
         * 商户请求中携带的子商户快照。
         */
        private SubMerchantInfo subMerchantInfo;
    }

    /**
     * 子商户快照。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubMerchantInfo {

        /**
         * 子商户编号。
         */
        private String subId;

        /**
         * 子商户名称。
         */
        private String subName;

        /**
         * 子商户公司名称。
         */
        private String subCompanyName;

        /**
         * 子商户国家或地区代码。
         */
        private String subCountryCode;

        /**
         * 子商户州或省。
         */
        private String subState;

        /**
         * 子商户城市。
         */
        private String subCity;

        /**
         * 子商户街道地址。
         */
        private String subStreet;

        /**
         * 商户类别码。
         */
        private String merchantCategory;

        /**
         * 渠道协议使用的 Intes 编码。
         */
        private String intesCode;

        /**
         * 子商户计费类型。
         */
        private String chargeType;
    }

    /**
     * 商户订单与累计金额信息。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderInfo {

        /**
         * 商户订单号。
         */
        private String orderNo;

        /**
         * 商户本次请求唯一标识。
         */
        private String orderId;

        /**
         * 商户标签订单金额。
         */
        private BigDecimal amount;

        /**
         * 商户标签订单币种。
         */
        private String currency;

        /**
         * 生命周期累计授权金额。
         */
        private BigDecimal totalAuthorizedAmount;

        /**
         * 生命周期累计授权取消金额。
         */
        private BigDecimal totalAuthorizedCancelAmount;

        /**
         * 生命周期累计请款金额。
         */
        private BigDecimal totalCapturedAmount;

        /**
         * 生命周期累计退款金额。
         */
        private BigDecimal totalRefundAmount;

        /**
         * 生命周期累计拒付金额。
         */
        private BigDecimal totalRefuseAmount;
    }

    /**
     * 当前交易动作结果。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionInfo {

        /**
         * 商户可见结果码。
         */
        private String code;

        /**
         * 商户可见结果描述。
         */
        private String message;

        /**
         * 平台交易号。
         */
        private String transactionId;

        /**
         * 原交易号，后续动作使用。
         */
        private String sourceTransactionId;

        /**
         * 交易类型。
         */
        private String transactionType;

        /**
         * 平台交易状态。
         */
        private String transactionStatus;

        /**
         * 平台处理阶段。
         */
        private String processStage;

        /**
         * 带业务时区偏移的交易时间文本。
         */
        private String transactionDateTime;

        /**
         * 支付方式。
         */
        private String paymentMethod;

        /**
         * 卡品牌。
         */
        private String cardBrand;

        /**
         * 卡 BIN，不包含完整卡号。
         */
        private String cardBin;

        /**
         * 渠道授权码。
         */
        private String authCode;

        /**
         * 收单参考号。
         */
        private String arn;

        /**
         * 商户请求中的交易描述。
         */
        private String description;

        /**
         * 商户配置的回调地址。
         */
        private String callbackUrl;

        /**
         * 商户可见失败原因码。
         */
        private String failReasonCode;

        /**
         * 商户可见失败原因描述。
         */
        private String failReasonMessage;

        /**
         * 非终态交易的挂起原因码。
         */
        private String pendingReasonCode;
    }

    /**
     * 当前交易的金额与汇率信息。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BillingInfo {

        /**
         * 商户标签金额。
         */
        private BigDecimal labelAmount;

        /**
         * 商户标签币种。
         */
        private String labelCurrency;

        /**
         * 上送渠道的交易金额。
         */
        private BigDecimal transactionAmount;

        /**
         * 上送渠道的交易币种。
         */
        private String transactionCurrency;

        /**
         * 首笔交易锁定的标签币种到交易币种汇率。
         */
        private BigDecimal transactionRate;

        /**
         * 汇率来源。
         */
        private String rateSource;

        /**
         * 带业务时区偏移的汇率锁定时间文本。
         */
        private String rateTime;

        /**
         * 商户结算币种。
         */
        private String settlementCurrency;

        /**
         * 商户结算金额。
         */
        private BigDecimal settlementAmount;
    }
}
