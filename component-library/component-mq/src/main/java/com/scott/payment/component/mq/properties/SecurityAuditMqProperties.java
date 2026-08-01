package com.scott.payment.component.mq.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SecurityAuditMqProperties
 * @date : 2026-08-01 18:00
 * @email : scott_x@163.com
 * @description : 安全拦截审计 MQ 配置，统一控制生产消费开关与 Redis 辅助幂等有效期
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "acquiring.security-audit.mq")
public class SecurityAuditMqProperties {

    /** 是否启用安全拦截审计 MQ 链路，默认启用。 */
    private boolean enabled = true;

    /** Redis MQ 辅助幂等有效期，单位秒，默认保留七天。 */
    private long consumeIdempotentTtlSeconds = 604800L;
}
