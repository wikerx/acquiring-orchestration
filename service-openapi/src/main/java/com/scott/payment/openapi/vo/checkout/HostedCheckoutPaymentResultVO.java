package com.scott.payment.openapi.vo.checkout;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 付款人浏览器支付提交或状态查询响应。
 */
@Data
public class HostedCheckoutPaymentResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Hosted Checkout 会话号。 */
    private String checkoutSessionId;

    /** 当前支付尝试号，用于状态轮询和 3DS 关联。 */
    private String checkoutAttemptId;

    /** 付款页状态，决定前端展示结果、3DS、失败或处理中页面。 */
    private String pageState;

    /** 支付已完成时的结果摘要。 */
    private PaymentResultVO result;

    /** 需要 3DS 时的受控跳转或桥接指令。 */
    private ThreeDsActionVO threeDsAction;

    /** 支付失败时可安全展示的原因和重试信息。 */
    private FailureVO failure;

    /** 支付处理中建议的轮询配置。 */
    private PollingVO polling;

    /** 商户返回和取消动作地址。 */
    private ActionVO actions;

    /**
     * Hosted Checkout 支付成功或终态结果摘要。
     */
    @Data
    public static class PaymentResultVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 支付金额，单位为 {@link #currency} 主币种单位。 */
        private BigDecimal amount;

        /** ISO 4217 三位币种代码。 */
        private String currency;

        /** 商户订单号。 */
        private String merchantOrderNo;

        /** 实际使用的支付方式。 */
        private String paymentMethod;

        /** 识别出的卡品牌；非银行卡场景可为空。 */
        private String cardBrand;

        /** 仅保留必要首尾位的脱敏卡号，绝不能是完整 PAN。 */
        private String cardNumberMasked;

        /** 平台交易号。 */
        private String transactionId;

        /** 带时区偏移的平台交易时间。 */
        private OffsetDateTime transactionDateTime;

        /** 渠道授权码，按最小必要原则展示。 */
        private String authCode;
    }

    /**
     * 付款人继续完成 3DS 验证所需的动作。
     */
    @Data
    public static class ThreeDsActionVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 3DS 动作类型，例如 HTML 桥接或外部跳转。 */
        private String actionType;

        /** 受控 3DS HTML 内容，前端必须按专用桥接页面处理，禁止拼接脚本。 */
        private String html;

        /** 3DS 验证完成后的受控返回地址。 */
        private String returnUrl;

        /** 3DS 动作超时时间，单位秒。 */
        private Integer timeoutSeconds;
    }

    /**
     * 付款页可展示的支付失败摘要。
     */
    @Data
    public static class FailureVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 稳定失败原因码，不暴露内部异常栈。 */
        private String reasonCode;

        /** 面向付款人的安全失败提示，不包含渠道敏感原文。 */
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
    public static class PollingVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 查询当前支付尝试状态的相对或受控绝对地址。 */
        private String statusUrl;

        /** 初始轮询间隔，单位秒。 */
        private Integer intervalSeconds;

        /** 退避后的最大轮询间隔，单位秒。 */
        private Integer maxIntervalSeconds;
    }

    /**
     * 支付结束后允许付款人执行的商户页面动作。
     */
    @Data
    public static class ActionVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 支付完成后返回商户页面的地址。 */
        private String returnUrl;

        /** 付款取消后返回商户页面的地址。 */
        private String cancelUrl;
    }
}
