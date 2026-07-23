package com.scott.payment.payment.service.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentPreparedChannelRequestDTO
 * @date : 2026-07-23 00:00
 * @email : scott_x@163.com
 * @description : 渠道调用预生成身份 DTO，位于 service-payment 服务 DTO 层，承载已在本地准备事务中提交的 request_id 和 channel_transaction_id。
 * @status : create
 */
@Data
public class PaymentPreparedChannelRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 平台渠道请求 ID，对应 transaction_channel_request.request_id。
     */
    private String requestId;

    /**
     * 渠道订单号，MPGS 场景为首次平台 transactionId。
     */
    private String channelOrderNo;

    /**
     * 渠道交易 ID，必须在渠道调用前生成并持久化。
     */
    private String channelTransactionId;
}
