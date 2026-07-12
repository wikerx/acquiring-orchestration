package com.scott.payment.channel.payment.mpgs;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsResponsePayload
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 响应载荷模型，位于 payment-channel-library 渠道实现层，仅承接渠道原始响应并映射为统一渠道响应。
 * @status : create
 */
@Data
public class MpgsResponsePayload {

    /**
     * MPGS 顶层 result，例如 SUCCESS、ERROR、PENDING、UNKNOWN。
     */
    private String result;

    /**
     * MPGS 网关入口标识，保留用于排查渠道侧链路。
     */
    private String gatewayEntryPoint;

    /**
     * MPGS 商户号。
     */
    private String merchant;

    /**
     * MPGS API 版本号。
     */
    private String version;

    /**
     * MPGS 网关响应摘要，包含网关码、收单响应和 CSC 校验结果。
     */
    private Response response;

    /**
     * MPGS 当前交易动作信息。
     */
    private Transaction transaction;

    /**
     * MPGS 订单信息。
     */
    private Order order;

    /**
     * MPGS 错误信息，result=ERROR 时通常存在。
     */
    private ErrorPayload error;

    @Data
    public static class Response {

        /**
         * MPGS 网关响应码，例如 APPROVED、DECLINED。
         */
        private String gatewayCode;

        /**
         * MPGS 网关处理建议。
         */
        private String gatewayRecommendation;

        /**
         * 收单机构响应码。
         */
        private String acquirerCode;

        /**
         * 收单机构响应描述，可能含渠道原始失败原因。
         */
        private String acquirerMessage;

        /**
         * 卡安全码校验结果，不是原始 CVV，但仍只作为内部排查字段使用。
         */
        private String cardSecurityCode;
    }

    @Data
    public static class Transaction {

        /**
         * MPGS 交易 ID。
         */
        private String id;

        /**
         * MPGS 交易类型。
         */
        private String type;

        /**
         * MPGS 交易金额，原始字符串。
         */
        private String amount;

        /**
         * MPGS 交易币种。
         */
        private String currency;

        /**
         * 授权码，成功授权或支付时可能返回。
         */
        private String authorizationCode;

        /**
         * 渠道交易参考号。
         */
        private String reference;

        /**
         * 渠道交易回单号。
         */
        private String receipt;

        /**
         * 交易来源。
         */
        private String source;
    }

    @Data
    public static class Order {

        /**
         * MPGS orderId，当前使用平台商户订单号构造。
         */
        private String id;

        /**
         * MPGS 订单金额，原始字符串。
         */
        private String amount;

        /**
         * MPGS 订单币种。
         */
        private String currency;

        /**
         * MPGS 订单状态。
         */
        private String status;

        /**
         * MPGS 订单参考号。
         */
        private String reference;
    }

    @Data
    public static class ErrorPayload {

        /**
         * MPGS 错误原因编码。
         */
        private String cause;

        /**
         * MPGS 错误说明，日志可记录但不得直接作为商户可见原因。
         */
        private String explanation;

        /**
         * 发生校验错误的字段。
         */
        private String field;

        /**
         * MPGS 校验错误类型。
         */
        private String validationType;
    }
}
