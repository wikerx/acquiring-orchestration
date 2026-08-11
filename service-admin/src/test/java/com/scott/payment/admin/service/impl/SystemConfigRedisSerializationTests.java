package com.scott.payment.admin.service.impl;

import com.scott.payment.component.db.systemconfig.model.SystemConfigSnapshot;
import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 跨服务系统参数快照 Redis 安全序列化测试。 */
class SystemConfigRedisSerializationTests {

    /** 统一配置快照必须能够通过登记类型序列化器跨服务往返。 */
    @Test
    void shouldRoundTripRegisteredSystemConfigSnapshot() {
        LocalDateTime now = LocalDateTime.now();
        SystemConfigSnapshot source = new SystemConfigSnapshot(
                1L,
                "系统名称",
                "system.name",
                "Vexra",
                1,
                "system",
                1,
                1,
                0,
                1,
                null,
                "system",
                "admin",
                now,
                now
        );
        RedisSerializer<Object> serializer = PaymentRedisSerializerFactory.create();

        Object restored = serializer.deserialize(serializer.serialize(source));

        assertThat(restored).isEqualTo(source);
    }
}
