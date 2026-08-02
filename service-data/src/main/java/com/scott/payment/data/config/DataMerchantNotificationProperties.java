package com.scott.payment.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationProperties
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知执行参数，集中限制 HTTP 超时和 PROCESSING 任务恢复窗口
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "data.merchant-notification")
public class DataMerchantNotificationProperties {

    /** 商户回调建连超时，单位毫秒，必须大于零。 */
    private int connectTimeoutMillis = 30_000;

    /** 商户回调读取超时，单位毫秒，必须大于零。 */
    private int readTimeoutMillis = 10_000;

    /** PROCESSING 任务无状态推进后的恢复阈值，单位秒，必须大于 HTTP 读取超时。 */
    private long processingTimeoutSeconds = 60L;

    /** 单季度每次扫描允许尝试恢复的最大 PROCESSING 候选数。 */
    private int recoveryBatchLimit = 100;
}
