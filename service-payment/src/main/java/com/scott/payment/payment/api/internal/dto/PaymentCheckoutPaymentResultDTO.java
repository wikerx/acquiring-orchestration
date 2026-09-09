package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutPaymentResultDTO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout 支付提交或状态查询结果。
 * @status : create
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
         * 订单编号字段，保存 {@code FormFieldsDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String orderNo;
        /**
         * 订单ID，用于定位 {@code FormFieldsDTO} 关联的上游配置、渠道、账号、角色或业务记录。
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
         * </p>
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;
        /**
         * 编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String code;
        /**
         * 说明字段，保存 {@code FormFieldsDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String message;
    }
}
