package com.scott.payment.openapi.security;

import com.scott.payment.component.core.exception.RateLimitExceededException;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import com.scott.payment.openapi.config.OpenApiRateLimitProperties;
import org.springframework.stereotype.Service;

/**
 * OpenAPI 认证链中的商户总量限流服务，在 IP 白名单后、防重放写入前执行
 */
@Service
public class OpenApiMerchantRateLimitService {

    /**
     * {@code RATE_LIMIT_SCOPE}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final String RATE_LIMIT_SCOPE = "openapiMerchant";

    private final RedisSlidingWindowRateLimiter rateLimiter;
    private final OpenApiRateLimitProperties properties;

    /**
     * 创建商户 OpenAPI 总量限流服务。
     *
     * @param rateLimiter Redis 精确滑动窗口限流器
     * @param properties 商户限流额度和故障策略
     */
    public OpenApiMerchantRateLimitService(RedisSlidingWindowRateLimiter rateLimiter,
                                           OpenApiRateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /**
     * 累计同一商户的全部开放接口请求；超过配额时拒绝且不占用防重放记录。
     *
     * @param merchantId 已通过 JWT 验签的商户号
     */
    public void check(String merchantId) {
        if (!properties.isEnabled()) {
            return;
        }
        SlidingWindowRateLimitResult result = rateLimiter.tryAcquire(
                RATE_LIMIT_SCOPE,
                merchantId,
                properties.getMaxRequests(),
                properties.getWindow()
        );
        if (!result.allowed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
    }
}
