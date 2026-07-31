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

    /** 已认证的会话所属商户摘要。 */
    private MerchantInfoVO merchantInfo;

    /** 会话号、付款地址、状态和过期时间。 */
    private CheckoutInfoVO checkoutInfo;

    /** 商户订单号与金额摘要。 */
    private OrderInfoVO orderInfo;

    /**
     * 创建会话响应中的商户摘要。
     */
    @Data
    public static class MerchantInfoVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 与 OpenAPI 认证身份一致的平台商户号。 */
        private String merchantId;
    }

    /**
     * 创建会话响应中的收银台访问资料。
     */
    @Data
    public static class CheckoutInfoVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** Hosted Checkout 会话号。 */
        private String checkoutSessionId;

        /** 付款人访问收银台的 URL，可能携带一次性不透明令牌，不得写日志。 */
        private String checkoutUrl;

        /** 会话当前状态。 */
        private String status;

        /** 带时区偏移的会话过期时间。 */
        private OffsetDateTime expireTime;

        /** 本次请求是否命中商户幂等记录。 */
        private Boolean idempotentHit;
    }

    /**
     * 创建会话响应中的订单摘要。
     */
    @Data
    public static class OrderInfoVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商户订单号。 */
        private String orderNo;

        /** 商户自定义订单标识。 */
        private String orderId;

        /** 订单金额，单位为 {@link #currency} 主币种单位。 */
        private BigDecimal amount;

        /** ISO 4217 三位币种代码。 */
        private String currency;
    }
}
