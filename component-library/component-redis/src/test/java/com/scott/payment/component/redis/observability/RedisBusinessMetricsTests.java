package com.scott.payment.component.redis.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisBusinessMetricsTests
 * @date : 2026-07-31 10:10
 * @email : scott_x@163.com
 * @description : 验证 Redis 业务指标的计数、耗时、枚举标签和敏感标签拒绝门禁
 * @status : create
 */
class RedisBusinessMetricsTests {

    /**
     * 验证操作、降级和 Lua 失败分别进入预期指标，且标签只来自封闭枚举。
     */
    @Test
    void shouldRecordBoundedRedisBusinessMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RedisBusinessMetrics metrics = new RedisBusinessMetrics(registry);

        metrics.recordOperation(
                RedisBusinessMetrics.Feature.LOCK,
                RedisBusinessMetrics.Operation.ACQUIRE,
                RedisBusinessMetrics.Outcome.SUCCESS,
                Duration.ofMillis(12).toNanos()
        );
        metrics.recordFallback(
                RedisBusinessMetrics.Feature.MQ_DEDUP,
                RedisBusinessMetrics.FallbackReason.CONNECTION_FAILURE
        );
        metrics.recordLuaFailure(
                RedisBusinessMetrics.Script.MQ_DEDUP_ACQUIRE,
                RedisBusinessMetrics.Failure.CONNECTION
        );

        Timer operation = registry.find(RedisBusinessMetrics.OPERATION_DURATION)
                .tags("feature", "lock", "operation", "acquire", "outcome", "success")
                .timer();
        Counter fallback = registry.find(RedisBusinessMetrics.FALLBACK_TOTAL)
                .tags("feature", "mq_dedup", "reason", "connection_failure")
                .counter();
        Counter luaFailure = registry.find(RedisBusinessMetrics.LUA_FAILURE_TOTAL)
                .tags("script", "mq_dedup_acquire", "failure", "connection")
                .counter();

        assertThat(operation).isNotNull();
        assertThat(operation.count()).isEqualTo(1L);
        assertThat(operation.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(12.0d);
        assertThat(fallback).isNotNull();
        assertThat(fallback.count()).isEqualTo(1.0d);
        assertThat(luaFailure).isNotNull();
        assertThat(luaFailure.count()).isEqualTo(1.0d);

        Set<String> tagKeys = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getKey())
                .collect(Collectors.toSet());
        assertThat(tagKeys)
                .containsExactlyInAnyOrder(
                        "feature",
                        "operation",
                        "outcome",
                        "reason",
                        "script",
                        "failure"
                );
    }

    /**
     * 验证 Redis 治理指标一旦携带业务 Key、商户号或订单标识标签就不会注册。
     */
    @Test
    void shouldDenySensitiveOrUnboundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(
                new RedisObservabilityAutoConfiguration().redisSensitiveTagMeterFilter()
        );

        Counter.builder("acquiring.redis.test")
                .tag("merchantId", "200045")
                .register(registry)
                .increment();
        Counter.builder("acquiring.redis.test")
                .tag("businessKey", "raw-order-no")
                .register(registry)
                .increment();
        Counter.builder("acquiring.redis.test")
                .tag("merchant_id", "200045")
                .register(registry)
                .increment();
        Counter.builder("acquiring.redis.test")
                .tag("exception-message", "redis unavailable")
                .register(registry)
                .increment();
        Counter.builder("acquiring.redis.test")
                .tag("feature", "lock")
                .register(registry)
                .increment();

        assertThat(registry.find("acquiring.redis.test").tag("merchantId", "200045").counter())
                .isNull();
        assertThat(registry.find("acquiring.redis.test").tag("businessKey", "raw-order-no").counter())
                .isNull();
        assertThat(registry.find("acquiring.redis.test").tag("merchant_id", "200045").counter())
                .isNull();
        assertThat(registry.find("acquiring.redis.test")
                .tag("exception-message", "redis unavailable")
                .counter()).isNull();
        assertThat(registry.find("acquiring.redis.test").tag("feature", "lock").counter())
                .isNotNull();
    }

    /**
     * 验证 Prometheus Registry 导出的名称与告警规则使用的 snake_case 指标一致。
     */
    @Test
    void shouldExportPrometheusMetricNamesUsedByAlerts() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        RedisBusinessMetrics metrics = new RedisBusinessMetrics(registry);

        metrics.recordOperation(
                RedisBusinessMetrics.Feature.LOCK,
                RedisBusinessMetrics.Operation.ACQUIRE,
                RedisBusinessMetrics.Outcome.ERROR,
                Duration.ofMillis(12).toNanos()
        );
        metrics.recordFallback(
                RedisBusinessMetrics.Feature.MQ_DEDUP,
                RedisBusinessMetrics.FallbackReason.CONNECTION_FAILURE
        );
        metrics.recordLuaFailure(
                RedisBusinessMetrics.Script.MQ_DEDUP_ACQUIRE,
                RedisBusinessMetrics.Failure.CONNECTION
        );

        assertThat(registry.scrape())
                .contains(
                        "acquiring_redis_operation_duration_seconds_count",
                        "acquiring_redis_fallback_total",
                        "acquiring_redis_lua_failure_total",
                        "feature=\"lock\"",
                        "outcome=\"error\""
                );
    }

    /**
     * 验证异常详情只归并为固定分类，不把原始异常文本转换为标签值。
     */
    @Test
    void shouldClassifyFailuresWithoutUsingExceptionTextAsTag() {
        RedisBusinessMetrics metrics = RedisBusinessMetrics.noop();

        assertThat(metrics.classifyFailure(new IllegalStateException("Redis connection refused")))
                .isEqualTo(RedisBusinessMetrics.Failure.CONNECTION);
        assertThat(metrics.classifyFallback(new IllegalStateException("command timed out")))
                .isEqualTo(RedisBusinessMetrics.FallbackReason.TIMEOUT);
        assertThat(metrics.classifyFailure(new IllegalStateException("WRONGTYPE")))
                .isEqualTo(RedisBusinessMetrics.Failure.EXECUTION);
    }
}
