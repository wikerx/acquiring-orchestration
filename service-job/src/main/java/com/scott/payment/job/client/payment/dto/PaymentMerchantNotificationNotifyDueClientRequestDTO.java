package com.scott.payment.job.client.payment.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentMerchantNotificationNotifyDueClientRequestDTO
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : service-job 调用 service-payment 到期商户通知补偿接口的客户端请求，位于任务客户端层，用于按 transaction_date_time 精确定位通知分表。
 * @status : create
 */
@Data
public class PaymentMerchantNotificationNotifyDueClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交易业务时间，用于定位 transaction_merchant_notification 物理分表。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 本次补偿最大处理数量。
     */
    private Integer limit;
}
