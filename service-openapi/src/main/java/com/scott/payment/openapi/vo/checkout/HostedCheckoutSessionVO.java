package com.scott.payment.openapi.vo.checkout;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 付款人浏览器查询收银台展示响应。
 */
@Data
public class HostedCheckoutSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String checkoutSessionId;
    private String pageState;
    private MerchantVO merchant;
    private OrderVO order;
    private List<PaymentMethodVO> paymentMethods;
    private CheckoutVO checkout;

    @Data
    public static class MerchantVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String displayName;
        private String logoUrl;
    }

    @Data
    public static class OrderVO implements Serializable {

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
    public static class PaymentMethodVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String paymentMethod;
        private String channelCode;
        private List<String> brands;
        private String threeDsMode;
    }

    @Data
    public static class CheckoutVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private OffsetDateTime expireTime;
        private Boolean retryAllowed;
        private Integer remainingAttemptCount;
        private Integer pollingIntervalSeconds;
    }
}
