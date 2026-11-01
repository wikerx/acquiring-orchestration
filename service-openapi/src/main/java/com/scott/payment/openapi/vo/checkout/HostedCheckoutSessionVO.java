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

    /**
     * Hosted Checkout 会话号，不包含访问令牌。
     */
    private String checkoutSessionId;

    /**
     * 付款页状态，用于前端选择展示、过期或结果页面。
     */
    private String pageState;

    /**
     * 付款页可公开展示的商户资料。
     */
    private MerchantVO merchant;

    /**
     * 付款页订单金额与商品快照。
     */
    private OrderVO order;

    /**
     * 当前会话允许选择的支付方式。
     */
    private List<PaymentMethodVO> paymentMethods;

    /**
     * 会话有效期、重试次数和轮询配置。
     */
    private CheckoutVO checkout;

    /**
     * 商户上送的付款人预填信息。
     */
    private PayerInfoVO payerInfo;

    /**
     * 商户上送的账单预填信息。
     */
    private BillingInfoVO billingInfo;

    /**
     * 最近一次支付尝试结果。
     */
    private HostedCheckoutPaymentResultVO paymentResult;

    /**
     * 卡数据加密公钥元数据和一次性 nonce，仅可支付状态返回。
     */
    private CardEncryptionVO cardEncryption;

    /**
     * 浏览器加密卡数据所需的算法、公钥、密钥编号和随机数。
     */
    @Data
    public static class CardEncryptionVO implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 卡数据混合加密协议标识，调用双方必须使用完全一致的算法组合。
         */
        private String algorithm;

        private String keyId;
        /**
         * 响应中的{@code publicKey}，用于管理端或商户端展示当前处理结果。
         */
        private String publicKey;
        /**
         * 响应中的随机数，用于管理端或商户端展示当前处理结果。
         */
        private String nonce;
    }

    /**
     * 首次交易冻结后可供收银台展示的付款人信息。
     */
    @Data
    public static class PayerInfoVO implements Serializable {
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
    }

    /**
     * 首次交易冻结后可供收银台展示的账单联系信息。
     */
    @Data
    public static class BillingInfoVO implements Serializable {
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
     * 付款页公开展示的商户资料。
     */
    @Data
    public static class MerchantVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户展示名称，不包含商户密钥或内部状态。
         */
        private String displayName;

        /**
         * 商户 Logo 的受控公开地址。
         */
        private String logoUrl;
    }

    /**
     * 付款页订单展示快照。
     */
    @Data
    public static class OrderVO implements Serializable {

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
         * 订单金额，单位为 {@link #currency} 主币种单位。
         */
        private BigDecimal amount;

        /**
         * ISO 4217 三位币种代码。
         */
        private String currency;

        /**
         * 币种小数位数，用于前端格式化，不用于金额重新计算。
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
    public static class PaymentMethodVO implements Serializable {

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
    }

    /**
     * 付款页会话行为配置。
     */
    @Data
    public static class CheckoutVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 带时区偏移的会话过期时间。
         */
        private OffsetDateTime expireTime;

        /**
         * 带时区偏移的支付服务响应时间，用于校准浏览器倒计时。
         */
        private OffsetDateTime serverTime;

        /**
         * 当前会话是否允许支付失败后重试。
         */
        private Boolean retryAllowed;

        /**
         * 状态轮询建议间隔，单位秒。
         */
        private Integer pollingIntervalSeconds;
    }
}
