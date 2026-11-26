package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 本机模拟钱包的内部指令。订单及渠道凭据由服务端解析。 */
public record CheckoutWalletSimulationCommand(
        @NotBlank String tokenHash,
        @NotBlank String checkoutSessionId,
        @Pattern(regexp = "APPLE_PAY|GOOGLE_PAY") @NotBlank String paymentMethod,
        @Pattern(regexp = "PREPARE|OPEN|STATUS") @NotBlank String action,
        PaymentCheckoutPaymentSubmitCommandDTO.BillingCardHolderInfoDTO billingCardHolderInfo) {
    @Override
    public String toString() {
        return "CheckoutWalletSimulationCommand";
    }
}
