package com.scott.payment.job.client.data.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationReconcileClientRequestDTO
 * @date : 2026-08-06 12:44
 * @email : scott_x@163.com
 * @description : service-job 请求 service-data 将到期通知重新可靠入 MQ 的内部 DTO
 * @status : create
 */
@Data
public class DataMerchantNotificationReconcileClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 可选季度定位时间；为空表示全部已发布季度。 */
    private List<LocalDateTime> transactionDateTimes = Collections.emptyList();

    /** 每季度最大入队数量。 */
    private Integer limit;
}
