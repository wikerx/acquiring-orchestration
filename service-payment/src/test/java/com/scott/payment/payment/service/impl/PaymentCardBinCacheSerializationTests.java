package com.scott.payment.payment.service.impl;

import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import com.scott.payment.payment.model.PaymentCardBinCacheEntry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;

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
