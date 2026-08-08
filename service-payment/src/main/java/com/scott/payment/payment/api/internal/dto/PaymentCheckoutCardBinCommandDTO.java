package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/** 收银台 BIN 品牌查询命令，只允许 6 到 11 位前缀。 */
@Data
public class PaymentCheckoutCardBinCommandDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    @NotBlank(message = "tokenHash is required")
    private String tokenHash;
    @NotBlank(message = "checkoutSessionId is required")
    private String checkoutSessionId;
    @NotBlank(message = "cardBin is required")
    @Pattern(regexp = "^\\d{6,11}$", message = "cardBin format does not match")
    private String cardBin;
    private String traceId;
}
