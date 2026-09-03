package com.scott.payment.openapi.dto.body;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiMerchantPaymentRequestDTO
 * @date : 2026-05-28 16:48
 * @email : scott_x@163.com
 * @description : API商户支付请求模型，位于 商户开放接口服务，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
 * @status : create
 */
@Data
@NoArgsConstructor
public class ApiMerchantPaymentRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于保证统一卡交易请求对象在接口、日志、消息等链路中的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 一步支付交易校验分组，适用于商户希望渠道一次完成授权和请款的请求。
     */
    public interface Payment {
    }

    /**
     * 授权交易校验分组，适用于首次授权或后续请款前的额度确认请求。
     */
    public interface Authorization {
    }

    /**
     * 预授权交易校验分组，适用于先冻结额度、后续再完成请款的请求。
     */
    public interface PreAuthorization {
    }

    /**
     * 增量授权校验分组，适用于对同一原始交易生命周期追加授权额度。
     */
    public interface IncrementalAuthorization {
    }

    /**
     * 请款交易校验分组，适用于对授权或预授权订单执行资金捕获。
     */
    public interface Capture {
    }

    /**
     * 预授权完成校验分组，适用于对预授权订单执行完成确认。
     */
    public interface PreAuthCompletion {
    }

    /**
     * 退款交易校验分组，适用于对成功交易发起全额或部分退款。
     */
    public interface Refund {
    }

    /**
     * 授权撤销校验分组，适用于撤销未完成清算的授权交易。
     */
    public interface AuthorizationCancel {
    }

    /**
     * 查询交易校验分组，适用于按商户订单号或平台交易号查询交易状态。
     */
    public interface Query {
    }

    /**
     * 冲正交易校验分组，适用于异常交易的反向更正。
     */
    public interface Reversal {
    }

    /**
     * 格式校验分组，统一执行字段长度、正则和枚举值合法性校验。
     */
    public interface Format {
    }

    /**
     * 商户信息，包含支付平台商户号以及子商户信息，所有收单类交易都需要用它定位商户配置。
     */
    @Valid
    @NotNull(message = "merchantInfo", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, PreAuthCompletion.class, Refund.class, AuthorizationCancel.class, Query.class, Reversal.class})
    private MerchantInfoDTO merchantInfo;

    /**
     * 订单信息，包含金额、币种、商户订单号和商户本次请求唯一标识。
     */
    @Valid
    @NotNull(message = "orderInfo", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, PreAuthCompletion.class, Refund.class, AuthorizationCancel.class, Query.class, Reversal.class})
    private OrderInfoDTO orderInfo;

    /** 商户可选上送的商品或服务明细，仅首次交易保存为生命周期快照。 */
    @Valid
    private List<GoodsInfoDTO> goodsInfo;

    /**
     * 3D Secure 认证信息，商户使用 3DS 交易时传入，用于渠道风控和责任转移判断。
     * <p>
     * API 文档使用字段名 threeDSInfo，这里保留 threeDsInfo Java 命名并通过 JSONField 兼容商户报文。
     */
    @Valid
    @JSONField(name = "threeDSInfo", alternateNames = {"threeDsInfo"})
    private ThreeDsInfoDTO threeDsInfo;

    /**
     * 持卡人账单信息，用于卡组织风控、AVS 校验和交易补充数据。
     */
    @Valid
    private BillingCardHolderInfoDTO billingCardHolderInfo;

    /** 付款人信息；首次交易必须提供对象和真实付款人公网 IP。 */
    @Valid
    @NotNull(message = "payerInfo", groups = {Payment.class, Authorization.class, PreAuthorization.class})
    private PayerInfoDTO payerInfo;

    /** 可选收货人及收货地址快照。 */
    @Valid
    private ShippingInfoDTO shippingInfo;

    /**
     * 卡信息，包含 PAN、有效期和安全码，是授权交易的核心敏感数据，日志中必须脱敏。
     */
    @Valid
    @NotNull(message = "cardInfo", groups = {Payment.class, Authorization.class, PreAuthorization.class})
    private CardInfoDTO cardInfo;

    /**
     * 平台交易信息。首次类交易不要求商户传入；后续动作通过 sourceTransactionId 定位原平台交易，查询接口可传 transactionId 精确过滤。
     */
    @Valid
    @NotNull(message = "transactionInfo", groups = {IncrementalAuthorization.class, Capture.class, PreAuthCompletion.class, Refund.class, AuthorizationCancel.class, Reversal.class})
    private TransactionInfoDTO transactionInfo;

    /**
     * 商户可选上送的交易风控上下文，仅用于实时风控匹配，不作为交易结果回显。
     */
    @Valid
    @JSONField(name = "riskInfo", alternateNames = {"riskContext"})
    private RiskInfoDTO riskInfo;

    /**
     * 撤销金额和币种由平台根据源交易计算，商户请求不得指定部分撤销金额。
     *
     * @return 非撤销校验或撤销请求未携带金额、币种时返回 true
     */
    @JSONField(serialize = false)
    @AssertTrue(message = "orderInfo.amount and orderInfo.currency are not accepted for void",
            groups = {AuthorizationCancel.class})
    public boolean isAuthorizationCancelAmountAndCurrencyAbsent() {
        return orderInfo == null || (orderInfo.getAmount() == null && !hasText(orderInfo.getCurrency()));
    }

    /**
     * 商户可选上送的交易风控上下文。
     *
     * <p>字段只用于实时名单、频率和地址风险匹配，不作为支付结果回显；
     * 地址、客户标识和设备指纹不得在普通日志中输出原文。</p>
     */
    @Data
    @NoArgsConstructor
    public static class RiskInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户体系内的客户唯一标识，用于客户维度白名单和交易频率控制。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E]{1,64}$",
                message = "riskContext.customerId format does not match", groups = {Format.class})
        private String customerId;

        /**
         * 商户生成的稳定设备指纹，不得包含原始设备采集报文。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E]{1,128}$",
                message = "riskContext.deviceFingerprint format does not match", groups = {Format.class})
        private String deviceFingerprint;

        /**
         * 收货街道地址，用于收货地址黑名单匹配。
         */
        @Pattern(regexp = "^$|^[\\x20-\\x7E]{1,256}$",
                message = "riskContext.shippingAddress format does not match", groups = {Format.class})
        private String shippingAddress;

        /**
         * 收货邮编，允许字母、数字、空格和短横线。
         */
        @Pattern(regexp = "^$|^(?=.{2,20}$)[A-Za-z0-9]+(?:[ -][A-Za-z0-9]+)*$",
                message = "riskContext.shippingPostalCode format does not match", groups = {Format.class})
        private String shippingPostalCode;

        /**
         * 收货国家或地区 ISO 3166-1 alpha-3 代码。
         */
        @Pattern(regexp = "^$|^[A-Z]{3}$",
                message = "riskContext.shippingCountry format does not match", groups = {Format.class})
        private String shippingCountry;
    }

    /** 商品或服务明细，金额是商品行总金额而不是单价。 */
    @Data
    @NoArgsConstructor
    public static class GoodsInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "goodsInfo.name", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Size(max = 128, message = "goodsInfo.name format does not match", groups = {Format.class})
        private String name;

        /**
         * {@code quantity}字段，保存 {@code GoodsInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotNull(message = "goodsInfo.quantity", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Positive(message = "goodsInfo.quantity must be greater than 0", groups = {Format.class})
        private Integer quantity;

        /**
         * 金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        @NotNull(message = "goodsInfo.amount", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @DecimalMin(value = "0", inclusive = false, message = "goodsInfo.amount must be greater than 0", groups = {Format.class})
        @Digits(integer = 12, fraction = 3, message = "goodsInfo.amount format does not match", groups = {Format.class})
        private BigDecimal amount;

        /**
         * 币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        @NotBlank(message = "goodsInfo.currency", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^[A-Z]{3}$", message = "goodsInfo.currency format does not match", groups = {Format.class})
        private String currency;
    }

    /** 付款人身份、联系方式、地址和浏览器上下文。 */
    @Data
    @NoArgsConstructor
    public static class PayerInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * {@code payerId}，用于定位 {@code PayerInfoDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 64, message = "payerInfo.payerId format does not match", groups = {Format.class})
        private String payerId;
        /**
         * 首个名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 32, message = "payerInfo.firstName format does not match", groups = {Format.class})
        private String firstName;
        /**
         * {@code lastName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 32, message = "payerInfo.lastName format does not match", groups = {Format.class})
        private String lastName;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 32, message = "payerInfo.phone format does not match", groups = {Format.class})
        private String phone;
        /**
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 64, message = "payerInfo.email format does not match", groups = {Format.class})
        private String email;
        /**
         * 国家或地区，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Pattern(regexp = "^$|^[A-Z]{3}$", message = "payerInfo.country format does not match", groups = {Format.class})
        private String country;
        /**
         * 状态，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 64, message = "payerInfo.state format does not match", groups = {Format.class})
        private String state;
        /**
         * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 64, message = "payerInfo.city format does not match", groups = {Format.class})
        private String city;
        /**
         * 街道，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 128, message = "payerInfo.street format does not match", groups = {Format.class})
        private String street;
        /**
         * 邮编，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 32, message = "payerInfo.postal format does not match", groups = {Format.class})
        private String postal;

        /**
         * {@code ipAddress}字段，保存 {@code PayerInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "payerInfo.ipAddress", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Size(max = 64, message = "payerInfo.ipAddress format does not match", groups = {Format.class})
        private String ipAddress;

        /**
         * {@code sessionId}，用于定位 {@code PayerInfoDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 128, message = "payerInfo.sessionId format does not match", groups = {Format.class})
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
        @Size(max = 512, message = "payerInfo.userAgent format does not match", groups = {Format.class})
        private String userAgent;
    }

    /** 可选收货人及收货地址。 */
    @Data
    @NoArgsConstructor
    public static class ShippingInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 首个名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 32, message = "shippingInfo.firstName format does not match", groups = {Format.class})
        private String firstName;
        /**
         * {@code lastName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 32, message = "shippingInfo.lastName format does not match", groups = {Format.class})
        private String lastName;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 32, message = "shippingInfo.phone format does not match", groups = {Format.class})
        private String phone;
        /**
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 64, message = "shippingInfo.email format does not match", groups = {Format.class})
        private String email;
        /**
         * 国家或地区，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
         * <p>
         * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持国家地区；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Pattern(regexp = "^$|^[A-Z]{3}$", message = "shippingInfo.country format does not match", groups = {Format.class})
        private String country;
        /**
         * 状态，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 3, message = "shippingInfo.state format does not match", groups = {Format.class})
        private String state;
        /**
         * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 64, message = "shippingInfo.city format does not match", groups = {Format.class})
        private String city;
        /**
         * 街道，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 128, message = "shippingInfo.street format does not match", groups = {Format.class})
        private String street;
        /**
         * 邮编，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 32, message = "shippingInfo.postal format does not match", groups = {Format.class})
        private String postal;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : MerchantInfoDTO
     * @date : 2026-05-28 16:48
     * @email : scott_x@163.com
     * @description : 开放支付请求的商户扩展信息，承载订单展示和渠道风控所需的商户属性。
     * @status : create
     */
    @Data
    @NoArgsConstructor
    public static class MerchantInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证商户信息对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 支付平台颁发的商户号，必须与 JWT 中的 merchantId 保持一致，避免商户冒用其他商户配置。
         */
        @NotBlank(message = "merchantInfo.merchantId", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, PreAuthCompletion.class, Refund.class, AuthorizationCancel.class, Query.class, Reversal.class})
        @Pattern(regexp = "^[2-9]\\d{5,15}$", message = "merchantInfo.merchantId format does not match", groups = {Format.class})
        private String merchantId;

        /**
         * 子商户信息，可选；商户上送时平台做字段格式校验并在响应中原样回显。
         */
        @Valid
        private SubMerchantInfoDTO subMerchantInfo;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : SubMerchantInfoDTO
     * @date : 2026-05-28 16:48
     * @email : scott_x@163.com
     * @description : 开放支付请求的子商户信息，供平台型商户传递实际经营主体快照。
     * @status : create
     */
    @Data
    @NoArgsConstructor
    public static class SubMerchantInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证子商户对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 子商户个人名称，个人商户可传；与 subCompanyName 至少填写一个。
         */
        @Pattern(regexp = "^$|^.{1,128}$", message = "merchantInfo.subMerchantInfo.subName format does not match", groups = {Format.class})
        private String subName;

        /**
         * 子商户公司名称，企业商户可传；与 subName 至少填写一个。
         */
        @Pattern(regexp = "^$|^.{1,128}$", message = "merchantInfo.subMerchantInfo.subCompanyName format does not match", groups = {Format.class})
        private String subCompanyName;

        /**
         * 商户侧子商户编号，用于聚合平台或平台代理模式下识别实际经营主体。
         */
        @NotBlank(message = "merchantInfo.subMerchantInfo.subId", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,32}$", message = "merchantInfo.subMerchantInfo.subId format does not match", groups = {Format.class})
        private String subId;

        /**
         * 子商户街道地址，用于卡组织和渠道侧商户补充信息。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,128}$", message = "merchantInfo.subMerchantInfo.subStreet format does not match", groups = {Format.class})
        private String subStreet;

        /**
         * 子商户城市，建议传英文或渠道要求的标准城市名称。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,64}$", message = "merchantInfo.subMerchantInfo.subCity format does not match", groups = {Format.class})
        private String subCity;

        /**
         * 子商户州/省代码，美国、加拿大等国家建议使用标准州代码。
         */
        @Pattern(regexp = "^$|^[a-zA-Z0-9]{1,3}$", message = "merchantInfo.subMerchantInfo.subState format does not match", groups = {Format.class})
        private String subState;

        /**
         * 子商户国家三字码，使用 ISO 3166-1 alpha-3 格式，例如 USA、CAN、GBR。
         */
        @Pattern(regexp = "^$|^[A-Z]{3}$", message = "merchantInfo.subMerchantInfo.subCountryCode format does not match", groups = {Format.class})
        private String subCountryCode;

        /**
         * 子商户邮箱，用于渠道风控和商户资料补充，可为空。
         */
        @Pattern(regexp = "^$|^(?=.{1,64}$)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "merchantInfo.subMerchantInfo.subEmail format does not match", groups = {Format.class})
        private String subEmail;

        /**
         * 子商户联系电话，用于渠道风控和商户资料补充，可为空。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,32}$", message = "merchantInfo.subMerchantInfo.subPhone format does not match", groups = {Format.class})
        private String subPhone;

        /**
         * 子商户邮编，卡组织和通道可能按国家、卡品牌执行不同长度规则，当前先按通用 ASCII 长度校验。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,32}$", message = "merchantInfo.subMerchantInfo.subPostal format does not match", groups = {Format.class})
        private String subPostal;

        /**
         * 子商户税号，供部分国家、区域或卡组织扩展风控使用。
         */
        @Pattern(regexp = "^$|^(?:[^\\u4e00-\\u9fa5·]{1,32})$", message = "merchantInfo.subMerchantInfo.subTaxId format does not match", groups = {Format.class})
        private String subTaxId;

        /**
         * 子商户 MCC 行业类别码，四位数字，用于卡组织行业识别和费率规则。
         */
        @Pattern(regexp = "^$|^\\d{4}$", message = "merchantInfo.subMerchantInfo.merchantCategory format does not match", groups = {Format.class})
        private String merchantCategory;

        /**
         * Intes 代码，当前主要服务 Diners 等卡组扩展参数，非相关卡组可不传。
         */
        @Pattern(regexp = "^$|^[a-zA-Z0-9]{3,4}$", message = "merchantInfo.subMerchantInfo.intesCode format does not match", groups = {Format.class})
        private String intesCode;

        /**
         * 费用类型，Diners 等卡组在请款或后续扩展交易中可能要求传入。
         */
        @Pattern(regexp = "^$|^[a-zA-Z0-9]{3}$", message = "merchantInfo.subMerchantInfo.chargeType format does not match", groups = {Format.class})
        private String chargeType;

        /**
         * 校验已登记子商户引用或临时子商户完整资料。
         * <p>
         * 只传 subId 时由平台读取已登记资料；只要附带任何资料字段，就必须提供完整经营主体资料。
         *
         * @return true 表示已登记引用或临时资料满足接口要求
         */
        @JSONField(serialize = false)
        @AssertTrue(message = "merchantInfo.subMerchantInfo must be a registered subId reference or complete temporary profile", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        public boolean isRegisteredReferenceOrCompleteProfile() {
            boolean hasProfileData = hasText(subName)
                    || hasText(subCompanyName)
                    || hasText(subStreet)
                    || hasText(subCity)
                    || hasText(subState)
                    || hasText(subCountryCode)
                    || hasText(subEmail)
                    || hasText(subPhone)
                    || hasText(subPostal)
                    || hasText(subTaxId)
                    || hasText(merchantCategory)
                    || hasText(intesCode)
                    || hasText(chargeType);
            if (!hasProfileData) {
                return true;
            }
            return (hasText(subName) || hasText(subCompanyName))
                    && hasText(subStreet)
                    && hasText(subCity)
                    && hasText(subCountryCode)
                    && hasText(merchantCategory);
        }
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : OrderInfoDTO
     * @date : 2026-05-28 16:48
     * @email : scott_x@163.com
     * @description : 开放支付请求的订单信息，承载商品描述、回调地址、跳转地址和语言等交易上下文。
     * @status : create
     */
    @Data
    @NoArgsConstructor
    public static class OrderInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证订单信息对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 订单金额，主币种单位，最多 12 位整数和 3 位小数，进入核心后可转换为最小币种单位。
         */
        @NotNull(message = "orderInfo.amount", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, PreAuthCompletion.class, Refund.class})
        @Digits(integer = 12, fraction = 3, message = "orderInfo.amount format does not match", groups = {Format.class})
        private BigDecimal amount;

        /**
         * 订单币种，使用 ISO 4217 三位大写币种代码，例如 USD、EUR、CNY。
         */
        @NotBlank(message = "orderInfo.currency", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, PreAuthCompletion.class})
        @Pattern(regexp = "^[A-Z]{3}$", message = "orderInfo.currency format does not match", groups = {Format.class})
        private String currency;

        /**
         * 商户订单号，由商户侧生成，是商户业务订单在平台侧的主要查询和对账字段。
         */
        @NotBlank(message = "orderInfo.orderNo", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, PreAuthCompletion.class, AuthorizationCancel.class, Query.class, Reversal.class})
        @Pattern(regexp = "^$|^[A-Za-z0-9]{1,64}$", message = "orderInfo.orderNo format does not match", groups = {Format.class})
        private String orderNo;

        /**
         * 商户本次 API 请求唯一标识，由商户侧生成，用作创建、请款、退款、撤销、查询等 OpenAPI 幂等键。
         */
        @NotBlank(message = "orderInfo.orderId", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, PreAuthCompletion.class, Refund.class, AuthorizationCancel.class, Query.class, Reversal.class})
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,64}$", message = "orderInfo.orderId format does not match", groups = {Format.class})
        private String orderId;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : BillingCardHolderInfoDTO
     * @date : 2026-05-28 16:48
     * @email : scott_x@163.com
     * @description : 开放支付请求的账单持卡人信息，属于可识别数据，转换和日志链路必须最小化及脱敏。
     * @status : create
     */
    @Data
    @NoArgsConstructor
    public static class BillingCardHolderInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证账单持卡人对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 持卡人名，用于卡组织风控、AVS 和渠道资料补充。
         */
        @NotBlank(message = "billingCardHolderInfo.firstName", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.firstName format does not match", groups = {Format.class})
        private String firstName;

        /**
         * 持卡人姓，用于卡组织风控、AVS 和渠道资料补充。
         */
        @NotBlank(message = "billingCardHolderInfo.lastName", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.lastName format does not match", groups = {Format.class})
        private String lastName;

        /**
         * 持卡人联系电话，用于卡组织风控和渠道补充资料。
         */
        @NotBlank(message = "billingCardHolderInfo.phone", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.phone format does not match", groups = {Format.class})
        private String phone;

        /**
         * 持卡人邮箱，用于交易风险识别、通知或渠道补充资料。
         */
        @NotBlank(message = "billingCardHolderInfo.email", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^(?=.{1,64}$)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "billingCardHolderInfo.email format does not match", groups = {Format.class})
        private String email;

        /**
         * 持卡人账单国家三字码，使用 ISO 3166-1 alpha-3 格式。
         */
        @NotBlank(message = "billingCardHolderInfo.country", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^[A-Z]{3}$", message = "billingCardHolderInfo.country format does not match", groups = {Format.class})
        private String country;

        /**
         * 持卡人账单州/省代码，美国、加拿大等国家建议传标准州代码。
         */
        @Pattern(regexp = "^$|^.{2,3}$", message = "billingCardHolderInfo.state format does not match", groups = {Format.class})
        private String state;

        /**
         * 持卡人账单城市，建议传英文或渠道要求的标准城市名称。
         */
        @NotBlank(message = "billingCardHolderInfo.city", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,64}$", message = "billingCardHolderInfo.city format does not match", groups = {Format.class})
        private String city;

        /**
         * 持卡人账单街道地址，用于 AVS 地址校验和渠道风控。
         */
        @NotBlank(message = "billingCardHolderInfo.street", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,128}$", message = "billingCardHolderInfo.street format does not match", groups = {Format.class})
        private String street;

        /**
         * 持卡人账单邮编，用于 AVS 地址校验和渠道风控。
         */
        @NotBlank(message = "billingCardHolderInfo.postal", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.postal format does not match", groups = {Format.class})
        private String postal;

        /**
         * 校验持卡人姓名总长度，避免渠道侧因 firstName + lastName 超长拒绝交易。
         *
         * @return true 表示姓名总长度满足限制
         */
        @AssertTrue(message = "The total length of billingCardHolderInfo.firstName and billingCardHolderInfo.lastName cannot exceed 64 characters", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @JSONField(serialize = false)
        public boolean isFirstNameAndLastNameValid() {
            return length(firstName) + length(lastName) <= 64;
        }
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CardInfoDTO
     * @date : 2026-05-28 16:48
     * @email : scott_x@163.com
     * @description : 开放支付请求的银行卡认证信息；PAN 和安全码只允许在受控内存链路使用，禁止明文落库或日志输出。
     * @status : create
     */
    @Data
    @NoArgsConstructor
    public static class CardInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证卡信息对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 持卡人 PAN 卡号，长度 11-19 位，只允许数字，日志和落库必须按 PCI 要求脱敏或加密。
         */
        @NotBlank(message = "cardInfo.cardNo", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^\\d{11,19}$", message = "cardInfo.cardNo format does not match", groups = {Format.class})
        private String cardNo;

        /**
         * 卡有效期月份，格式 MM，取值 01-12。
         */
        @NotBlank(message = "cardInfo.expirationMonth", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "cardInfo.expirationMonth format does not match", groups = {Format.class})
        private String expirationMonth;

        /**
         * 卡有效期年份，格式 yyyy，例如 2028。
         */
        @NotBlank(message = "cardInfo.expirationYear", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^\\d{4}$", message = "cardInfo.expirationYear format does not match", groups = {Format.class})
        private String expirationYear;

        /**
         * 卡安全码 CVV/CVC，长度 3-4 位，只允许数字，严禁落库和明文日志输出。
         */
        @NotBlank(message = "cardInfo.securityCode", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^\\d{3,4}$", message = "cardInfo.securityCode format does not match", groups = {Format.class})
        private String securityCode;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ThreeDsInfoDTO
     * @date : 2026-05-28 16:48
     * @email : scott_x@163.com
     * @description : 商户支付请求中的 3DS 认证信息模型，位于 service-openapi 入站 DTO 层，字段按开放接口协议校验且不承担认证状态写入职责。
     * @status : create
     */
    @Data
    @NoArgsConstructor
    public static class ThreeDsInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证 3DS 信息对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * ECI 电子商务指示符，用于说明 3DS 认证结果和交易责任归属。
         */
        @Pattern(regexp = "^$|^\\d{2}$", message = "threeDsInfo.eci format does not match", groups = {Format.class})
        private String eci;

        /**
         * CAVV 持卡人认证验证值，由 3DS 认证结果返回，用于渠道提交授权。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{28}$", message = "threeDsInfo.cavv format does not match", groups = {Format.class})
        private String cavv;

        /**
         * 3DS 交易唯一标识，由目录服务器或 3DS Server 返回，用于认证链路追踪。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{36}$", message = "threeDsInfo.dsTransactionId format does not match", groups = {Format.class})
        private String dsTransactionId;

        /**
         * 3DS 协议版本，例如 2.1.0、2.2.0，用于渠道判断支持能力。
         */
        @Pattern(regexp = "^$|^2\\.[1-9]\\.[0-9]$", message = "threeDsInfo.threeDsVersion format does not match", groups = {Format.class})
        private String threeDsVersion;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : TransactionInfoDTO
     * @date : 2026-05-28 16:48
     * @email : scott_x@163.com
     * @description : 开放支付请求的交易关联信息，承载来源交易、描述、网站、回调和页面跳转上下文。
     * @status : create
     */
    @Data
    @NoArgsConstructor
    public static class TransactionInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证交易扩展信息对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 原平台交易 ID，后续请款、退款、撤销、冲正和查询场景用于定位原交易。
         */
        @NotBlank(message = "transactionInfo.sourceTransactionId", groups = {IncrementalAuthorization.class, Capture.class, PreAuthCompletion.class, Refund.class, AuthorizationCancel.class, Reversal.class})
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,64}$", message = "transactionInfo.sourceTransactionId format does not match", groups = {Format.class})
        private String sourceTransactionId;

        /**
         * 平台当前交易唯一标识。查询接口可选传入；传入时只返回该商户订单下命中的单笔交易动作。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,64}$", message = "transactionInfo.transactionId format does not match", groups = {Format.class})
        private String transactionId;

        /**
         * 交易描述或备注，用于商户侧订单说明、渠道补充信息和客服排查。
         */
        @Pattern(regexp = "^$|^.{1,128}$", message = "transactionInfo.description format does not match", groups = {Format.class})
        private String description;

        /**
         * 商户通知回调地址，交易状态变化后系统可按该地址推送异步通知。
         */
        @Size(max = 512, message = "transactionInfo.callbackUrl format does not match", groups = {Format.class})
        @Pattern(regexp = "^$|^(?i:https?)://\\S+$", message = "transactionInfo.callbackUrl format does not match", groups = {Format.class})
        private String callbackUrl;

        /**
         * 商户发起支付的网站原始 URL，仅支付、授权和预授权用于来源网址限定，并在交易响应中原样返回。
         */
        @Size(max = 512, message = "transactionInfo.merchantWebsite format does not match", groups = {Format.class})
        @Pattern(regexp = "^$|^(?i:https?)://\\S+$",
                message = "transactionInfo.merchantWebsite format does not match", groups = {Format.class})
        private String merchantWebsite;

        /**
         * 校验商户网站必须包含可解析的 HTTP/HTTPS 主机名，避免非法 authority 绕过来源网址限定。
         *
         * @return 空值或合法商户网站返回 {@code true}
         */
        @JSONField(serialize = false)
        @AssertTrue(message = "transactionInfo.merchantWebsite format does not match", groups = {Format.class})
        public boolean isMerchantWebsiteValid() {
            if (!hasText(merchantWebsite)) {
                return true;
            }
            try {
                URI uri = URI.create(merchantWebsite);
                return uri.getUserInfo() == null
                        && hasText(uri.getHost())
                        && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }

    /**
     * 判断文本是否包含去除首尾空白后的有效字符。
     *
     * @param value 待检查文本
     * @return 非空且非纯空白时返回 {@code true}
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 安全计算可空文本长度。
     *
     * @param value 待计算文本
     * @return 文本长度；输入为 {@code null} 时返回 0
     */
    private static int length(String value) {
        return value == null ? 0 : value.length();
    }
}
