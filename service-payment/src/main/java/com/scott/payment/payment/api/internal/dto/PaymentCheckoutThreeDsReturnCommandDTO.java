package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * Hosted Checkout 3DS 浏览器回跳内部命令。
 */
@Data
public class PaymentCheckoutThreeDsReturnCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "threeDsReturnTokenHash is required")
    private String threeDsReturnTokenHash;

    @NotBlank(message = "checkoutSessionId is required")
    private String checkoutSessionId;

    @NotBlank(message = "checkoutAttemptId is required")
    private String checkoutAttemptId;

    private String authenticationDataJsonMasked;
    private String traceId;
    private String clientIpHash;
    private String userAgentHash;
}
