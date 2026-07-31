package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hosted Checkout 支付提交或状态查询结果。
 */
@Data
public class PaymentCheckoutPaymentResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Hosted Checkout 会话号。 */
    private String checkoutSessionId;
    /** 当前支付尝试号。 */
    private String checkoutAttemptId;
    /** 前端页面状态，不等同于可自行覆盖的交易数据库状态。 */
    private String pageState;
    /** 支付成功或已确定结果时的安全摘要。 */
    private PaymentResultDTO result;
    /** 需要付款人继续完成 3DS 时的动作。 */
    private ThreeDsActionDTO threeDsAction;
    /** 可安全展示的失败原因和重试信息。 */
    private FailureDTO failure;
    /** 支付处理中建议的轮询配置。 */
    private PollingDTO polling;
    /** 支付结束后允许返回商户页面的动作地址。 */
    private ActionDTO actions;

    /**
     * 付款页可展示的支付结果摘要。
     */
    @Data
    public static class PaymentResultDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 支付金额，单位为 {@link #currency} 的主币种单位。 */
        private BigDecimal amount;
        /** ISO 4217 三位币种代码。 */
        private String currency;
        /** 商户订单号。 */
        private String merchantOrderNo;
        /** 实际使用的支付方式。 */
        private String paymentMethod;
        /** 识别出的卡品牌；非银行卡场景可为空。 */
        private String cardBrand;
        /** 脱敏卡号，绝不能包含完整 PAN。 */
        private String cardNumberMasked;
        /** 平台交易号。 */
        private String transactionId;
        /** 支付核心记录的交易业务时间。 */
        private LocalDateTime transactionDateTime;
        /** 渠道授权码，按最小必要原则返回。 */
        private String authCode;
    }

    /**
     * 付款人继续完成 3DS 验证所需动作。
     */
    @Data
    public static class ThreeDsActionDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 3DS 动作类型，例如 HTML 桥接或外部跳转。 */
        private String actionType;
        /** 受控 3DS HTML，禁止写入普通日志或拼接未转义脚本。 */
        private String html;
        /** 3DS 完成后的平台受控返回地址。 */
        private String returnUrl;
        /** 3DS 动作超时时间，单位秒。 */
        private Integer timeoutSeconds;
    }

    /**
     * 付款页可展示的失败摘要。
     */
    @Data
    public static class FailureDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 稳定失败原因码，不暴露内部异常栈。 */
        private String reasonCode;
        /** 面向付款人的安全提示，不包含渠道敏感原文。 */
        private String message;
        /** 当前失败是否允许在同一会话内重试。 */
        private Boolean retryAllowed;
        /** 当前会话剩余支付尝试次数。 */
        private Integer remainingAttemptCount;
    }

    /**
     * 支付处理中状态轮询建议。
     */
    @Data
    public static class PollingDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 查询当前尝试状态的受控地址。 */
        private String statusUrl;
        /** 初始轮询间隔，单位秒。 */
        private Integer intervalSeconds;
        /** 退避后的最大轮询间隔，单位秒。 */
        private Integer maxIntervalSeconds;
    }

    /**
     * 支付结束后允许执行的商户页面动作。
     */
    @Data
    public static class ActionDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 支付完成后返回商户页面的地址。 */
        private String returnUrl;
        /** 付款取消后返回商户页面的地址。 */
        private String cancelUrl;
    }
}
