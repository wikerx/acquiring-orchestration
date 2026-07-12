package com.scott.payment.component.redis.set.impl;

import com.scott.payment.component.redis.set.RedisSetService;
import com.scott.payment.component.redis.support.RedisKeySupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisSetServiceImpl
 * @date : 2026-05-31 22:00
 * @email : scott_x@163.com
 * @description : Redis Set 数据结构服务实现
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisSetServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis Set Service Impl，位于 component-library/component-redis 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
@ConditionalOnBean(RedisTemplate.class)
public class RedisSetServiceImpl implements RedisSetService {

    /**
     * RedisTemplate，Set Value 使用统一 JSON 序列化。
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 Redis Set 服务实现。
     *
     * @param redisTemplate RedisTemplate
     */
    public RedisSetServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 添加集合元素。
     *
     * @param key    Redis Key
     * @param values 集合元素
     * @return 新增元素数量
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param values 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public long add(String key, Object... values) {
        RedisKeySupport.requireKey(key);
        if (values == null || values.length == 0) {
            return 0L;
        }
        Long added = redisTemplate.opsForSet().add(key, values);
        return added == null ? 0L : added;
    }

    /**
     * 添加集合元素并设置过期时间。
     *
     * @param key    Redis Key
     * @param ttl    过期时间
     * @param values 集合元素
     * @return 是否设置过期成功
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param ttl 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param values 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public boolean add(String key, Duration ttl, Object... values) {
        add(key, values);
        return expire(key, ttl);
    }

    /**
     * 获取集合所有元素。
     *
     * @param key Redis Key
     * @return 集合元素
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public Set<Object> members(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return Collections.emptySet();
        }
        Set<Object> members = redisTemplate.opsForSet().members(key);
        return members == null ? Collections.emptySet() : members;
    }

    /**
     * 判断元素是否存在。
     *
     * @param key   Redis Key
     * @param value 集合元素
     * @return 是否存在
     */
    /**
     * 判断收单支付条件是否满足，供业务分支或权限控制使用。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public boolean isMember(String key, Object value) {
        return RedisKeySupport.hasKey(key) && Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
    }

    /**
     * 获取集合大小。
     *
     * @param key Redis Key
     * @return 集合大小
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public long size(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return 0L;
        }
        Long size = redisTemplate.opsForSet().size(key);
        return size == null ? 0L : size;
    }

    /**
     * 移除集合元素。
     *
     * @param key    Redis Key
     * @param values 集合元素
     * @return 移除数量
     */
    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param values 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public long remove(String key, Object... values) {
        if (!RedisKeySupport.hasKey(key) || values == null || values.length == 0) {
            return 0L;
        }
        Long removed = redisTemplate.opsForSet().remove(key, values);
        return removed == null ? 0L : removed;
    }

    /**
     * 删除整个集合 Key。
     *
     * @param key Redis Key
     * @return 是否删除成功
     */
    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public boolean delete(String key) {
        return RedisKeySupport.hasKey(key) && Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * 设置集合 Key 过期时间。
     *
     * @param key Redis Key
     * @param ttl 过期时间
     * @return 是否设置成功
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param key 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param ttl 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public boolean expire(String key, Duration ttl) {
        RedisKeySupport.requireKey(key);
        RedisKeySupport.requirePositiveTtl(ttl);
        return Boolean.TRUE.equals(redisTemplate.expire(key, ttl));
    }
}
