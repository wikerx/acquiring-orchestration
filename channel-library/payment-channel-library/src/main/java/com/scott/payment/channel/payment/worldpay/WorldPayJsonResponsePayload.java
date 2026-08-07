package com.scott.payment.channel.payment.worldpay;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayJsonResponsePayload
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : WorldPay JSON 响应载荷模型，位于 payment-channel-library 渠道实现层，用于承接 WPGJSON HTTP 响应并映射为平台统一渠道响应。
 * @status : create
 */
@Data
public class WorldPayJsonResponsePayload {

    /**
     * Access Worldpay 响应结果，例如 authorized、refused、sentForSettlement。
     * <p>
     * 单位：无；格式：Access Worldpay outcome 文本；允许为空；非敏感字段。
     * 数据来源：渠道 HTTP 响应体；与 status、resultCode 共同参与平台状态映射。
     * </p>
     */
    private String outcome;

    /**
     * Access Worldpay 支付 ID，用于后续查询、请款、退款、撤销或渠道后台排查。
     * <p>
     * 单位：无；格式：Worldpay payment id；成功或处理中交易通常不为空；非敏感字段。
     * 数据来源：渠道响应体；与 _links/actions 一起定位后续动作。
     * </p>
     */
    private String paymentId;

    /**
     * WorldPay 原始交易状态，例如 AUTHORISED、CAPTURED、REFUSED、ERROR。
     * <p>
     * 单位：无；格式：渠道状态文本；允许为空；非敏感字段。
     * 数据来源：渠道响应体；由 response mapper 归一为 rawChannelStatus。
     * </p>
     */
    private String status;

    /**
     * 渠道结果码，失败时通常承载拒绝或错误分类。
     * <p>
     * 单位：无；格式：渠道结果码或拒付码；允许为空；非敏感字段。
     * 数据来源：渠道响应体；用于平台失败码映射和日志排查。
     * </p>
     */
    private String resultCode;

    /**
     * 渠道结果描述，进入日志和落库前必须避免包含完整敏感数据。
     * <p>
     * 单位：无；格式：渠道返回文本；允许为空；非敏感字段但不得包含 PAN、CVC、CAVV、密钥或 Authorization。
     * 数据来源：渠道响应体；用于后台排查和商户问题定位。
     * </p>
     */
    private String resultMessage;

    /**
     * WorldPay 订单号或商户订单号回显。
     * <p>
     * 单位：无；格式：渠道订单号或商户订单号；允许为空；非敏感字段。
     * 数据来源：渠道响应体；与平台 channelOrderNo 关联同一笔交易。
     * </p>
     */
    private String orderCode;

    /**
     * WorldPay 渠道交易号。
     * <p>
     * 单位：无；格式：渠道交易标识；允许为空；非敏感字段。
     * 数据来源：渠道响应体；用于后续动作、查询和渠道后台检索。
     * </p>
     */
    private String transactionId;

    /**
     * WorldPay 渠道请求号或交互 ID。
     * <p>
     * 单位：无；格式：渠道请求标识；允许为空；非敏感字段。
     * 数据来源：渠道响应体；与平台 operationId 一起定位一次请求交互。
     * </p>
     */
    private String requestId;

    /**
     * 渠道侧返回的收单机构响应码。
     * <p>
     * 单位：无；格式：收单或 ISO 响应码；允许为空；非敏感字段。
     * 数据来源：渠道响应体或 issuer 节点；用于排查发卡/收单拒绝原因。
     * </p>
     */
    private String acquirerCode;

    /**
     * 渠道侧返回的响应码。
     * <p>
     * 单位：无；格式：渠道或 ISO 响应码；允许为空；非敏感字段。
     * 数据来源：渠道响应体；与 acquirerCode、issuer.responseCode 共同解释结果。
     * </p>
     */
    private String responseCode;

    /**
     * 授权码，成功授权或支付时可能返回。
     * <p>
     * 单位：无；格式：发卡/收单授权码；允许为空；非敏感字段。
     * 数据来源：渠道响应体或 issuer 节点；用于请款、对账和争议处理。
     * </p>
     */
    private String authorizationCode;

    /**
     * 系统跟踪审计号，用于对账和渠道排查。
     * <p>
     * 单位：无；格式：数字或渠道定义文本；允许为空；非敏感字段。
     * 数据来源：渠道响应体或 issuer 节点；用于收单侧交易追踪。
     * </p>
     */
    private String stan;

    /**
     * 检索参考号或渠道回单号。
     * <p>
     * 单位：无；格式：RRN/reference 文本；允许为空；非敏感字段。
     * 数据来源：渠道响应体或 issuer 节点；用于商户排查、对账和拒付。
     * </p>
     */
    private String rrn;

    /**
     * 收单机构交易参考号，用于争议和对账。
     * <p>
     * 单位：无；格式：收单参考文本；允许为空；非敏感字段。
     * 数据来源：渠道响应体或 issuer 节点；用于收单侧定位。
     * </p>
     */
    private String acquirerReference;

    /**
     * 渠道响应的金额节点；amount 为最小辅币单位，必须结合 currency 和可选 exponent 解释。
     */
    private ValuePayload value;

    /**
     * 渠道返回的支付工具摘要。
     * <p>
     * 单位：无；格式：对象；允许为空；只允许包含卡品牌、BIN、尾四位、脱敏卡号和发卡信息。
     * 数据来源：渠道响应体；不得包含完整 PAN 或 CVC。
     * </p>
     */
    private PaymentInstrument paymentInstrument;

    /**
     * 发卡行响应摘要，Access Worldpay 成功或拒付时可能包含授权码和 ISO 响应码。
     * <p>
     * 单位：无；格式：对象；允许为空；非敏感字段集合。
     * 数据来源：渠道 issuer 节点；用于授权排查和收单对账。
     * </p>
     */
    private IssuerPayload issuer;

    /**
     * 拒付码，Access Worldpay 被拒交易常用字段。
     * <p>
     * 单位：无；格式：渠道拒付码；允许为空；非敏感字段。
     * 数据来源：渠道响应体；用于平台失败码映射和商户排查。
     * </p>
     */
    private String refusalCode;

    /**
     * 拒付描述，进入日志和落库前必须避免包含完整敏感数据。
     * <p>
     * 单位：无；格式：渠道拒付描述；允许为空；非敏感字段但不得包含请求敏感值。
     * 数据来源：渠道响应体；用于后台展示和问题定位。
     * </p>
     */
    private String refusalDescription;

    /**
     * 拒付来源，例如 issuer、risk 或 processor。
     * <p>
     * 单位：无；格式：issuer、risk、processor 等渠道定义文本；允许为空；非敏感字段。
     * 数据来源：渠道响应体；用于区分发卡拒绝、风控拒绝或处理机构拒绝。
     * </p>
     */
    private String refusalSource;

    /**
     * 风险响应摘要，部分 Access Worldpay 版本在拒付或风控命中时返回。
     * <p>
     * 单位：无；格式：对象；允许为空；不得包含完整卡号、邮箱、电话或 IP 库明细。
     * 数据来源：渠道 risk 节点；用于商户排查渠道风控拒绝。
     * </p>
     */
    private RiskPayload risk;

    /**
     * 渠道错误对象，HTTP 4xx/5xx 或业务拒绝时可能存在。
     * <p>
     * 单位：无；格式：对象；允许为空；错误描述不得包含请求敏感字段。
     * 数据来源：渠道 error 节点；用于协议错误、认证失败和渠道异常排查。
     * </p>
     */
    private ErrorPayload error;

    /**
     * Access Worldpay HATEOAS 链接集合。
     * <p>
     * 典型 key 包括 cardPayments:settle、cardPayments:refund、cardPayments:cancel、payments:events 等；值只保存 href 和 method，
     * 不保存任何认证头或密钥。
     * </p>
     */
    @JSONField(name = "_links")
    private Map<String, LinkPayload> links;

    /**
     * Access Worldpay 可执行动作集合；部分版本把动作放在 _actions 中。
     * <p>
     * 单位：无；格式：Map&lt;String, LinkPayload&gt;；允许为空；非敏感字段集合。
     * 数据来源：渠道 _actions 节点；用于保存请款、退款、撤销或查询的后续动作链接。
     * </p>
     */
    @JSONField(name = "_actions")
    private Map<String, LinkPayload> actions;

    /** Worldpay JSON 响应金额节点。 */
    @Data
    public static class ValuePayload {
        private BigDecimal amount;
        private String currency;
        private Integer exponent;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PaymentInstrument
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WorldPay JSON 支付工具摘要节点，位于渠道适配层，用于保存渠道返回的卡品牌、卡组织、脱敏卡号和发卡信息。
     * @status : create
     */
    @Data
    public static class PaymentInstrument {

        /**
         * 支付方式，例如 CARD。
         * <p>
         * 单位：无；格式：CARD、card/plain 等渠道文本；允许为空；非敏感字段。
         * 数据来源：渠道 paymentInstrument 节点；用于平台支付工具摘要。
         * </p>
         */
        private String type;

        /**
         * 卡品牌或支付品牌。
         * <p>
         * 单位：无；格式：VISA、MASTERCARD 等；允许为空；非敏感字段。
         * 数据来源：渠道 paymentInstrument.brand；与 cardBrand 互为兼容字段。
         * </p>
         */
        private String brand;

        /**
         * Worldpay 返回的卡品牌字段，兼容 cardBrand 命名。
         * <p>
         * 单位：无；格式：VISA、MASTERCARD 等；允许为空；非敏感字段。
         * 数据来源：渠道 paymentInstrument.cardBrand；用于补齐品牌摘要。
         * </p>
         */
        private String cardBrand;

        /**
         * 卡组织或渠道 scheme。
         * <p>
         * 单位：无；格式：渠道定义的 scheme 文本；允许为空；非敏感字段。
         * 数据来源：渠道 paymentInstrument.scheme；用于支付工具展示和排查。
         * </p>
         */
        private String scheme;

        /**
         * 卡 BIN 前六位。可用于脱敏摘要拼接，禁止当作完整 PAN。
         * <p>
         * 单位：无；格式：6 位数字；允许为空；低敏卡摘要字段。
         * 数据来源：渠道返回的卡摘要；只能与 lastFour 拼接为脱敏卡号。
         * </p>
         */
        private String cardBin;

        /**
         * 卡尾四位。可用于脱敏摘要拼接，禁止当作完整 PAN。
         * <p>
         * 单位：无；格式：4 位数字；允许为空；低敏卡摘要字段。
         * 数据来源：渠道返回的卡摘要；与 cardBin 共同生成 masked PAN。
         * </p>
         */
        private String lastFour;

        /**
         * 脱敏卡号，禁止完整 PAN。
         * <p>
         * 单位：无；格式：前 6 后 4 加掩码字符；允许为空；脱敏卡摘要字段。
         * 数据来源：渠道响应或 mapper 根据 cardBin/lastFour 生成；不得还原完整卡号。
         * </p>
         */
        private String cardNumberMasked;

        /**
         * 卡有效期月份。
         * <p>
         * 单位：月；格式：两位数字；允许为空；非敏感字段。
         * 数据来源：渠道卡摘要；与 expiryYear 共同描述卡有效期。
         * </p>
         */
        private String expiryMonth;

        /**
         * 卡有效期年份。
         * <p>
         * 单位：年；格式：四位数字；允许为空；非敏感字段。
         * 数据来源：渠道卡摘要；与 expiryMonth 共同描述卡有效期。
         * </p>
         */
        private String expiryYear;

        /**
         * 发卡国家或地区代码。
         * <p>
         * 单位：无；格式：ISO 3166 国家地区代码；允许为空；非敏感字段。
         * 数据来源：渠道卡摘要；用于风控、统计和排查。
         * </p>
         */
        private String issuerCountry;

        /**
         * 发卡国家或地区代码，兼容 Access Worldpay countryCode。
         * <p>
         * 单位：无；格式：ISO 3166 国家地区代码；允许为空；非敏感字段。
         * 数据来源：渠道 paymentInstrument.countryCode；与 issuerCountry 互为兼容字段。
         * </p>
         */
        private String countryCode;

        /**
         * 资金类型，例如 CREDIT、DEBIT。
         * <p>
         * 单位：无；格式：CREDIT、DEBIT、PREPAID 等；允许为空；非敏感字段。
         * 数据来源：渠道卡摘要；用于支付工具展示和风险分析。
         * </p>
         */
        private String fundingMethod;

        /**
         * 资金类型，兼容 Access Worldpay fundingType。
         * <p>
         * 单位：无；格式：CREDIT、DEBIT、PREPAID 等；允许为空；非敏感字段。
         * 数据来源：渠道 paymentInstrument.fundingType；与 fundingMethod 互为兼容字段。
         * </p>
         */
        private String fundingType;

        /**
         * CSC/CVV 校验结果，不是原始 CVV。
         * <p>
         * 单位：无；格式：渠道定义的校验结果码；允许为空；非敏感结果字段。
         * 数据来源：渠道卡摘要；用于失败排查和风控。
         * </p>
         */
        private String cscResult;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : IssuerPayload
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WorldPay JSON 发卡行响应节点，位于渠道适配层，用于保存授权码、响应码、STAN 和 RRN 等对账排查字段。
     * @status : create
     */
    @Data
    public static class IssuerPayload {

        /**
         * 授权码。
         * <p>
         * 单位：无；格式：发卡行授权码；允许为空；非敏感字段。
         * 数据来源：issuer.authorizationCode；用于请款、对账和争议处理。
         * </p>
         */
        private String authorizationCode;

        /**
         * 发卡行或 ISO8583 响应码。
         * <p>
         * 单位：无；格式：ISO8583 或渠道响应码；允许为空；非敏感字段。
         * 数据来源：issuer.responseCode；用于失败码映射和发卡行排查。
         * </p>
         */
        private String responseCode;

        /**
         * 发卡行响应描述。
         * <p>
         * 单位：无；格式：渠道返回文本；允许为空；非敏感字段但不得包含请求敏感信息。
         * 数据来源：issuer.responseMessage；用于后台排查。
         * </p>
         */
        private String responseMessage;

        /**
         * System Trace Audit Number。
         * <p>
         * 单位：无；格式：数字或渠道返回文本；允许为空；非敏感字段。
         * 数据来源：issuer.stan；用于收单链路追踪和对账。
         * </p>
         */
        private String stan;

        /**
         * 检索参考号。
         * <p>
         * 单位：无；格式：RRN 文本；允许为空；非敏感字段。
         * 数据来源：issuer.retrievalReferenceNumber；用于商户排查、对账和拒付。
         * </p>
         */
        private String retrievalReferenceNumber;

        /**
         * 收单机构交易参考。
         * <p>
         * 单位：无；格式：收单机构参考文本；允许为空；非敏感字段。
         * 数据来源：issuer.acquirerReferenceNumber；用于收单侧定位。
         * </p>
         */
        private String acquirerReferenceNumber;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : RiskPayload
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WorldPay JSON 风控响应节点，位于渠道适配层，用于保留风险分值、决定和原因摘要；不得承载完整卡号或认证数据。
     * @status : create
     */
    @Data
    public static class RiskPayload {

        /**
         * 风控结论。
         * <p>
         * 单位：无；格式：ACCEPT、REJECT、REVIEW 等渠道文本；允许为空；非敏感字段。
         * 数据来源：risk.decision；用于定位渠道风控结果。
         * </p>
         */
        private String decision;

        /**
         * 风控原因码。
         * <p>
         * 单位：无；格式：渠道风险原因码；允许为空；非敏感字段。
         * 数据来源：risk.reasonCode；用于排查风控拒绝原因。
         * </p>
         */
        private String reasonCode;

        /**
         * 风险评分。
         * <p>
         * 单位：分；格式：渠道定义的分值文本；允许为空；非敏感字段。
         * 数据来源：risk.score；与 decision、reasonCode 共同解释风险结论。
         * </p>
         */
        private String score;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : ErrorPayload
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WorldPay JSON 错误响应节点，位于渠道适配层，用于保留渠道拒绝或异常原因，供后台排查和平台失败码映射使用。
     * @status : create
     */
    @Data
    public static class ErrorPayload {

        /**
         * 渠道错误码。
         * <p>
         * 单位：无；格式：Worldpay 错误码；允许为空；非敏感字段。
         * 数据来源：error.code；用于协议错误、认证错误和渠道异常排查。
         * </p>
         */
        private String code;

        /**
         * 渠道错误描述，不得包含完整 PAN、CVV 或认证头。
         * <p>
         * 单位：无；格式：渠道错误文本；允许为空；非敏感字段但不得包含请求敏感值。
         * 数据来源：error.message；用于后台展示和平台失败码映射。
         * </p>
         */
        private String message;

        /**
         * 渠道错误分类，例如 VALIDATION、REFUSED、SYSTEM。
         * <p>
         * 单位：无；格式：渠道定义分类文本；允许为空；非敏感字段。
         * 数据来源：error.type；用于区分参数错误、拒绝和系统异常。
         * </p>
         */
        private String type;
    }

    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : LinkPayload
     * @date : 2026-07-26 00:00
     * @email : scott_x@163.com
     * @description : WorldPay JSON 链接节点，位于渠道适配层，用于保存响应中的后续动作 href 和 HTTP method，供请款、退款、撤销或查询勾兑复用。
     * @status : create
     */
    @Data
    public static class LinkPayload {

        /**
         * Worldpay 后续动作 URL 或 path。
         * <p>
         * 单位：无；格式：HTTP/HTTPS URL 或相对 path；允许为空；非敏感字段但不得包含 Authorization 或密钥。
         * 数据来源：_links/_actions.href；供后续请款、退款、撤销或查询复用。
         * </p>
         */
        private String href;

        /**
         * Worldpay 后续动作推荐 HTTP 方法。
         * <p>
         * 单位：无；格式：GET、POST、PUT 等 HTTP method；允许为空；非敏感字段。
         * 数据来源：_links/_actions.method；与 href 共同决定后续动作请求方式。
         * </p>
         */
        private String method;
    }
}
