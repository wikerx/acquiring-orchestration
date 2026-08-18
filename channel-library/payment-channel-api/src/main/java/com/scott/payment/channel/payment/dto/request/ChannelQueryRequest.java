package com.scott.payment.channel.payment.dto.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelQueryRequest
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道查询请求，位于 payment-channel-api DTO 层，用于按平台或渠道交易标识查询交易结果。
 * @status : create
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
