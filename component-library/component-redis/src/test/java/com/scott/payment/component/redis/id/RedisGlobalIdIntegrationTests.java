package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdConstants;
import com.scott.payment.component.core.id.GlobalIdValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGlobalIdIntegrationTests
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis Global Id Integration Tests，位于 component-library/component-redis 的测试层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@EnabledIfSystemProperty(named = "global-id.redis.integration.enabled", matches = "true")
class RedisGlobalIdIntegrationTests {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private LettuceConnectionFactory connectionFactory;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private RedisGlobalIdProperties properties;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                System.getProperty("global-id.redis.host", "127.0.0.1"),
                Integer.parseInt(System.getProperty("global-id.redis.port", "6379"))
        );
        configuration.setDatabase(Integer.parseInt(System.getProperty("global-id.redis.database", "0")));
        String password = System.getProperty("global-id.redis.password", "");
        if (!password.isBlank()) {
            configuration.setPassword(password);
        }
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();

        properties = new RedisGlobalIdProperties();
        properties.setSeqKeyPrefix("it:{global_id}:seq:");
        properties.setLastMillisKey("it:{global_id}:last_millis");
        properties.setRetrySleepMillis(1L);
        cleanIntegrationKeys();
    }

    @AfterEach
    void tearDown() {
        cleanIntegrationKeys();
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void redisConnectionShouldExposeServerTime() {
        RedisServerTimeProvider timeProvider = new RedisServerTimeProvider(stringRedisTemplate);

        long currentMillis = timeProvider.currentTimeMillis();

        assertThat(currentMillis).isPositive();
    }

    @Test
    void nextIdShouldUseRealRedisAndWriteKeys() {
        RedisGlobalIdGenerator generator = newGenerator();

        String id = generator.nextId();

        assertThat(id).hasSize(GlobalIdConstants.ID_LENGTH);
        assertThat(id).containsOnlyDigits();
        assertThat(GlobalIdValidator.isValid(id)).isTrue();
        assertThat(stringRedisTemplate.hasKey(properties.getLastMillisKey())).isTrue();

        Set<String> sequenceKeys = stringRedisTemplate.keys(properties.getSeqKeyPrefix() + "*");
        assertThat(sequenceKeys).isNotNull().isNotEmpty();
        for (String sequenceKey : sequenceKeys) {
            assertThat(stringRedisTemplate.getExpire(sequenceKey, TimeUnit.SECONDS)).isPositive();
        }
    }

    @Test
    void nextIdShouldNotDuplicateWhenCalledContinuouslyWithRealRedis() {
        RedisGlobalIdGenerator generator = newGenerator();
        Set<String> ids = ConcurrentHashMap.newKeySet();

        for (int index = 0; index < 10_000; index++) {
            String id = generator.nextId();
            assertThat(GlobalIdValidator.isValid(id)).isTrue();
            ids.add(id);
        }

        assertThat(ids).hasSize(10_000);
    }

    @Test
    void nextIdShouldNotDuplicateWhenCalledConcurrentlyWithRealRedis() throws InterruptedException {
        RedisGlobalIdGenerator generator = newGenerator();
        /**
         * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        int threadCount = 20;
        /**
         * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        int perThreadCount = 5_000;
        Set<String> ids = ConcurrentHashMap.newKeySet();
        List<Throwable> failures = new ArrayList<>();
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    for (int index = 0; index < perThreadCount; index++) {
                        String id = generator.nextId();
                        if (!GlobalIdValidator.isValid(id)) {
                            failures.add(new AssertionError("invalid global id: " + id));
                        }
                        ids.add(id);
                    }
                } catch (Throwable throwable) {
                    failures.add(throwable);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        assertThat(finishLatch.await(60, TimeUnit.SECONDS)).isTrue();
        executorService.shutdownNow();
        assertThat(failures).isEmpty();
        assertThat(ids).hasSize(threadCount * perThreadCount);
    }

    @Test
    void nextIdShouldFailWhenFutureMillisSequenceAlreadyOverflowed() {
        long futureMillis = new RedisServerTimeProvider(stringRedisTemplate).currentTimeMillis() + 60_000L;
        String overflowSeqKey = properties.getSeqKeyPrefix() + futureMillis;
        stringRedisTemplate.opsForValue().set(properties.getLastMillisKey(), String.valueOf(futureMillis));
        stringRedisTemplate.opsForValue().set(overflowSeqKey, String.valueOf(properties.getMaxSequence()), Duration.ofMinutes(5L));
        properties.setMaxRetryTimes(0);
        RedisGlobalIdGenerator generator = newGenerator();

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("全局唯一标识序列超过毫秒上限");
    }

    private RedisGlobalIdGenerator newGenerator() {
        return new RedisGlobalIdGenerator(
                stringRedisTemplate,
                new RedisServerTimeProvider(stringRedisTemplate),
                properties
        );
    }

    private void cleanIntegrationKeys() {
        if (stringRedisTemplate == null) {
            return;
        }
        Set<String> keys = stringRedisTemplate.keys("it:{global_id}:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
