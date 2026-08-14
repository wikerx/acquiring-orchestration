package com.scott.payment.channel.payment.worldpay;

import lombok.Data;

import java.time.LocalDate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayXmlRequestPayload
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : WorldPay XML 请求对象模型，位于 payment-channel-worldpay 渠道协议层，用于封装 WPG Direct XML 的 paymentService、submit、modify 和 inquiry 节点；输入来自平台统一渠道请求，输出交由 XML 编码器序列化，不直接执行 HTTP 调用。
 * @status : create
 */
@Data
public class WorldPayXmlRequestPayload {

    /**
     * WPG XML 接口版本。
     * <p>
     * 单位：无；格式：Worldpay DTD 版本字符串，默认 1.4；非敏感字段；不允许为空。
     * 数据来源：MID 扩展配置 mid.version 或系统默认值。
     * </p>
     */
    private String version;

    /**
     * Worldpay XML 商户编码。
     * <p>
     * 单位：无；格式：Worldpay merchantCode；非敏感但属于商户配置字段；不允许为空。
     * 数据来源：后台渠道 MID 配置，必须与 Basic Auth 凭据匹配。
     * </p>
     */
    private String merchantCode;

    /**
     * WPG submit 请求节点，支付、授权和预授权首笔交易使用。
     * <p>
     * 单位：无；格式：对象；首笔交易不允许为空；包含 order 节点和卡支付明细。
     * 数据来源：平台交易类型映射结果；与 modify、inquiry 三者在同一个请求中只应出现一种。
     * </p>
     */
    private Submit submit;

    /**
     * WPG modify 请求节点，请款、退款、撤销和冲正后续动作使用。
     * <p>
     * 单位：无；格式：对象；后续交易不允许为空；不包含 PAN/CVC。
     * 数据来源：平台后续动作请求；与 submit、inquiry 三者在同一个请求中只应出现一种。
     * </p>
     */
    private Modify modify;

    /**
     * WPG inquiry 请求节点，交易查询和勾兑使用。
     * <p>
     * 单位：无；格式：对象；查询交易不允许为空；不包含金额和敏感卡数据。
     * 数据来源：平台查询请求；与 submit、modify 三者在同一个请求中只应出现一种。
     * </p>
     */
    private Inquiry inquiry;

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Submit
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML submit 节点对象，封装首笔支付、授权和预授权订单请求；业务边界限定为渠道报文结构，不保存平台交易状态。
     * @status : create
     */
    @Data
    public static class Submit {

        /**
         * 首笔交易订单节点。
         * <p>
         * 单位：无；格式：对象；submit 请求不允许为空；封装 orderCode、金额、支付明细和消费者摘要。
         * 数据来源：WorldPayXmlRequestMapper 根据平台统一渠道请求组装。
         * </p>
         */
        private Order order;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Order
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML order 节点对象，封装渠道订单号、金额、卡支付信息、订单内容、消费者信息和账单摘要；卡号与 CVC 只在序列化前内存使用。
     * @status : create
     */
    @Data
    public static class Order {

        /**
         * Worldpay orderCode，通常使用平台渠道订单号或平台交易号。
         * <p>
         * 单位：无；格式：Worldpay 允许的订单编号文本；不允许为空；非敏感字段。
         * 数据来源：channelOrderNo 或 transactionId；用于后续请款、退款、撤销、查询和回调匹配。
         * </p>
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

        /**
         * 订单金额，value 为最小辅币单位。
         * <p>
         * 单位：由 amount.currencyCode 与 amount.exponent 决定；格式：对象；金额类首笔交易不允许为空；非敏感字段。
         * 数据来源：平台交易金额转换结果；必须与 transaction currency 保持一致。
         * </p>
         */
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
         * 支付明细节点，当前支持 CARD-SSL。
         * <p>
         * 单位：无；格式：对象；首笔卡交易不允许为空；包含 PAN、CVC、有效期、会话和 3DS 信息。
         * 数据来源：商户请求解密后的内存卡信息；高敏字段禁止日志明文输出。
         * </p>
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
     * @author : scott
     * @version : v1.0.0
     * @classname : PaymentDetails
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML paymentDetails 节点对象，封装卡支付明细、会话信息和 3DS 认证数据；PAN、CVC、CAVV 均为敏感字段，禁止日志明文输出。
     * @status : create
     */
    @Data
    public static class PaymentDetails {

        /**
         * CARD-SSL 明文卡支付节点。
         * <p>
         * 单位：无；格式：对象；首笔卡交易不允许为空；包含 PAN、有效期、持卡人姓名、CVC 和账单地址。
         * 数据来源：平台统一渠道请求；节点内高敏字段只允许当前渠道请求内短暂使用。
         * </p>
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
     * @author : scott
     * @version : v1.0.0
     * @classname : CardSsl
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML CARD-SSL 节点对象，封装明文银行卡、有效期、持卡人姓名、CVC 和账单地址；仅用于当前渠道请求内短暂序列化。
     * @status : create
     */
    @Data
    public static class CardSsl {

        /**
         * 完整 PAN 卡号。
         * <p>
         * 高敏感字段；格式：银行卡号数字串；不允许为空；数据来源：商户 OpenAPI 明文解密后的内存请求。
         * 禁止入库、禁止日志明文、禁止异常消息输出。
         * </p>
         */
        private String cardNumber;

        /**
         * 卡有效期。
         * <p>
         * 单位：无；格式：month 两位、year 四位；不允许为空；非敏感认证辅助字段。
         * 数据来源：商户交易请求；与 cardNumber 共同描述支付卡。
         * </p>
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

        /**
         * CVC/CVV 安全码。
         * <p>
         * 高敏感认证数据；格式：3 到 4 位数字；不允许为空；数据来源：商户交易请求。
         * 禁止入库、禁止日志明文、禁止写入异常消息。
         * </p>
         */
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
     * @author : scott
     * @version : v1.0.0
     * @classname : ExpiryDate
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML expiryDate 节点对象，封装银行卡有效期月份和年份；月份为两位，年份为四位。
     * @status : create
     */
    @Data
    public static class ExpiryDate {

        /**
         * 卡有效期月份。
         * <p>
         * 单位：月；格式：两位数字 01 至 12；不允许为空；非敏感字段。
         * 数据来源：商户交易请求 expirationMonth；与 year 共同组成卡有效期。
         * </p>
         */
        private String month;

        /**
         * 卡有效期年份。
         * <p>
         * 单位：年；格式：四位数字；不允许为空；非敏感字段。
         * 数据来源：商户交易请求 expirationYear；两位年份会归一为 20xx。
         * </p>
         */
        private String year;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : CardAddress
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML cardAddress 节点对象，封装持卡人账单地址；地址字段可能包含个人信息，日志中只允许输出脱敏摘要。
     * @status : create
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
     * @author : scott
     * @version : v1.0.0
     * @classname : Address
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML address 节点对象，封装街道、邮编、城市、州省和国家代码；输入来自账单地址，不参与平台状态机。
     * @status : create
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
     * @author : scott
     * @version : v1.0.0
     * @classname : Session
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML session 节点对象，封装消费者浏览器会话 ID 和 IP 地址，用于渠道风控和交易关联。
     * @status : create
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
     * @author : scott
     * @version : v1.0.0
     * @classname : Info3DSecure
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML info3DSecure 节点对象，封装 3DS 版本、DS 交易号、CAVV 和 ECI；CAVV 属于敏感认证数据，禁止明文日志输出。
     * @status : create
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
     * @author : scott
     * @version : v1.0.0
     * @classname : Shopper
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML shopper 节点对象，封装消费者邮箱、商户侧 shopperId 和浏览器头摘要；邮箱属于个人信息，日志必须脱敏或截断。
     * @status : create
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
     * @author : scott
     * @version : v1.0.0
     * @classname : Browser
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML browser 节点对象，封装 acceptHeader 和 userAgentHeader，用于渠道侧浏览器环境识别。
     * @status : create
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
     * @author : scott
     * @version : v1.0.0
     * @classname : Modify
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML modify 节点对象，封装请款、退款、撤销和冲正等后续交易动作；请求以原 Worldpay orderCode 定位原交易。
     * @status : create
     */
    @Data
    public static class Modify {

        /**
         * 订单修改节点。
         * <p>
         * 单位：无；格式：对象；modify 请求不允许为空；包含原 orderCode 和单一后续动作。
         * 数据来源：平台请款、退款、撤销或冲正请求。
         * </p>
         */
        private OrderModification orderModification;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : OrderModification
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML orderModification 节点对象，封装原订单号和具体修改动作；同一对象只允许设置 capture、refund 或 cancel 之一。
     * @status : create
     */
    @Data
    public static class OrderModification {

        /**
         * 原 Worldpay orderCode。
         * <p>
         * 单位：无；格式：Worldpay 原订单号；后续动作不允许为空；非敏感字段。
         * 数据来源：sourceOrderCode、worldpayOrderCode、channelOrderNo 或原交易号；用于定位首笔交易。
         * </p>
         */
        private String orderCode;

        /**
         * 请款动作节点。
         * <p>
         * 单位：无；格式：对象；请款或预授权完成时不允许为空；非敏感字段。
         * 数据来源：平台请款动作；与 refund、cancel 互斥。
         * </p>
         */
        private Capture capture;

        /**
         * 退款动作节点。
         * <p>
         * 单位：无；格式：对象；退款时不允许为空；非敏感字段。
         * 数据来源：平台退款动作；与 capture、cancel 互斥。
         * </p>
         */
        private Refund refund;

        /**
         * 撤销动作节点。
         * <p>
         * 单位：无；格式：空节点；撤销或冲正时不允许为空；非敏感字段。
         * 数据来源：平台撤销/冲正动作；与 capture、refund 互斥。
         * </p>
         */
        private Cancel cancel;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Capture
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML capture 节点对象，封装请款日期和请款金额；金额 value 为最小辅币单位，currencyCode 和 exponent 必须同时存在。
     * @status : create
     */
    @Data
    public static class Capture {

        /**
         * 请款日期。
         * <p>
         * 单位：日历日期；格式：dayOfMonth、month、year 三属性；不允许为空；非敏感字段。
         * 数据来源：平台当前业务日期；与 amount 共同构成请款请求。
         * </p>
         */
        private DateValue date;

        /**
         * 请款金额。
         * <p>
         * 单位：最小辅币单位；格式：amount 对象；不允许为空；非敏感字段。
         * 数据来源：平台请款金额和币种辅币位；不能超过授权可请款余额。
         * </p>
         */
        private Amount amount;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Refund
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML refund 节点对象，封装退款引用号和退款金额；退款金额使用 debitCreditIndicator=credit。
     * @status : create
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

        /**
         * 退款金额。
         * <p>
         * 单位：最小辅币单位；格式：amount 对象并携带 debitCreditIndicator=credit；不允许为空；非敏感字段。
         * 数据来源：平台退款金额和币种辅币位；不能超过原交易可退余额。
         * </p>
         */
        private Amount amount;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Cancel
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML cancel 节点对象，表示撤销或冲正原 Worldpay 订单；该节点无金额入参。
     * @status : create
     */
    @Data
    public static class Cancel {
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Inquiry
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML inquiry 节点对象，封装订单状态查询请求；输出 orderInquiry 节点。
     * @status : create
     */
    @Data
    public static class Inquiry {

        /**
         * 订单查询节点。
         * <p>
         * 单位：无；格式：对象；查询请求不允许为空；不包含敏感字段。
         * 数据来源：平台查询动作；用于按原 Worldpay orderCode 查询订单状态。
         * </p>
         */
        private OrderInquiry orderInquiry;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : OrderInquiry
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML orderInquiry 节点对象，封装待查询的 Worldpay orderCode；不包含敏感数据。
     * @status : create
     */
    @Data
    public static class OrderInquiry {

        /**
         * 原 Worldpay orderCode。
         * <p>
         * 单位：无；格式：Worldpay 原订单号；查询请求不允许为空；非敏感字段。
         * 数据来源：sourceOrderCode、worldpayOrderCode、channelOrderNo 或原交易号。
         * </p>
         */
        private String orderCode;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Amount
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML amount 节点对象，封装最小辅币单位金额、ISO 币种和辅币位；金额可记录但必须同时记录 currencyCode。
     * @status : create
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

        /**
         * ISO 4217 三位币种代码。
         * <p>
         * 单位：无；格式：三位大写 ISO 4217；金额类交易不允许为空；非敏感字段。
         * 数据来源：平台交易币种；决定 value 的主币种还原方式。
         * </p>
         */
        private String currencyCode;

        /**
         * 币种辅币位。
         * <p>
         * 单位：位；格式：0 至 9 的整数；金额类交易不允许为空；非敏感字段。
         * 数据来源：currencyExponent 扩展或币种解析器；与 value、currencyCode 共同解释金额。
         * </p>
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
     * @author : scott
     * @version : v1.0.0
     * @classname : DateValue
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML date 节点对象，封装日、月、年三个属性；用于请款日期。
     * @status : create
     */
    @Data
    public static class DateValue {

        /**
         * 月内日期。
         * <p>
         * 单位：日；格式：1 至 31 整数；不允许为空；非敏感字段。
         * 数据来源：LocalDate；与 month、year 共同组成请款日期。
         * </p>
         */
        private int dayOfMonth;

        /**
         * 月份。
         * <p>
         * 单位：月；格式：1 至 12 整数；不允许为空；非敏感字段。
         * 数据来源：LocalDate；与 dayOfMonth、year 共同组成请款日期。
         * </p>
         */
        private int month;

        /**
         * 年份。
         * <p>
         * 单位：年；格式：四位年份；不允许为空；非敏感字段。
         * 数据来源：LocalDate；与 dayOfMonth、month 共同组成请款日期。
         * </p>
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
