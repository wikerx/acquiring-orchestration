package com.scott.payment.payment.service.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentPreparedChannelRequestDTO
 * @date : 2026-07-23 00:00
 * @email : scott_x@163.com
 * @description : 渠道调用预生成身份 DTO，位于 service-payment 服务 DTO 层，承载已在本地准备事务中提交的 request_id、渠道交易身份和查询扩展标识。
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

    /**
     * 渠道查询所需的额外业务标识，例如退款查询使用的 merchantRefundNo。
     * <p>
     * 该字段只在内存调用链中传递，不写入交易事实表；扩展值必须是非敏感的渠道业务标识。
     * </p>
     */
    private Map<String, String> extension = new LinkedHashMap<>();
}
