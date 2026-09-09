package com.scott.payment.openapi.dto.body;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HostedCheckoutBrowserRequestDTOs
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 付款人浏览器 Hosted Checkout 请求模型集合。
 * @status : create
 */
public final class HostedCheckoutBrowserRequestDTOs {

    private HostedCheckoutBrowserRequestDTOs() {
    }

    /**
     * 付款人浏览器打开收银台会话的请求。
     */
    @Getter
    @Setter
    public static class SessionQueryRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 不透明会话访问令牌，服务端只能校验或取摘要，禁止记录明文。 */
        @NotBlank(message = "opaqueToken is required")
        private String opaqueToken;

        /** 可选页面封面或主题标识，不作为安全凭据。 */
        private String cover;

        /** 浏览器环境摘要，用于安全审计和 3DS 信息补充。 */
        private ClientContextDTO clientContext;
    }

    /**
     * 付款人提交 Hosted Checkout 支付的请求。
     */
    @Getter
    @Setter
    public static class PaymentSubmitRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 不透明会话访问令牌，禁止日志、持久化或前端错误页回显。 */
        @NotBlank(message = "opaqueToken is required")
        private String opaqueToken;

        /** Hosted Checkout 会话号，必须与令牌绑定关系一致。 */
        @NotBlank(message = "checkoutSessionId is required")
        private String checkoutSessionId;

        /** 单次支付尝试请求号，用于提交幂等，重试时应保持稳定。 */
        @NotBlank(message = "attemptRequestId is required")
        private String attemptRequestId;

        /** 付款人选择的支付方式编码。 */
        @NotBlank(message = "paymentMethod is required")
        private String paymentMethod;

        /** 浏览器生成的卡数据密文信封，服务端入口禁止接收明文 PAN、CVV 和有效期。 */
        @Valid
        @NotNull(message = "cardDataEnvelope is required")
        private CardDataEnvelopeDTO cardDataEnvelope;

        /** 账单持卡人资料，用于渠道和 3DS 校验，不得完整写入日志。 */
        @Valid
        @NotNull(message = "billingCardHolderInfo is required")
        private BillingCardHolderInfoDTO billingCardHolderInfo;

        /** 浏览器环境摘要，用于 3DS 和异常访问审计。 */
        private ClientContextDTO clientContext;
    }

    /**
     * 付款人轮询 Hosted Checkout 支付状态的请求。
     */
    @Getter
    @Setter
    public static class PaymentStatusRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 不透明会话访问令牌，禁止记录明文。 */
        @NotBlank(message = "opaqueToken is required")
        private String opaqueToken;

        /** Hosted Checkout 会话号，必须与令牌绑定关系一致。 */
        @NotBlank(message = "checkoutSessionId is required")
        private String checkoutSessionId;

        /** 可选支付尝试号，用于限定本次状态查询。 */
        private String checkoutAttemptId;

        /** 浏览器环境摘要，用于会话访问风险比对。 */
        private ClientContextDTO clientContext;
    }

    /** 卡号输入完成后的 BIN 品牌识别请求，只发送 6 到 11 位前缀。 */
    @Getter
    @Setter
    public static class CardBinRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 请求中的{@code opaqueToken}，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；敏感安全字段，日志只允许记录长度、摘要或掩码。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "opaqueToken is required")
        private String opaqueToken;
        /**
         * {@code checkoutSessionId}，用于定位 卡BIN请求 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "checkoutSessionId is required")
        private String checkoutSessionId;
        /**
         * 卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。
         * <p>
         * 单位：无；格式：卡 BIN 或尾号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅保存识别片段，不保存完整 PAN；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "cardBin is required")
        @Pattern(regexp = "^\\d{6,11}$", message = "cardBin format does not match")
        private String cardBin;
    }

    /**
     * 付款人从 3DS 页面返回收银台的请求。
     */
    @Getter
    @Setter
    public static class ThreeDsReturnRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 一次性 3DS 回跳令牌，只能校验摘要，禁止日志和重复使用。 */
        @NotBlank(message = "threeDsReturnToken is required")
        private String threeDsReturnToken;

        /** Hosted Checkout 会话号，必须与回跳令牌绑定。 */
        @NotBlank(message = "checkoutSessionId is required")
        private String checkoutSessionId;

        /** 发起 3DS 的支付尝试号，必须与回跳令牌绑定。 */
        @NotBlank(message = "checkoutAttemptId is required")
        private String checkoutAttemptId;

        /** 渠道返回的 3DS 认证数据，属于敏感协议载荷，禁止完整记录。 */
        private String authenticationData;

        /** 使用 3DS 动作响应中新 nonce 加密的卡数据，供服务端继续认证或认证后扣款。 */
        @Valid
        @NotNull(message = "cardDataEnvelope is required")
        private CardDataEnvelopeDTO cardDataEnvelope;

        /** 继续认证和资金动作所需的账单持卡人资料。 */
        @Valid
        private BillingCardHolderInfoDTO billingCardHolderInfo;

        /** 3DS 返回时的浏览器环境摘要。 */
        private ClientContextDTO clientContext;
    }

    /** 浏览器提交的 RSA-OAEP-256 + AES-256-GCM 卡数据密文信封。 */
    @Getter
    @Setter
    public static class CardDataEnvelopeDTO implements Serializable {

        private static final long serialVersionUID = 1L;
        /**
         * 卡数据混合加密协议标识，调用双方必须使用完全一致的算法组合。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "cardDataEnvelope.algorithm is required")
        private String algorithm;
        /**
         * 密钥ID，用于定位 {@code CardDataEnvelopeDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "cardDataEnvelope.keyId is required")
        private String keyId;
        /**
         * {@code encryptedKey}字段，保存 {@code CardDataEnvelopeDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；敏感安全字段，日志只允许记录长度、摘要或掩码。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "cardDataEnvelope.encryptedKey is required")
        @Pattern(regexp = "^[A-Za-z0-9_-]{64,1024}$", message = "cardDataEnvelope.encryptedKey format does not match")
        private String encryptedKey;
        /**
         * {@code iv}字段，保存 {@code CardDataEnvelopeDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；敏感安全字段，日志只允许记录长度、摘要或掩码。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "cardDataEnvelope.iv is required")
        @Pattern(regexp = "^[A-Za-z0-9_-]{16,32}$", message = "cardDataEnvelope.iv format does not match")
        private String iv;
        /**
         * {@code ciphertext}字段，保存 {@code CardDataEnvelopeDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；敏感安全字段，日志只允许记录长度、摘要或掩码。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "cardDataEnvelope.ciphertext is required")
        @Pattern(regexp = "^[A-Za-z0-9_-]{16,8192}$", message = "cardDataEnvelope.ciphertext format does not match")
        private String ciphertext;
        /**
         * 随机数字段，保存 {@code CardDataEnvelopeDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；敏感安全字段，日志只允许记录长度、摘要或掩码。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "cardDataEnvelope.nonce is required")
        @Pattern(regexp = "^[A-Za-z0-9_-]{16,128}$", message = "cardDataEnvelope.nonce format does not match")
        private String nonce;
    }

    /**
     * 账单持卡人资料，用于渠道支付与 3DS 风控。
     */
    @Getter
    @Setter
    public static class BillingCardHolderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 持卡人名字，属于个人信息。 */
        @NotBlank(message = "billingCardHolderInfo.firstName is required")
        private String firstName;

        /** 持卡人姓氏，属于个人信息。 */
        @NotBlank(message = "billingCardHolderInfo.lastName is required")
        private String lastName;

        /**
         * 付款人邮箱，进入 payment 链路后只能按通道需要传递或脱敏保存。
         */
        @NotBlank(message = "billingCardHolderInfo.email is required")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "billingCardHolderInfo.email format does not match")
        private String email;

        /**
         * 付款人联系电话，作为 3DS/渠道风控补充字段传递。
         */
        private String phone;

        /**
         * ISO 3166-1 alpha-3 账单国家/地区代码。
         */
        @NotBlank(message = "billingCardHolderInfo.country is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "billingCardHolderInfo.country format does not match")
        private String country;

        /**
         * 账单州/省，部分卡组织或收单通道会用于 AVS/3DS 风控。
         */
        private String state;

        /**
         * 账单城市，随 3DS 请求进入渠道侧风控模型。
         */
        private String city;

        /**
         * 账单街道地址，禁止在日志中输出明文请求体。
         */
        private String street;

        /**
         * 账单邮编，随账单地址一起用于渠道侧校验。
         */
        private String postal;
    }

    /**
     * 浏览器环境摘要，service-openapi 会转换为 hash 或脱敏 JSON 供安全审计和 3DS 使用。
     */
    @Getter
    @Setter
    public static class ClientContextDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 浏览器时区偏移，用于 3DS browserInfo 和异常访问排查。
         */
        private String timezoneOffset;

        /**
         * 浏览器语言，前端可用于国际化，后端只作为会话打开审计信息。
         */
        private String language;

        /**
         * 屏幕信息，进入 3DS browserInfo 前会整体脱敏保存。
         */
        private String screen;

        /** ACS challenge 窗口尺寸，例如 FULL_SCREEN。 */
        private String challengeWindowSize;

        /** 浏览器屏幕颜色深度。 */
        private Integer colorDepth;

        /** 浏览器是否启用 Java。 */
        private Boolean javaEnabled;

        /** 浏览器是否启用 JavaScript。 */
        private Boolean javaScriptEnabled;

        /** 浏览器屏幕高度。 */
        private Integer screenHeight;

        /** 浏览器屏幕宽度。 */
        private Integer screenWidth;

        /**
         * 前端生成的设备标识，内部只传输 hash，不能作为唯一安全凭据。
         */
        private String deviceId;
    }
}
