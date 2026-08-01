package com.scott.payment.component.redis.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.File;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisSerializerFactoryTests
 * @date : 2026-07-29 19:10
 * @email : scott_x@163.com
 * @description : 验证 Redis JSON 序列化器的存量格式兼容和反序列化类型边界
 * @status : create
 */
class PaymentRedisSerializerFactoryTests {

    /**
     * 集中序列化器必须能够读取当前 Redis Serializer 已写入的受信业务类型。
     */
    @Test
    void shouldReadTrustedValueWrittenByCurrentSerializer() {
        GenericJackson2JsonRedisSerializer currentSerializer = currentSerializer();
        RedisSerializer<Object> targetSerializer = PaymentRedisSerializerFactory.create();
        List<Map<String, Object>> source = riskTimeline();

        Object restored = targetSerializer.deserialize(currentSerializer.serialize(source));

        assertThat(restored)
                .isInstanceOf(List.class)
                .usingRecursiveComparison()
                .isEqualTo(source);
    }

    /**
     * 新 Serializer 写出的 Value 必须仍可被历史 Serializer 读取，以支持滚动发布。
     */
    @Test
    void shouldWriteTrustedValueReadableByCurrentSerializer() {
        GenericJackson2JsonRedisSerializer currentSerializer = currentSerializer();
        RedisSerializer<Object> targetSerializer = PaymentRedisSerializerFactory.create();
        List<Map<String, Object>> source = riskTimeline();

        Object restored = currentSerializer.deserialize(targetSerializer.serialize(source));

        assertThat(restored)
                .isInstanceOf(List.class)
                .usingRecursiveComparison()
                .isEqualTo(source);
    }

    /**
     * 平台配置使用的 String 根值必须保持双向兼容。
     */
    @Test
    void shouldKeepPlatformConfigStringCompatible() {
        GenericJackson2JsonRedisSerializer currentSerializer = currentSerializer();
        RedisSerializer<Object> targetSerializer = PaymentRedisSerializerFactory.create();
        String source = "payment.retry.max=3";

        assertThat(targetSerializer.deserialize(currentSerializer.serialize(source))).isEqualTo(source);
        assertThat(currentSerializer.deserialize(targetSerializer.serialize(source))).isEqualTo(source);
    }

    /**
     * 非支付业务包和非基础容器类型不得通过 Redis 多态反序列化边界。
     */
    @Test
    void shouldRejectUntrustedPolymorphicType() {
        GenericJackson2JsonRedisSerializer currentSerializer = currentSerializer();
        RedisSerializer<Object> targetSerializer = PaymentRedisSerializerFactory.create();
        byte[] untrustedValue = currentSerializer.serialize(new File("/tmp/payment-redis"));

        assertThatThrownBy(() -> targetSerializer.deserialize(untrustedValue))
                .isInstanceOf(SerializationException.class);
    }

    /**
     * 位于项目包内但未登记为 Redis Value 的类型也必须拒绝，不能继续信任整个业务包。
     */
    @Test
    void shouldRejectUnregisteredPaymentType() {
        GenericJackson2JsonRedisSerializer currentSerializer = currentSerializer();
        RedisSerializer<Object> targetSerializer = PaymentRedisSerializerFactory.create();
        byte[] untrustedValue = currentSerializer.serialize(new CacheSample("merchant-200045", 2));

        assertThatThrownBy(() -> targetSerializer.deserialize(untrustedValue))
                .isInstanceOf(SerializationException.class);
    }

    /**
     * 新写入不得接受未登记的根对象，避免继续产生任意类名的 Redis Value。
     */
    @Test
    void shouldRejectUnregisteredRootValueOnWrite() {
        RedisSerializer<Object> targetSerializer = PaymentRedisSerializerFactory.create();

        assertThatThrownBy(() -> targetSerializer.serialize(new CacheSample("merchant-200045", 2)))
                .isInstanceOf(SerializationException.class);
    }

    /**
     * 已登记容器中也不得夹带未登记类型，避免兼容旧节点时扩大实例化范围。
     */
    @Test
    void shouldRejectUnregisteredNestedValueOnWrite() {
        RedisSerializer<Object> targetSerializer = PaymentRedisSerializerFactory.create();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("unsupportedTime", MonthDay.of(7, 30));

        assertThatThrownBy(() -> targetSerializer.serialize(value))
                .isInstanceOf(SerializationException.class);
    }

    /**
     * JDK 容器同样必须按实际缓存形态登记，不能信任整个 java.util 包。
     */
    @Test
    void shouldRejectUnregisteredJavaUtilType() {
        GenericJackson2JsonRedisSerializer currentSerializer = currentSerializer();
        RedisSerializer<Object> targetSerializer = PaymentRedisSerializerFactory.create();
        byte[] untrustedValue = currentSerializer.serialize(new PriorityQueue<>(List.of("REVIEW")));

        assertThatThrownBy(() -> targetSerializer.deserialize(untrustedValue))
                .isInstanceOf(SerializationException.class);
    }

    /**
     * 时间类型只登记缓存契约实际使用的集合，不能信任整个 java.time 包。
     */
    @Test
    void shouldRejectUnregisteredJavaTimeType() {
        GenericJackson2JsonRedisSerializer currentSerializer = currentSerializer();
        RedisSerializer<Object> targetSerializer = PaymentRedisSerializerFactory.create();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("unsupportedTime", MonthDay.of(7, 30));
        byte[] untrustedValue = currentSerializer.serialize(value);

        assertThatThrownBy(() -> targetSerializer.deserialize(untrustedValue))
                .isInstanceOf(SerializationException.class);
    }

    /**
     * 金额类型只允许明确登记的 BigDecimal，不能信任整个 java.math 包。
     */
    @Test
    void shouldRejectUnregisteredJavaMathType() {
        GenericJackson2JsonRedisSerializer currentSerializer = currentSerializer();
        RedisSerializer<Object> targetSerializer = PaymentRedisSerializerFactory.create();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("unsupportedNumber", BigInteger.valueOf(1001L));
        byte[] untrustedValue = currentSerializer.serialize(value);

        assertThatThrownBy(() -> targetSerializer.deserialize(untrustedValue))
                .isInstanceOf(SerializationException.class);
    }

    /**
     * RedisTemplate 必须使用集中定义的受控反序列化边界。
     */
    @Test
    void shouldApplyRestrictedSerializerToRedisTemplate() {
        byte[] untrustedValue = currentSerializer().serialize(new File("/tmp/payment-redis"));
        RedisTemplate<String, Object> redisTemplate = new RedisTemplateConfig()
                .redisTemplate(mock(RedisConnectionFactory.class));

        assertThatThrownBy(() -> redisTemplate.getValueSerializer().deserialize(untrustedValue))
                .isInstanceOf(SerializationException.class);
    }

    private GenericJackson2JsonRedisSerializer currentSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    private List<Map<String, Object>> riskTimeline() {
        List<Map<String, Object>> source = new ArrayList<>();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("riskEventId", 1001L);
        event.put("stageOrder", 3);
        event.put("decision", "REVIEW");
        event.put("decisionTime", LocalDateTime.of(2026, 7, 30, 20, 0));
        source.add(event);
        return source;
    }

    static class CacheSample {

        /**
         * 测试用商户号，用于验证缓存对象序列化后租户维度不丢失。
         */
        private String merchantId;

        /**
         * 测试用对象版本，用于验证数值字段往返序列化。
         */
        private int version;

        CacheSample() {
        }

        CacheSample(String merchantId, int version) {
            this.merchantId = merchantId;
            this.version = version;
        }

        /**
         * 返回测试对象的商户号。
         *
         * @return 商户号
         */
        public String getMerchantId() {
            return merchantId;
        }

        /**
         * 设置测试对象的商户号。
         *
         * @param merchantId 商户号
         */
        public void setMerchantId(String merchantId) {
            this.merchantId = merchantId;
        }

        /**
         * 返回测试对象版本。
         *
         * @return 对象版本
         */
        public int getVersion() {
            return version;
        }

        /**
         * 设置测试对象版本。
         *
         * @param version 对象版本
         */
        public void setVersion(int version) {
            this.version = version;
        }
    }
}
