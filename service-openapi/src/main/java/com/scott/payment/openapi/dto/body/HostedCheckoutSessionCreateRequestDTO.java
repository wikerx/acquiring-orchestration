package com.scott.payment.openapi.dto.body;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HostedCheckoutSessionCreateRequestDTO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户创建 Hosted Checkout 会话请求。字段结构严格对齐商户文档 8.1，支付方式、有效期、重试和 3DS 策略由平台配置决定。
 * @status : create
 */
@Data
public class HostedCheckoutSessionCreateRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    public interface Create {
    }

    public interface Format {
    }

    /** 商户身份及可选子商户信息。 */
    @Valid
    @NotNull(message = "merchantInfo", groups = Create.class)
    private MerchantInfoDTO merchantInfo;

    /** 商户订单号、幂等号、金额和币种。 */
    @Valid
    @NotNull(message = "orderInfo", groups = Create.class)
    private OrderInfoDTO orderInfo;

    /** 可选商品或服务明细。 */
    @Valid
    private List<ApiMerchantPaymentRequestDTO.GoodsInfoDTO> goodsInfo;

    /** 可选账单持卡人预填对象；存在时整对象优先。 */
    @Valid
    private ApiMerchantPaymentRequestDTO.BillingCardHolderInfoDTO billingCardHolderInfo;

    /** 付款人快照；ipAddress 必传。 */
    @Valid
    @NotNull(message = "payerInfo", groups = Create.class)
    private ApiMerchantPaymentRequestDTO.PayerInfoDTO payerInfo;

    /** 可选收货人快照。 */
    @Valid
    private ApiMerchantPaymentRequestDTO.ShippingInfoDTO shippingInfo;

    /** 当前版本预留的风控扩展对象，不参与处理且不返回。 */
    @Valid
    private ApiMerchantPaymentRequestDTO.RiskInfoDTO riskInfo;

    /** 可选交易描述、终态通知、结果页返回地址和语言。 */
    @Valid
    private TransactionInfoDTO transactionInfo;

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : MerchantInfoDTO
     * @date : 2026-09-02 08:03
     * @email : scott_x@163.com
     * @description : 商户信息传输模型，承载当前接口或跨层调用所需字段，不直接执行状态写入。
     * @status : create
     */
    @Data
    public static class MerchantInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
         * </p>
         */
        @NotBlank(message = "merchantInfo.merchantId", groups = Create.class)
        @Pattern(regexp = "^[2-9]\\d{5,15}$", message = "merchantInfo.merchantId format does not match", groups = Format.class)
        private String merchantId;

        /**
         * {@code subMerchantInfo}字段，保存 {@code MerchantInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Valid
        private ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO subMerchantInfo;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : OrderInfoDTO
     * @date : 2026-09-02 08:03
     * @email : scott_x@163.com
     * @description : 订单信息传输模型，承载当前接口或跨层调用所需字段，不直接执行状态写入。
     * @status : create
     */
    @Data
    public static class OrderInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        @NotNull(message = "orderInfo.amount", groups = Create.class)
        @DecimalMin(value = "0", inclusive = false, message = "orderInfo.amount must be greater than 0", groups = Format.class)
        @Digits(integer = 12, fraction = 3, message = "orderInfo.amount format does not match", groups = Format.class)
        private BigDecimal amount;

        /**
         * 币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        @NotBlank(message = "orderInfo.currency", groups = Create.class)
        @Pattern(regexp = "^[A-Z]{3}$", message = "orderInfo.currency format does not match", groups = Format.class)
        private String currency;

        /**
         * 订单编号字段，保存 {@code OrderInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "orderInfo.orderNo", groups = Create.class)
        @Pattern(regexp = "^[A-Za-z0-9]{1,64}$", message = "orderInfo.orderNo format does not match", groups = Format.class)
        private String orderNo;

        /**
         * 订单ID，用于定位 {@code OrderInfoDTO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @NotBlank(message = "orderInfo.orderId", groups = Create.class)
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,64}$", message = "orderInfo.orderId format does not match", groups = Format.class)
        private String orderId;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : TransactionInfoDTO
     * @date : 2026-09-02 08:03
     * @email : scott_x@163.com
     * @description : 交易信息传输模型，承载当前接口或跨层调用所需字段，不直接执行状态写入。
     * @status : create
     */
    @Data
    public static class TransactionInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 说明，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 128, message = "transactionInfo.description format does not match", groups = Format.class)
        private String description;

        /**
         * 回调地址，表示回调、通知、来源站点或远程接口地址。
         * <p>
         * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和协议由调用方校验；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 transactionId、operationId 和通知状态共同定位异步回调处理。
         * </p>
         */
        @Size(max = 512, message = "transactionInfo.callbackUrl format does not match", groups = Format.class)
        @Pattern(regexp = "^$|^(?i:https?)://\\S+$", message = "transactionInfo.callbackUrl format does not match", groups = Format.class)
        private String callbackUrl;

        /**
         * 重定向地址URL，表示回调、通知、来源站点或远程接口地址。
         * <p>
         * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和协议由调用方校验；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 512, message = "transactionInfo.redirectUrl format does not match", groups = Format.class)
        @Pattern(regexp = "^$|^(?i:https?)://\\S+$", message = "transactionInfo.redirectUrl format does not match", groups = Format.class)
        private String redirectUrl;

        /**
         * {@code language}字段，保存 {@code TransactionInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @Size(max = 8, message = "transactionInfo.language format does not match", groups = Format.class)
        @Pattern(regexp = "^$|^[A-Za-z]{2,3}(?:-[A-Za-z]{2,4})?$", message = "transactionInfo.language format does not match", groups = Format.class)
        private String language;
    }
}
