package com.scott.payment.channel.payment.dto.request;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 收单渠道统一请求基类，位于 payment-channel-api DTO 层，用于承载渠道调用所需交易、金额、卡、账单和 3DS 上下文；敏感字段只允许内存使用。
 */
@Data
public class ChannelPaymentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台登记的渠道编码，由路由结果提供。
     */
    private String channelCode;

    /**
     * 平台内部生命周期关联标识，同一原始交易生命周期共用，不直接上送渠道。
     */
    private String operationId;

    /**
     * 平台当前交易唯一标识，每一笔授权、请款、退款、撤销都不同。
     */
    private String transactionId;

    /**
     * 原平台交易 ID，后续动作关联授权、支付或请款时使用。
     */
    private String sourceTransactionId;

    /**
     * 渠道订单号；首次支付或授权与后续交易必须复用同一渠道订单身份。
     */
    private String channelOrderNo;

    /**
     * 渠道交易 ID；用于查询和关联后续动作，部分渠道在请求阶段可为空。
     */
    private String channelTransactionId;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 商户订单号。
     */
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识，来自 orderInfo.orderId。
     */
    private String merchantOrderId;

    /**
     * 交易类型，对齐 transaction_type。
     */
    private String transactionType;

    /**
     * 支付方式，例如 BANK_CARD。
     */
    private String paymentMethod;

    /**
     * 交易金额，主币种单位。
     */
    private BigDecimal amount;

    /**
     * 交易币种，ISO 4217 三位大写代码。
     */
    private String currency;

    /**
     * 交易业务时间。
     */
    private LocalDateTime transactionDateTime;

    /**
     * PAN 卡号，只允许内存渠道调用，不允许明文日志、MQ 或落库。
     */
    private String cardNo;

    /**
     * 卡有效期月份。
     */
    private String expirationMonth;

    /**
     * 卡有效期年份。
     */
    private String expirationYear;

    /**
     * CVV/CVC 安全码，只允许内存渠道调用，不允许明文日志、MQ 或落库。
     */
    private String securityCode;

    /**
     * 卡面持卡人姓名，只允许在当前渠道调用链中使用，不允许明文日志或落库。
     */
    private String cardholderName;

    /**
     * 卡品牌。
     */
    private String cardBrand;

    /**
     * 持卡人账单信息。
     */
    private BillingInfo billingInfo;

    /**
     * 3DS 认证信息。
     */
    private ThreeDsInfo threeDsInfo;

    /**
     * 渠道差异化扩展参数，不建议承载通用核心字段。
     */
    private Map<String, String> extension = new HashMap<>();

    /**
     * 渠道支付请求的账单地址信息，供渠道风控和认证使用且不得完整写入普通日志。
     */
    @Data
    public static class BillingInfo implements Serializable {

        private static final long serialVersionUID = 1L;


        private String firstName;


        private String lastName;

        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String phone;

        /**
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         */
        private String email;


        private String country;

        /**
         * 状态，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String state;

        /**
         * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String city;

        /**
         * 街道，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String street;

        /**
         * 邮编，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String postal;
    }

    /**
     * 渠道支付请求中的 3DS 认证信息模型，位于 payment-channel-api 协议层，仅承载渠道适配所需字段，不记录平台认证状态。
     */
    @Data
    public static class ThreeDsInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 渠道 3DS authentication transaction id，支付或授权时必须引用同一 provider 的已通过认证交易。
         */
        private String authenticationTransactionId;

        /**
         * 3DS 电子商务指示码，用于渠道判断认证和责任转移结果。
         */
        private String eci;

        /**
         * CAVV 属于认证敏感值，日志必须脱敏。
         */
        private String cavv;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String dsTransactionId;


        private String threeDsVersion;
    }
}
