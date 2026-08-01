package com.scott.payment.openapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantSecretCacheProperties
 * @date : 2026-08-01 15:10
 * @email : scott_x@163.com
 * @description : OpenAPI 单实例敏感密钥缓存边界配置，限制材料驻留时间和最大商户数量
 * @status : create
 */
@ConfigurationProperties(prefix = "openapi.security.secret-cache")
public class OpenApiMerchantSecretCacheProperties {

    /** 敏感密钥在单实例内的最长驻留时间，默认 2 分钟且不得超过 10 分钟。 */
    private Duration ttl = Duration.ofMinutes(2);

    /** 单实例最多缓存的商户版本条目数，默认 2048。 */
    private int maxEntries = 2048;

    /**
     * 获取敏感密钥本地缓存有效期。
     *
     * @return 正数且不超过 10 分钟的有效期
     */
    public Duration getTtl() {
        return ttl;
    }

    /**
     * 设置敏感密钥本地缓存有效期。
     *
     * @param ttl 正数且不超过 10 分钟的有效期
     */
    public void setTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("OpenAPI secret cache TTL must be between 1 nanosecond and 10 minutes");
        }
        this.ttl = ttl;
    }

    /**
     * 获取单实例最大密钥条目数。
     *
     * @return 1 至 100000 的容量上限
     */
    public int getMaxEntries() {
        return maxEntries;
    }

    /**
     * 设置单实例最大密钥条目数。
     *
     * @param maxEntries 1 至 100000 的容量上限
     */
    public void setMaxEntries(int maxEntries) {
        if (maxEntries <= 0 || maxEntries > 100_000) {
            throw new IllegalArgumentException("OpenAPI secret cache max entries must be between 1 and 100000");
        }
        this.maxEntries = maxEntries;
    }
}
