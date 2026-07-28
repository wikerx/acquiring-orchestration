package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 查询 Hosted Checkout 会话内部结果。
 */
@Data
public class PaymentCheckoutSessionQueryResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String checkoutSessionId;
    private String pageState;
    private MerchantDTO merchant;
    private OrderDTO order;
    private List<PaymentMethodDTO> paymentMethods;
    private CheckoutDTO checkout;

    @Data
    public static class MerchantDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String displayName;
        private String logoUrl;
    }

    @Data
    public static class OrderDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String orderNo;
        private String subject;
        private String description;
        private BigDecimal amount;
        private String currency;
        private Integer currencyExponent;
        private String itemsJson;
    }

    @Data
    public static class PaymentMethodDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String paymentMethod;
        private String channelCode;
        private List<String> brands;
        private String threeDsMode;
    }

    @Data
    public static class CheckoutDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private LocalDateTime expireTime;
        private Boolean retryAllowed;
        private Integer remainingAttemptCount;
        private Integer pollingIntervalSeconds;
    }
}
