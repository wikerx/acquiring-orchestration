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
     * 渠道扩展响应，进入日志或落库前必须脱敏。
     */
    private Map<String, String> rawResponse = new HashMap<>();

}
