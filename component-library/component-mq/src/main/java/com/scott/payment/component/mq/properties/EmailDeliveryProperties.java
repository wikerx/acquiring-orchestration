package com.scott.payment.component.mq.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailDeliveryProperties
 * @date : 2026-08-02 23:40
 * @email : scott_x@163.com
 * @description : 业务邮件异步投递、退避重试、处理中超时恢复和批量重投参数
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "acquiring.email.delivery")
public class EmailDeliveryProperties {

    /** 是否启用业务邮件 MQ 消费与恢复任务。 */
    private boolean enabled = false;
    /** 业务邮件默认最大重试次数，不包含首次发送。 */
    private int defaultMaxRetryCount = 3;
    /** 第一次重试等待秒数，后续按指数退避。 */
    private long retryDelaySeconds = 30L;
    /** 单次退避等待上限秒数。 */
    private long maxRetryDelaySeconds = 3600L;
    /** SENDING 状态允许占用的最大秒数，必须大于 SMTP 读写超时。 */
    private long processingTimeoutSeconds = 300L;
    /** 每次重投扫描的最大记录数。 */
    private int batchSize = 100;
    /** 重投与超时恢复任务间隔毫秒数。 */
    private long relayIntervalMillis = 30000L;

    /**
     * 按已失败次数计算有上限的指数退避，首次失败使用基础间隔。
     *
     * @param completedRetries 当前记录已经完成的失败重试次数
     * @return 下一次重试至少等待的秒数
     */
    public long calculateRetryDelaySeconds(Integer completedRetries) {
        long maximum = Math.max(maxRetryDelaySeconds, 1L);
        long delay = Math.min(Math.max(retryDelaySeconds, 1L), maximum);
        int retries = completedRetries == null ? 0 : Math.max(completedRetries, 0);
        for (int index = 0; index < retries && delay < maximum; index++) {
            delay = delay > maximum / 2 ? maximum : Math.min(delay * 2, maximum);
        }
        return delay;
    }

    /**
     * 校验超时恢复窗口覆盖完整 SMTP 网络等待预算，避免发送未结束时被恢复任务重复抢占。
     *
     * @param connectTimeoutMs SMTP 建连超时，单位毫秒
     * @param readTimeoutMs SMTP 单次读取和写入超时，单位毫秒
     */
    public void validateSmtpTimeoutBudget(Integer connectTimeoutMs, Integer readTimeoutMs) {
        if (connectTimeoutMs == null || connectTimeoutMs <= 0 || readTimeoutMs == null || readTimeoutMs <= 0) {
            throw new IllegalStateException("SMTP timeout must be positive");
        }
        long smtpTimeoutBudgetMs = (long) connectTimeoutMs + (long) readTimeoutMs * 2L;
        long recoveryTimeoutMs = Math.max(processingTimeoutSeconds, 0L) * 1000L;
        if (recoveryTimeoutMs <= smtpTimeoutBudgetMs) {
            throw new IllegalStateException("email delivery processing timeout must exceed SMTP timeout budget");
        }
    }
}
