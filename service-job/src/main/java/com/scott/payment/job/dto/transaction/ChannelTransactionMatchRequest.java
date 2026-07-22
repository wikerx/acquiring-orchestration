package com.scott.payment.job.dto.transaction;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelTransactionMatchRequest
 * @date : 2026-07-19 22:40
 * @email : scott_x@163.com
 * @description : 渠道交易查询勾兑任务请求，位于 service-job 任务 DTO 层，用于指定分表时间、渠道编码和单次处理上限；任务只触发 service-payment 查询已落库待确认交易。
 * @status : create
 */
@Data
public class ChannelTransactionMatchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 单个交易时间点；未配置 transactionDateTimes 时使用该字段定位分表。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 多个交易时间点，用于手动补偿跨分表任务。
     */
    private List<LocalDateTime> transactionDateTimes = Collections.emptyList();

    /**
     * 渠道编码，可为空；例如 MPGS、WPGXML、WPGJSON，其中 WPGXML/WPGJSON 按两个独立渠道分别扫描。
     */
    private String channelCode;

    /**
     * 单个分表本次最多处理数量。
     */
    private Integer limit;
}
