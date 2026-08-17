package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
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
        /** 当前 HTML 所属 3DS 阶段，INITIALIZE 表示 Method，AUTHENTICATE 表示 ACS Challenge。 */
        private String phase;
        /** 受控 3DS HTML，禁止写入普通日志或拼接未转义脚本。 */
        private String html;
        /** 3DS 完成后的平台受控返回地址。 */
        private String returnUrl;
        /** 3DS 动作超时时间，单位秒。 */
        private Integer timeoutSeconds;
        /** 下一浏览器阶段重新加密卡数据所需的新公钥元数据和一次性 nonce。 */
        private PaymentCheckoutSessionQueryResultDTO.CardEncryptionDTO cardEncryption;
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

    /** 支付终态后允许执行的浏览器 Form POST 动作。 */
    @Data
    public static class ActionDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 固定 POST，前端不得改用 GET。 */
        private String method;
        /** 商户创建会话时提供的结果页地址。 */
        private String redirectUrl;
        /** 自动提交前的倒计时秒数。 */
        private Integer delaySeconds;
        /** 文档 8.4 规定的九个表单字段。 */
        private FormFieldsDTO formFields;
    }

    /** 返回商户网站的浏览器表单字段，不可作为资金结果凭证。 */
    @Data
    public static class FormFieldsDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String orderNo;
        private String orderId;
        private String transactionId;
        private String transactionType;
        private String transactionStatus;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;
        private String code;
        private String message;
    }
}
