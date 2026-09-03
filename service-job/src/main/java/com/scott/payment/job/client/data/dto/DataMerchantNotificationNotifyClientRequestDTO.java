package com.scott.payment.job.client.data.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationNotifyClientRequestDTO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : service-job 调用 service-data 精确重试单笔商户通知的请求。
 * @status : create
 */
@Data
public class DataMerchantNotificationNotifyClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 平台交易号，不承担分片时间解析职责。 */
    private String transactionId;

    /** 从交易记录或管理端请求传入的分片业务时间。 */
    private LocalDateTime transactionDateTime;
}
