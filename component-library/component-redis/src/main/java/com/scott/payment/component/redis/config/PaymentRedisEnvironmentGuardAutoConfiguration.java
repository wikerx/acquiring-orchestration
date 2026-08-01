package com.scott.payment.component.redis.config;

import com.scott.payment.component.redis.cache.PaymentCacheProperties;
import com.scott.payment.component.redis.cache.PaymentRedisCacheAutoConfiguration;
import com.scott.payment.component.redis.id.RedisGlobalIdAutoConfiguration;
import com.scott.payment.component.redis.id.RedisGlobalIdProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisEnvironmentGuardAutoConfiguration
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : Redis 环境前缀启动门禁，阻止 test、uat、prod 复用其他环境的物理 Key 命名空间。
 * @status : create
 */
@AutoConfiguration(before = {RedisGlobalIdAutoConfiguration.class, PaymentRedisCacheAutoConfiguration.class})
@EnableConfigurationProperties({
        PaymentRedisProperties.class,
        PaymentCacheProperties.class,
        RedisGlobalIdProperties.class
})
public class PaymentRedisEnvironmentGuardAutoConfiguration {

    private static final Set<String> PROTECTED_PROFILES = Set.of("test", "uat", "prod");

    /**
     * 校验受保护环境的 Redis Key 前缀。
     *
     * @param environment        Spring 运行环境
     * @param redisProperties    Redis 物理 Key 配置
     * @param cacheProperties    Spring Cache Key 配置
     * @param globalIdProperties 全局编号 Redis 状态配置
     * @return 环境前缀门禁标记 Bean
     */
    @Bean
    public RedisEnvironmentPrefixGuard redisEnvironmentPrefixGuard(
            Environment environment,
            PaymentRedisProperties redisProperties,
            PaymentCacheProperties cacheProperties,
            RedisGlobalIdProperties globalIdProperties) {
        String protectedProfile = resolveProtectedProfile(environment);
        if (protectedProfile == null) {
            return new RedisEnvironmentPrefixGuard();
        }

        String environmentPrefix = "acquiring:" + protectedProfile;
        requireExact(
                "payment.redis.key-prefix",
                redisProperties.getKeyPrefix(),
                environmentPrefix,
                protectedProfile
        );
        requireExact(
                "payment.cache.redis.key-prefix",
                cacheProperties.getKeyPrefix(),
                environmentPrefix,
                protectedProfile
        );
        requireExact(
                "payment.global-id.state-key",
                globalIdProperties.getStateKey(),
                environmentPrefix + ":global-id:state",
                protectedProfile
        );
        return new RedisEnvironmentPrefixGuard();
    }

    /**
     * 解析当前唯一受保护环境。
     * <p>
     * 同时激活多个受保护 profile 会让 Redis Key 环境归属不确定，因此启动阶段直接拒绝。
     * 本地等非保护 profile 返回 {@code null}，不套用生产前缀门禁。
     * </p>
     *
     * @param environment Spring 运行环境
     * @return 规范化受保护 profile；未命中时返回 {@code null}
     */
    private String resolveProtectedProfile(Environment environment) {
        List<String> protectedProfiles = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.trim().toLowerCase(Locale.ROOT))
                .filter(PROTECTED_PROFILES::contains)
                .distinct()
                .toList();
        if (protectedProfiles.size() > 1) {
            throw new IllegalStateException(
                    "Redis environment prefix guard does not allow multiple protected profiles: "
                            + protectedProfiles
            );
        }
        return protectedProfiles.isEmpty() ? null : protectedProfiles.get(0);
    }

    /**
     * 要求受保护环境的 Redis 配置与预期前缀完全一致。
     *
     * @param propertyName    配置项名称
     * @param actualValue     实际配置值
     * @param expectedValue   当前环境期望值
     * @param protectedProfile 当前受保护 profile
     * @throws IllegalStateException 配置值不精确匹配时抛出
     */
    private void requireExact(String propertyName,
                              String actualValue,
                              String expectedValue,
                              String protectedProfile) {
        if (!expectedValue.equals(actualValue)) {
            throw invalidPrefix(propertyName, expectedValue, protectedProfile);
        }
    }

    /**
     * 构造带配置项、预期前缀和环境信息的启动失败异常。
     *
     * @param propertyName     配置项名称
     * @param expectedValue    当前环境期望值
     * @param protectedProfile 当前受保护 profile
     * @return Redis 环境隔离校验异常
     */
    private IllegalStateException invalidPrefix(String propertyName,
                                                String expectedValue,
                                                String protectedProfile) {
        return new IllegalStateException(
                propertyName + " must use " + expectedValue
                        + " when the " + protectedProfile + " profile is active"
        );
    }

    /**
     * Redis 环境前缀校验已通过的标记 Bean。
     */
    public static final class RedisEnvironmentPrefixGuard {
    }
}
