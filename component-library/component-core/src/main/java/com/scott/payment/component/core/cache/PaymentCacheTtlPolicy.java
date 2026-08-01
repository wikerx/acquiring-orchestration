package com.scott.payment.component.core.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCacheTtlPolicy
 * @date : 2026-07-30 21:10
 * @email : scott_x@163.com
 * @description : 统一计算 Spring Cache 与直连 Redis 的有界 TTL 抖动，避免业务方法散落随机过期算法
 * @status : create
 */
public final class PaymentCacheTtlPolicy {

    /**
     * 允许的最大抖动百分比，防止错误配置把缓存生命周期压缩或放大到不可控范围。
     */
    public static final int MAX_JITTER_PERCENT = 50;

    private PaymentCacheTtlPolicy() {
    }

    /**
     * 根据基础 TTL 生成正负对称的随机抖动结果。
     *
     * @param baseTtl       基础缓存生命周期，必须大于零且能够转换为正毫秒数
     * @param jitterPercent 抖动百分比，取值范围为 0 至 50，0 表示保持基础 TTL
     * @return 位于基础 TTL 正负抖动区间内的有效生命周期
     * @throws IllegalArgumentException 基础 TTL 非正或抖动百分比越界时抛出
     */
    public static Duration jitter(Duration baseTtl, int jitterPercent) {
        validate(baseTtl, jitterPercent);
        long baseTtlMillis = baseTtl.toMillis();
        long jitterRangeMillis = baseTtlMillis / 100 * jitterPercent
                + baseTtlMillis % 100 * jitterPercent / 100;
        if (jitterRangeMillis == 0) {
            return baseTtl;
        }
        long offsetMillis = ThreadLocalRandom.current()
                .nextLong(-jitterRangeMillis, jitterRangeMillis + 1);
        return Duration.ofMillis(baseTtlMillis + offsetMillis);
    }

    /**
     * 校验基础 TTL 和抖动比例是否满足统一缓存策略。
     *
     * @param baseTtl       基础缓存生命周期
     * @param jitterPercent 抖动百分比
     * @throws IllegalArgumentException 任一参数超出安全范围时抛出
     */
    public static void validate(Duration baseTtl, int jitterPercent) {
        if (baseTtl == null || baseTtl.isZero() || baseTtl.isNegative() || baseTtl.toMillis() <= 0) {
            throw new IllegalArgumentException("Redis cache base TTL must be positive");
        }
        if (jitterPercent < 0 || jitterPercent > MAX_JITTER_PERCENT) {
            throw new IllegalArgumentException("Redis cache TTL jitter percent must be between 0 and "
                    + MAX_JITTER_PERCENT);
        }
    }
}
