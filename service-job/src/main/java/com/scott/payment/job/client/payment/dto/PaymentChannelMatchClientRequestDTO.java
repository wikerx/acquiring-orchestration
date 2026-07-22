package com.scott.payment.job.client.payment.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelMatchClientRequestDTO
 * @date : 2026-07-19 22:40
 * @email : scott_x@163.com
 * @description : service-job 调用 service-payment 渠道查询勾兑接口的客户端请求，位于任务客户端层，用于按 transaction_date_time 精确定位动作单分表并限制单次扫描规模。
 * @status : create
 */
@Data
public class PaymentChannelMatchClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交易业务时间，用于定位 transaction_operation 物理分表。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 渠道编码，可为空；WPGXML 和 WPGJSON 是两个独立渠道编码，是否能真实查询取决于对应渠道客户端实现。
     */
    private String channelCode;

    /**
     * 本次最大处理数量。
     */
    private Integer limit;
}
