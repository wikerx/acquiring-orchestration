package com.scott.payment.openapi.vo.checkout;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 商户创建 Hosted Checkout 会话响应。
 */
@Data
public class HostedCheckoutSessionCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private MerchantInfoVO merchantInfo;
    private CheckoutInfoVO checkoutInfo;
    private OrderInfoVO orderInfo;

    @Data
    public static class MerchantInfoVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String merchantId;
    }

    @Data
    public static class CheckoutInfoVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String checkoutSessionId;
        private String checkoutUrl;
        private String status;
        private OffsetDateTime expireTime;
        private Boolean idempotentHit;
    }

    @Data
    public static class OrderInfoVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String orderNo;
        private String orderId;
        private BigDecimal amount;
        private String currency;
    }
}
