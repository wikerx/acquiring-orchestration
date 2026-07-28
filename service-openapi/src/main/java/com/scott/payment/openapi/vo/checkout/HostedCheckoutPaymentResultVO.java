package com.scott.payment.openapi.vo.checkout;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 付款人浏览器支付提交或状态查询响应。
 */
@Data
public class HostedCheckoutPaymentResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String checkoutSessionId;
    private String checkoutAttemptId;
    private String pageState;
    private PaymentResultVO result;
    private ThreeDsActionVO threeDsAction;
    private FailureVO failure;
    private PollingVO polling;
    private ActionVO actions;

    @Data
    public static class PaymentResultVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private BigDecimal amount;
        private String currency;
        private String merchantOrderNo;
        private String paymentMethod;
        private String cardBrand;
        private String cardNumberMasked;
        private String transactionId;
        private OffsetDateTime transactionDateTime;
        private String authCode;
    }

    @Data
    public static class ThreeDsActionVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String actionType;
        private String html;
        private String returnUrl;
        private Integer timeoutSeconds;
    }

    @Data
    public static class FailureVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String reasonCode;
        private String message;
        private Boolean retryAllowed;
        private Integer remainingAttemptCount;
    }

    @Data
    public static class PollingVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String statusUrl;
        private Integer intervalSeconds;
        private Integer maxIntervalSeconds;
    }

    @Data
    public static class ActionVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String returnUrl;
        private String cancelUrl;
    }
}
