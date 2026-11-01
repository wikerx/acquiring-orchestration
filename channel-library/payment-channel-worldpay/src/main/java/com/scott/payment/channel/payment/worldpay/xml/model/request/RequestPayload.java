package com.scott.payment.channel.payment.worldpay.xml.model.request;

import lombok.Data;

import java.time.LocalDate;

/**
 * WorldPay XML 请求对象模型，位于 payment-channel-worldpay 渠道协议层，用于封装 WPG Direct XML 的 paymentService、submit、modify 和 inquiry 节点；输入来自平台统一渠道请求，输出交由 XML 编码器序列化，不直接执行 HTTP 调用。
 */
@Data
public class RequestPayload {

    /**
     * WPG XML paymentService 协议版本。
     */
    private String version;

    /**
     * 后台 MID 配置中的 Worldpay merchantCode。
     */
    private String merchantCode;

    /**
     * 首笔支付、授权或预授权的 submit 节点。
     */
    private Submit submit;

    /**
     * 请款、退款或撤销的 modify 节点。
     */
    private Modify modify;

    /**
     * 订单状态查询的 inquiry 节点。
     */
    private Inquiry inquiry;

    /**
     * WPG XML submit 节点对象，封装首笔支付、授权和预授权订单请求；业务边界限定为渠道报文结构，不保存平台交易状态。
     */
    @Data
    public static class Submit {

        /**
         * 待提交的 Worldpay 订单。
         */
        private Order order;
    }

    /**
     * WPG XML order 节点对象，封装渠道订单号、金额、卡支付信息、订单内容、消费者信息和账单摘要；卡号与 CVC 只在序列化前内存使用。
     */
    @Data
    public static class Order {

        /**
         * 平台系统订单号，用作 Worldpay orderCode。
         */
        private String orderCode;

        /**
         * 自动请款延迟天数。
         * <p>
         * 单位：天；格式：整数文本；非敏感字段；一步支付默认 0，授权类交易为空。
         * 数据来源：MID 配置 mid.captureDelay、渠道请求扩展 captureDelay 或系统默认值。
         * </p>
         */
        private String captureDelay;

        /**
         * 订单描述。
         * <p>
         * 单位：无；格式：Worldpay 允许的短文本；允许为空；非敏感字段。
         * 数据来源：MID 描述、请求扩展、merchantOrderNo 或 transactionId；不得包含完整卡号、CVC、密钥或个人敏感信息。
         * </p>
         */
        private String description;


        private Amount amount;

        /**
         * 订单内容，允许使用 CDATA 输出。
         * <p>
         * 单位：无；格式：文本；允许为空；非敏感字段。
         * 数据来源：request.extension.orderContent 或 merchantOrderNo；用于渠道后台展示和人工排查。
         * </p>
         */
        private String orderContent;

        /**
         * 卡、会话和 3DS 认证明细。
         */
        private PaymentDetails paymentDetails;

        /**
         * 消费者摘要节点。
         * <p>
         * 单位：无；格式：对象；允许为空；可能包含邮箱、shopperId 和浏览器头。
         * 数据来源：账单信息和请求扩展；邮箱和浏览器头日志中必须脱敏或摘要化。
         * </p>
         */
        private Shopper shopper;

        /**
         * 账单叙述字段。
         * <p>
         * 单位：无；格式：Worldpay 允许的账单文本；允许为空；非敏感字段。
         * 数据来源：MID 元数据 statementNarrative/narrative 或请求扩展；不应包含卡号、密钥、邮箱或电话。
         * </p>
         */
        private String statementNarrative;
    }

    /**
     * WPG XML paymentDetails 节点对象，封装卡支付明细、会话信息和 3DS 认证数据；PAN、CVC、CAVV 均为敏感字段，禁止日志明文输出。
     */
    @Data
    public static class PaymentDetails {

        /**
         * WPG XML CARD-SSL 卡支付节点。
         */
        private CardSsl cardSsl;

        /**
         * 浏览器会话标识和消费者 IP。
         * <p>
         * 单位：无；格式：对象；允许为空；非敏感但涉及消费者网络环境。
         * 数据来源：sessionId、shopperIp 或 clientIp 扩展；与 shopper/browser 共同用于渠道风控。
         * </p>
         */
        private Session session;

        /**
         * 3DS 认证结果节点。
         * <p>
         * 单位：无；格式：对象；允许为空；CAVV 属于敏感认证数据。
         * 数据来源：threeDsInfo；与卡交易共同提交以支持责任转移和渠道风控。
         * </p>
         */
        private Info3DSecure info3DSecure;
    }

    /**
     * WPG XML CARD-SSL 节点对象，封装明文银行卡、有效期、持卡人姓名、CVC 和账单地址；仅用于当前渠道请求内短暂序列化。
     */
    @Data
    public static class CardSsl {

        /**
         * 卡编号，表示银行卡号或脱敏卡号字段。
         */
        private String cardNumber;

        /**
         * 卡有效期，包含月份和四位年份。
         */
        private ExpiryDate expiryDate;

        /**
         * 持卡人姓名。
         * <p>
         * 单位：无；格式：姓名文本；允许为空；个人信息字段。
         * 数据来源：request.extension.cardHolderName 或 billingInfo 姓名拼接；日志只能输出脱敏摘要。
         * </p>
         */
        private String cardHolderName;


        private String cvc;

        /**
         * 持卡人账单地址。
         * <p>
         * 单位：无；格式：对象；允许为空；个人信息字段。
         * 数据来源：billingInfo；与 cardHolderName 一起用于 AVS、风控和渠道排查。
         * </p>
         */
        private CardAddress cardAddress;
    }

    /**
     * WPG XML expiryDate 节点对象，封装银行卡有效期月份和年份；月份为两位，年份为四位。
     */
    @Data
    public static class ExpiryDate {

        /**
         * 两位有效期月份。
         */
        private String month;

        /**
         * 四位有效期年份。
         */
        private String year;
    }

    /**
     * WPG XML cardAddress 节点对象，封装持卡人账单地址；地址字段可能包含个人信息，日志中只允许输出脱敏摘要。
     */
    @Data
    public static class CardAddress {

        /**
         * 地址明细。
         * <p>
         * 单位：无；格式：对象；允许为空；个人信息字段。
         * 数据来源：billingInfo；包含街道、邮编、城市、州省和国家地区代码。
         * </p>
         */
        private Address address;
    }

    /**
     * WPG XML address 节点对象，封装街道、邮编、城市、州省和国家代码；输入来自账单地址，不参与平台状态机。
     */
    @Data
    public static class Address {

        /**
         * 街道地址第一行。
         * <p>
         * 单位：无；格式：地址文本；允许为空；个人信息字段。
         * 数据来源：billingInfo.street；用于渠道 AVS 和风控。
         * </p>
         */
        private String address1;

        /**
         * 邮政编码。
         * <p>
         * 单位：无；格式：国家地区相关邮编格式；允许为空；个人信息字段。
         * 数据来源：billingInfo.postal；与 countryCode 共同解释地址区域。
         * </p>
         */
        private String postalCode;

        /**
         * 城市。
         * <p>
         * 单位：无；格式：城市名称；允许为空；个人信息字段。
         * 数据来源：billingInfo.city；用于渠道 AVS 和风控。
         * </p>
         */
        private String city;

        /**
         * 州、省或地区。
         * <p>
         * 单位：无；格式：地区名称或缩写；允许为空；属于账单地址信息。
         * 数据来源：billingInfo.state；与 countryCode 共同描述地址。
         * </p>
         */
        private String state;

        /**
         * 国家或地区代码。
         * <p>
         * 单位：无；格式：ISO 3166-1 alpha-2；允许为空；非敏感字段。
         * 数据来源：billingInfo.country 归一化结果；影响 Worldpay AVS、风控和合规判断。
         * </p>
         */
        private String countryCode;
    }

    /**
     * WPG XML session 节点对象，封装消费者浏览器会话 ID 和 IP 地址，用于渠道风控和交易关联。
     */
    @Data
    public static class Session {

        /**
         * 消费者 IP 地址。
         * <p>
         * 单位：无；格式：IPv4 或 IPv6 文本；允许为空；个人网络标识，日志需摘要化。
         * 数据来源：shopperIp 或 clientIp 扩展；用于渠道风控和争议排查。
         * </p>
         */
        private String shopperIPAddress;

        /**
         * 商户侧或平台侧会话 ID。
         * <p>
         * 单位：无；格式：会话标识文本；允许为空；非敏感但不应承载密钥或完整身份凭据。
         * 数据来源：sessionId 扩展；与 shopperIPAddress 共同标识一次消费者浏览会话。
         * </p>
         */
        private String id;
    }

    /**
     * WPG XML info3DSecure 节点对象，封装 3DS 版本、DS 交易号、CAVV 和 ECI；CAVV 属于敏感认证数据，禁止明文日志输出。
     */
    @Data
    public static class Info3DSecure {

        /**
         * 3DS 协议版本。
         * <p>
         * 单位：无；格式：例如 2.1.0、2.2.0；允许为空；非敏感字段。
         * 数据来源：threeDsInfo.threeDsVersion；与 dsTransactionId、cavv、eci 共同描述认证结果。
         * </p>
         */
        private String threeDSVersion;

        /**
         * Directory Server 交易号。
         * <p>
         * 单位：无；格式：UUID 或渠道返回标识；允许为空；非敏感但用于认证链路排查。
         * 数据来源：threeDsInfo.dsTransactionId；与 CAVV/ECI 共同关联一次 3DS 认证。
         * </p>
         */
        private String dsTransactionId;

        /**
         * 3DS 认证值。
         * <p>
         * 单位：无；格式：Base64 或渠道认证值；允许为空；敏感认证数据。
         * 数据来源：threeDsInfo.cavv；禁止日志、落库、异常消息和 MQ 明文输出。
         * </p>
         */
        private String cavv;

        /**
         * 电子商务交易指示值。
         * <p>
         * 单位：无；格式：渠道/卡组织定义的 ECI 代码；允许为空；非敏感字段。
         * 数据来源：threeDsInfo.eci；与 CAVV 共同影响授权责任转移。
         * </p>
         */
        private String eci;
    }

    /**
     * WPG XML shopper 节点对象，封装消费者邮箱、商户侧 shopperId 和浏览器头摘要；邮箱属于个人信息，日志必须脱敏或截断。
     */
    @Data
    public static class Shopper {

        /**
         * 消费者邮箱。
         * <p>
         * 单位：无；格式：邮箱地址；允许为空；个人信息字段。
         * 数据来源：billingInfo.email；日志和审计字段只能输出脱敏值。
         * </p>
         */
        private String shopperEmailAddress;

        /**
         * 商户侧消费者标识。
         * <p>
         * 单位：无；格式：商户 shopperId 或平台商户号；允许为空；非敏感字段。
         * 数据来源：request.extension.shopperId 或 merchantId；用于渠道消费者关联。
         * </p>
         */
        private String authenticatedShopperID;

        /**
         * 浏览器头摘要。
         * <p>
         * 单位：无；格式：对象；允许为空；可能包含 userAgent 等设备环境信息。
         * 数据来源：acceptHeader 和 userAgent 扩展；用于渠道风控和认证环境识别。
         * </p>
         */
        private Browser browser;
    }

    /**
     * WPG XML browser 节点对象，封装 acceptHeader 和 userAgentHeader，用于渠道侧浏览器环境识别。
     */
    @Data
    public static class Browser {

        /**
         * HTTP Accept 头摘要。
         * <p>
         * 单位：无；格式：HTTP Accept 头文本；允许为空；非敏感但可能较长。
         * 数据来源：request.extension.acceptHeader；与 userAgentHeader 共同描述浏览器环境。
         * </p>
         */
        private String acceptHeader;

        /**
         * HTTP User-Agent 头摘要。
         * <p>
         * 单位：无；格式：浏览器 User-Agent 文本；允许为空；设备环境信息。
         * 数据来源：request.extension.userAgent；日志中应截断或摘要化。
         * </p>
         */
        private String userAgentHeader;
    }

    /**
     * WPG XML modify 节点对象，封装请款、退款、撤销和冲正等后续交易动作；请求以原 Worldpay orderCode 定位原交易。
     */
    @Data
    public static class Modify {

        /**
         * 原订单及唯一后续动作节点。
         */
        private OrderModification orderModification;
    }

    /**
     * WPG XML orderModification 节点对象，封装原订单号和具体修改动作；同一对象只允许设置 capture、refund 或 cancel 之一。
     */
    @Data
    public static class OrderModification {

        /**
         * 原 Worldpay 交易的 orderCode。
         */
        private String orderCode;

        /**
         * 请款动作；与 refund、cancel 互斥。
         */
        private Capture capture;

        /**
         * 退款动作；与 capture、cancel 互斥。
         */
        private Refund refund;

        /**
         * 撤销动作；与 capture、refund 互斥。
         */
        private Cancel cancel;
    }

    /**
     * WPG XML capture 节点对象，封装请款日期和请款金额；金额 value 为最小辅币单位，currencyCode 和 exponent 必须同时存在。
     */
    @Data
    public static class Capture {

        /**
         * Worldpay 请款业务日期。
         */
        private DateValue date;


        private Amount amount;
    }

    /**
     * WPG XML refund 节点对象，封装退款引用号和退款金额；退款金额使用 debitCreditIndicator=credit。
     */
    @Data
    public static class Refund {

        /**
         * 退款引用号，用于渠道侧退款识别。
         * <p>
         * 单位：无；格式：渠道引用文本；允许为空；非敏感字段。
         * 数据来源：refundReference 扩展或原渠道交易号；用于渠道后台检索退款。
         * </p>
         */
        private String reference;


        private Amount amount;
    }

    /**
     * WPG XML cancel 节点对象，表示撤销或冲正原 Worldpay 订单；该节点无金额入参。
     */
    @Data
    public static class Cancel {
    }

    /**
     * WPG XML inquiry 节点对象，封装订单状态查询请求；输出 orderInquiry 节点。
     */
    @Data
    public static class Inquiry {

        /**
         * 按原订单号查询的 orderInquiry 节点。
         */
        private OrderInquiry orderInquiry;
    }

    /**
     * WPG XML orderInquiry 节点对象，封装待查询的 Worldpay orderCode；不包含敏感数据。
     */
    @Data
    public static class OrderInquiry {

        /**
         * 待查询的原 Worldpay orderCode。
         */
        private String orderCode;
    }

    /**
     * WPG XML amount 节点对象，封装最小辅币单位金额、ISO 币种和辅币位；金额可记录但必须同时记录 currencyCode。
     */
    @Data
    public static class Amount {

        /**
         * 最小辅币单位金额。
         * <p>
         * 单位：currencyCode 对应的最小辅币单位；格式：长整型；金额类交易必须大于 0；非敏感字段。
         * 数据来源：平台 BigDecimal 金额按 exponent 无舍入转换；必须与 currencyCode、exponent 同时解释。
         * </p>
         */
        private long value;


        private String currencyCode;

        /**
         * ISO 币种的辅币位数。
         */
        private int exponent;

        /**
         * 借贷方向，退款使用 credit。
         * <p>
         * 单位：无；格式：Worldpay 定义的方向文本；退款为 credit，非退款允许为空；非敏感字段。
         * 数据来源：交易类型；用于表达退款金额方向。
         * </p>
         */
        private String debitCreditIndicator;
    }

    /**
     * WPG XML date 节点对象，封装日、月、年三个属性；用于请款日期。
     */
    @Data
    public static class DateValue {

        /**
         * 月内日期，范围为 1 至 31。
         */
        private int dayOfMonth;

        /**
         * 月份，范围为 1 至 12。
         */
        private int month;

        /**
         * 四位业务年份。
         */
        private int year;

        /**
         * 根据本地日期构造 WPGXML 日期对象。
         *
         * @param date 当前业务日期
         * @return WPGXML 日期对象
         */
        public static DateValue from(LocalDate date) {
            DateValue value = new DateValue();
            value.setDayOfMonth(date.getDayOfMonth());
            value.setMonth(date.getMonthValue());
            value.setYear(date.getYear());
            return value;
        }
    }
}
