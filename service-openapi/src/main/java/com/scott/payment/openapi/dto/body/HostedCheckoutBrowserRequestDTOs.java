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

    @Getter
    @Setter
    public static class SessionQueryRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "opaqueToken is required")
        private String opaqueToken;

        private String cover;
        private ClientContextDTO clientContext;
    }

    @Getter
    @Setter
    public static class PaymentSubmitRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "opaqueToken is required")
        private String opaqueToken;

        @NotBlank(message = "checkoutSessionId is required")
        private String checkoutSessionId;

        @NotBlank(message = "attemptRequestId is required")
        private String attemptRequestId;

        @NotBlank(message = "paymentMethod is required")
        private String paymentMethod;

        @Valid
        @NotNull(message = "cardInfo is required")
        private CardInfoDTO cardInfo;

        @Valid
        @NotNull(message = "billingCardHolderInfo is required")
        private BillingCardHolderInfoDTO billingCardHolderInfo;

        private ClientContextDTO clientContext;
    }

    @Getter
    @Setter
    public static class PaymentStatusRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "opaqueToken is required")
        private String opaqueToken;

        @NotBlank(message = "checkoutSessionId is required")
        private String checkoutSessionId;

        private String checkoutAttemptId;
        private ClientContextDTO clientContext;
    }

    @Getter
    @Setter
    public static class ThreeDsReturnRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "threeDsReturnToken is required")
        private String threeDsReturnToken;

        @NotBlank(message = "checkoutSessionId is required")
        private String checkoutSessionId;

        @NotBlank(message = "checkoutAttemptId is required")
        private String checkoutAttemptId;

        private String authenticationData;
        private ClientContextDTO clientContext;
    }

    @Getter
    @Setter
    public static class CardInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "cardInfo.cardNo is required")
        @Pattern(regexp = "^\\d{12,19}$", message = "cardInfo.cardNo format does not match")
        private String cardNo;

        @NotBlank(message = "cardInfo.expirationMonth is required")
        @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "cardInfo.expirationMonth format does not match")
        private String expirationMonth;

        @NotBlank(message = "cardInfo.expirationYear is required")
        @Pattern(regexp = "^20\\d{2}$", message = "cardInfo.expirationYear format does not match")
        private String expirationYear;

        @NotBlank(message = "cardInfo.securityCode is required")
        @Pattern(regexp = "^\\d{3,4}$", message = "cardInfo.securityCode format does not match")
        private String securityCode;

        @NotBlank(message = "cardInfo.cardholderName is required")
        private String cardholderName;
    }

    @Getter
    @Setter
    public static class BillingCardHolderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "billingCardHolderInfo.firstName is required")
        private String firstName;

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
