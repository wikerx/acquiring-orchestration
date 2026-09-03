package com.scott.payment.channel.payout.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelPayoutResponse
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : 平台统一代付渠道响应，保留标准状态和脱敏排障信息，不决定平台代付终态。
 * @status : create
 */
@Data
public class ChannelPayoutResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 实际响应的渠道编码。 */
    private String channelCode;

    /** 平台代付生命周期标识。 */
    private String operationId;

    /** 平台代付订单号。 */
    private String payoutOrderNo;

    /** 渠道代付订单号。 */
    private String channelOrderNo;

    /** 渠道交易流水号。 */
    private String channelTransactionId;

    /** 渠道原始状态映射后的统一状态。 */
    private String channelPayoutStatus;

    /** 渠道原始状态，仅供受控状态映射和排障。 */
    private String rawChannelStatus;

    /** 渠道响应码。 */
    private String channelResponseCode;

    /** 已标准化且不含敏感信息的渠道响应说明。 */
    private String channelResponseMessage;

    /** 渠道明确返回的金额，单位为币种主单位。 */
    private BigDecimal channelAmount;

    /** 渠道明确返回的 ISO 4217 币种。 */
    private String channelCurrency;

    /** 脱敏后的 Provider 扩展响应。 */
    private Map<String, String> rawResponse = new HashMap<>();
}
