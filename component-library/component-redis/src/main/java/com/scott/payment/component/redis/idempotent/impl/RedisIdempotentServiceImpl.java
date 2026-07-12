package com.scott.payment.component.redis.idempotent.impl;

import com.scott.payment.component.redis.idempotent.IdempotentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisIdempotentServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis Idempotent Service Impl，位于 component-library/component-redis 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Slf4j
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
     * 降级告警是否已经输出，避免日志刷屏。
     */
    private volatile boolean warningLogged;

    /**
     * 创建 Redis 幂等控制服务。
     *
     * @param stringRedisTemplateProvider Spring 字符串 Redis 模板提供器
     */
    public RedisIdempotentServiceImpl(ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    /**
     * 获取幂等处理权。
     *
     * @param idempotentKey 幂等业务键
     * @param ttlSeconds    幂等有效期，单位秒
     * @return 是否获取成功
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param idempotentKey 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param ttlSeconds 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public boolean acquire(String idempotentKey, long ttlSeconds) {
        if (!StringUtils.hasText(idempotentKey)) {
            throw new IllegalArgumentException("idempotent key can not be blank");
        }
        if (ttlSeconds <= 0) {
            return false;
        }
        if (stringRedisTemplate == null) {
            logWarningIfNecessary();
            return true;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, IDEMPOTENT_VALUE, Duration.ofSeconds(ttlSeconds)));
    }

    /**
     * Redis 未装配时输出一次性降级告警。
     */
    private void logWarningIfNecessary() {
        if (warningLogged) {
            return;
        }
        synchronized (this) {
            if (warningLogged) {
                return;
            }
            log.warn("StringRedisTemplate 未装配，IdempotentService 已降级为放行模式，当前不会执行真实的 Redis 幂等控制。");
            warningLogged = true;
        }
    }
}
