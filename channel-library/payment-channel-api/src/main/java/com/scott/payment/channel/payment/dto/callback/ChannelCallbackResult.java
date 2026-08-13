package com.scott.payment.channel.payment.dto.callback;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackResult
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道回调解析结果，位于 payment-channel-api DTO 层，仅表达渠道原始事件和映射后的渠道状态，不直接推进平台交易状态机。
 * @status : create
 */
@Data
public class ChannelCallbackResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 渠道编码。
     */
    private String channelCode;

    /**
     * 渠道回调事件 ID。
     */
    private String callbackEventId;

    /**
     * 渠道订单号。
     */
    private String channelOrderNo;

    /**
     * 渠道交易 ID。
     */
    private String channelTransactionId;

    /**
     * 渠道原始状态。
     */
    private String rawChannelStatus;

    /**
     * 映射后的渠道统一状态。
     */
    private String channelTradeStatus;

    /**
     * 回调金额，主币种单位。
     */
    private BigDecimal amount;

    /**
     * 回调币种。
     */
    private String currency;

    /**
     * 验签是否通过。
     */
    private boolean signatureValid;

    /**
     * 是否重复事件，最终幂等仍由 service-payment 落库兜底。
     */
    private boolean duplicate;

    /**
     * 渠道响应码或错误码。
     */
    private String channelResponseCode;

    /**
     * 渠道响应描述。
     */
    private String channelResponseMessage;

    /**
     * 脱敏后的扩展字段。
     */
    private Map<String, String> extension = new LinkedHashMap<>();
}
