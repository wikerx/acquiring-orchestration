package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.id.LocalGlobalIdGenerator;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.observability.RedisBusinessMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGlobalIdAutoConfiguration
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : 装配全局 ID 生成器，并在启动期校验模式、环境隔离状态 Key 和受控恢复参数
 * @status : create
 */
@AutoConfiguration
@EnableConfigurationProperties({RedisGlobalIdProperties.class, PaymentRedisProperties.class})
@ConditionalOnProperty(prefix = "payment.global-id", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisGlobalIdAutoConfiguration {

    /**
     * 校验编号生成模式，避免错误配置导致容器中没有 GlobalIdGenerator。
     *
     * @param properties      Redis 全局编号配置
     * @param redisProperties Redis Key 环境前缀配置
     * @param environment     Spring 环境
     * @return 配置校验标记 Bean
     */
    @Bean
    public GlobalIdModeGuard globalIdModeGuard(RedisGlobalIdProperties properties,
                                               PaymentRedisProperties redisProperties,
                                               Environment environment) {
        validateMode(properties, redisProperties, environment);
        return new GlobalIdModeGuard();
    }

    /**
     * 装配 Redis Server Time 提供器。
     *
     * @param stringRedisTemplate Spring 字符串 Redis 模板
     * @return Redis Server Time 提供器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "payment.global-id", name = "mode", havingValue = "redis", matchIfMissing = true)
    public RedisServerTimeProvider redisServerTimeProvider(StringRedisTemplate stringRedisTemplate) {
        return new RedisServerTimeProvider(stringRedisTemplate);
    }

    /**
     * Redis 模式下装配分布式全局唯一标识生成器。
     *
     * @param stringRedisTemplate     Spring 字符串 Redis 模板
     * @param redisServerTimeProvider Redis 服务端时间提供器
     * @param properties              Redis 全局编号配置
     * @param metricsProvider         可选 Redis 业务指标记录器；隔离上下文未启用指标时使用 noop
     * @return Redis 全局唯一标识生成器
     */
    @Bean
    @ConditionalOnMissingBean(GlobalIdGenerator.class)
    @ConditionalOnProperty(prefix = "payment.global-id", name = "mode", havingValue = "redis", matchIfMissing = true)
    public GlobalIdGenerator redisGlobalIdGenerator(StringRedisTemplate stringRedisTemplate,
                                                    RedisServerTimeProvider redisServerTimeProvider,
                                                    RedisGlobalIdProperties properties,
                                                    ObjectProvider<RedisBusinessMetrics> metricsProvider) {
        return new RedisGlobalIdGenerator(
                stringRedisTemplate,
                redisServerTimeProvider,
                properties,
                metricsProvider.getIfAvailable(RedisBusinessMetrics::noop)
        );
    }

    /**
     * local 模式下装配本地全局唯一标识生成器。
     *
     * @param properties  Redis 全局编号配置
     * @param environment Spring 环境
     * @return 本地全局唯一标识生成器
     */
    @Bean
    @ConditionalOnMissingBean(GlobalIdGenerator.class)
    @ConditionalOnProperty(prefix = "payment.global-id", name = "mode", havingValue = "local")
    public GlobalIdGenerator localGlobalIdGenerator(RedisGlobalIdProperties properties, Environment environment) {
        validateLocalMode(properties, environment);
        return new LocalGlobalIdGenerator();
    }

    /**
     * 禁止 UAT 和生产环境启用 local 编号模式。
     *
     * @param properties  Redis 全局编号配置
     * @param environment Spring 环境
     */
    private void validateLocalMode(RedisGlobalIdProperties properties, Environment environment) {
        String mode = properties.getMode() == null ? "redis" : properties.getMode().trim().toLowerCase(Locale.ROOT);
        if (!"local".equals(mode)) {
            return;
        }
        boolean forbiddenProfile = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> "prod".equals(profile) || "uat".equals(profile));
        if (forbiddenProfile) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "UAT/生产环境禁止使用本地全局唯一标识生成模式");
        }
    }

    /**
     * 校验 mode 只允许 redis 或 local。
     *
     * @param properties      Redis 全局编号配置
     * @param redisProperties Redis Key 环境前缀配置
     * @param environment     Spring 环境
     */
    private void validateMode(RedisGlobalIdProperties properties,
                              PaymentRedisProperties redisProperties,
                              Environment environment) {
        String mode = properties.getMode() == null ? "redis" : properties.getMode().trim().toLowerCase(Locale.ROOT);
        if (!"redis".equals(mode) && !"local".equals(mode)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "全局唯一标识生成模式配置非法");
        }
        validateLocalMode(properties, environment);
        if ("redis".equals(mode)) {
            validateRedisState(properties, redisProperties, environment);
        }
    }

    /**
     * 校验 Redis 状态 Key 与环境前缀一致，并阻断未确认或缺少时间下限的状态恢复配置。
     *
     * @param properties      Redis 全局编号配置
     * @param redisProperties Redis Key 环境前缀配置
     * @param environment     Spring 环境
     */
    private void validateRedisState(RedisGlobalIdProperties properties,
                                    PaymentRedisProperties redisProperties,
                                    Environment environment) {
        String expectedStateKey = redisProperties.businessKey("global-id", "state");
        if (!expectedStateKey.equals(properties.getStateKey())) {
            boolean protectedProfile = isProtectedProfile(environment);
            String scope = protectedProfile ? "受保护环境" : "当前环境";
            throw new ServiceException(
                    ApiResultEnum.PARAM_INVALID.getCode(),
                    scope + "全局唯一标识 state-key 必须匹配 payment.redis.key-prefix"
            );
        }
        boolean hasRestoreFloor = properties.getRestoreFloorEpochMillis() > 0L;
        if (properties.getRestoreFloorEpochMillis() < 0L
                || properties.isRestoreAcknowledged() != hasRestoreFloor) {
            throw new ServiceException(
                    ApiResultEnum.PARAM_INVALID.getCode(),
                    "全局唯一标识状态恢复必须同时提供确认标识和正数时间下限"
            );
        }
    }

    /**
     * 判断当前是否为禁止弱化全局 ID 保护的 UAT 或生产环境。
     *
     * @param environment Spring 环境
     * @return UAT 或生产 profile 激活时为 true
     */
    private boolean isProtectedProfile(Environment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> "prod".equals(profile) || "uat".equals(profile));
    }

    /**
     * 编号模式配置校验标记 Bean。
     */
    public static final class GlobalIdModeGuard {
    }
}
