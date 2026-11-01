package com.scott.payment.component.mq.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * service-data 风控审计消费配置，控制消费开关和 Redis 辅助幂等有效期
 */
@Data
@ConfigurationProperties(prefix = "acquiring.risk-audit.mq")
public class RiskAuditMqProperties {

    /**
     * 是否启用风控审计消费，默认启用。
     */
    private boolean enabled = true;

    /**
     * Redis MQ 辅助幂等有效期，单位秒，默认保留七天。
     */
    private long consumeIdempotentTtlSeconds = 604800L;
}
