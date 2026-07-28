package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 查询 Hosted Checkout 会话内部命令。
 */
@Data
public class PaymentCheckoutSessionQueryCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "tokenHash is required")
    private String tokenHash;

    private String cover;
    private String clientIpHash;
    private String userAgentHash;
    private String originHash;
    private String refererHash;
    private String deviceIdHash;
    private String language;
    private String timezoneOffset;
    private String traceId;
}
