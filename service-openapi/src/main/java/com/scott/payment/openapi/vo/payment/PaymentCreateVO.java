package com.scott.payment.openapi.vo.payment;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateVO
 * @date : 2026-05-28 10:23
 * @email : scott_x@163.com
 * @description : 支付create响应模型，位于 商户开放接口服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
 * @status : create
 */
@Data
public class PaymentCreateVO implements Serializable {

    /**
     * 序列化版本号，用于保证响应对象在网关、日志、缓存等链路中的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 商户响应信息，支付接口按商户请求 merchantInfo 原样回显。
     */
    private MerchantInfoVO merchantInfo;

    /**
     * 商户订单响应信息，原样返回商户请求中的订单号、幂等请求号和订单金额。
     */
    private OrderInfoVO orderInfo;

    /** 首次交易保存的商品或服务明细。 */
    private List<GoodsInfoVO> goodsInfo;

    /**
     * 账单持卡人信息，支付接口按商户请求 billingCardHolderInfo 原样回显，不包含卡号和 CVV。
     */
    private BillingCardHolderInfoVO billingCardHolderInfo;

    /** 首次交易保存的付款人信息。 */
    private PayerInfoVO payerInfo;

    /** 首次交易保存的收货人信息。 */
    private ShippingInfoVO shippingInfo;

    /** 当前动作可向商户返回的 3DS 安全字段子集。 */
    private ThreeDsInfoVO threeDSInfo;

    /**
     * 平台交易响应信息，包含平台当前交易 ID、原交易 ID 和交易状态。
     */
    private TransactionInfoVO transactionInfo;

    /**
     * 账单和换汇信息，包含标签金额、平台交易金额、汇率和结算摘要。
     */
    private BillingInfoVO billingInfo;

    /**
     * 交易币种，使用 ISO 4217 三位大写币种代码；仅保留 Java 兼容读取，商户响应 JSON 不再输出顶层 currency。
     */
    @JSONField(serialize = false)
    private String currency;

    /**
     * 交易状态；仅保留 Java 兼容读取，商户响应 JSON 不再输出顶层 status。
     */
    @JSONField(serialize = false)
    private String status;

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : MerchantInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : 商户信息响应模型，位于 商户开放接口服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class MerchantInfoVO implements Serializable {

        /**
         * 序列化版本号，用于响应对象跨网关和测试链路传递兼容。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 支付平台颁发的商户号。
         */
        private String merchantId;

        /**
         * 子商户响应摘要，返回商户上送且允许回显的子商户字段。
         */
        private SubMerchantInfoVO subMerchantInfo;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : SubMerchantInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : sub商户信息响应模型，位于 商户开放接口服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class SubMerchantInfoVO implements Serializable {

        /**
         * 序列化版本号，用于响应对象跨网关和测试链路传递兼容。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 商户侧子商户编号。
         */
        private String subId;

        /**
         * 子商户个人名称。
         */
        private String subName;

        /**
         * 子商户公司名称。
         */
        private String subCompanyName;

        /**
         * 子商户国家三字码。
         */
        private String subCountryCode;

        /**
         * 子商户州/省代码。
         */
        private String subState;

        /**
         * 子商户城市。
         */
        private String subCity;

        /**
         * 子商户街道地址。
         */
        private String subStreet;

        /**
         * 子商户邮编。
         */
        private String subPostal;

        /**
         * 子商户邮箱。
         */
        private String subEmail;

        /**
         * 子商户联系电话。
         */
        private String subPhone;

        /**
         * 子商户税号。
         */
        private String subTaxId;

        /**
         * 子商户 MCC 行业类别码。
         */
        private String merchantCategory;

        /**
         * Intes 代码，部分卡组扩展字段。
         */
        private String intesCode;

        /**
         * 费用类型，部分卡组扩展字段。
         */
        private String chargeType;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : BillingCardHolderInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : 账单持卡人信息响应模型，位于 商户开放接口服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class BillingCardHolderInfoVO implements Serializable {

        /**
         * 序列化版本号，用于响应对象跨网关和测试链路传递兼容。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 持卡人名。
         */
        private String firstName;

        /**
         * 持卡人姓。
         */
        private String lastName;

        /**
         * 持卡人联系电话。
         */
        private String phone;

        /**
         * 持卡人邮箱。
         */
        private String email;

        /**
         * 持卡人账单国家三字码。
         */
        private String country;

        /**
         * 持卡人账单州/省代码。
         */
        private String state;

        /**
         * 持卡人账单城市。
         */
        private String city;

        /**
         * 持卡人账单街道地址。
         */
        private String street;

        /**
         * 持卡人账单邮编。
         */
        private String postal;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : OrderInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : 订单信息响应模型，位于 商户开放接口服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class OrderInfoVO implements Serializable {

        /**
         * 序列化版本号，用于响应对象跨网关和测试链路传递兼容。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 商户订单号，原样返回 orderInfo.orderNo。
         */
        private String orderNo;

        /**
         * 商户本次 API 请求唯一标识，原样返回 orderInfo.orderId。
         */
        private String orderId;

        /**
         * 商户上送订单金额，主币种单位。
         */
        private BigDecimal amount;

        /**
         * 商户上送订单币种，ISO 4217 三位大写币种代码。
         */
        private String currency;

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
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : TransactionInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : 交易信息响应模型，位于 商户开放接口服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class TransactionInfoVO implements Serializable {

        /**
         * 序列化版本号，用于响应对象跨网关和测试链路传递兼容。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 平台当前交易唯一标识，每一笔授权、请款、退款、撤销都不同。
         */
        private String transactionId;

        /**
         * 原平台交易唯一标识，后续动作响应中返回商户请求传入的 sourceTransactionId。
         */
        private String sourceTransactionId;

        /** 后续动作源交易发生时间；首次交易不返回。 */
        private OffsetDateTime sourceTransactionDateTime;

        /**
         * 当前动作商户响应码，例如 T200、T202、T203、F210。
         */
        private String code;

        /**
         * 当前动作商户响应描述，面向商户展示。
         */
        private String message;

        /**
         * 交易类型，对齐系统字典 transaction_type。
         */
        private String transactionType;

        /**
         * 交易状态，对齐系统字典 transaction_status。
         */
        private String transactionStatus;

        /**
         * 内部处理阶段，用于说明交易等待渠道、3DS、风控审核或已完成。
         */
        @JSONField(serialize = false)
        private String processStage;

        /**
         * 交易发生时间，按交易业务时区展示。
         */
        private OffsetDateTime transactionDateTime;

        /** 生命周期根主单时间仅供内部兼容，商户响应不输出。 */
        @JSONField(serialize = false)
        private OffsetDateTime rootTransactionDateTime;

        /**
         * 支付方式，如 BANK_CARD。
         */
        private String paymentMethod;

        /**
         * 卡品牌或支付品牌，如 MASTERCARD、VISA。
         */
        private String cardBrand;

        /**
         * 脱敏卡 BIN，格式为前六位 + **** + 后四位。
         */
        private String cardBin;

        /**
         * 授权码，渠道成功返回时填写。
         */
        private String authCode;

        /**
         * ARN 或收单机构参考号，请款或渠道返回时填写。
         */
        private String arn;

        /**
         * 订单备注或描述，商户上送后原样返回。
         */
        private String description;

        /**
         * 商户通知回调地址，商户上送或配置存在时返回。
         */
        private String callbackUrl;

        /**
         * 商户发起支付的网站原始 URL，按首次请求保存值原样返回。
         */
        private String merchantWebsite;

        /** Hosted Checkout 结果页返回地址。 */
        private String redirectUrl;

        /** Hosted Checkout 创建会话语言。 */
        private String language;

        /**
         * 内部失败原因码，仅用于后台和测试链路兼容，不返回商户。
         */
        @JSONField(serialize = false)
        private String failReasonCode;

        /**
         * 内部失败原因描述，仅用于后台和测试链路兼容，不返回商户。
         */
        @JSONField(serialize = false)
        private String failReasonMessage;

        /**
         * 挂起原因码，PENDING 状态时返回。
         */
        private String pendingReasonCode;
    }

    /** 商品或服务明细响应。 */
    @Data
    public static class GoodsInfoVO implements Serializable {
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
         * 响应中的{@code quantity}，用于管理端或商户端展示当前处理结果。
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

    /** 付款人信息响应。 */
    @Data
    public static class PayerInfoVO implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * {@code payerId}，用于定位 {@code PayerInfoVO} 关联的上游配置、渠道、账号、角色或业务记录。
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
         * 响应中的{@code ipAddress}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String ipAddress;
        /**
         * {@code sessionId}，用于定位 {@code PayerInfoVO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String sessionId;
        /**
         * 响应中的{@code browserInfo}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private java.util.Map<String, Object> browserInfo;
        /**
         * 响应中的{@code userAgent}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String userAgent;
    }

    /** 收货人信息响应。 */
    @Data
    public static class ShippingInfoVO implements Serializable {
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

    /** 允许商户接收的 3DS 安全结果，不包含 CAVV 和协议原文。 */
    @Data
    public static class ThreeDsInfoVO implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 响应中的{@code eci}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String eci;
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
        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String status;
        /**
         * {@code liabilityShifted}，用于明确 {@code ThreeDsInfoVO} 当前业务分支是否成立。
         * <p>
         * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Boolean liabilityShifted;
    }

    /** 已形成的费用明细。 */
    @Data
    public static class FeeItemVO implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 响应中的{@code categories}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String categories;
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
        /**
         * 响应中的汇率，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private BigDecimal rate;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : BillingInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : 账单信息响应模型，位于 商户开放接口服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class BillingInfoVO implements Serializable {

        /**
         * 序列化版本号，用于响应对象跨网关和测试链路传递兼容。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 商户上送或页面标签展示的原始金额，主币种单位。
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
         * 标签金额转平台交易金额使用的汇率，未换汇时为 1.00000000。
         */
        private BigDecimal transactionRate;

        /**
         * 汇率来源编码。
         */
        private String rateSource;

        /**
         * 汇率生效或报价时间，按交易业务时区展示。
         */
        private OffsetDateTime rateTime;

        /** 已形成的结算换汇汇率。 */
        private BigDecimal settlementRate;

        /**
         * 预计或最终结算金额。
         */
        private BigDecimal settlementAmount;

        /**
         * 预计或最终结算币种。
         */
        private String settlementCurrency;

        /** 已形成的结算费用金额。 */
        private BigDecimal settlementFeeAmount;

        /** 已形成的费用明细。 */
        private List<FeeItemVO> feeItems;
    }
}
