package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hosted Checkout 支付提交或状态查询结果。
 */
@Data
public class PaymentCheckoutPaymentResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String checkoutSessionId;
    private String checkoutAttemptId;
    private String pageState;
    private PaymentResultDTO result;
    private ThreeDsActionDTO threeDsAction;
    private FailureDTO failure;
    private PollingDTO polling;
    private ActionDTO actions;

    @Data
    public static class PaymentResultDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private BigDecimal amount;
        private String currency;
        private String merchantOrderNo;
        private String paymentMethod;
        private String cardBrand;
        private String cardNumberMasked;
        private String transactionId;
        private LocalDateTime transactionDateTime;
        private String authCode;
    }

    @Data
    public static class ThreeDsActionDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String actionType;
        private String html;
        private String returnUrl;
        private Integer timeoutSeconds;
    }

    @Data
    public static class FailureDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String reasonCode;
        private String message;
        private Boolean retryAllowed;
        private Integer remainingAttemptCount;
    }

    @Data
    public static class PollingDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String statusUrl;
        private Integer intervalSeconds;
        private Integer maxIntervalSeconds;
    }

    @Data
    public static class ActionDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String returnUrl;
        private String cancelUrl;
    }
}
