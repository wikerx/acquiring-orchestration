package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.id.LocalGlobalIdGenerator;
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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis Global Id Auto Configuration，位于 component-library/component-redis 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@AutoConfiguration
@EnableConfigurationProperties(RedisGlobalIdProperties.class)
@ConditionalOnProperty(prefix = "payment.global-id", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisGlobalIdAutoConfiguration {

    /**
     * 校验编号生成模式，避免错误配置导致容器中没有 GlobalIdGenerator。
     *
     * @param properties  Redis 全局编号配置
     * @param environment Spring 环境
     * @return 配置校验标记 Bean
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param properties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param environment 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    public GlobalIdModeGuard globalIdModeGuard(RedisGlobalIdProperties properties, Environment environment) {
        validateMode(properties, environment);
        return new GlobalIdModeGuard();
    }

    /**
     * 装配 Redis Server Time 提供器。
     *
     * @param stringRedisTemplate Spring 字符串 Redis 模板
     * @return Redis Server Time 提供器
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param stringRedisTemplate 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
     * @return Redis 全局唯一标识生成器
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param stringRedisTemplate 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param redisServerTimeProvider 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param properties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    @ConditionalOnMissingBean(GlobalIdGenerator.class)
    @ConditionalOnProperty(prefix = "payment.global-id", name = "mode", havingValue = "redis", matchIfMissing = true)
    public GlobalIdGenerator redisGlobalIdGenerator(StringRedisTemplate stringRedisTemplate,
                                                    RedisServerTimeProvider redisServerTimeProvider,
                                                    RedisGlobalIdProperties properties) {
        return new RedisGlobalIdGenerator(stringRedisTemplate, redisServerTimeProvider, properties);
    }

    /**
     * local 模式下装配本地全局唯一标识生成器。
     *
     * @param properties  Redis 全局编号配置
     * @param environment Spring 环境
     * @return 本地全局唯一标识生成器
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param properties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param environment 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
     * @param properties  Redis 全局编号配置
     * @param environment Spring 环境
     */
    private void validateMode(RedisGlobalIdProperties properties, Environment environment) {
        String mode = properties.getMode() == null ? "redis" : properties.getMode().trim().toLowerCase(Locale.ROOT);
        if (!"redis".equals(mode) && !"local".equals(mode)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "全局唯一标识生成模式配置非法");
        }
        validateLocalMode(properties, environment);
    }

    /**
     * 编号模式配置校验标记 Bean。
     */
    public static final class GlobalIdModeGuard {
    }
}
