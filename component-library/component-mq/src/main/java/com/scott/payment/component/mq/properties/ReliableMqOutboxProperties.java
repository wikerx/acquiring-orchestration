package com.scott.payment.component.mq.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqOutboxProperties
 * @date : 2026-08-02 22:15
 * @email : scott_x@163.com
 * @description : 非交易可靠 MQ Outbox 批量、重试和超时恢复配置
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "acquiring.mq.outbox")
public class ReliableMqOutboxProperties {

    /** 是否启用定时补偿 Relay；即时 afterCommit 投递不受此开关影响。 */
    private boolean relayEnabled = true;
    /** 单次扫描最大消息数。 */
    private int batchSize = 100;
    /** 单条消息最大失败重试次数。 */
    private int maxRetryCount = 8;
    /** 首次重试等待秒数，后续按指数退避。 */
    private long retryDelaySeconds = 10L;
    /** PROCESSING 状态超时秒数。 */
    private long processingTimeoutSeconds = 120L;
}
