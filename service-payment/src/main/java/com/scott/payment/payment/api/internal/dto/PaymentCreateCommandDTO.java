package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateCommandDTO
 * @date : 2026-05-31 21:00
 * @email : scott_x@163.com
 * @description : service-openapi 调用 service-payment 创建收单交易的内部请求参数，承载渠道调用、风控执行和生命周期关联所需上下文；卡号和安全码只允许内存传递，禁止日志、MQ 和落库明文保存。
 * @status : create
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
     * 首次交易在本地准备事务中生成后写入，用于后续风控、路由、渠道和日志链路关联；商户创建请求不需要传入该字段。
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
    @NotBlank(message = "currency is required")
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
     * 卡信息，仅允许在 Payment 到渠道调用的内存链路中使用。
     */
    private CardInfoDTO cardInfo;

    /**
     * 3DS 认证信息，用于渠道授权和责任转移判断。
     */
    private ThreeDsInfoDTO threeDsInfo;

    /** Hosted Checkout 已命中强制 3DS；为 true 时支付核心只接受服务端确认的 PASSED 结果。 */
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
     * 付款人 IP，来自网关转发头或请求远端地址，用于风控识别。
     */
    private String payerIp;

    /**
     * 付款人浏览器 User-Agent，用于风控、3DS 和排查。
     */
    private String userAgent;

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : SubMerchantInfoDTO
     * @date : 2026-05-31 21:00
     * @email : scott_x@163.com
     * @description : Sub Merchant Info DTO 传输模型，位于 支付核心服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class SubMerchantInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * sub Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String subName;

        /**
         * sub Company Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；银行卡敏感字段，只允许脱敏或摘要化使用。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String subCompanyName;

        /**
         * sub ID，用于定位 Sub Merchant Info DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String subId;

        /**
         * sub Street，用于保存 Sub Merchant Info DTO 中与 substreet 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String subStreet;

        /**
         * sub City，用于保存 Sub Merchant Info DTO 中与 subcity 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String subCity;

        /**
         * sub State，用于保存 Sub Merchant Info DTO 中与 substate 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String subState;

        /**
         * sub Country Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String subCountryCode;

        /**
         * sub Email，用于保存 Sub Merchant Info DTO 中与 subemail 相关的业务属性。
         * <p>
         * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String subEmail;

        /**
         * sub Phone，用于保存 Sub Merchant Info DTO 中与 subphone 相关的业务属性。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String subPhone;

        /**
         * sub Postal，用于保存 Sub Merchant Info DTO 中与 subpostal 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String subPostal;

        /**
         * sub Tax ID，用于定位 Sub Merchant Info DTO 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String subTaxId;

        /**
         * merchant Category，用于保存 Sub Merchant Info DTO 中与 商户category 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String merchantCategory;

        /**
         * intes Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String intesCode;

        /**
         * charge Type，用于区分 Sub Merchant Info DTO 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String chargeType;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : BillingCardHolderInfoDTO
     * @date : 2026-05-31 21:00
     * @email : scott_x@163.com
     * @description : Billing Card Holder Info DTO 传输模型，位于 支付核心服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class BillingCardHolderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * first Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String firstName;

        /**
         * last Name，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String lastName;

        /**
         * phone，用于保存 Billing Card Holder Info DTO 中与 phone 相关的业务属性。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String phone;

        /**
         * email，用于保存 Billing Card Holder Info DTO 中与 email 相关的业务属性。
         * <p>
         * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String email;

        /**
         * country，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String country;

        /**
         * state，用于保存 Billing Card Holder Info DTO 中与 state 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String state;

        /**
         * city，用于保存 Billing Card Holder Info DTO 中与 city 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String city;

        /**
         * street，用于保存 Billing Card Holder Info DTO 中与 street 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String street;

        /**
         * postal，用于保存 Billing Card Holder Info DTO 中与 postal 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String postal;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CardInfoDTO
     * @date : 2026-05-31 21:00
     * @email : scott_x@163.com
     * @description : Card Info DTO 传输模型，位于 支付核心服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class CardInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * PAN 卡号，只允许用于内存渠道调用，不允许明文日志、MQ 或落库。
         */
        private String cardNo;

        /**
         * expiration Month，用于保存 Card Info DTO 中与 expirationmonth 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String expirationMonth;

        /**
         * expiration Year，用于保存 Card Info DTO 中与 expirationyear 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String expirationYear;

        /**
         * CVV/CVC 安全码，只允许用于内存渠道调用，不允许明文日志、MQ 或落库。
         */
        private String securityCode;
    }

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDsInfoDTO
     * @date : 2026-05-31 21:00
     * @email : scott_x@163.com
     * @description : Three Ds Info DTO 传输模型，位于 支付核心服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
    public static class ThreeDsInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 平台服务端确认的认证状态；资金动作只接受 PASSED。 */
        private String authenticationStatus;

        /**
         * MPGS 3DS authentication transaction id，PAY/AUTHORIZE 必须引用同一认证交易。
         */
        private String authenticationTransactionId;

        /**
         * eci，用于保存 Three Ds Info DTO 中与 eci 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String eci;

        /**
         * CAVV 属于认证敏感值，日志必须脱敏。
         */
        private String cavv;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String dsTransactionId;

        /**
         * three Ds Version，用于保存 Three Ds Info DTO 中与 threedsversion 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String threeDsVersion;
    }

    /**
     * 渠道侧订单和交易标识。
     */
    @Data
    public static class ChannelIdentityDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 3DS 策略评估和后续资金动作共同使用的渠道编码。 */
        private String channelCode;
        /** 已路由渠道主键。 */
        private Long channelId;
        /** 已路由渠道 MID 配置主键。 */
        private Long channelMidConfigId;

        /** 渠道订单号。 */
        private String channelOrderNo;
        /** 渠道交易号。 */
        private String channelTransactionId;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : TransactionInfoDTO
     * @date : 2026-05-31 21:00
     * @email : scott_x@163.com
     * @description : Transaction Info DTO 传输模型，位于 支付核心服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
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
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 transactionId 建立后续请款、退款、撤销和原交易之间的关联。
         * </p>
         */
        private String sourceTransactionId;

        /**
         * 原交易业务时间，用于按 transaction_date_time + transaction_id 精确定位交易主单所在物理分表。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime sourceTransactionDateTime;

        /** 生命周期根主单的 transaction_date_time，由调用链透传，禁止从业务编号解析。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;

        /**
         * 原交易对应的渠道交易 ID，由支付核心按 sourceTransactionId 查询原动作单后补齐，不要求商户上送。
         */
        private String sourceChannelTransactionId;

        /**
         * description，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String description;

        /**
         * callback URL，表示回调、通知、来源站点或远程接口地址。
         * <p>
         * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和协议由调用方校验；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
         * </p>
         */
        private String callbackUrl;

        /**
         * 商户发起支付的网站原始 URL，用于来源网址限定、交易主单留存和商户响应回显。
         */
        private String merchantWebsite;

        /**
         * card Brand，用于保存 Transaction Info DTO 中与 cardbrand 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private String cardBrand;

        /** BIN 基础数据识别出的发卡国家/地区，优先保存 ISO Alpha-2 编码。 */
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
