package com.scott.payment.openapi.config;

import com.scott.payment.component.redis.rate.RateLimitFailurePolicy;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRateLimitProperties
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : OpenAPI 商户维度滑动窗口限流配置
 * @status : create
 */
@ConfigurationProperties(prefix = "payment.openapi.rate-limit")
public class OpenApiRateLimitProperties implements InitializingBean {

    /**
     * 是否启用商户维度限流；开启后同一 {@code merchantId} 的全部 OpenAPI 接口共享额度。
     * UAT 和生产环境默认关闭，完成容量评估和压测后通过环境变量显式开启。
     */
    private boolean enabled;

    /** 单个商户在一个精确滚动窗口内允许通过的请求数。 */
    private int maxRequests = 200;

    /** 精确滚动窗口长度，配置文件支持 {@code 1m}、{@code 60s} 或 {@code PT1M}。 */
    private Duration window = Duration.ofMinutes(1);

    /** Redis 故障降级期间，单个 OpenAPI 实例允许保留的本地商户身份上限。 */
    private int localMaxIdentities = 10_000;

    /** Redis 无法执行全局判定时使用本地窗口或直接拒绝请求。 */
    private RateLimitFailurePolicy failurePolicy = RateLimitFailurePolicy.LOCAL_FALLBACK;

    /**
     * 获取是否启用商户限流。
     *
     * @return {@code true} 表示启用商户维度限流
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用商户限流。
     *
     * @param enabled {@code true} 启用，{@code false} 关闭
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取单个商户的窗口请求额度。
     *
     * @return 窗口内允许通过的请求数
     */
    public int getMaxRequests() {
        return maxRequests;
    }

    /**
     * 设置单个商户的窗口请求额度。
     *
     * @param maxRequests 正整数请求数
     */
    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    /**
     * 获取精确滚动窗口长度。
     *
     * @return 大于零的窗口时长
     */
    public Duration getWindow() {
        return window;
    }

    /**
     * 设置精确滚动窗口长度。
     *
     * @param window 大于零的窗口时长
     */
    public void setWindow(Duration window) {
        this.window = window;
    }

    public int getLocalMaxIdentities() {
        return localMaxIdentities;
    }

    public void setLocalMaxIdentities(int localMaxIdentities) {
        this.localMaxIdentities = localMaxIdentities;
    }

    public RateLimitFailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    public void setFailurePolicy(RateLimitFailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    /** 在应用启动时校验额度、窗口、本地容量和故障策略。 */
    @Override
    public void afterPropertiesSet() {
        if (maxRequests <= 0 || window == null || window.isZero() || window.isNegative()
                || localMaxIdentities <= 0 || failurePolicy == null) {
            throw new IllegalStateException("OpenAPI rate limit configuration is invalid");
        }
    }
}
