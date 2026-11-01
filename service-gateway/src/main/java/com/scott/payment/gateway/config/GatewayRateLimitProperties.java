package com.scott.payment.gateway.config;

import com.scott.payment.component.redis.rate.RateLimitFailurePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.InitializingBean;

import java.time.Duration;

/**
 * Gateway 客户端 IP 总量滑动窗口限流配置
 */
@ConfigurationProperties(prefix = "acquiring.gateway.rate-limit")
public class GatewayRateLimitProperties implements InitializingBean {

    /**
     * 是否启用客户端 IP 总量限流；关闭时请求不访问限流 Redis。
     */
    private boolean enabled;

    /**
     * 同一客户端 IP 在一个精确滚动窗口内允许通过的请求数。
     */
    private int maxRequests = 200;

    /**
     * 精确滚动窗口长度，配置文件支持 {@code 1m}、{@code 60s} 或 {@code PT1M}。
     */
    private Duration window = Duration.ofMinutes(1);

    /**
     * Redis 故障降级期间，单个 Gateway 实例允许保留的本地 IP 身份上限。
     */
    private int localMaxIdentities = 10_000;

    /**
     * Redis 无法执行全局判定时使用本地窗口或直接拒绝请求。
     */
    private RateLimitFailurePolicy failurePolicy = RateLimitFailurePolicy.LOCAL_FALLBACK;

    /**
     * 获取是否启用 IP 限流。
     *
     * @return {@code true} 表示对全部 {@code /api/rest/**} 请求执行限流
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 IP 限流。
     *
     * @param enabled {@code true} 启用，{@code false} 关闭
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取单个 IP 的窗口请求额度。
     *
     * @return 窗口内允许通过的请求数
     */
    public int getMaxRequests() {
        return maxRequests;
    }

    /**
     * 设置单个 IP 的窗口请求额度。
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

    /**
     * 获取 Redis 故障时的本地身份容量。
     *
     * @return 每个 Gateway 实例允许保存的 IP 身份数
     */
    public int getLocalMaxIdentities() {
        return localMaxIdentities;
    }

    /**
     * 设置 Redis 故障时的本地身份容量；达到上限后本地限流器拒绝新的 IP 身份，避免 JVM 内存无界增长。
     *
     * @param localMaxIdentities 正整数身份容量
     */
    public void setLocalMaxIdentities(int localMaxIdentities) {
        this.localMaxIdentities = localMaxIdentities;
    }

    /**
     * 查询失败策略；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 网关服务 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public RateLimitFailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    /**
     * 写入失败策略，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * @param failurePolicy Redis 或限流脚本不可用时采用的放行或拒绝策略
     */
    public void setFailurePolicy(RateLimitFailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy;
    }

    /**
     * 在应用启动时拒绝无法形成有效窗口或可能造成无界内存的配置。
     */
    @Override
    public void afterPropertiesSet() {
        if (maxRequests <= 0 || window == null || window.isZero() || window.isNegative()
                || localMaxIdentities <= 0 || failurePolicy == null) {
            throw new IllegalStateException("Gateway rate limit configuration is invalid");
        }
    }
}
