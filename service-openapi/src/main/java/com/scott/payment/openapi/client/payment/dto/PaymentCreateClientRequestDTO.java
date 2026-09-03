package com.scott.payment.openapi.client.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateClientRequestDTO
 * @date : 2026-05-31 21:10
 * @email : scott_x@163.com
 * @description : OpenAPI 调用 service-payment 创建收单交易的内部请求参数，承载渠道调用、风控执行和生命周期关联所需上下文；卡号和安全码只允许内存传递，禁止日志、MQ 和落库明文保存。
 * @status : create
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

    /** 支付核心内部使用的来源标识，不改变商户 OpenAPI 报文。 */
    private String requestSource;

    /** OpenAPI 申请主体稳定标识，当前使用已认证商户号。 */
    private String applicantId;

    /** 商户上送的退款原因摘要，仅在退款动作中使用。 */
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

    /** 首次交易的商品或服务明细快照。 */
    private List<GoodsInfoDTO> goodsInfo;

    /** 商户上送的付款人身份、地址和浏览器上下文。 */
    private PayerInfoDTO payerInfo;

    /** 商户可选上送的收货人及收货地址快照。 */
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
     * 付款人 IP，来自网关转发头或请求远端地址，用于风控识别。
     */
    private String payerIp;

    /**
     * 付款人浏览器 User-Agent，用于风控、3DS 和排查。
     */
    private String userAgent;

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : SubMerchantInfoDTO
     * @date : 2026-05-31 21:10
     * @email : scott_x@163.com
     * @description : service-openapi 调用支付核心时传递的子商户快照，字段来自已校验的开放接口请求。
     * @status : create
     */
    @Data
    public static class SubMerchantInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * {@code subName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String subName;

        /**
         * {@code subCompanyName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String subCompanyName;

        /**
         * {@code subId}，用于定位 {@code SubMerchantInfoDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String subId;

        /**
         * {@code subStreet}字段，保存 {@code SubMerchantInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String subStreet;

        /**
         * {@code subCity}字段，保存 {@code SubMerchantInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String subCity;

        /**
         * {@code subState}字段，保存 {@code SubMerchantInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String subState;

        /**
         * {@code subCountryCode}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String subCountryCode;

        /**
         * {@code subEmail}，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String subEmail;

        /**
         * {@code subPhone}，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String subPhone;

        /**
         * {@code subPostal}字段，保存 {@code SubMerchantInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String subPostal;

        /**
         * {@code subTaxId}，用于定位 {@code SubMerchantInfoDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String subTaxId;

        /**
         * 商户类别字段，保存 {@code SubMerchantInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String merchantCategory;

        /**
         * {@code intesCode}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String intesCode;

        /**
         * {@code chargeType}，用于区分 {@code SubMerchantInfoDTO} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String chargeType;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : BillingCardHolderInfoDTO
     * @date : 2026-05-31 21:10
     * @email : scott_x@163.com
     * @description : service-openapi 调用支付核心时传递的账单持卡人信息，日志和异常链路必须脱敏。
     * @status : create
     */
    @Data
    public static class BillingCardHolderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 首个名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String firstName;

        /**
         * {@code lastName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String lastName;

        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String phone;

        /**
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String email;

        /**
         * 国家或地区，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String country;

        /**
         * 状态，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String state;

        /**
         * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String city;

        /**
         * 街道，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String street;

        /**
         * 邮编，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String postal;
    }

    /** 商品或服务行信息，amount 为该行总金额。 */
    @Data
    public static class GoodsInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String name;
        /**
         * {@code quantity}字段，保存 {@code GoodsInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer quantity;
        /**
         * 金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal amount;
        /**
         * 币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String currency;
    }

    /** 付款人信息，属于敏感业务快照，普通日志必须脱敏。 */
    @Data
    public static class PayerInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * {@code payerId}，用于定位 {@code PayerInfoDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String payerId;
        /**
         * 首个名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String firstName;
        /**
         * {@code lastName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String lastName;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String phone;
        /**
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String email;
        /**
         * 国家或地区，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String country;
        /**
         * 状态，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String state;
        /**
         * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String city;
        /**
         * 街道，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String street;
        /**
         * 邮编，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String postal;
        /**
         * {@code ipAddress}字段，保存 {@code PayerInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String ipAddress;
        /**
         * {@code sessionId}，用于定位 {@code PayerInfoDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String sessionId;
        /**
         * {@code browserInfo}字段，保存 {@code PayerInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Map<String, Object> browserInfo;
        /**
         * {@code userAgent}字段，保存 {@code PayerInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String userAgent;
    }

    /** 收货人及收货地址信息，普通日志必须脱敏。 */
    @Data
    public static class ShippingInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 首个名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String firstName;
        /**
         * {@code lastName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String lastName;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String phone;
        /**
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String email;
        /**
         * 国家或地区，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String country;
        /**
         * 状态，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String state;
        /**
         * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String city;
        /**
         * 街道，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String street;
        /**
         * 邮编，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String postal;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CardInfoDTO
     * @date : 2026-05-31 21:10
     * @email : scott_x@163.com
     * @description : service-openapi 调用支付核心时短暂传递的银行卡认证信息，禁止通过 MQ、日志或数据库保存 PAN/CVV 明文。
     * @status : create
     */
    @Data
    public static class CardInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * PAN 卡号，只允许用于内存渠道调用，不允许明文日志、MQ 或落库。
         */
        private String cardNo;

        /**
         * {@code expirationMonth}字段，保存 {@code CardInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String expirationMonth;

        /**
         * {@code expirationYear}字段，保存 {@code CardInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String expirationYear;

        /**
         * CVV/CVC 安全码，只允许用于内存渠道调用，不允许明文日志、MQ 或落库。
         */
        private String securityCode;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDsInfoDTO
     * @date : 2026-05-31 21:10
     * @email : scott_x@163.com
     * @description : threeds信息传输模型，承载当前接口或跨层调用所需字段，不直接执行状态写入。
     * @status : create
     */
    @Data
    public static class ThreeDsInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * {@code eci}字段，保存 {@code ThreeDsInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
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
         * </p>
         */
        private String dsTransactionId;

        /**
         * {@code threeDsVersion}，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String threeDsVersion;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : TransactionInfoDTO
     * @date : 2026-05-31 21:10
     * @email : scott_x@163.com
     * @description : service-openapi 调用支付核心时传递的交易关联上下文，包含来源交易和商户通知/跳转信息。
     * @status : create
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
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 transactionId 建立后续请款、退款、撤销和原交易之间的关联。
         * </p>
         */
        private String sourceTransactionId;

        /**
         * 原交易业务时间，用于 service-payment 按 transaction_date_time + transaction_id 定位原交易分表。
         * <p>
         * 后续动作和查询必须直接透传平台响应中的真实时间，不允许由支付核心从交易号解析。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime sourceTransactionDateTime;

        /** 生命周期根主单的 transaction_date_time，用于 service-payment 精确路由 transaction_order。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;

        /**
         * 说明，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String description;

        /**
         * 回调地址，表示回调、通知、来源站点或远程接口地址。
         * <p>
         * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和协议由调用方校验；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
         * </p>
         */
        private String callbackUrl;

        /** 商户发起支付的网站原始 URL，用于来源网址限定和响应回显。 */
        private String merchantWebsite;

        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
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

        /** 商户体系内客户标识，用于客户维度名单和频率规则。 */
        private String customerId;

        /** 商户提供的稳定设备指纹，不得包含原始设备采集报文。 */
        private String deviceFingerprint;

        /** 收货街道地址，属于个人信息，仅用于风控匹配。 */
        private String shippingAddress;

        /** 收货邮编，用于地址风险规则。 */
        private String shippingPostalCode;

        /** ISO 3166-1 alpha-3 收货国家/地区代码。 */
        private String shippingCountry;
    }
}
