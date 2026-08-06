package com.scott.payment.job.dto.transaction;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationRetryRequest
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 商户通知补偿任务请求，位于 service-job 任务 DTO 层，用于指定一个或多个 transaction_date_time 分表时间点和单次处理上限。
 * @status : create
 */
@Data
public class MerchantNotificationRetryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 单个交易时间点；未配置 transactionDateTimes 时使用该字段定位分表。
     */
    private LocalDateTime transactionDateTime;

    /** 可选平台交易号；传入时只补偿这一笔，并强制同时传入 transactionDateTime。 */
    private String transactionId;

    /**
     * 多个交易时间点，用于手动补偿跨分表任务。
     */
    private List<LocalDateTime> transactionDateTimes = Collections.emptyList();

    /**
     * 单个分表本次最多处理数量。
     */
    private Integer limit;

    /** 自动补偿模式：MQ 为默认主链路，JOB 为紧急直接回调模式。 */
    private String mode;
}
