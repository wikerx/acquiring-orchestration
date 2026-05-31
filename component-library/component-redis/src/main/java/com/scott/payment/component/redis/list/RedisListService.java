package com.scott.payment.component.redis.list;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisListService
 * @date : 2026-05-31 21:56
 * @email : scott_x@163.com
 * @description : Redis List 数据结构服务
 * @status : create
 */
public interface RedisListService {

    /**
     * 从左侧压入元素。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @return 压入后的列表长度
     */
    long leftPush(String key, Object value);

    /**
     * 从右侧压入元素。
     *
     * @param key   Redis Key
     * @param value 元素值
     * @return 压入后的列表长度
     */
    long rightPush(String key, Object value);

    /**
     * 从左侧批量压入元素。
     *
     * @param key    Redis Key
     * @param values 元素集合
     * @return 压入后的列表长度
     */
    long leftPushAll(String key, Collection<?> values);

    /**
     * 从右侧批量压入元素。
     *
     * @param key    Redis Key
     * @param values 元素集合
     * @return 压入后的列表长度
     */
    long rightPushAll(String key, Collection<?> values);

    /**
     * 获取指定范围内的列表元素。
     *
     * @param key   Redis Key
     * @param start 起始下标
     * @param end   结束下标，-1 表示末尾
     * @return 列表元素
     */
    List<Object> range(String key, long start, long end);

    /**
     * 按下标获取列表元素。
     *
     * @param key   Redis Key
     * @param index 元素下标
     * @return 元素值；不存在时返回 null
     */
    Object index(String key, long index);

    /**
     * 从左侧弹出元素。
     *
     * @param key Redis Key
     * @return 元素值；不存在时返回 null
     */
    Object leftPop(String key);

    /**
     * 从右侧弹出元素。
     *
     * @param key Redis Key
     * @return 元素值；不存在时返回 null
     */
    Object rightPop(String key);

    /**
     * 获取列表长度。
     *
     * @param key Redis Key
     * @return 列表长度
     */
    long size(String key);

    /**
     * 按下标更新列表元素。
     *
     * @param key   Redis Key
     * @param index 元素下标
     * @param value 新值
     */
    void set(String key, long index, Object value);

    /**
     * 删除指定元素。
     *
     * @param key   Redis Key
     * @param count 删除数量，0 表示全部匹配元素
     * @param value 元素值
     * @return 删除数量
     */
    long remove(String key, long count, Object value);

    /**
     * 设置列表 Key 过期时间。
     *
     * @param key Redis Key
     * @param ttl 过期时间
     * @return 是否设置成功
     */
    boolean expire(String key, Duration ttl);
}
