package com.scott.payment.openapi.vo.payment;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;


/**
 * 商户支付类创建响应，展示订单、交易状态、商户金额和后续动作，不暴露渠道或内部持久化事实。
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

    /**
     * 首次交易保存的商品或服务明细。
     */
    private List<GoodsInfoVO> goodsInfo;

    /**
     * 账单持卡人信息，支付接口按商户请求 billingCardHolderInfo 原样回显，不包含卡号和 CVV。
     */
    private BillingCardHolderInfoVO billingCardHolderInfo;

    /**
     * 首次交易保存的付款人信息。
     */
    private PayerInfoVO payerInfo;

    /**
     * 首次交易保存的收货人信息。
     */
    private ShippingInfoVO shippingInfo;

    /**
     * 当前动作可向商户返回的 3DS 安全字段子集。
     */
    private ThreeDsInfoVO threeDSInfo;

    /**
     * 平台交易响应信息，包含平台当前交易 ID、原交易 ID 和交易状态。
     */
    private TransactionInfoVO transactionInfo;

    /**
     * 商户账单信息，包含标签金额和已形成的结算摘要。
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
     * 当前交易归属的商户号和首次交易冻结的子商户快照。
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
     * 平台型商户首次交易冻结的实际经营主体快照。
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
     * 商户提交并由支付核心冻结的账单持卡人联系信息。
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
     * 商户订单标签金额及其累计授权、请款、退款和撤销金额。
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
         * 当前生命周期累计授权成功金额，商户标签币种单位。
         */
        private BigDecimal totalAuthorizedAmount;

        /**
         * 当前生命周期累计请款成功金额，商户标签币种单位。
         */
        private BigDecimal totalCapturedAmount;

        /**
         * 当前生命周期累计退款成功金额，商户标签币种单位。
         */
        private BigDecimal totalRefundAmount;

        /**
         * 当前生命周期累计授权取消、预授权取消或未请款金额释放成功金额，商户标签币种单位。
         */
        private BigDecimal totalAuthorizedCancelAmount;

        /**
         * 当前生命周期累计拒付成立或确认成功金额，商户标签币种单位。
         */
        private BigDecimal totalRefuseAmount;
    }

    /**
     * 当前交易动作的公开状态、平台响应码、卡信息摘要和渠道参考号。
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

        /**
         * 后续动作源交易发生时间；首次交易不返回。
         */
        private OffsetDateTime sourceTransactionDateTime;

        /**
         * 当前动作 OpenAPI V1.3 交易结果码，例如 T0000、T0001、T0002、T0003 或 F3000。
         * 外层 A0000 只表示 API 请求处理完成，不能替代本字段判断当前交易动作结果。
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

        /**
         * 生命周期根主单时间仅供内部兼容，商户响应不输出。
         */
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

        /**
         * Hosted Checkout 结果页返回地址。
         */
        private String redirectUrl;

        /**
         * Hosted Checkout 创建会话语言。
         */
        private String language;

        /**
         * OpenAPI 投影使用的受控失败原因码，仅保留 Java 兼容读取，不属于商户响应契约。
         */
        @JSONField(serialize = false)
        private String failReasonCode;

        /**
         * OpenAPI 投影使用的安全失败说明，仅保留 Java 兼容读取，不属于商户响应契约。
         */
        @JSONField(serialize = false)
        private String failReasonMessage;

        /**
         * 内部挂起原因码，仅用于选择现有 code/message，不直接返回商户。
         */
        @JSONField(serialize = false)
        private String pendingReasonCode;
    }

    /**
     * 商品或服务明细响应。
     */
    @Data
    public static class GoodsInfoVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        /**
         * 响应中的{@code quantity}，用于管理端或商户端展示当前处理结果。
         */
        private Integer quantity;

        private BigDecimal amount;

        private String currency;
    }

    /**
     * 付款人信息响应。
     */
    @Data
    public static class PayerInfoVO implements Serializable {
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
        /**
         * 响应中的{@code ipAddress}，用于管理端或商户端展示当前处理结果。
         */
        private String ipAddress;

        private String sessionId;
        /**
         * 商户上送的浏览器环境扩展对象。
         *
         * <p>公开协议允许浏览器、引擎、设备和网络信息增加子字段，因此响应层保留动态对象并原样返回；
         * 该对象不得包含卡数据或密钥，也不得以原文写入日志。</p>
         */
        private java.util.Map<String, Object> browserInfo;
        /**
         * 响应中的{@code userAgent}，用于管理端或商户端展示当前处理结果。
         */
        private String userAgent;
    }

    /**
     * 收货人信息响应。
     */
    @Data
    public static class ShippingInfoVO implements Serializable {
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
     * 允许商户接收的 3DS 安全结果，不包含 CAVV 和协议原文。
     */
    @Data
    public static class ThreeDsInfoVO implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 响应中的{@code eci}，用于管理端或商户端展示当前处理结果。
         */
        private String eci;
        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String dsTransactionId;

        private String threeDsVersion;

        private String status;

        private Boolean liabilityShifted;
    }

    /**
     * 已形成的费用明细。
     */
    @Data
    public static class FeeItemVO implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 响应中的{@code categories}，用于管理端或商户端展示当前处理结果。
         */
        private String categories;

        private BigDecimal amount;

        private String currency;
        /**
         * 响应中的汇率，用于管理端或商户端展示当前处理结果。
         */
        private BigDecimal rate;
    }

    /**
     * 商户标签金额、结算金额和费用明细。
     *
     * <p>渠道交易金额、渠道交易币种、交易汇率及汇率来源属于平台内部成本事实，
     * 不属于商户 OpenAPI 响应契约。</p>
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
         * 已形成的结算换汇汇率。
         */
        private BigDecimal settlementRate;

        /**
         * 预计或最终结算金额。
         */
        private BigDecimal settlementAmount;

        /**
         * 预计或最终结算币种。
         */
        private String settlementCurrency;

        /**
         * 已形成的结算费用金额。
         */
        private BigDecimal settlementFeeAmount;

        /**
         * 已形成的费用明细。
         */
        private List<FeeItemVO> feeItems;
    }
}
