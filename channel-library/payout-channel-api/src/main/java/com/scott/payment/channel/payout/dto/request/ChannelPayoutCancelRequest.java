package com.scott.payment.channel.payout.dto.request;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelPayoutCancelRequest
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : 代付渠道取消请求，位于 payout-channel-api DTO 层，仅承载已持久化的渠道身份和受控扩展参数。
 * @status : create
 */
@Data
public class ChannelPayoutCancelRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路由选中的代付渠道编码。 */
    private String channelCode;

    /** 平台代付生命周期标识。 */
    private String operationId;

    /** 平台代付订单号。 */
    private String payoutOrderNo;

    /** 渠道代付订单号。 */
    private String channelOrderNo;

    /** 渠道交易流水号。 */
    private String channelTransactionId;

    /** Provider 差异化参数；不得承载日志可见的敏感值。 */
    private Map<String, String> extension = new HashMap<>();
}
