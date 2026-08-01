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

    /** Hosted Checkout 会话号，不包含访问令牌。 */
    private String checkoutSessionId;

    /** 付款页状态，用于前端选择展示、过期或结果页面。 */
    private String pageState;

    /** 付款页可公开展示的商户资料。 */
    private MerchantVO merchant;

    /** 付款页订单金额与商品快照。 */
    private OrderVO order;

    /** 当前会话允许选择的支付方式。 */
    private List<PaymentMethodVO> paymentMethods;

    /** 会话有效期、重试次数和轮询配置。 */
    private CheckoutVO checkout;

    /**
     * 付款页公开展示的商户资料。
     */
    @Data
    public static class MerchantVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商户展示名称，不包含商户密钥或内部状态。 */
        private String displayName;

        /** 商户 Logo 的受控公开地址。 */
        private String logoUrl;
    }

    /**
     * 付款页订单展示快照。
     */
    @Data
    public static class OrderVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商户订单号。 */
        private String orderNo;

        /** 订单主题。 */
        private String subject;

        /** 订单说明。 */
        private String description;

        /** 订单金额，单位为 {@link #currency} 主币种单位。 */
        private BigDecimal amount;

        /** ISO 4217 三位币种代码。 */
        private String currency;

        /** 币种小数位数，用于前端格式化，不用于金额重新计算。 */
        private Integer currencyExponent;

        /** 商品明细 JSON 快照，不包含卡号、CVV 或密钥。 */
        private String itemsJson;
    }

    /**
     * 付款页可选支付方式。
     */
    @Data
    public static class PaymentMethodVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 平台支付方式编码。 */
        private String paymentMethod;

        /** 路由已限定的渠道编码；允许为空。 */
        private String channelCode;

        /** 可选卡品牌或支付品牌。 */
        private List<String> brands;

        /** 当前支付方式的 3DS 执行模式。 */
        private String threeDsMode;
    }

    /**
     * 付款页会话行为配置。
     */
    @Data
    public static class CheckoutVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 带时区偏移的会话过期时间。 */
        private OffsetDateTime expireTime;

        /** 当前会话是否允许支付失败后重试。 */
        private Boolean retryAllowed;

        /** 当前会话剩余支付尝试次数。 */
        private Integer remainingAttemptCount;

        /** 状态轮询建议间隔，单位秒。 */
        private Integer pollingIntervalSeconds;
    }
}
