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

    /** Hosted Checkout 会话号，不包含访问令牌。 */
    private String checkoutSessionId;
    /** 前端页面状态，用于选择付款、处理中或终态页面。 */
    private String pageState;
    /** 付款页可公开展示的商户资料。 */
    private MerchantDTO merchant;
    /** 创建会话时固化的订单展示快照。 */
    private OrderDTO order;
    /** 当前会话允许的支付方式快照。 */
    private List<PaymentMethodDTO> paymentMethods;
    /** 会话有效期、重试次数和轮询配置。 */
    private CheckoutDTO checkout;
    /** 商户上送的付款人预填信息。 */
    private PayerInfoDTO payerInfo;
    /** 商户上送的账单预填信息。 */
    private BillingInfoDTO billingInfo;
    /** 最近一次支付尝试结果，用于同一链接再次打开时直接回显。 */
    private PaymentCheckoutPaymentResultDTO paymentResult;
    /** 仅可支付状态下发的卡数据加密公钥元数据和一次性 nonce。 */
    private CardEncryptionDTO cardEncryption;

    @Data
    public static class CardEncryptionDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 固定为 RSA-OAEP-256+A256GCM，禁止算法降级。 */
        private String algorithm;
        /** 当前服务端卡数据密钥版本。 */
        private String keyId;
        /** X.509 DER Base64 RSA 公钥。 */
        private String publicKey;
        /** 与会话绑定且只允许消费一次的随机值。 */
        private String nonce;
    }

    @Data
    public static class PayerInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String payerId;
        private String email;
        private String firstName;
        private String lastName;
        private String phone;
        private String country;
        private String state;
        private String city;
        private String street;
        private String postal;
        private String ipAddress;
        private String sessionId;
        private java.util.Map<String, Object> browserInfo;
        private String userAgent;
    }

    @Data
    public static class BillingInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String country;
        private String state;
        private String city;
        private String street;
        private String postal;
    }

    /**
     * 付款页允许公开展示的商户资料。
     */
    @Data
    public static class MerchantDTO implements Serializable {

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
    public static class OrderDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商户订单号。 */
        private String orderNo;
        /** 订单主题。 */
        private String subject;
        /** 订单说明。 */
        private String description;
        /** 订单金额，单位为 {@link #currency} 的主币种单位。 */
        private BigDecimal amount;
        /** ISO 4217 三位币种代码。 */
        private String currency;
        /** 币种小数位数，仅供前端格式化。 */
        private Integer currencyExponent;
        /** 商品明细 JSON 快照，不包含卡号、CVV 或密钥。 */
        private String itemsJson;
    }

    /**
     * 付款页可选支付方式。
     */
    @Data
    public static class PaymentMethodDTO implements Serializable {

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
    public static class CheckoutDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 会话失效时间。 */
        private LocalDateTime expireTime;
        /** 当前会话是否允许失败后重试。 */
        private Boolean retryAllowed;
        /** 当前会话剩余支付尝试次数。 */
        private Integer remainingAttemptCount;
        /** 建议状态轮询间隔，单位秒。 */
        private Integer pollingIntervalSeconds;
    }
}
