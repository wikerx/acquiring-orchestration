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
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
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
    /**
     * 判断收单支付条件是否满足，供业务分支或权限控制使用。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 判断收单支付条件是否满足，供业务分支或权限控制使用。
     * @param ttl 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static boolean hasTtl(Duration ttl) {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    /**
     * 校验 TTL 必须大于 0。
     *
     * @param ttl Redis 过期时间
     */
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param ttl 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public static void requirePositiveTtl(Duration ttl) {
        if (!hasTtl(ttl)) {
            throw new IllegalArgumentException("redis ttl must be positive");
        }
    }
}
