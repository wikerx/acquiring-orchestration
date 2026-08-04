package com.scott.payment.payment.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationProperties
 * @date : 2026-08-03 00:00
 * @email : scott_x@163.com
 * @description : 商户交易结果通知配置，位于 service-payment 配置层，限制新建通知任务的失败重试上限。
 * @status : create
 */
@Data
@Validated
@ConfigurationProperties(prefix = "payment.transaction.merchant-notification")
public class MerchantNotificationProperties {

    /**
     * 单个商户通知任务的最大投递次数；只影响新建任务，不回写已有任务快照。
     */
    @Min(1)
    @Max(100)
    private int maxRetryCount = 10;
}
