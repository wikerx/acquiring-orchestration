package com.scott.payment.openapi.vo.checkout;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HostedCheckoutSessionVO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 付款人浏览器查询收银台展示响应。
 * @status : create
 */
@Data
public class HostedCheckoutSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Hosted Checkout 会话号，不包含访问令牌。 */
    private String checkoutSessionId;

    /** 付款页状态，用于前端选择展示、过期或结果页面。 */
    private String pageState;

    /** 付款页可公开展示的商户资料。 */
    private MerchantVO merchant;

    /** 付款页订单金额与商品快照。 */
    private OrderVO order;

    /** 当前会话允许选择的支付方式。 */
    private List<PaymentMethodVO> paymentMethods;

    /** 会话有效期、重试次数和轮询配置。 */
    private CheckoutVO checkout;

    /** 商户上送的付款人预填信息。 */
    private PayerInfoVO payerInfo;

    /** 商户上送的账单预填信息。 */
    private BillingInfoVO billingInfo;

    /** 最近一次支付尝试结果。 */
    private HostedCheckoutPaymentResultVO paymentResult;

    /** 卡数据加密公钥元数据和一次性 nonce，仅可支付状态返回。 */
    private CardEncryptionVO cardEncryption;

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CardEncryptionVO
     * @date : 2026-09-02 08:03
     * @email : scott_x@163.com
     * @description : 卡encryption响应模型，位于 商户开放接口服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class CardEncryptionVO implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 卡数据混合加密协议标识，调用双方必须使用完全一致的算法组合。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String algorithm;
        /**
         * 密钥ID，用于定位 {@code CardEncryptionVO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String keyId;
        /**
         * 响应中的{@code publicKey}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；敏感安全字段，日志只允许记录长度、摘要或掩码。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String publicKey;
        /**
         * 响应中的随机数，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；敏感安全字段，日志只允许记录长度、摘要或掩码。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String nonce;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PayerInfoVO
     * @date : 2026-09-02 08:03
     * @email : scott_x@163.com
     * @description : payer信息响应模型，位于 商户开放接口服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
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
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String email;
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
     * @classname : BillingInfoVO
     * @date : 2026-09-02 08:03
     * @email : scott_x@163.com
     * @description : 账单信息响应模型，位于 商户开放接口服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     * @status : create
     */
    @Data
    public static class BillingInfoVO implements Serializable {
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
         * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：邮箱地址或邮箱地址集合；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String email;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         * <p>
         * 单位：无；格式：电话号码字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：长度和格式由接口校验约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String phone;
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
     * 付款页公开展示的商户资料。
     */
    @Data
    public static class MerchantVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商户展示名称，不包含商户密钥或内部状态。 */
        private String displayName;

        /** 商户 Logo 的受控公开地址。 */
        private String logoUrl;
    }

    /**
     * 付款页订单展示快照。
     */
    @Data
    public static class OrderVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商户订单号。 */
        private String orderNo;

        /** 订单主题。 */
        private String subject;

        /** 订单说明。 */
        private String description;

        /** 订单金额，单位为 {@link #currency} 主币种单位。 */
        private BigDecimal amount;

        /** ISO 4217 三位币种代码。 */
        private String currency;

        /** 币种小数位数，用于前端格式化，不用于金额重新计算。 */
        private Integer currencyExponent;

        /** 商品明细 JSON 快照，不包含卡号、CVV 或密钥。 */
        private String itemsJson;
    }

    /**
     * 付款页可选支付方式。
     */
    @Data
    public static class PaymentMethodVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 平台支付方式编码。 */
        private String paymentMethod;

        /** 路由已限定的渠道编码；允许为空。 */
        private String channelCode;

        /** 可选卡品牌或支付品牌。 */
        private List<String> brands;

        /** 当前支付方式的 3DS 执行模式。 */
        private String threeDsMode;
    }

    /**
     * 付款页会话行为配置。
     */
    @Data
    public static class CheckoutVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 带时区偏移的会话过期时间。 */
        private OffsetDateTime expireTime;

        /** 带时区偏移的支付服务响应时间，用于校准浏览器倒计时。 */
        private OffsetDateTime serverTime;

        /** 当前会话是否允许支付失败后重试。 */
        private Boolean retryAllowed;

        /** 状态轮询建议间隔，单位秒。 */
        private Integer pollingIntervalSeconds;
    }
}
