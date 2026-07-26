package com.scott.payment.channel.payment.worldpay;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

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
     */
    private String outcome;

    /**
     * Access Worldpay 支付 ID，用于后续查询、请款、退款、撤销或渠道后台排查。
     */
    private String paymentId;

    /**
     * WorldPay 原始交易状态，例如 AUTHORISED、CAPTURED、REFUSED、ERROR。
     */
    private String status;

    /**
     * 渠道结果码，失败时通常承载拒绝或错误分类。
     */
    private String resultCode;

    /**
     * 渠道结果描述，进入日志和落库前必须避免包含完整敏感数据。
     */
    private String resultMessage;

    /**
     * WorldPay 订单号或商户订单号回显。
     */
    private String orderCode;

    /**
     * WorldPay 渠道交易号。
     */
    private String transactionId;

    /**
     * WorldPay 渠道请求号或交互 ID。
     */
    private String requestId;

    /**
     * 渠道侧返回的收单机构响应码。
     */
    private String acquirerCode;

    /**
     * 渠道侧返回的响应码。
     */
    private String responseCode;

    /**
     * 授权码，成功授权或支付时可能返回。
     */
    private String authorizationCode;

    /**
     * 系统跟踪审计号，用于对账和渠道排查。
     */
    private String stan;

    /**
     * 检索参考号或渠道回单号。
     */
    private String rrn;

    /**
     * 收单机构交易参考号，用于争议和对账。
     */
    private String acquirerReference;

    /**
     * 渠道返回的支付工具摘要。
     */
    private PaymentInstrument paymentInstrument;

    /**
     * 渠道错误对象，HTTP 4xx/5xx 或业务拒绝时可能存在。
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
     */
    @JSONField(name = "_actions")
    private Map<String, LinkPayload> actions;

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
         */
        private String type;

        /**
         * 卡品牌或支付品牌。
         */
        private String brand;

        /**
         * 卡组织或渠道 scheme。
         */
        private String scheme;

        /**
         * 脱敏卡号，禁止完整 PAN。
         */
        private String cardNumberMasked;

        /**
         * 卡有效期月份。
         */
        private String expiryMonth;

        /**
         * 卡有效期年份。
         */
        private String expiryYear;

        /**
         * 发卡国家或地区代码。
         */
        private String issuerCountry;

        /**
         * 资金类型，例如 CREDIT、DEBIT。
         */
        private String fundingMethod;

        /**
         * CSC/CVV 校验结果，不是原始 CVV。
         */
        private String cscResult;
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
         */
        private String code;

        /**
         * 渠道错误描述，不得包含完整 PAN、CVV 或认证头。
         */
        private String message;

        /**
         * 渠道错误分类，例如 VALIDATION、REFUSED、SYSTEM。
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
         */
        private String href;

        /**
         * Worldpay 后续动作推荐 HTTP 方法。
         */
        private String method;
    }
}
