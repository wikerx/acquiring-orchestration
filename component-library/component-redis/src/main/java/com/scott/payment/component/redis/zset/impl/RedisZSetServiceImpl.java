package com.scott.payment.component.redis.zset.impl;

import com.scott.payment.component.redis.support.RedisKeySupport;
import com.scott.payment.component.redis.zset.RedisZSetService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisZSetServiceImpl
 * @date : 2026-05-31 22:03
 * @email : scott_x@163.com
 * @description : Redis ZSet 有序集合服务实现
 * @status : create
 */
@Service
@ConditionalOnBean(RedisTemplate.class)
public class RedisZSetServiceImpl implements RedisZSetService {

    /**
     * RedisTemplate，ZSet Value 使用统一 JSON 序列化。
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 Redis ZSet 服务实现。
     *
     * @param redisTemplate RedisTemplate
     */
    public RedisZSetServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 添加有序集合元素。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @param score 分数
     * @return 是否新增成功
     */
    @Override
    public boolean add(String key, Object value, double score) {
        RedisKeySupport.requireKey(key);
        return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(key, value, score));
    }

    /**
     * 添加有序集合元素并设置过期时间。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @param score 分数
     * @param ttl   过期时间
     * @return 是否新增成功
     */
    @Override
    public boolean add(String key, Object value, double score, Duration ttl) {
        boolean added = add(key, value, score);
        if (added) {
            RedisKeySupport.requirePositiveTtl(ttl);
            redisTemplate.expire(key, ttl);
        }
        return added;
    }

    /**
     * 批量添加有序集合元素。
     *
     * @param key    Redis Key
     * @param tuples 元素与分数集合
     * @return 新增数量
     */
    @Override
    public long add(String key, Collection<ZSetOperations.TypedTuple<Object>> tuples) {
        RedisKeySupport.requireKey(key);
        if (tuples == null || tuples.isEmpty()) {
            return 0L;
        }
        Long added = redisTemplate.opsForZSet().add(key, Set.copyOf(tuples));
        return added == null ? 0L : added;
    }

    /**
     * 按排名范围查询元素，分数从小到大。
     *
     * @param key   Redis Key
     * @param start 起始排名
     * @param end   结束排名
     * @return 元素集合
     */
    @Override
    public Set<Object> range(String key, long start, long end) {
        if (!RedisKeySupport.hasKey(key)) {
            return Collections.emptySet();
        }
        Set<Object> values = redisTemplate.opsForZSet().range(key, start, end);
        return values == null ? Collections.emptySet() : values;
    }

    /**
     * 按分数范围查询元素，分数从小到大。
     *
     * @param key      Redis Key
     * @param minScore 最小分数
     * @param maxScore 最大分数
     * @return 元素集合
     */
    @Override
    public Set<Object> rangeByScore(String key, double minScore, double maxScore) {
        if (!RedisKeySupport.hasKey(key)) {
            return Collections.emptySet();
        }
        Set<Object> values = redisTemplate.opsForZSet().rangeByScore(key, minScore, maxScore);
        return values == null ? Collections.emptySet() : values;
    }

    /**
     * 按分数范围分页查询元素。
     *
     * @param key      Redis Key
     * @param minScore 最小分数
     * @param maxScore 最大分数
     * @param offset   偏移量
     * @param count    查询数量
     * @return 元素集合
     */
    @Override
    public Set<Object> rangeByScore(String key, double minScore, double maxScore, long offset, long count) {
        if (!RedisKeySupport.hasKey(key)) {
            return Collections.emptySet();
        }
        Set<Object> values = redisTemplate.opsForZSet().rangeByScore(key, minScore, maxScore, offset, count);
        return values == null ? Collections.emptySet() : values;
    }

    /**
     * 获取元素分数。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @return 分数；不存在时返回 null
     */
    @Override
    public Double score(String key, Object value) {
        if (!RedisKeySupport.hasKey(key)) {
            return null;
        }
        return redisTemplate.opsForZSet().score(key, value);
    }

    /**
     * 获取有序集合大小。
     *
     * @param key Redis Key
     * @return 集合大小
     */
    @Override
    public long size(String key) {
        if (!RedisKeySupport.hasKey(key)) {
            return 0L;
        }
        Long size = redisTemplate.opsForZSet().size(key);
        return size == null ? 0L : size;
    }

    /**
     * 统计分数范围内的元素数量。
     *
     * @param key      Redis Key
     * @param minScore 最小分数
     * @param maxScore 最大分数
     * @return 元素数量
     */
    @Override
    public long count(String key, double minScore, double maxScore) {
        if (!RedisKeySupport.hasKey(key)) {
            return 0L;
        }
        Long count = redisTemplate.opsForZSet().count(key, minScore, maxScore);
        return count == null ? 0L : count;
    }

    /**
     * 获取元素正序排名。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @return 排名；不存在时返回 null
     */
    @Override
    public Long rank(String key, Object value) {
        if (!RedisKeySupport.hasKey(key)) {
            return null;
        }
        return redisTemplate.opsForZSet().rank(key, value);
    }

    /**
     * 获取元素倒序排名。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @return 排名；不存在时返回 null
     */
    @Override
    public Long reverseRank(String key, Object value) {
        if (!RedisKeySupport.hasKey(key)) {
            return null;
        }
        return redisTemplate.opsForZSet().reverseRank(key, value);
    }

    /**
     * 给元素增加分数。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @param delta 分数增量
     * @return 增加后的分数
     */
    @Override
    public Double incrementScore(String key, Object value, double delta) {
        RedisKeySupport.requireKey(key);
        return redisTemplate.opsForZSet().incrementScore(key, value, delta);
    }

    /**
     * 移除指定元素。
     *
     * @param key    Redis Key
     * @param values 元素值
     * @return 移除数量
     */
    @Override
    public long remove(String key, Object... values) {
        if (!RedisKeySupport.hasKey(key) || values == null || values.length == 0) {
            return 0L;
        }
        Long removed = redisTemplate.opsForZSet().remove(key, values);
        return removed == null ? 0L : removed;
    }

    /**
     * 按排名范围移除元素。
     *
     * @param key   Redis Key
     * @param start 起始排名
     * @param end   结束排名
     * @return 移除数量
     */
    @Override
    public long removeRange(String key, long start, long end) {
        if (!RedisKeySupport.hasKey(key)) {
            return 0L;
        }
        Long removed = redisTemplate.opsForZSet().removeRange(key, start, end);
        return removed == null ? 0L : removed;
    }

    /**
     * 按分数范围移除元素。
     *
     * @param key      Redis Key
     * @param minScore 最小分数
     * @param maxScore 最大分数
     * @return 移除数量
     */
    @Override
    public long removeRangeByScore(String key, double minScore, double maxScore) {
        if (!RedisKeySupport.hasKey(key)) {
            return 0L;
        }
        Long removed = redisTemplate.opsForZSet().removeRangeByScore(key, minScore, maxScore);
        return removed == null ? 0L : removed;
    }
}
