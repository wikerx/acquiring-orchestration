package com.scott.payment.component.redis.idempotent;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.idempotent.impl.RedisIdempotentServiceImpl;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisIdempotentServiceImplTests
 * @date : 2026-07-30 18:20
 * @email : scott_x@163.com
 * @description : 验证 MQ 去重时间桶、容量降级、摘要保护和失败释放边界，不连接共享 Redis
 * @status : create
 */
@Slf4j
class RedisIdempotentServiceImplTests {

    /**
     * 固定 Redis 服务端时间，单位为 epoch 毫秒，用于稳定计算去重时间桶。
     */
    private static final long REDIS_NOW_MILLIS = 1_782_296_218_123L;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldUseCoLocatedBucketsAndHashedBusinessKey() {
        log.info("测试 MQ 去重 Key，关键输入: test 环境、60 秒 TTL、业务键只写 SHA-256 摘要");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(REDIS_NOW_MILLIS);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        RedisIdempotentServiceImpl service = new RedisIdempotentServiceImpl(
                provider(redisTemplate),
                properties
        );

        IdempotentAcquireResult result = service.acquireMq(
                "risk-audit", "sensitive-long-business-key", 60L);

        long currentBucket = Math.floorDiv(REDIS_NOW_MILLIS, 60_000L);
        List<String> expectedKeys = List.of(
                properties.coLocatedBusinessKey(
                        "mq", "dedup", "risk-audit", "risk-audit", String.valueOf(currentBucket)),
                properties.coLocatedBusinessKey(
                        "mq", "dedup", "risk-audit", "risk-audit", String.valueOf(currentBucket - 1L))
        );
        assertThat(result).isEqualTo(IdempotentAcquireResult.ACQUIRED);
        verify(redisTemplate).execute(
                any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(expectedKeys),
                org.mockito.ArgumentMatchers.eq(RedisKeyDigest.sha256("sensitive-long-business-key")),
                org.mockito.ArgumentMatchers.eq("60000"),
                org.mockito.ArgumentMatchers.eq("100000")
        );
        log.info("MQ 去重 Key 测试完成，结果: ACQUIRED，物理 Key 数量: {}", expectedKeys.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRemoveHashedMemberFromCurrentAndPreviousBuckets() {
        log.info("测试 MQ 失败释放，关键输入: 获取时 TTL 60 秒、当前桶与前一桶");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(REDIS_NOW_MILLIS);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        RedisIdempotentServiceImpl service = new RedisIdempotentServiceImpl(
                provider(redisTemplate),
                properties
        );

        service.releaseMq("admin-operation-log", "ADMIN-LOG-001", 60L);

        verify(zSetOperations, times(2)).remove(
                anyString(), org.mockito.ArgumentMatchers.eq(RedisKeyDigest.sha256("ADMIN-LOG-001")));
        log.info("MQ 失败释放测试完成，结果: 两个候选时间桶均执行摘要移除");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldReturnDuplicateWhenScriptFindsExistingDigest() {
        log.info("测试 MQ 重复消息，关键输入: Lua 返回 0");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(REDIS_NOW_MILLIS);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(0L);
        RedisIdempotentServiceImpl service = new RedisIdempotentServiceImpl(
                provider(redisTemplate), properties());

        IdempotentAcquireResult result = service.acquireMq("risk-audit", "RK-001", 60L);

        assertThat(result).isEqualTo(IdempotentAcquireResult.DUPLICATE);
        log.info("MQ 重复消息测试完成，结果: DUPLICATE");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldFallbackWhenBucketCapacityIsReached() {
        log.info("测试 MQ 去重容量保护，关键输入: Lua 返回 -1");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(REDIS_NOW_MILLIS);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(-1L);
        RedisIdempotentServiceImpl service = new RedisIdempotentServiceImpl(
                provider(redisTemplate), properties());

        IdempotentAcquireResult result = service.acquireMq("risk-audit", "RK-002", 60L);

        assertThat(result).isEqualTo(IdempotentAcquireResult.FALLBACK);
        log.info("MQ 去重容量保护测试完成，结果: FALLBACK 到数据库唯一约束");
    }

    @Test
    void shouldFallbackWhenRedisTemplateIsUnavailable() {
        log.info("测试 MQ 去重组件缺失，关键输入: Spring 容器无 StringRedisTemplate");
        RedisIdempotentServiceImpl service = new RedisIdempotentServiceImpl(
                provider(null), properties());

        IdempotentAcquireResult result = service.acquireMq("admin-operation-log", "LOG-001", 60L);

        assertThat(result).isEqualTo(IdempotentAcquireResult.FALLBACK);
        log.info("MQ 去重组件缺失测试完成，结果: FALLBACK 到数据库唯一约束");
    }

    @Test
    void shouldRejectTtlAboveConfiguredBoundary() {
        log.info("测试 MQ 去重 TTL 门禁，关键输入: 30 天上限外再增加 1 秒");
        PaymentRedisProperties properties = properties();
        RedisIdempotentServiceImpl service = new RedisIdempotentServiceImpl(
                provider(null), properties);

        assertThatThrownBy(() -> service.acquireMq(
                "merchant-operation-log",
                "LOG-002",
                properties.getMqDedup().getMaxTtlSeconds() + 1L
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TTL");
        log.info("MQ 去重 TTL 门禁测试完成，结果: 非法配置在 Redis 调用前被拒绝");
    }

    private PaymentRedisProperties properties() {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        return properties;
    }

    private ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate redisTemplate) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (redisTemplate != null) {
            beanFactory.addBean("stringRedisTemplate", redisTemplate);
        }
        return beanFactory.getBeanProvider(StringRedisTemplate.class);
    }
}
