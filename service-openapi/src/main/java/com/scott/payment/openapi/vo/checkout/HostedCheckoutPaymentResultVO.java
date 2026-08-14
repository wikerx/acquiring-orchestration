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

    /** 终态时可用的浏览器 Form POST 动作；未配置 redirectUrl 时为空。 */
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

        /** 当前 HTML 所属 3DS 阶段，供收银台选择 Method 或 Challenge 编排。 */
        private String phase;

        /** 受控 3DS HTML 内容，前端必须按专用桥接页面处理，禁止拼接脚本。 */
        private String html;

        /** 3DS 验证完成后的受控返回地址。 */
        private String returnUrl;

        /** 3DS 动作超时时间，单位秒。 */
        private Integer timeoutSeconds;

        /** 下一浏览器阶段重新加密卡数据所需的新公钥元数据和 nonce。 */
        private HostedCheckoutSessionVO.CardEncryptionVO cardEncryption;
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

    /** 支付终态后允许付款人执行的商户页面动作。 */
    @Data
    public static class ActionVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 固定 POST，自动返回和按钮返回必须使用同一方法。 */
        private String method;
        /** 商户创建会话时提供的结果页地址。 */
        private String redirectUrl;
        /** 自动提交前的倒计时秒数。 */
        private Integer delaySeconds;
        /** 文档 8.4 定义的九个表单字段。 */
        private FormFieldsVO formFields;
    }

    /** 浏览器 Form POST 的非权威交易摘要。 */
    @Data
    public static class FormFieldsVO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String orderNo;
        private String orderId;
        private String transactionId;
        private String transactionType;
        private String transactionStatus;
        private OffsetDateTime transactionDateTime;
        private String code;
        private String message;
    }
}
