package com.scott.payment.component.redis.dedup;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisDeduplicationService
 * @date : 2026-05-31 22:10
 * @email : scott_x@163.com
 * @description : Redis 去重服务
 * @status : create
 */
public interface RedisDeduplicationService {

    /**
     * 检查并写入去重集合。
     *
     * @param setKey Redis Set Key
     * @param value  待去重值
     * @param ttl    集合过期时间
     * @return true 表示重复；false 表示首次出现
     */
    boolean checkAndAdd(String setKey, String value, Duration ttl);

    /**
     * 生成唯一收单参考号。
     *
     * @param merchantId 商户号
     * @param ttl        去重集合过期时间
     * @return 唯一收单参考号
     */
    String nextUniqueArn(String merchantId, Duration ttl);

    /**
     * 生成每日商户维度三位文件序号。
     *
     * @param merchantId 商户号
     * @param ttl        计数器过期时间
     * @return 三位文件序号
     */
    String nextDailyFileId(String merchantId, Duration ttl);
}
