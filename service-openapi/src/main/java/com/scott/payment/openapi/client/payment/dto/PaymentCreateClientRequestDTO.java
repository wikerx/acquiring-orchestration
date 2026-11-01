package com.scott.payment.openapi.client.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 调用 service-payment 创建收单交易的内部请求参数，承载渠道调用、风控执行和生命周期关联所需上下文；卡号和安全码只允许内存传递，禁止日志、MQ 和落库明文保存。
 */
@Data
public class PaymentCreateClientRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 支付平台颁发的商户号。
     */
    private String merchantId;

    /**
     * 商户订单号，来自 orderInfo.orderNo。
     */
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识，来自 orderInfo.orderId，用作资金类幂等键。
     */
    private String merchantOrderId;

    /**
     * 交易类型，对齐字典 transaction_type，例如 AUTHORIZATION、PAYMENT、CAPTURE、REFUND。
     */
    private String transactionType;

    /**
     * 支付方式，例如 BANK_CARD、PAYPAL、APPLE_PAY。
     */
    private String paymentMethod;

    /**
     * 请求唯一号，当前与 merchantOrderId 一致，用于链路排查。
     */
    private String requestId;

    /**
     * 支付核心内部使用的来源标识，不改变商户 OpenAPI 报文。
     */
    private String requestSource;

    /**
     * 网关认证并规范化的接入客户端 IP，仅用于商户接入白名单和安全审计。
     * 该字段属于内部服务契约，不接受商户请求体赋值。
     */
    private String ingressClientIp;

    /**
     * OpenAPI 申请主体稳定标识，当前使用已认证商户号。
     */
    private String applicantId;

    /**
     * 商户上送的退款原因摘要，仅在退款动作中使用。
     */
    private String requestReason;

    /**
     * 订单金额，主币种单位。
     */
    private BigDecimal amount;

    /**
     * 交易币种，ISO 4217 三位大写字母。
     */
    private String currency;

    /**
     * 交易业务时间，数据库与分表均按 UTC+8 处理。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime transactionDateTime;

    /**
     * OpenAPI 收到的密文请求体指纹，仅用于排查链路，不包含原始密文或卡号。
     */
    private String requestFingerprint;

    /**
     * OpenAPI 请求路径，用于后台商户请求日志排查。
     */
    private String openApiRequestPath;

    /**
     * OpenAPI 请求进入服务层的时间。
     */
    private LocalDateTime openApiRequestTime;

    /**
     * 商户请求密文掩码，只保留首尾短片段，禁止传递完整密文。
     */
    private String merchantRequestCipherMasked;

    /**
     * 商户请求脱敏明文 JSON，卡号、CVV、JWT、密钥等敏感字段必须脱敏。
     */
    private String merchantRequestPlainJsonMasked;

    /**
     * 商户侧子商户信息，用于风控、渠道资料补充和 MID 路由。
     */
    private SubMerchantInfoDTO subMerchantInfo;

    /**
     * 持卡人账单信息，用于 AVS、风控和渠道请求。
     */
    private BillingCardHolderInfoDTO billingCardHolderInfo;

    /**
     * 首次交易的商品或服务明细快照。
     */
    private List<GoodsInfoDTO> goodsInfo;

    /**
     * 商户上送的付款人身份、地址和浏览器上下文。
     */
    private PayerInfoDTO payerInfo;

    /**
     * 商户可选上送的收货人及收货地址快照。
     */
    private ShippingInfoDTO shippingInfo;

    /**
     * 卡信息，仅允许在 OpenAPI 到 Payment 再到渠道调用的内存链路中使用。
     */
    private CardInfoDTO cardInfo;

    /**
     * 3DS 认证信息，用于渠道授权和责任转移判断。
     */
    private ThreeDsInfoDTO threeDsInfo;

    /**
     * 交易扩展信息，包含商户交易 ID、原交易引用和回调地址。
     */
    private TransactionInfoDTO transactionInfo;

    /**
     * 商户上送的可选实时风控上下文。
     */
    private RiskContextDTO riskContext;

    /**
     * 商户通知回调地址，交易状态变化后系统可按该地址推送异步通知。
     */
    private String callbackUrl;

    /**
     * 请求来源站点，优先来自 Origin 或 Referer，用于来源网址风控。
     */
    private String sourceUrl;

    /**
     * 付款人 IP；直连 OpenAPI 优先使用商户上送值，Hosted Checkout 使用真实浏览器 IP。
     * 该字段用于付款人名单、频控和 3DS，不得用于商户接入白名单。
     */
    private String payerIp;

    /**
     * 付款人浏览器 User-Agent，用于风控、3DS 和排查。
     */
    private String userAgent;

    /**
     * service-openapi 调用支付核心时传递的子商户快照，字段来自已校验的开放接口请求。
     */
    @Data
    public static class SubMerchantInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;


        private String subName;


        private String subCompanyName;


        private String subId;


        private String subStreet;


        private String subCity;


        private String subState;


        private String subCountryCode;

        /**
         * {@code subEmail}，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         */
        private String subEmail;

        /**
         * {@code subPhone}，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String subPhone;


        private String subPostal;


        private String subTaxId;


        private String merchantCategory;


        private String intesCode;

        /**
         * {@code chargeType}，用于区分 {@code SubMerchantInfoDTO} 记录的处理类别、配置维度或外部协议枚举。
         */
        private String chargeType;
    }

    /**
     * service-openapi 调用支付核心时传递的账单持卡人信息，日志和异常链路必须脱敏。
     */
    @Data
    public static class BillingCardHolderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;


        private String firstName;


        private String lastName;

        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String phone;

        /**
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         */
        private String email;


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
     * 商品或服务行信息，amount 为该行总金额。
     */
    @Data
    public static class GoodsInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;


        private String name;

        private Integer quantity;

        private BigDecimal amount;

        private String currency;
    }

    /**
     * 付款人信息，属于敏感业务快照，普通日志必须脱敏。
     */
    @Data
    public static class PayerInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;


        private String payerId;

        private String firstName;

        private String lastName;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String phone;
        /**
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         */
        private String email;

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
         * 传递给支付核心的浏览器环境扩展对象。
         *
         * <p>该对象承载公开协议中可扩展的浏览器、设备和网络子字段，内部调用不得丢弃未知字段；
         * 内容不得包含卡数据或密钥，也不得以原文写入日志。</p>
         */
        private Map<String, Object> browserInfo;

        private String userAgent;
    }

    /**
     * 收货人及收货地址信息，普通日志必须脱敏。
     */
    @Data
    public static class ShippingInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;


        private String firstName;

        private String lastName;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        private String phone;
        /**
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         */
        private String email;

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
     * service-openapi 调用支付核心时短暂传递的银行卡认证信息，禁止通过 MQ、日志或数据库保存 PAN/CVV 明文。
     */
    @Data
    public static class CardInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * PAN 卡号，只允许用于内存渠道调用，不允许明文日志、MQ 或落库。
         */
        private String cardNo;


        private String expirationMonth;


        private String expirationYear;

        /**
         * CVV/CVC 安全码，只允许用于内存渠道调用，不允许明文日志、MQ 或落库。
         */
        private String securityCode;
    }

    /**
     * 商户提交给支付核心的 3DS 认证偏好和既有认证结果。
     */
    @Data
    public static class ThreeDsInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;


        private String eci;

        /**
         * CAVV 属于认证敏感值，日志必须脱敏。
         */
        private String cavv;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String dsTransactionId;


        private String threeDsVersion;
    }

    /**
     * service-openapi 调用支付核心时传递的交易关联上下文，包含来源交易和商户通知/跳转信息。
     */
    @Data
    public static class TransactionInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 查询接口可选平台当前交易 ID；传入时 service-payment 只返回该商户订单下命中的单笔交易动作。
         */
        private String transactionId;

        /**
         * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
         */
        private String sourceTransactionId;

        /**
         * 原交易业务时间，用于 service-payment 按 transaction_date_time + transaction_id 定位原交易分表。
         * <p>
         * 后续动作和查询必须直接透传平台响应中的真实时间，不允许由支付核心从交易号解析。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime sourceTransactionDateTime;

        /**
         * 生命周期根主单的 transaction_date_time，用于 service-payment 精确路由 transaction_order。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;


        private String description;


        private String callbackUrl;

        /**
         * 商户发起支付的网站原始 URL，用于来源网址限定和响应回显。
         */
        private String merchantWebsite;

        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         */
        private String cardBrand;
    }

    /**
     * service-openapi 传给支付服务的附加风控上下文。
     *
     * <p>字段属于可识别个人或设备信息，支付服务只可用于实时风控和脱敏审计，
     * 不得在普通日志中输出原文。</p>
     */
    @Data
    public static class RiskContextDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户体系内客户标识，用于客户维度名单和频率规则。
         */
        private String customerId;

        /**
         * 商户提供的稳定设备指纹，不得包含原始设备采集报文。
         */
        private String deviceFingerprint;

        /**
         * 收货街道地址，属于个人信息，仅用于风控匹配。
         */
        private String shippingAddress;

        /**
         * 收货邮编，用于地址风险规则。
         */
        private String shippingPostalCode;

        /**
         * ISO 3166-1 alpha-3 收货国家/地区代码。
         */
        private String shippingCountry;
    }
}
