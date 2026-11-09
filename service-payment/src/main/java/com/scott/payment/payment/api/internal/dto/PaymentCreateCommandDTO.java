package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * service-openapi 调用 service-payment 创建收单交易的内部请求参数，承载渠道调用、风控执行和生命周期关联所需上下文；卡号和安全码只允许内存传递，禁止日志、MQ 和落库明文保存。
 */
@Data
public class PaymentCreateCommandDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 支付平台颁发的商户号，用于定位商户、通道、风控和费率配置。
     */
    @NotBlank(message = "merchantId is required")
    private String merchantId;

    /**
     * 商户订单号，来自 orderInfo.orderNo，用于商户侧查询和对账。
     */
    @NotBlank(message = "merchantOrderNo is required")
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识，来自 orderInfo.orderId，用作资金类请求幂等键。
     */
    @NotBlank(message = "merchantOrderId is required")
    private String merchantOrderId;

    /**
     * 支付核心生成的平台当前交易号。
     * <p>
     * 首次交易在事务外业务规划开始时生成，用于后续风控、路由、持久化、渠道和日志链路关联；商户创建请求不需要传入该字段。
     * 非敏感字段，格式遵循平台交易号规则。
     * </p>
     */
    private String transactionId;

    /**
     * 交易类型，对齐字典 transaction_type，例如 AUTHORIZATION、PAYMENT、CAPTURE、REFUND。
     */
    private String transactionType;

    /**
     * 支付方式，例如 BANK_CARD、PAYPAL、APPLE_PAY。
     */
    private String paymentMethod;

    /**
     * 请求唯一标识，当前默认与 merchantOrderId 一致，用于链路追踪。
     */
    private String requestId;

    /**
     * 内部动作来源，例如 OPENAPI、ADMIN_PORTAL、MERCHANT_PORTAL 或 SYSTEM。
     * 该字段不属于商户 OpenAPI 外部协议。
     */
    private String requestSource;

    /**
     * 网关认证并规范化的接入客户端 IP，仅用于商户接入白名单和安全审计。
     * 该字段不属于商户外部协议，不得由付款人信息覆盖。
     */
    private String ingressClientIp;

    /**
     * 申请主体稳定标识；OpenAPI 使用商户号，后台使用认证账号 ID。
     */
    private String applicantId;

    /**
     * 申请时的显示名称快照。
     */
    private String applicantName;

    /**
     * 退款或撤销申请原因，不允许包含卡号、CVV、密钥或完整渠道报文。
     */
    private String requestReason;

    /**
     * 支付核心在校验历史成功和未终态退款后确定的退款范围。
     */
    private String refundScope;

    /**
     * 订单金额，主币种单位，例如 123.45 USD。
     * <p>
     * 该字段保留商户上送的标签金额；渠道不支持标签币种时，支付核心会在内部交易金额字段中保存 EDC 换汇后的金额。
     */
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
    private BigDecimal amount;

    /**
     * 订单币种，使用 ISO 4217 三位大写币种代码。
     * <p>
     * 该字段保留商户上送的标签币种；渠道不支持标签币种时，支付核心会在内部交易币种字段中保存 EDC 目标币种。
     */
    private String currency;

    /**
     * 商户上送或页面标签展示的原始金额。首次交易默认等于 amount，后续动作在归一化交易币种前保留商户请求值。
     */
    private BigDecimal labelAmount;

    /**
     * 商户上送或页面标签展示的原始币种。首次交易默认等于 currency，后续动作在归一化交易币种前保留商户请求值。
     */
    private String labelCurrency;

    /**
     * 标签币种 ISO 4217 小数位精度，由支付核心根据币种字典解析，不接受商户直接控制。
     */
    private Integer labelCurrencyExponent;

    /**
     * 平台交易金额，主币种单位；未启用 DCC/EDC 时等于标签金额，启用 EDC 时为换汇后上送渠道的金额。
     */
    private BigDecimal transactionAmount;

    /**
     * 平台交易币种，ISO 4217 三位代码；未启用 DCC/EDC 时等于标签币种，启用 EDC 时为渠道支持的目标币种。
     */
    private String transactionCurrency;

    /**
     * 标签金额转平台交易金额使用的汇率。未换汇时固定为 1.00000000。
     */
    private BigDecimal transactionRate;

    /**
     * 汇率来源编码，例如 BOC、PLATFORM；未换汇时为空。
     */
    private String rateSource;

    /**
     * 汇率生效或报价时间；未换汇时为空。
     */
    private LocalDateTime rateTime;

    /**
     * 是否启用 DCC，0 否、1 是；当前收单链路暂不启用 DCC。
     */
    private Integer dccEnabled;

    /**
     * 是否启用 EDC，0 否、1 是；渠道不支持标签币种且平台换汇后上送渠道时置为 1。
     */
    private Integer edcEnabled;

    /**
     * 交易请求时间，按 UTC+8 业务时区写入。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime transactionDateTime;

    /**
     * 请求体安全摘要，OpenAPI 层传入用于排查，但不保存完整密文和敏感卡信息。
     */
    private String requestFingerprint;

    /**
     * 内部风控评估流水号，仅用于交易主单、时间轴和风控审计关联；不作为商户响应字段。
     */
    private String riskRecordNo;

    /**
     * 内部风控原因码，仅用于交易时间轴和后台排障；不向商户暴露规则细节。
     */
    private String riskCode;

    /**
     * 内部风控原因摘要，仅用于后台排障，禁止包含完整 PAN、CVV 或名单明文。
     */
    private String riskMessage;

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
     * 首次交易商品或服务明细，用于生命周期快照和商户结果回显。
     */
    private List<GoodsInfoDTO> goodsInfo;

    /**
     * 商户上送的付款人信息，用于名单、AML、IP 和地域风控。
     */
    private PayerInfoDTO payerInfo;

    /**
     * 商户可选上送的收货人及收货地址，用于收货地址风险校验。
     */
    private ShippingInfoDTO shippingInfo;

    /**
     * 卡信息，仅允许在 Payment 到渠道调用的内存链路中使用。
     */
    private CardInfoDTO cardInfo;

    /**
     * 3DS 认证信息，用于渠道授权和责任转移判断。
     */
    private ThreeDsInfoDTO threeDsInfo;

    /**
     * Hosted Checkout 已命中强制 3DS；为 true 时支付核心只接受服务端确认的 PASSED 结果。
     */
    private Boolean threeDsRequired;

    /**
     * Hosted Checkout 等内部编排透传的渠道身份，不对商户开放。
     */
    private ChannelIdentityDTO channelIdentity;

    /**
     * 交易扩展信息，包含原平台交易 ID、描述和回调地址。
     */
    private TransactionInfoDTO transactionInfo;

    /**
     * 商户上送的可选实时风控上下文，只在交易受理和风控调用内存链路中使用。
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
     * 付款人 IP，用于付款人名单、频控和 3DS；不得用于商户接入白名单。
     */
    private String payerIp;

    /**
     * 付款人浏览器 User-Agent，用于风控、3DS 和排查。
     */
    private String userAgent;

    /**
     * 支付核心命令中的子商户快照，参与路由、风控和交易事实持久化。
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
     * 支付核心命令中的账单持卡人信息，属于可识别数据，只允许按渠道最小需要传递并脱敏记录。
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
     * 商品或服务行信息，amount 表示该行总金额。
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
     * 付款人身份、联系方式、地址和浏览器上下文。
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
         * 浏览器、设备和网络扩展信息；协议允许新增字段，因此保留动态结构以透传未知上下文。
         * 该结构可能包含可识别信息，禁止直接写入普通日志。
         */
        private Map<String, Object> browserInfo;

        private String userAgent;
    }

    /**
     * 收货人及收货地址信息。
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
     * 支付核心命令中的银行卡认证信息；PAN/CVV 只允许内存传递，禁止明文落库、日志或 MQ 投递。
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

        /**
         * 卡面持卡人姓名，只允许在当前支付渠道调用链使用。
         */
        private String cardholderName;
    }

    /**
     * 支付核心内部命令中的 3DS 认证信息模型，仅承载当前请求上下文；认证结果必须通过交易状态机和事实表持久化。
     */
    @Data
    public static class ThreeDsInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台服务端确认的认证状态；资金动作只接受 PASSED。
         */
        private String authenticationStatus;

        /**
         * MPGS 3DS authentication transaction id，PAY/AUTHORIZE 必须引用同一认证交易。
         */
        private String authenticationTransactionId;


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
     * 渠道侧订单和交易标识。
     */
    @Data
    public static class ChannelIdentityDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 3DS 策略评估和后续资金动作共同使用的渠道编码。
         */
        private String channelCode;
        /**
         * 已路由渠道主键。
         */
        private Long channelId;
        /**
         * 已路由渠道 MID 配置主键。
         */
        private Long channelMidConfigId;

        /**
         * 渠道订单号。
         */
        private String channelOrderNo;
        /**
         * 渠道交易号。
         */
        private String channelTransactionId;
    }

    /**
     * 支付核心命令中的交易关联信息，承载来源交易、商户回调、页面跳转和语言上下文。
     */
    @Data
    public static class TransactionInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台当前交易 ID；商户查询接口可选传入，用于在同一商户订单下精确过滤单笔交易动作。
         */
        private String transactionId;

        /**
         * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
         */
        private String sourceTransactionId;

        /**
         * 生命周期首笔平台交易 ID，由 transaction_locator 内部补齐，不接受商户上送。
         */
        private String rootTransactionId;

        /**
         * 原交易业务时间，用于按 transaction_date_time + transaction_id 精确定位交易主单所在物理分表。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime sourceTransactionDateTime;

        /**
         * 生命周期根主单的 transaction_date_time，由调用链透传，禁止从业务编号解析。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;

        /**
         * 原交易对应的渠道交易 ID，由支付核心按 sourceTransactionId 查询原动作单后补齐，不要求商户上送。
         */
        private String sourceChannelTransactionId;


        private String description;


        private String callbackUrl;

        /**
         * 商户发起支付的网站原始 URL，用于来源网址限定、交易主单留存和商户响应回显。
         */
        private String merchantWebsite;

        /**
         * Hosted Checkout 结果页返回地址，仅允许加密持久化。
         */
        private String redirectUrl;

        /**
         * Hosted Checkout 创建会话时指定的显示语言。
         */
        private String language;

        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         */
        private String cardBrand;

        /**
         * BIN 基础数据识别出的发卡国家/地区，优先保存 ISO Alpha-2 编码。
         */
        private String issuerCountry;
    }

    /**
     * 商户上送的支付风控上下文。
     *
     * <p>设备标识和收货地址仅按风控最小必要原则传递，日志必须脱敏；任何单一字段都不能
     * 作为放行交易或确认客户身份的唯一依据。</p>
     */
    @Data
    public static class RiskContextDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户体系内客户标识。
         */
        private String customerId;

        /**
         * 商户生成的稳定设备指纹。
         */
        private String deviceFingerprint;

        /**
         * 收货街道地址。
         */
        private String shippingAddress;

        /**
         * 收货邮编。
         */
        private String shippingPostalCode;

        /**
         * 收货国家或地区三字码。
         */
        private String shippingCountry;
    }
}
