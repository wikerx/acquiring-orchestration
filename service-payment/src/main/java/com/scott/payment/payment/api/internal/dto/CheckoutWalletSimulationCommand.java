package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 本机模拟钱包的内部指令。订单及渠道凭据由服务端解析。 */
public record CheckoutWalletSimulationCommand(
        @NotBlank String tokenHash,
        @NotBlank String checkoutSessionId,
        @Pattern(regexp = "APPLE_PAY|GOOGLE_PAY") @NotBlank String paymentMethod,
        @Pattern(regexp = "PREPARE|OPEN|STATUS") @NotBlank String action,
        PaymentCheckoutPaymentSubmitCommandDTO.BillingCardHolderInfoDTO billingCardHolderInfo,
        @Pattern(regexp = "[A-Z]{3}") String payerCountry,
        @Pattern(regexp = "[A-Za-z0-9_-]{1,40}") String attemptRequestId) {
    public CheckoutWalletSimulationCommand(String token, String checkoutSessionId, String paymentMethod, String action,
            PaymentCheckoutPaymentSubmitCommandDTO.BillingCardHolderInfoDTO billing, String payerCountry) {
        this(token, checkoutSessionId, paymentMethod, action, billing, payerCountry, null);
    }

    @Override
    public String toString() {
        return "CheckoutWalletSimulationCommand";
    }
}
