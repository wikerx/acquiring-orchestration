package com.scott.payment.channel.payment.worldpay;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayXmlResponsePayload
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : WorldPay XML 响应对象模型，位于 payment-channel-worldpay 渠道协议层，用于承载 WPGXML orderStatus、payment、journal、ok 和 error 节点解析结果；输出给响应 mapper 映射为平台统一渠道响应。
 * @status : create
 */
@Data
public class WorldPayXmlResponsePayload {

    /**
     * 响应中的 Worldpay 商户编码。
     * <p>
     * 单位：无；格式：Worldpay merchantCode；允许为空；非敏感字段但日志只输出摘要。
     * 数据来源：paymentService 响应根节点；用于确认响应归属的 MID。
     * </p>
     */
    private String merchantCode;

    /**
     * 响应中的 WPG XML 版本。
     * <p>
     * 单位：无；格式：Worldpay DTD 版本字符串；允许为空；非敏感字段。
     * 数据来源：paymentService 响应根节点；用于协议排查。
     * </p>
     */
    private String version;

    /**
     * 订单状态节点。
     * <p>
     * 单位：无；格式：对象；查询和首笔交易响应通常存在；不包含敏感卡数据。
     * 数据来源：orderStatus XML 节点；用于映射渠道交易状态和响应扩展字段。
     * </p>
     */
    private OrderStatus orderStatus;

    /**
     * 修改类请求接收成功节点。
     * <p>
     * 单位：无；格式：对象；请款、退款、撤销等请求被接收时可能存在；非敏感字段。
     * 数据来源：ok XML 节点；用于把修改类交易映射为处理中或待回调状态。
     * </p>
     */
    private Ok ok;

    /**
     * 渠道错误节点。
     * <p>
     * 单位：无；格式：对象；渠道拒绝或协议错误时可能存在；不应包含请求敏感字段。
     * 数据来源：error XML 节点；用于平台失败码映射和人工排查。
     * </p>
     */
    private Error error;

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : OrderStatus
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML orderStatus 响应节点对象，封装渠道订单号、支付状态、流水日志和金额信息。
     * @status : create
     */
    @Data
    public static class OrderStatus {

        /**
         * Worldpay orderCode。
         * <p>
         * 单位：无；格式：渠道订单号；允许为空；非敏感字段。
         * 数据来源：orderStatus.orderCode；与平台 channelOrderNo 关联同一笔订单。
         * </p>
         */
        private String orderCode;

        /**
         * payment 响应节点。
         * <p>
         * 单位：无；格式：对象；首笔支付或授权响应通常存在；不包含完整卡号或 CVC。
         * 数据来源：payment XML 节点；用于提取 lastEvent、授权码和收单响应。
         * </p>
         */
        private Payment payment;

        /**
         * journal 响应节点。
         * <p>
         * 单位：无；格式：对象；修改类交易或订单事件可能存在；非敏感字段。
         * 数据来源：journal XML 节点；用于请款、退款、撤销事件映射。
         * </p>
         */
        private Journal journal;

        /**
         * amount 响应节点。
         * <p>
         * 单位：最小辅币单位；格式：对象；允许为空；非敏感字段。
         * 数据来源：amount XML 节点；与 payment/journal 共同用于对账和金额排查。
         * </p>
         */
        private Amount amount;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Payment
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML payment 响应节点对象，封装渠道交易号、原始事件、响应码、授权码、检索号和收单参考；不承载平台终态决策。
     * @status : create
     */
    @Data
    public static class Payment {

        /**
         * Worldpay payment id 或交易号。
         * <p>
         * 单位：无；格式：Worldpay 交易标识；允许为空；非敏感字段。
         * 数据来源：payment.id；用于后续动作、查询和渠道后台检索。
         * </p>
         */
        private String id;

        /**
         * Worldpay lastEvent 原始状态。
         * <p>
         * 单位：无；格式：AUTHORISED、CAPTURED、REFUSED 等渠道状态；允许为空；非敏感字段。
         * 数据来源：payment.lastEvent；由 response mapper 转为 rawChannelStatus 后交给 service-payment 状态解析器。
         * </p>
         */
        private String lastEvent;

        /**
         * 渠道响应码或 ISO8583 响应码。
         * <p>
         * 单位：无；格式：渠道/ISO 响应码；允许为空；非敏感字段。
         * 数据来源：payment.responseCode 或 ISO8583ReturnCode.code；用于失败码映射和排查。
         * </p>
         */
        private String responseCode;

        /**
         * 渠道响应描述。
         * <p>
         * 单位：无；格式：渠道返回文本；允许为空；非敏感字段但不得包含请求敏感信息。
         * 数据来源：payment.message、ISO8583ReturnCode.description 或 refusalReason；用于后台排查。
         * </p>
         */
        private String message;

        /**
         * 授权码。
         * <p>
         * 单位：无；格式：收单/发卡机构授权码；成功授权时可能存在；非敏感字段。
         * 数据来源：payment.authorisationCode 或 AuthorisationId；用于对账、请款和争议处理。
         * </p>
         */
        private String authorisationCode;

        /**
         * 检索参考或渠道引用。
         * <p>
         * 单位：无；格式：渠道 reference/RRN 文本；允许为空；非敏感字段。
         * 数据来源：payment.reference；用于渠道后台检索和商户排查。
         * </p>
         */
        private String reference;

        /**
         * 收单参考号。
         * <p>
         * 单位：无；格式：收单机构参考文本；允许为空；非敏感字段。
         * 数据来源：payment.acquirerReference；用于对账、拒付和收单侧问题追踪。
         * </p>
         */
        private String acquirerReference;

        /**
         * 收单响应码。
         * <p>
         * 单位：无；格式：收单/ISO 响应码；允许为空；非敏感字段。
         * 数据来源：payment.acquirerCode 或 ISO8583ReturnCode；与 responseCode 一起解释失败原因。
         * </p>
         */
        private String acquirerCode;

        /**
         * System Trace Audit Number。
         * <p>
         * 单位：无；格式：数字或渠道返回文本；允许为空；非敏感字段。
         * 数据来源：payment.stan；用于收单交易追踪和对账排查。
         * </p>
         */
        private String stan;

        /**
         * CVC 校验结果。
         * <p>
         * 单位：无；格式：渠道定义的结果码；允许为空；非敏感结果字段，不是原始 CVC。
         * 数据来源：CVCResultCode；用于风控和交易失败排查。
         * </p>
         */
        private String cvcResultCode;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Journal
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML journal 响应节点对象，封装请款、退款、撤销等修改类交易的接收或处理事件。
     * @status : create
     */
    @Data
    public static class Journal {

        /**
         * Worldpay journal id。
         * <p>
         * 单位：无；格式：Worldpay journal 标识；允许为空；非敏感字段。
         * 数据来源：journal.id；用于修改类交易事件追踪。
         * </p>
         */
        private String id;

        /**
         * Worldpay journalType。
         * <p>
         * 单位：无；格式：CAPTURED、REFUNDED、CANCELLED 等事件类型；允许为空；非敏感字段。
         * 数据来源：journal.journalType；用于后续动作状态映射。
         * </p>
         */
        private String journalType;

        /**
         * Worldpay type 兼容字段。
         * <p>
         * 单位：无；格式：渠道事件类型文本；允许为空；非敏感字段。
         * 数据来源：journal.type；用于兼容不同 XML 响应样例。
         * </p>
         */
        private String type;

        /**
         * 渠道响应码。
         * <p>
         * 单位：无；格式：渠道/ISO 响应码；允许为空；非敏感字段。
         * 数据来源：journal.responseCode 或 ISO8583ReturnCode.code；用于失败码映射。
         * </p>
         */
        private String responseCode;

        /**
         * 渠道响应描述。
         * <p>
         * 单位：无；格式：渠道返回文本；允许为空；非敏感字段但不得包含请求敏感信息。
         * 数据来源：journal.message 或 ISO8583ReturnCode.description；用于后台排查。
         * </p>
         */
        private String message;

        /**
         * 检索参考或渠道引用。
         * <p>
         * 单位：无；格式：渠道 reference/RRN 文本；允许为空；非敏感字段。
         * 数据来源：journal.reference；用于修改类交易排查。
         * </p>
         */
        private String reference;

        /**
         * 收单参考号。
         * <p>
         * 单位：无；格式：收单机构参考文本；允许为空；非敏感字段。
         * 数据来源：journal.acquirerReference；用于对账和争议处理。
         * </p>
         */
        private String acquirerReference;

        /**
         * 收单响应码。
         * <p>
         * 单位：无；格式：收单/ISO 响应码；允许为空；非敏感字段。
         * 数据来源：journal.acquirerCode 或 ISO8583ReturnCode；与 responseCode 共同解释渠道结果。
         * </p>
         */
        private String acquirerCode;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Amount
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML amount 响应节点对象，封装最小辅币单位金额、ISO 币种和辅币位；用于日志、对账和问题排查。
     * @status : create
     */
    @Data
    public static class Amount {

        /**
         * 最小辅币单位金额。
         * <p>
         * 单位：currencyCode 对应的最小辅币单位；格式：整数字符串；允许为空；非敏感字段。
         * 数据来源：amount.value；必须结合 currencyCode 和 exponent 还原主币种金额。
         * </p>
         */
        private String value;

        /**
         * ISO 4217 三位币种代码。
         * <p>
         * 单位：无；格式：三位大写 ISO 4217；允许为空；非敏感字段。
         * 数据来源：amount.currencyCode；决定 value 的币种语义。
         * </p>
         */
        private String currencyCode;

        /**
         * 币种辅币位。
         * <p>
         * 单位：位；格式：整数字符串；允许为空；非敏感字段。
         * 数据来源：amount.exponent；与 value、currencyCode 共同解释金额。
         * </p>
         */
        private String exponent;

        /**
         * 借贷方向。
         * <p>
         * 单位：无；格式：debit、credit 或渠道定义文本；允许为空；非敏感字段。
         * 数据来源：amount.debitCreditIndicator；用于区分退款等贷记方向。
         * </p>
         */
        private String debitCreditIndicator;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Ok
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML ok 响应节点对象，封装修改类交易已被 Worldpay 接收的状态摘要，例如 captureReceived、refundReceived 或 cancelReceived。
     * @status : create
     */
    @Data
    public static class Ok {

        /**
         * 归一化后的修改类接收状态。
         * <p>
         * 单位：无；格式：CAPTURE_REQUESTED、REFUND_REQUESTED、CANCEL_REQUESTED 或 PROCESSING；允许为空；非敏感字段。
         * 数据来源：ok 子节点类型；由 mapper 映射为平台待处理状态。
         * </p>
         */
        private String status;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : Error
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WPG XML error 响应节点对象，封装渠道错误码和错误描述；错误描述不得拼接请求敏感字段。
     * @status : create
     */
    @Data
    public static class Error {

        /**
         * Worldpay 错误码。
         * <p>
         * 单位：无；格式：渠道错误码；允许为空；非敏感字段。
         * 数据来源：error.code；用于平台失败码映射和排查。
         * </p>
         */
        private String code;

        /**
         * Worldpay 错误描述。
         * <p>
         * 单位：无；格式：渠道错误文本；允许为空；非敏感字段但不得包含请求 PAN、CVC、CAVV 或 Basic Auth。
         * 数据来源：error 文本内容；用于后台展示和问题定位。
         * </p>
         */
        private String message;
    }
}
