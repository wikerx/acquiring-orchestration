package com.scott.payment.component.redis.dedup.impl;

import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.dedup.RedisDeduplicationService;
import com.scott.payment.component.redis.support.RedisKeySupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisDeduplicationServiceImpl
 * @date : 2026-05-31 22:10
 * @email : scott_x@163.com
 * @description : Redis 去重服务实现
 * @status : create
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisDeduplicationServiceImpl implements RedisDeduplicationService {

    /**
     * 支付业务统一时区。
     */
    private static final ZoneId PAYMENT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 日期格式，用于按天隔离去重集合。
     */
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    /**
     * 生成唯一值时的最大重试次数。
     */
    private static final int MAX_RETRY_TIMES = 5;

    /**
     * 收单参考号前缀。
     */
    private static final String ARN_PREFIX = "ARN";

    /**
     * 文件序号最大值。
     */
    private static final long MAX_FILE_ID = 999L;

    /**
     * 文件序号最小值。
     */
    private static final long MIN_FILE_ID = 1L;

    /**
     * 三位文件序号格式。
     */
    private static final String FILE_ID_FORMAT = "%03d";

    /**
     * Spring 字符串 Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 提供环境隔离 Key 构造能力；消息业务键在进入 Key 前必须先转换为 SHA-256 摘要。
     */
    private final PaymentRedisProperties redisProperties;

    /**
     * 创建 Redis 去重服务实现。
     *
     * @param stringRedisTemplate Spring 字符串 Redis 模板
     */
    public RedisDeduplicationServiceImpl(StringRedisTemplate stringRedisTemplate,
                                         PaymentRedisProperties redisProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisProperties = redisProperties;
    }

    /**
     * 检查并写入去重集合。
     *
     * @param setKey Redis Set Key
     * @param value  待去重值
     * @param ttl    集合过期时间
     * @return true 表示重复；false 表示首次出现
     */
    @Override
    public boolean checkAndAdd(String setKey, String value, Duration ttl) {
        RedisKeySupport.requireKey(setKey);
        RedisKeySupport.requireKey(value);
        Long added = stringRedisTemplate.opsForSet().add(setKey, value);
        boolean firstSeen = added != null && added > 0L;
        if (firstSeen && RedisKeySupport.hasTtl(ttl)) {
            stringRedisTemplate.expire(setKey, ttl);
        }
        return !firstSeen;
    }

    /**
     * 生成唯一收单参考号。
     *
     * @param merchantId 商户号
     * @param ttl        去重集合过期时间
     * @return 唯一收单参考号
     */
    @Override
    public String nextUniqueArn(String merchantId, Duration ttl) {
        RedisKeySupport.requireKey(merchantId);
        String setKey = redisProperties.key("dedup", "arn", today(), merchantId);
        for (int index = 0; index < MAX_RETRY_TIMES; index++) {
            String arn = PaymentOrderNoGenerator.nextOrderNo(ARN_PREFIX);
            if (!checkAndAdd(setKey, arn, ttl)) {
                return arn;
            }
        }
        throw new IllegalStateException("unique arn can not be generated");
    }

    /**
     * 生成每日商户维度三位文件序号。
     *
     * @param merchantId 商户号
     * @param ttl        计数器过期时间
     * @return 三位文件序号
     */
    @Override
    public String nextDailyFileId(String merchantId, Duration ttl) {
        RedisKeySupport.requireKey(merchantId);
        RedisKeySupport.requirePositiveTtl(ttl);
        String counterKey = redisProperties.key("dedup", "file", today(), merchantId);
        Long fileId = stringRedisTemplate.opsForValue().increment(counterKey);
        if (fileId != null && fileId == MIN_FILE_ID) {
            stringRedisTemplate.expire(counterKey, ttl);
        }
        if (fileId != null && fileId > MAX_FILE_ID) {
            stringRedisTemplate.opsForValue().set(counterKey, String.valueOf(MIN_FILE_ID), ttl);
            fileId = MIN_FILE_ID;
        }
        return String.format(FILE_ID_FORMAT, fileId == null ? MIN_FILE_ID : fileId);
    }

    /**
     * 获取当前业务日期。
     *
     * @return 当前业务日期
     */
    private String today() {
        return LocalDate.now(PAYMENT_ZONE_ID).format(DAY_FORMATTER);
    }
}
