package com.scott.payment.component.redis.set;

import java.time.Duration;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisSetService
 * @date : 2026-05-31 22:00
 * @email : scott_x@163.com
 * @description : Redis Set 数据结构服务
 * @status : create
 */
public interface RedisSetService {

    /**
     * 添加集合元素。
     *
     * @param key    Redis Key
     * @param values 集合元素
     * @return 新增元素数量
     */
    long add(String key, Object... values);

    /**
     * 添加集合元素并设置过期时间。
     *
     * @param key    Redis Key
     * @param ttl    过期时间
     * @param values 集合元素
     * @return 是否设置过期成功
     */
    boolean add(String key, Duration ttl, Object... values);

    /**
     * 获取集合所有元素。
     *
     * @param key Redis Key
     * @return 集合元素
     */
    Set<Object> members(String key);

    /**
     * 判断元素是否存在。
     *
     * @param key   Redis Key
     * @param value 集合元素
     * @return 是否存在
     */
    boolean isMember(String key, Object value);

    /**
     * 获取集合大小。
     *
     * @param key Redis Key
     * @return 集合大小
     */
    long size(String key);

    /**
     * 移除集合元素。
     *
     * @param key    Redis Key
     * @param values 集合元素
     * @return 移除数量
     */
    long remove(String key, Object... values);

    /**
     * 删除整个集合 Key。
     *
     * @param key Redis Key
     * @return 是否删除成功
     */
    boolean delete(String key);

    /**
     * 设置集合 Key 过期时间。
     *
     * @param key Redis Key
     * @param ttl 过期时间
     * @return 是否设置成功
     */
    boolean expire(String key, Duration ttl);
}
