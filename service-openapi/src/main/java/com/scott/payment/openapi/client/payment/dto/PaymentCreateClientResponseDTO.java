package com.scott.payment.openapi.client.payment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateClientResponseDTO
 * @date : 2026-05-31 21:11
 * @email : scott_x@163.com
 * @description : service-payment 创建收单交易的内部响应参数，返回商户 OpenAPI 需要回显的订单、交易、金额、卡品牌和平台响应码摘要；operationId 仅内部使用，不返回商户。
 * @status : create
 */
@Data
public class PaymentCreateClientResponseDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台当前交易唯一标识，每一笔授权、请款、退款、撤销都不同。
     */
    private String transactionId;

    /**
     * 原平台交易唯一标识，后续动作返回商户请求传入的 sourceTransactionId。
     */
    private String sourceTransactionId;

    /**
     * 平台内部关联订单标识，同一交易生命周期共用，不返回商户。
     */
    private String operationId;

    /**
     * 商户订单号，来自 orderInfo.orderNo。
     */
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识，来自 orderInfo.orderId。
     */
    private String merchantOrderId;

    /**
     * 支付平台颁发的商户号。
     */
    private String merchantId;

    /**
     * 商户侧子商户信息，用于响应中回显允许展示的子商户摘要。
     */
    private SubMerchantInfoDTO subMerchantInfo;

    /**
     * 交易类型，对齐字典 transaction_type。
     */
    private String transactionType;

    /**
     * 交易状态，对齐字典 transaction_status。
     */
    private String status;

    /**
     * 当前动作商户响应码，例如 T200、T202、T203、F210。
     */
    private String merchantResponseCode;

    /**
     * 当前动作商户响应描述。
     */
    private String merchantResponseMessage;

    /**
     * 内部处理阶段。
     */
    private String processStage;

    /**
     * 失败原因码。
     */
    private String failReasonCode;

    /**
     * 挂起原因码。
     */
    private String pendingReasonCode;

    /**
     * 商户可见失败原因描述，失败时返回模糊原因。
     */
    private String failReasonMessage;

    /**
     * 交易金额，最小币种单位；兼容旧调用方，新接入优先读取 transactionAmount。
     */
    private Long amount;

    /**
     * 交易币种；兼容旧调用方，新接入优先读取 transactionCurrency。
     */
    private String currency;

    /**
     * 商户上送订单金额，主币种单位。
     */
    private BigDecimal orderAmount;

    /**
     * 商户上送订单币种。
     */
    private String orderCurrency;

    /**
     * 当前生命周期累计授权成功金额，平台交易币种单位。
     */
    private BigDecimal totalAuthorizedAmount;

    /**
     * 当前生命周期累计请款成功金额，平台交易币种单位。
     */
    private BigDecimal totalCapturedAmount;

    /**
     * 当前生命周期累计退款成功金额，平台交易币种单位。
     */
    private BigDecimal totalRefundAmount;

    /**
     * 当前生命周期累计授权取消、预授权取消或未请款金额释放成功金额，平台交易币种单位。
     */
    private BigDecimal totalAuthorizedCancelAmount;

    /**
     * 当前生命周期累计拒付成立或确认成功金额，平台交易币种单位。
     */
    private BigDecimal totalRefuseAmount;

    /**
     * 商户上送或页面标签展示的原始金额。
     */
    private BigDecimal labelAmount;

    /**
     * 商户上送或页面标签展示的原始币种。
     */
    private String labelCurrency;

    /**
     * 平台上送渠道的交易金额，主币种单位。
     */
    private BigDecimal transactionAmount;

    /**
     * 平台上送渠道的交易币种。
     */
    private String transactionCurrency;

    /**
     * 标签金额转平台交易金额使用的汇率。
     */
    private BigDecimal transactionRate;

    /**
     * 汇率来源编码。
     */
    private String rateSource;

    /**
     * 汇率生效或报价时间。
     */
    private LocalDateTime rateTime;

    /**
     * 预计或最终结算金额。
     */
    private BigDecimal settlementAmount;

    /**
     * 预计或最终结算币种。
     */
    private String settlementCurrency;

    /**
     * 交易发生时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime transactionDateTime;

    /** 生命周期根主单的分片时间，后续动作必须原样回传。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime rootTransactionDateTime;

    /**
     * 交易发生时区。
     */
    private String transactionTimeZone;

    /**
     * 支付方式，如 BANK_CARD。
     */
    private String paymentMethod;

    /**
     * 卡品牌或支付品牌，如 MASTERCARD、VISA。
     */
    private String paymentBrand;

    /**
     * 脱敏卡 BIN，格式为前六位 + **** + 后四位。
     */
    private String cardBin;

    /**
     * 授权码，渠道成功返回时填写。
     */
    private String authCode;

    /**
     * ARN 或收单机构参考号。
     */
    private String acquirerReferenceNo;

    /**
     * 订单备注或描述，商户上送后原样返回。
     */
    private String description;

    /**
     * 商户通知回调地址，商户上送或配置存在时返回。
     */
    private String callbackUrl;

    /**
     * 首次交易保存的商户网站原始 URL，创建和幂等响应必须返回同一值。
     */
    private String merchantWebsite;

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : SubMerchantInfoDTO
     * @date : 2026-05-31 21:11
     * @email : scott_x@163.com
     * @description : Sub Merchant Info DTO 传输模型，位于 商户开放接口服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
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

}
