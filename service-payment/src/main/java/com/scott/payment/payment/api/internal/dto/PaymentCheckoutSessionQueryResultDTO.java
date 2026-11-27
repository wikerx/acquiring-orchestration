package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 查询 Hosted Checkout 会话内部结果。
 */
@Data
public class PaymentCheckoutSessionQueryResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Hosted Checkout 会话号，不包含访问令牌。
     */
    private String checkoutSessionId;
    /**
     * 前端页面状态，用于选择付款、处理中或终态页面。
     */
    private String pageState;
    /** 支付链接持有人可能不同，前端仅按此标志启用链接钱包付款人流程。 */
    private Boolean paymentLink;
    /**
     * 付款页可公开展示的商户资料。
     */
    private MerchantDTO merchant;
    /**
     * 创建会话时固化的订单展示快照。
     */
    private OrderDTO order;
    /**
     * 当前会话允许的支付方式快照。
     */
    private List<PaymentMethodDTO> paymentMethods;
    /** 付款人可查看的真实选项，未适配的方式保留名称和禁止提交状态。 */
    private List<PaymentMethodDTO> paymentOptions;

    /** 本单付款国家展示范围。按方式或卡品牌键给出 ALL 默认值和具体国家例外，空值兼容旧版本。 */
    private Map<String, Map<String, Boolean>> paymentCountryScopes;
    /**
     * 会话有效期、重试次数和轮询配置。
     */
    private CheckoutDTO checkout;
    /**
     * 商户上送的付款人预填信息。
     */
    private PayerInfoDTO payerInfo;
    /**
     * 商户上送的账单预填信息。
     */
    private BillingInfoDTO billingInfo;
    /**
     * 最近一次支付尝试结果，用于同一链接再次打开时直接回显。
     */
    private PaymentCheckoutPaymentResultDTO paymentResult;
    /**
     * 仅可支付状态下发的卡数据加密公钥元数据和一次性 nonce。
     */
    private CardEncryptionDTO cardEncryption;

    /**
     * 卡数据加密公钥和一次性随机数传输模型。
     */
    @Data
    public static class CardEncryptionDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 固定为 RSA-OAEP-256+A256GCM，禁止算法降级。
         */
        private String algorithm;
        /**
         * 当前服务端卡数据密钥版本。
         */
        private String keyId;
        /**
         * X.509 DER Base64 RSA 公钥。
         */
        private String publicKey;
        /**
         * 与会话绑定且只允许消费一次的随机值。
         */
        private String nonce;
    }

    /**
     * 付款人预填信息传输模型。
     */
    @Data
    public static class PayerInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String payerId;
        /**
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         */
        private String email;

        private String firstName;

        private String lastName;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String phone;

        private String country;
        /**
         * 状态，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String state;
        /**
         * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String city;
        /**
         * 街道，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String street;
        /**
         * 邮编，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String postal;

        private String ipAddress;

        private String sessionId;
        /**
         * 浏览器、设备和网络扩展信息；协议允许新增字段，因此保留动态结构以完整回传原始上下文。
         * 该结构可能包含可识别信息，禁止直接写入普通日志。
         */
        private java.util.Map<String, Object> browserInfo;

        private String userAgent;
    }

    /**
     * 账单信息传输模型，承载当前接口或跨层调用所需字段，不直接执行状态写入。
     */
    @Data
    public static class BillingInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String firstName;

        private String lastName;
        /**
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         */
        private String email;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String phone;

        private String country;
        /**
         * 状态，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String state;
        /**
         * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String city;
        /**
         * 街道，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String street;
        /**
         * 邮编，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String postal;
    }

    /**
     * 付款页允许公开展示的商户资料。
     */
    @Data
    public static class MerchantDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户展示名称，不包含商户密钥或内部状态。
         */
        private String displayName;
        /**
         * 商户 Logo 的受控公开地址。
         */
        private String logoUrl;
        /** 当前币种下商户有效收单能力，仅用于品牌展示，不改变本会话的付款授权。 */
        private List<String> supportedPaymentMethods = List.of();
        /** 商户银行卡能力的实际品牌交集，不包含 MID 或渠道配置。 */
        private List<String> supportedCardBrands = List.of();
    }

    /**
     * 付款页订单展示快照。
     */
    @Data
    public static class OrderDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户订单号。
         */
        private String orderNo;
        /**
         * 订单主题。
         */
        private String subject;
        /**
         * 订单说明。
         */
        private String description;
        /**
         * 订单金额，单位为 {@link #currency} 的主币种单位。
         */
        private BigDecimal amount;
        /**
         * ISO 4217 三位币种代码。
         */
        private String currency;
        /**
         * 币种小数位数，仅供前端格式化。
         */
        private Integer currencyExponent;
        /**
         * 商品明细 JSON 快照，不包含卡号、CVV 或密钥。
         */
        private String itemsJson;
    }

    /**
     * 付款页可选支付方式。
     */
    @Data
    public static class PaymentMethodDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台支付方式编码。
         */
        private String paymentMethod;
        /**
         * 路由已限定的渠道编码；允许为空。
         */
        private String channelCode;
        /**
         * 可选卡品牌或支付品牌。
         */
        private List<String> brands;
        /**
         * 当前支付方式的 3DS 执行模式。
         */
        private String threeDsMode;
        /** 当前服务端是否有对应的提交协议。浏览器端仍需确认本地组件已准备。 */
        private boolean serverSubmitAllowed;
        /** 服务端已接通、组件暂未接通或当前状态不允许提交。 */
        private String executionStatus;
    }


    /**
     * 付款页会话行为配置。
     */
    @Data
    public static class CheckoutDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 会话失效时间。
         */
        private LocalDateTime expireTime;
        /**
         * 支付服务生成响应时的系统时间，用于前端校准付款倒计时。
         */
        private LocalDateTime serverTime;
        /**
         * 当前会话是否允许失败后重试。
         */
        private Boolean retryAllowed;
        /**
         * 建议状态轮询间隔，单位秒。
         */
        private Integer pollingIntervalSeconds;
    }
}
