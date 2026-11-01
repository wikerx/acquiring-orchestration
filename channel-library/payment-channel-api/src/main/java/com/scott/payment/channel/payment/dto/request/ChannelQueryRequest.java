package com.scott.payment.channel.payment.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 渠道查询请求，位于 payment-channel-api DTO 层，用于按平台或渠道交易标识查询交易结果。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelQueryRequest extends ChannelPaymentRequest {

    /**
     * 平台渠道请求 ID，对应 transaction_channel_request.request_id。
     * <p>
     * 该字段只可在渠道明确支持时作为查询引用；默认不得把本地 request_id 视为渠道交易 ID。
     */
    private String requestId;
}
