package com.scott.payment.openapi.security;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisSerializerBusinessCompatibilityTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Redis Serializer v2 与历史格式对真实商户缓存 DTO 的双向兼容性。
 * @status : create
 */
class PaymentRedisSerializerBusinessCompatibilityTests {

    @Test
    void shouldKeepMerchantRuntimeProfileCompatibleWithHistoricalValues() {
        MerchantRuntimeProfile source = new MerchantRuntimeProfile();
        source.setId(1001L);
        source.setMerchantId("200045");
        source.setMerchantStatus(1);
        source.setMerchantCategoryCode("5734");
        source.setCountryCode("CHN");
        source.setSettlementCurrency("CNY");
        source.setTimezone("Asia/Shanghai");
        source.setRiskLevel(2);

        assertBidirectionalCompatibility(source);
    }

    @Test
    void shouldKeepMerchantOpenApiAccessPolicyCompatibleWithHistoricalValues() {
        MerchantOpenApiAccessPolicy source = new MerchantOpenApiAccessPolicy();
        source.setAllowedIps(new LinkedHashSet<>(Set.of("192.0.2.10", "198.51.100.20")));

        assertBidirectionalCompatibility(source);
    }

    @Test
    void shouldAcceptOnlyKnownLegacyFieldsInMerchantOpenApiAccessPolicy() throws Exception {
        RedisSerializer<Object> serializer = PaymentRedisSerializerFactory.create();
        MerchantOpenApiAccessPolicy source = new MerchantOpenApiAccessPolicy();
        source.setAllowedIps(new LinkedHashSet<>(Set.of("192.0.2.10", "198.51.100.20")));
        ObjectMapper jsonMapper = new ObjectMapper();
        ObjectNode historicalValue = (ObjectNode) jsonMapper.readTree(serializer.serialize(source));
        historicalValue.put("configured", true);
        historicalValue.put("whitelistEnabled", true);

        Object restored = serializer.deserialize(jsonMapper.writeValueAsBytes(historicalValue));

        assertThat(restored).isInstanceOf(MerchantOpenApiAccessPolicy.class);
        assertThat(((MerchantOpenApiAccessPolicy) restored).getAllowedIps())
                .containsExactlyInAnyOrder("192.0.2.10", "198.51.100.20");
        ObjectNode currentValue = (ObjectNode) jsonMapper.readTree(serializer.serialize(restored));
        assertThat(currentValue.has("configured")).isFalse();
        assertThat(currentValue.has("whitelistEnabled")).isFalse();

        ObjectNode unknownValue = historicalValue.deepCopy();
        unknownValue.put("unexpectedSecuritySwitch", true);
        assertThatThrownBy(() -> serializer.deserialize(jsonMapper.writeValueAsBytes(unknownValue)))
                .isInstanceOf(SerializationException.class);
    }

    @Test
    void shouldRejectUnregisteredNestedTypeInMerchantPolicy() {
        MerchantOpenApiAccessPolicy source = new MerchantOpenApiAccessPolicy();
        source.setAllowedIps(new TreeSet<>(Set.of("192.0.2.10")));

        assertThatThrownBy(() -> PaymentRedisSerializerFactory.create().serialize(source))
                .isInstanceOf(SerializationException.class);
    }

    private void assertBidirectionalCompatibility(Object source) {
        GenericJackson2JsonRedisSerializer historicalSerializer = historicalSerializer();
        RedisSerializer<Object> serializerV2 = PaymentRedisSerializerFactory.create();

        Object historicalValue = serializerV2.deserialize(historicalSerializer.serialize(source));
        Object rollingUpgradeValue = historicalSerializer.deserialize(serializerV2.serialize(source));

        assertThat(historicalValue)
                .isInstanceOf(source.getClass())
                .usingRecursiveComparison()
                .isEqualTo(source);
        assertThat(rollingUpgradeValue)
                .isInstanceOf(source.getClass())
                .usingRecursiveComparison()
                .isEqualTo(source);
    }

    private GenericJackson2JsonRedisSerializer historicalSerializer() {
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
}
