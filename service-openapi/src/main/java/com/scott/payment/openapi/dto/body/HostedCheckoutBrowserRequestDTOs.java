package com.scott.payment.openapi.dto.body;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 付款人浏览器 Hosted Checkout 请求模型集合。
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

        /** 敏感卡数据，仅允许短暂传入支付链路，禁止写日志或业务库。 */
        @Valid
        @NotNull(message = "cardInfo is required")
        private CardInfoDTO cardInfo;

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

        /** 3DS 返回时的浏览器环境摘要。 */
        private ClientContextDTO clientContext;
    }

    /**
     * 浏览器提交的敏感银行卡资料。
     */
    @Getter
    @Setter
    public static class CardInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 12 至 19 位完整 PAN，仅允许短暂传入支付处理，禁止日志和持久化。 */
        @NotBlank(message = "cardInfo.cardNo is required")
        @Pattern(regexp = "^\\d{12,19}$", message = "cardInfo.cardNo format does not match")
        private String cardNo;

        /** 两位卡片到期月份，格式 {@code MM}。 */
        @NotBlank(message = "cardInfo.expirationMonth is required")
        @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "cardInfo.expirationMonth format does not match")
        private String expirationMonth;

        /** 四位卡片到期年份，格式 {@code yyyy}。 */
        @NotBlank(message = "cardInfo.expirationYear is required")
        @Pattern(regexp = "^20\\d{2}$", message = "cardInfo.expirationYear format does not match")
        private String expirationYear;

        /** 三或四位 CVV/CVC，严禁日志、缓存、数据库或响应回显。 */
        @NotBlank(message = "cardInfo.securityCode is required")
        @Pattern(regexp = "^\\d{3,4}$", message = "cardInfo.securityCode format does not match")
        private String securityCode;

        /** 卡面持卡人姓名，属于个人信息，日志中必须脱敏。 */
        @NotBlank(message = "cardInfo.cardholderName is required")
        private String cardholderName;
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

        /**
         * 前端生成的设备标识，内部只传输 hash，不能作为唯一安全凭据。
         */
        private String deviceId;
    }
}
