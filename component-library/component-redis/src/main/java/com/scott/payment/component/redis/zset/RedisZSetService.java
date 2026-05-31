package com.scott.payment.component.redis.zset;

import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisZSetService
 * @date : 2026-05-31 22:03
 * @email : scott_x@163.com
 * @description : Redis ZSet 有序集合服务
 * @status : create
 */
public interface RedisZSetService {

    /**
     * 添加有序集合元素。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @param score 分数
     * @return 是否新增成功
     */
    boolean add(String key, Object value, double score);

    /**
     * 添加有序集合元素并设置过期时间。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @param score 分数
     * @param ttl   过期时间
     * @return 是否新增成功
     */
    boolean add(String key, Object value, double score, Duration ttl);

    /**
     * 批量添加有序集合元素。
     *
     * @param key    Redis Key
     * @param tuples 元素与分数集合
     * @return 新增数量
     */
    long add(String key, Collection<ZSetOperations.TypedTuple<Object>> tuples);

    /**
     * 按排名范围查询元素，分数从小到大。
     *
     * @param key   Redis Key
     * @param start 起始排名
     * @param end   结束排名
     * @return 元素集合
     */
    Set<Object> range(String key, long start, long end);

    /**
     * 按分数范围查询元素，分数从小到大。
     *
     * @param key      Redis Key
     * @param minScore 最小分数
     * @param maxScore 最大分数
     * @return 元素集合
     */
    Set<Object> rangeByScore(String key, double minScore, double maxScore);

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
    Set<Object> rangeByScore(String key, double minScore, double maxScore, long offset, long count);

    /**
     * 获取元素分数。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @return 分数；不存在时返回 null
     */
    Double score(String key, Object value);

    /**
     * 获取有序集合大小。
     *
     * @param key Redis Key
     * @return 集合大小
     */
    long size(String key);

    /**
     * 统计分数范围内的元素数量。
     *
     * @param key      Redis Key
     * @param minScore 最小分数
     * @param maxScore 最大分数
     * @return 元素数量
     */
    long count(String key, double minScore, double maxScore);

    /**
     * 获取元素正序排名。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @return 排名；不存在时返回 null
     */
    Long rank(String key, Object value);

    /**
     * 获取元素倒序排名。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @return 排名；不存在时返回 null
     */
    Long reverseRank(String key, Object value);

    /**
     * 给元素增加分数。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @param delta 分数增量
     * @return 增加后的分数
     */
    Double incrementScore(String key, Object value, double delta);

    /**
     * 移除指定元素。
     *
     * @param key    Redis Key
     * @param values 元素值
     * @return 移除数量
     */
    long remove(String key, Object... values);

    /**
     * 按排名范围移除元素。
     *
     * @param key   Redis Key
     * @param start 起始排名
     * @param end   结束排名
     * @return 移除数量
     */
    long removeRange(String key, long start, long end);

    /**
     * 按分数范围移除元素。
     *
     * @param key      Redis Key
     * @param minScore 最小分数
     * @param maxScore 最大分数
     * @return 移除数量
     */
    long removeRangeByScore(String key, double minScore, double maxScore);
}
