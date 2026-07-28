package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * Hosted Checkout 查询支付状态内部命令。
 */
@Data
public class PaymentCheckoutPaymentStatusCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "tokenHash is required")
    private String tokenHash;

    @NotBlank(message = "checkoutSessionId is required")
    private String checkoutSessionId;

    private String checkoutAttemptId;
    private String traceId;
    private String clientIpHash;
    private String userAgentHash;
}
