package com.scott.payment.component.redis.lock.impl;

import com.scott.payment.component.redis.lock.RedisLockService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisLockServiceImpl
 * @date : 2026-05-31 20:48
 * @email : scott_x@163.com
 * @description : Redis 分布式锁服务实现
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisLockServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis Lock Service Impl，位于 component-library/component-redis 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisLockServiceImpl implements RedisLockService {

    /**
     * 原子释放锁脚本，只有 value 匹配时才删除锁。
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    /**
     * Spring 字符串 Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建 Redis 分布式锁服务。
     *
     * @param stringRedisTemplate Spring 字符串 Redis 模板
     */
    public RedisLockServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取 Redis 分布式锁。
     *
     * @param key        锁键
     * @param value      锁值
     * @param ttlSeconds 锁过期时间，单位秒
     * @return 是否获取成功
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param ttlSeconds 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public boolean tryLock(String key, String value, long ttlSeconds) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value) || ttlSeconds <= 0) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds)));
    }

    /**
     * 释放 Redis 分布式锁。
     *
     * @param key   锁键
     * @param value 锁值
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void unlock(String key, String value) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(key), value);
    }
}
