package com.scott.payment.component.redis.idempotent.impl;

import com.scott.payment.component.redis.idempotent.IdempotentService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisIdempotentServiceImpl
 * @date : 2026-05-31 20:47
 * @email : scott_x@163.com
 * @description : Redis 幂等控制服务实现
 * @status : create
 */
@Service
public class RedisIdempotentServiceImpl implements IdempotentService {

    /**
     * 幂等占位值，业务只关心键是否存在，不依赖值内容。
     */
    private static final String IDEMPOTENT_VALUE = "1";

    /**
     * Spring 字符串 Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建 Redis 幂等控制服务。
     *
     * @param stringRedisTemplate Spring 字符串 Redis 模板
     */
    public RedisIdempotentServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获取幂等处理权。
     *
     * @param idempotentKey 幂等业务键
     * @param ttlSeconds    幂等有效期，单位秒
     * @return 是否获取成功
     */
    @Override
    public boolean acquire(String idempotentKey, long ttlSeconds) {
        if (!StringUtils.hasText(idempotentKey)) {
            throw new IllegalArgumentException("idempotent key can not be blank");
        }
        if (ttlSeconds <= 0) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, IDEMPOTENT_VALUE, Duration.ofSeconds(ttlSeconds)));
    }
}
