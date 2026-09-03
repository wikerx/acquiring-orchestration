package com.scott.payment.openapi.client.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentQueryClientResponseDTO
 * @date : 2026-07-17 21:12
 * @email : scott_x@163.com
 * @description : OpenAPI 调用 service-payment 查询交易的内部响应，按商户订单聚合返回交易动作列表。
 * @status : create
 */
@Data
public class PaymentQueryClientResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
     * </p>
     */
    private String merchantId;

    /**
     * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与 merchantId、transactionId 共同支持幂等、查询和对账。
     * </p>
     */
    private String merchantOrderNo;

    /**
     * 商户请求订单标识，用于区分同一商户订单下的一次接口提交或后续交易动作。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与 merchantId、transactionId 共同支持幂等、查询和对账。
     * </p>
     */
    private String merchantOrderId;

    /** 首次交易保存的子商户快照。 */
    private PaymentCreateClientRequestDTO.SubMerchantInfoDTO subMerchantInfo;

    /** 首次交易保存的商品或服务明细。 */
    private List<PaymentCreateClientRequestDTO.GoodsInfoDTO> goodsInfo = new ArrayList<>();

    /** 首次交易保存的持卡人账单信息。 */
    private PaymentCreateClientRequestDTO.BillingCardHolderInfoDTO billingCardHolderInfo;

    /** 首次交易保存的付款人信息。 */
    private PaymentCreateClientRequestDTO.PayerInfoDTO payerInfo;

    /** 首次交易保存的收货人信息。 */
    private PaymentCreateClientRequestDTO.ShippingInfoDTO shippingInfo;

    /** 查询目标动作可向商户返回的 3DS 安全字段子集。 */
    private PaymentCreateClientResponseDTO.ThreeDsInfoDTO threeDSInfo;

    /**
     * 订单金额，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal orderAmount;

    /**
     * 订单币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private String orderCurrency;

    /**
     * {@code totalAuthorizedAmount}，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal totalAuthorizedAmount;

    /**
     * {@code totalCapturedAmount}，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal totalCapturedAmount;

    /**
     * 合计退款金额，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal totalRefundAmount;

    /**
     * {@code totalAuthorizedCancelAmount}，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal totalAuthorizedCancelAmount;

    /**
     * {@code totalRefuseAmount}，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal totalRefuseAmount;

    /**
     * 标签金额，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal labelAmount;

    /**
     * 标签币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private String labelCurrency;

    /**
     * 交易金额，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal transactionAmount;

    /**
     * 交易币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private String transactionCurrency;

    /**
     * 交易汇率字段，保存 {@code PaymentQueryClientResponseDTO} 当前处理所需的业务取值。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private BigDecimal transactionRate;

    /**
     * 汇率来源字段，保存 {@code PaymentQueryClientResponseDTO} 当前处理所需的业务取值。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String rateSource;

    /**
     * 汇率时间字段，保存 {@code PaymentQueryClientResponseDTO} 当前处理所需的业务取值。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private LocalDateTime rateTime;

    /**
     * 结算金额，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private BigDecimal settlementAmount;

    /**
     * 结算币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private String settlementCurrency;

    /** 已形成的结算换汇汇率。 */
    private BigDecimal settlementRate;

    /** 已形成的结算费用金额。 */
    private BigDecimal settlementFeeAmount;

    /** 已形成的费用明细。 */
    private List<PaymentCreateClientResponseDTO.FeeItemDTO> feeItems = new ArrayList<>();

    /**
     * 交易业务时区，使用 IANA 时区标识解释本地交易时间。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String transactionTimeZone;

    /**
     * 交易信息集合，承载 {@code PaymentQueryClientResponseDTO} 当前请求或响应中的多值数据。
     * <p>
     * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
     * </p>
     */
    private List<TransactionInfoDTO> transactionInfo = new ArrayList<>();

    /**
     * 查询返回的单笔交易动作摘要。
     */
    @Data
    public static class TransactionInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 operationId、merchantOrderNo 共同定位一笔平台交易。
         * </p>
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

        /** 后续动作源交易发生时间；首次交易为空。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime sourceTransactionDateTime;

        /**
         * 编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String code;

        /**
         * 说明字段，保存 {@code TransactionInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String message;

        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String transactionType;

        /** 当前动作交易状态。 */
        private String transactionStatus;

        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;

        /** 生命周期根主单的分片时间，供商户从查询结果继续发起动作。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;

        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentMethod;

        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String cardBrand;

        /**
         * 卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。
         * <p>
         * 单位：无；格式：卡 BIN 或尾号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅保存识别片段，不保存完整 PAN；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String cardBin;

        /**
         * {@code authCode}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String authCode;

        /**
         * {@code arn}字段，保存 {@code TransactionInfoDTO} 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String arn;

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

        /**
         * 生命周期首次交易保存的商户网站原始 URL。
         */
        private String merchantWebsite;

        /** Hosted Checkout 结果页返回地址。 */
        private String redirectUrl;

        /** Hosted Checkout 创建会话语言。 */
        private String language;
    }
}
