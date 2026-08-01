package com.scott.payment.job.client.data.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationNotifyDueClientRequestDTO
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-job 调用 service-data 商户通知补偿接口的请求，按交易业务时间精确定位通知分表
 * @status : create
 */
@Data
public class DataMerchantNotificationNotifyDueClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 交易业务时间，用于定位通知任务物理分表，不允许为空。 */
    private LocalDateTime transactionDateTime;

    /** 本次补偿最大处理数量，必须大于零。 */
    private Integer limit;
}
