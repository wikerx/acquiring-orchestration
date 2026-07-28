package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
/**
 * Hosted Checkout 提交支付内部命令。
 */
@Getter
@Setter
public class PaymentCheckoutPaymentSubmitCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "tokenHash is required")
    private String tokenHash;

    @NotBlank(message = "checkoutSessionId is required")
    private String checkoutSessionId;

    @NotBlank(message = "attemptRequestId is required")
    private String attemptRequestId;

    @NotBlank(message = "paymentMethod is required")
    private String paymentMethod;

    private String requestFingerprint;
    private String traceId;
    private String clientIpHash;
    private String userAgentHash;
    private String originHash;
    private String refererHash;
    private String browserInfoJson;
    private String deviceInfoJson;

    @Valid
    private CardInfoDTO cardInfo;

    @Valid
    private BillingCardHolderInfoDTO billingCardHolderInfo;

    @Getter
    @Setter
    public static class CardInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String cardNo;
        private String expirationMonth;
        private String expirationYear;
        private String securityCode;
        private String cardholderName;
    }

    @Getter
    @Setter
    public static class BillingCardHolderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String country;
        private String state;
        private String city;
        private String street;
        private String postal;
    }
}
