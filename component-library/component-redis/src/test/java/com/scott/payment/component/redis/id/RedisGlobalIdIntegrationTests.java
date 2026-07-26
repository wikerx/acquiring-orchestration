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

@EnabledIfSystemProperty(named = "global-id.redis.integration.enabled", matches = "true")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisGlobalIdIntegrationTests
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : RedisGlobalIdIntegrationTests 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class RedisGlobalIdIntegrationTests {

    /**
     * connection Factory 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private LettuceConnectionFactory connectionFactory;

    /**
     * string Redis Template 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private StringRedisTemplate stringRedisTemplate;

    /**
     * properties 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
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
        int threadCount = 20;
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
