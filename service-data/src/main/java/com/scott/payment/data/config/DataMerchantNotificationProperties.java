package com.scott.payment.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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

    /** 平台签发回调 JWT 的有效期，单位秒，最大 300 秒。 */
    private long callbackJwtTtlSeconds = 180L;

    /** 商户回调敏感材料在单实例内的最长驻留时间，默认两分钟。 */
    private Duration securityMaterialCacheTtl = Duration.ofMinutes(2);

    /** 单个 service-data 实例允许缓存的商户密钥版本条目上限。 */
    private int securityMaterialCacheMaxEntries = 2048;

    /** 是否允许明文 HTTP，仅限隔离开发环境显式开启。 */
    private boolean allowHttp;

    /** 是否允许回环、私网和保留地址，仅限隔离开发环境显式开启。 */
    private boolean allowPrivateNetwork;

}
