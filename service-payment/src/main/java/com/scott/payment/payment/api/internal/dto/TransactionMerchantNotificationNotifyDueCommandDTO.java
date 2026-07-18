package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationNotifyDueCommandDTO
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : service-payment 内部到期商户通知补偿请求，位于内部接口 DTO 层，用于按 transaction_date_time 定位通知任务分表并限制单次处理数量。
 * @status : create
 */
@Data
public class TransactionMerchantNotificationNotifyDueCommandDTO implements Serializable {

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
