package com.scott.payment.channel.payout.dto.request;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelPayoutQueryRequest
 * @date : 2026-08-12 00:00
 * @description : 平台统一代付查单请求，通过平台或渠道持久化标识定位交易。
 * @status : create
 */
@Data
public class ChannelPayoutQueryRequest implements Serializable {

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

    /** Provider 查单差异化参数，不得包含可直接使用的明文凭据。 */
    private Map<String, String> extension = new HashMap<>();
}
