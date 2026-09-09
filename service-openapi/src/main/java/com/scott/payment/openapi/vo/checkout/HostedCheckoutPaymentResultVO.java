package com.scott.payment.openapi.vo.checkout;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HostedCheckoutPaymentResultVO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 付款人浏览器支付提交或状态查询响应。
 * @status : create
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
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
         * </p>
         */
        private String merchantId;
        /**
         * 响应中的订单编号，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String orderNo;
        /**
         * 订单ID，用于定位 {@code FormFieldsVO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String orderId;
        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 operationId、merchantOrderNo 共同定位一笔平台交易。
         * </p>
         */
        private String transactionId;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String transactionType;
        /**
         * 交易状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String transactionStatus;
        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private OffsetDateTime transactionDateTime;
        /**
         * 编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String code;
        /**
         * 响应中的说明，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String message;
    }
}
