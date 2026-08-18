package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelMatchCommandDTO
 * @date : 2026-07-19 22:20
 * @email : scott_x@163.com
 * @description : 渠道交易查询勾兑命令，位于 service-payment 内部接口 DTO 层，用于定时任务按交易时间分表触发渠道状态确认；该命令只扫描已落库待勾兑动作，不重新受理交易。
 * @status : create
 */
@Data
public class TransactionChannelMatchCommandDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交易业务时间，用于定位 transaction_operation 物理分表。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 渠道编码，可为空；为空时扫描全部待勾兑渠道，每个 provider 编码独立过滤。
     */
    private String channelCode;

    /**
     * 本次最多处理数量，避免单次任务长事务扫描过多交易。
     */
    private Integer limit;
}
