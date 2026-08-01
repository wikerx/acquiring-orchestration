package com.scott.payment.component.redis.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisObservabilityAutoConfiguration
 * @date : 2026-07-31 10:00
 * @email : scott_x@163.com
 * @description : Redis 治理指标自动配置，在 Micrometer 可用时注册业务指标门面并拒绝敏感或无界标签
 * @status : create
 */
@AutoConfiguration(after = MetricsAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class RedisObservabilityAutoConfiguration {

    /**
     * Redis 治理指标禁止使用的归一化标签名。
     *
     * <p>归一化后只保留小写字母和数字，因此 camelCase、snake_case、短横线和点分形式
     * 都不能绕过敏感标签门禁。</p>
     */
    private static final Set<String> FORBIDDEN_TAG_KEYS = Set.of(
            "rediskey",
            "key",
            "businesskey",
            "merchantid",
            "storeid",
            "orderno",
            "merchantorderno",
            "transactionid",
            "operationid",
            "messageid",
            "traceid",
            "requestid",
            "exceptionmessage"
    );

    /**
     * 注册 Redis 业务指标门面。
     *
     * @param meterRegistry Micrometer 指标注册器
     * @return Redis 业务指标记录器
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public RedisBusinessMetrics redisBusinessMetrics(MeterRegistry meterRegistry) {
        return new RedisBusinessMetrics(meterRegistry);
    }

    /**
     * 拒绝 Redis 治理指标中的敏感或无界标签。
     *
     * <p>过滤器只约束 {@code acquiring.redis.*} 指标，不影响应用、实例、环境等平台公共标签。
     * 新增指标标签时必须继续使用枚举值，并在自动化测试中证明标签基数有界。</p>
     *
     * @return Redis 指标敏感标签过滤器
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisSensitiveTagMeterFilter")
    public MeterFilter redisSensitiveTagMeterFilter() {
        return new MeterFilter() {
            @Override
            public MeterFilterReply accept(io.micrometer.core.instrument.Meter.Id id) {
                if (!id.getName().startsWith("acquiring.redis.")) {
                    return MeterFilterReply.NEUTRAL;
                }
                boolean containsForbiddenTag = id.getTags().stream()
                        .map(tag -> normalizeTagKey(tag.getKey()))
                        .anyMatch(FORBIDDEN_TAG_KEYS::contains);
                return containsForbiddenTag ? MeterFilterReply.DENY : MeterFilterReply.NEUTRAL;
            }
        };
    }

    /**
     * 将标签名归一化为小写字母和数字，统一识别常见命名风格。
     *
     * @param tagKey Micrometer 原始标签名
     * @return 用于敏感标签门禁比较的归一化名称
     */
    private static String normalizeTagKey(String tagKey) {
        if (tagKey == null) {
            return "";
        }
        return tagKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
