package com.scott.payment.payment.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 商户交易结果通知配置，位于 service-payment 配置层，限制新建通知任务的失败重试上限。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "payment.transaction.merchant-notification")
public class MerchantNotificationProperties {

    /**
     * 单个商户通知任务的自动投递次数，当前协议固定为五次。
     */
    @Min(5)
    @Max(5)
    private int maxRetryCount = 5;
}
