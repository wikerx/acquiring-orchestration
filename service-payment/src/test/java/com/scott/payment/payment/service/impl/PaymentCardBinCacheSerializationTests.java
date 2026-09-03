package com.scott.payment.payment.service.impl;

import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import com.scott.payment.payment.model.PaymentCardBinCacheEntry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCardBinCacheSerializationTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证卡 BIN 缓存条目通过平台注册序列化器往返后保持类型和业务字段一致
 * @status : create
 */
class PaymentCardBinCacheSerializationTests {

    @Test
    void shouldRoundTripRegisteredCardBinCacheEntry() {
        PaymentCardBinCacheEntry source = new PaymentCardBinCacheEntry();
        source.setCardBinPrefix("51234500000");
        source.setMatched(Boolean.TRUE);
        source.setRangeId(90001L);
        source.setBinLength(6);
        source.setCardBrand("MASTERCARD");
        source.setIssuerCountryAlpha2("AE");
        RedisSerializer<Object> serializer = PaymentRedisSerializerFactory.create();

        Object restored = serializer.deserialize(serializer.serialize(source));

        assertThat(restored).isInstanceOf(PaymentCardBinCacheEntry.class);
        assertThat((PaymentCardBinCacheEntry) restored)
                .usingRecursiveComparison()
                .isEqualTo(source);
    }
}
