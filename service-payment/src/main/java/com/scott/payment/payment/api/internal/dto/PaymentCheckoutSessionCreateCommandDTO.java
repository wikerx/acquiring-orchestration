package com.scott.payment.payment.api.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建 Hosted Checkout 会话内部命令。
 */
@Data
public class PaymentCheckoutSessionCreateCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "merchantId is required")
    private String merchantId;

    @NotBlank(message = "merchantOrderNo is required")
    private String merchantOrderNo;

    @NotBlank(message = "merchantRequestId is required")
    private String merchantRequestId;

    @NotBlank(message = "requestFingerprint is required")
    private String requestFingerprint;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    private String currency;

    @NotNull(message = "currencyExponent is required")
    private Integer currencyExponent;

    private String paymentAction;
    private String orderSubject;
    private String orderDescription;
    private String orderItemsJson;

    @Valid
    private List<AllowedPaymentMethodDTO> allowedPaymentMethods;

    @NotBlank(message = "checkoutDomain is required")
    private String checkoutDomain;

    private String locale;
    private String merchantDisplayName;
    private String merchantLogoUrl;
    private String merchantReturnUrl;
    private String merchantCancelUrl;
    private String merchantNotifyUrlHash;
    private String payerCountry;
    private String payerEmailMasked;
    private String payerEmailHash;
    private Integer retryAllowed;
    private Integer maxAttemptCount;
    private LocalDateTime expireTime;
    private String requestSource;
    private String traceId;

    @Data
    public static class AllowedPaymentMethodDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "paymentMethod is required")
        private String paymentMethod;

        private String channelCode;
        private List<String> brands;
        private String threeDsMode;
    }
}
