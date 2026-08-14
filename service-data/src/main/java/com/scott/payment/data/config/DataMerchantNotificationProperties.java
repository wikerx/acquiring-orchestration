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

    /** 平台签发回调 JWT 的有效期，单位秒，最大 300 秒。 */
    private long callbackJwtTtlSeconds = 180L;

    /** 是否允许明文 HTTP，仅限隔离开发环境显式开启。 */
    private boolean allowHttp;

    /** 是否允许回环、私网和保留地址，仅限隔离开发环境显式开启。 */
    private boolean allowPrivateNetwork;

    /** 与 service-payment 一致的交易敏感字段 AES-GCM 密钥，只能由 Secret/KMS 注入。 */
    private String sensitiveFieldEncryptionKey = "dev-hosted-checkout-field-key-change-me";

    /** 当前可解密的交易敏感字段密钥版本。 */
    private String sensitiveFieldKeyVersion = "dev-v1";
}
