package com.scott.payment.component.redis.support;

import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisKeySupport
 * @date : 2026-05-31 21:45
 * @email : scott_x@163.com
 * @description : Redis Key 与 TTL 校验工具
 * @status : create
 */
public final class RedisKeySupport {

    /**
     * 永不过期时 RedisTemplate 返回的过期时间。
     */
    public static final long NEVER_EXPIRE_SECONDS = -1L;

    /**
     * Key 不存在时 RedisTemplate 返回的过期时间。
     */
    public static final long KEY_NOT_EXIST_SECONDS = -2L;

    /**
     * 私有构造方法，禁止实例化工具类。
     */
    private RedisKeySupport() {
    }

    /**
     * 校验 Redis Key 必须存在文本内容。
     *
     * @param key Redis Key
     */
    public static void requireKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("redis key can not be blank");
        }
    }

    /**
     * 判断 Redis Key 是否有文本内容。
     *
     * @param key Redis Key
     * @return 是否为有效 Key
     */
    public static boolean hasKey(String key) {
        return StringUtils.hasText(key);
    }

    /**
     * 判断 TTL 是否需要设置过期时间。
     *
     * @param ttl Redis 过期时间
     * @return 是否需要设置过期时间
     */
    public static boolean hasTtl(Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    /**
     * 校验 TTL 必须大于 0。
     *
     * @param ttl Redis 过期时间
     */
    public static void requirePositiveTtl(Duration ttl) {
        if (!hasTtl(ttl)) {
            throw new IllegalArgumentException("redis ttl must be positive");
        }
    }
}
