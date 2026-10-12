package com.scott.payment.openapi.security;

import com.scott.payment.component.core.exception.RateLimitExceededException;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import com.scott.payment.openapi.config.OpenApiRateLimitProperties;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantRateLimitService
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : OpenAPI 认证链中的商户总量限流服务，在 IP 白名单后、防重放写入前执行
 * @status : create
 */
@Service
public class OpenApiMerchantRateLimitService {

    private static final String RATE_LIMIT_SCOPE = "openapiMerchant";

    private final RedisSlidingWindowRateLimiter rateLimiter;
    private final OpenApiRateLimitProperties properties;

    public OpenApiMerchantRateLimitService(RedisSlidingWindowRateLimiter rateLimiter,
                                           OpenApiRateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /**
     * 累计同一商户的全部开放接口请求；超过配额时拒绝且不占用防重放记录。
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
