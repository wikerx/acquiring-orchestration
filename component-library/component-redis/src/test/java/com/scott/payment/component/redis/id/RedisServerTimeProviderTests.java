package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisServerTimeProviderTests {

    @Test
    void currentTimeMillisShouldReturnRedisTime() {
        RedisServerTimeProvider provider = new RedisServerTimeProvider(new TimeStringRedisTemplate(1782295818123L, null));

        assertThat(provider.currentTimeMillis()).isEqualTo(1782295818123L);
    }

    @Test
    void currentTimeMillisShouldThrowWhenRedisTimeIsNull() {
        RedisServerTimeProvider provider = new RedisServerTimeProvider(new TimeStringRedisTemplate(null, null));

        assertThatThrownBy(provider::currentTimeMillis)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Redis TIME 获取失败");
    }

    @Test
    void currentTimeMillisShouldKeepCauseWhenRedisFails() {
        RedisServerTimeProvider provider = new RedisServerTimeProvider(
                new TimeStringRedisTemplate(null, new IllegalStateException("redis down"))
        );

        assertThatThrownBy(provider::currentTimeMillis)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Redis TIME 获取失败")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private static class TimeStringRedisTemplate extends StringRedisTemplate {

        private final Long currentMillis;

        private final RuntimeException failure;

        TimeStringRedisTemplate(Long currentMillis, RuntimeException failure) {
            this.currentMillis = currentMillis;
            this.failure = failure;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisCallback<T> action) {
            if (failure != null) {
                throw failure;
            }
            return (T) currentMillis;
        }
    }
}
