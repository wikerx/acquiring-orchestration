package com.scott.payment.component.redis.constant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisKeyConstants
 * @date : 2026-05-31 21:45
 * @email : scott_x@163.com
 * @description : 支付系统 Redis Key 常量
 * @status : create
 */
public final class RedisKeyConstants {

    /**
     * Redis Key 统一分隔符。
     */
    public static final String SEPARATOR = ":";

    /**
     * 支付系统 Redis Key 顶级前缀。
     */
    public static final String PAYMENT_PREFIX = "payment:";

    /**
     * 订单号序列 Redis Key 前缀。
     */
    public static final String ORDER_NO_PREFIX = PAYMENT_PREFIX + "order:no:";

    /**
     * 幂等 Redis Key 前缀。
     */
    public static final String IDEMPOTENT_PREFIX = PAYMENT_PREFIX + "idempotent:";

    /**
     * 分布式锁 Redis Key 前缀。
     */
    public static final String LOCK_PREFIX = PAYMENT_PREFIX + "lock:";

    /**
     * 商户信息 Redis Key 前缀。
     */
    public static final String MERCHANT_PREFIX = PAYMENT_PREFIX + "merchant:";

    /**
     * 系统追踪号 Redis Key 前缀。
     */
    public static final String STAN_PREFIX = PAYMENT_PREFIX + "stan:";

    /**
     * 去重集合 Redis Key 前缀。
     */
    public static final String DEDUPLICATION_PREFIX = PAYMENT_PREFIX + "dedup:";

    /**
     * 私有构造方法，禁止实例化常量类。
     */
    private RedisKeyConstants() {
    }
}
