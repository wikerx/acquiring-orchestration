package com.scott.payment.openapi.vo.payment;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateVO
 * @date : 2026-05-28 10:23
 * @email : scott_x@163.com
 * @description : Payment Create VO 传输模型，位于 商户开放接口服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
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

    /**
     * 账单持卡人信息，支付接口按商户请求 billingCardHolderInfo 原样回显，不包含卡号和 CVV。
     */
    private BillingCardHolderInfoVO billingCardHolderInfo;

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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : MerchantInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : Merchant Info VO 传输模型，位于 商户开放接口服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : SubMerchantInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : Sub Merchant Info VO 传输模型，位于 商户开放接口服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : BillingCardHolderInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : Billing Card Holder Info VO 传输模型，位于 商户开放接口服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : OrderInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : Order Info VO 传输模型，位于 商户开放接口服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : TransactionInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : Transaction Info VO 传输模型，位于 商户开放接口服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
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
        @JSONField(serialize = false)
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

        /** 生命周期根主单时间，必须与后续动作请求一并回传。 */
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

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : BillingInfoVO
     * @date : 2026-05-28 10:23
     * @email : scott_x@163.com
     * @description : Billing Info VO 传输模型，位于 商户开放接口服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
     * @status : create
     */
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

        /**
         * 预计或最终结算金额。
         */
        private BigDecimal settlementAmount;

        /**
         * 预计或最终结算币种。
         */
        private String settlementCurrency;
    }
}
