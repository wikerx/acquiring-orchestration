package com.scott.payment.component.redis.cache;

import com.scott.payment.component.core.cache.PaymentCacheTtlPolicy;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;

/**
 * 为 Spring Cache Redis 写入统一生成基础 TTL 加有界随机抖动，降低集中失效风险
 */
final class PaymentRedisCacheTtlFunction implements RedisCacheWriter.TtlFunction {

    /**
     * 基础缓存生命周期，必须大于零。
     */
    private final Duration baseTtl;

    /**
     * 单次写入允许增加或减少的百分比，取值范围为 0 至 50。
     */
    private final int jitterPercent;

    /**
     * 创建有界随机 TTL 计算器并立即校验基础生命周期和抖动比例。
     *
     * @param baseTtl 基础缓存生命周期
     * @param jitterPercent 允许正负浮动的百分比
     */
    PaymentRedisCacheTtlFunction(Duration baseTtl, int jitterPercent) {
        PaymentCacheTtlPolicy.validate(baseTtl, jitterPercent);
        this.baseTtl = baseTtl;
        this.jitterPercent = jitterPercent;
    }

    /**
     * 为每次缓存写入计算有界随机 TTL。
     *
     * @param key   Spring Cache 逻辑 Key，不参与敏感日志
     * @param value 待缓存值，不读取其内容
     * @return 基础 TTL 加减配置范围内随机毫秒数
     */
    @Override
    public Duration getTimeToLive(Object key, Object value) {
        return PaymentCacheTtlPolicy.jitter(baseTtl, jitterPercent);
    }
}
