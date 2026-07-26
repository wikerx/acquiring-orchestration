package com.scott.payment.channel.payment.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelPaymentResponse
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道统一响应，位于 payment-channel-library DTO 层，用于承载渠道原始状态映射后的统一结果；平台交易状态由 service-payment 状态机决定。
 * @status : create
 */
@Data
public class ChannelPaymentResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 渠道编码。
     */
    private String channelCode;

    /**
     * 平台内部生命周期关联标识，同一原始交易生命周期共用，不返回商户。
     */
    private String operationId;

    /**
     * 平台当前交易唯一标识。
     */
    private String transactionId;

    /**
     * 渠道侧订单号；MPGS 对应 orderId。
     */
    private String channelOrderNo;

    /**
     * 渠道侧交易 ID；MPGS 对应 transactionId，部分渠道可为空。
     */
    private String channelTransactionId;

    /**
     * 统一渠道交易状态，例如 SUCCESS、FAILED、PENDING、PROCESSING、NEED_REDIRECT。
     */
    private String channelTradeStatus;

    /**
     * 渠道原始状态。
     */
    private String rawChannelStatus;

    /**
     * 渠道响应码。
     */
    private String channelResponseCode;

    /**
     * 渠道响应描述。
     */
    private String channelResponseMessage;

    /**
     * 授权码。渠道适配层负责把渠道原始字段映射为该平台标准字段，交易核心禁止再猜测渠道 rawResponse 的字段名。
     */
    private String authCode;

    /**
     * 检索参考号或渠道回单号。
     */
    private String rrn;

    /**
     * 收单机构参考号，用于对账、争议和后台排查。
     */
    private String acquirerReferenceNo;

    /**
     * 3DS 或渠道跳转地址。
     */
    private String redirectUrl;

    /**
     * 渠道返回的支付工具摘要，供 payment 核心补充卡品牌、发卡国家、资金类型等合规展示字段。
     */
    private PaymentMethodSummary paymentMethodSummary;

    /**
     * 渠道扩展响应，进入日志或落库前必须脱敏。
     */
    private Map<String, String> rawResponse = new HashMap<>();

    /**
     * 渠道 HTTP 状态码，渠道未暴露或请求异常时允许为空。
     */
    private Integer httpStatus;

    /**
     * 渠道真实 HTTP 方法，例如 MPGS 交易变更类接口为 PUT，查询接口为 GET。
     */
    private String httpMethod;

    /**
     * 脱敏后的渠道真实请求 URL，不包含认证头或密钥。
     */
    private String requestUrlMasked;

    /**
     * 脱敏后的渠道请求头 JSON，禁止保存 Authorization、API Key 等完整敏感值。
     */
    private String requestHeaderJsonMasked;

    /**
     * 脱敏后的渠道请求体 JSON，卡号、CVV、密码等敏感字段必须脱敏。
     */
    private String requestBodyJsonMasked;

    /**
     * 脱敏后的渠道响应头 JSON。
     */
    private String responseHeaderJsonMasked;

    /**
     * 脱敏后的渠道原始响应体 JSON。
     */
    private String responseBodyJsonMasked;

    @Data
    /**
     * @author : scott
     * @version : v1.0.0
     * @classname : PaymentMethodSummary
     * @date : 2026-07-12 00:00
     * @email : scott_x@163.com
     * @description : PaymentMethodSummary 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
     * @status : create
     */
    public static class PaymentMethodSummary implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 支付方式，例如 CARD、BANK_CARD。
         */
        private String paymentMethod;

        /**
         * 卡品牌或支付品牌，例如 MASTERCARD、VISA。
         */
        private String paymentBrand;

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
         * 渠道返回的存储凭证标识，例如 STORED 或 NOT_STORED。
         */
        private String storedOnFile;

        /**
         * CSC/CVV 校验结果。
         */
        private String cscResult;
    }

}
