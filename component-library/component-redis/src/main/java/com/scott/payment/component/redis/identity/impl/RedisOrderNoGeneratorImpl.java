package com.scott.payment.component.redis.identity.impl;

import com.scott.payment.component.redis.constant.RedisKeyConstants;
import com.scott.payment.component.redis.identity.RedisOrderNoGenerator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisOrderNoGeneratorImpl
 * @date : 2026-05-31 20:50
 * @email : scott_x@163.com
 * @description : Redis 分布式订单号生成服务实现
 * @status : create
 */
@Service
public class RedisOrderNoGeneratorImpl implements RedisOrderNoGenerator {

    /**
     * 支付业务统一时区。
     */
    private static final ZoneId PAYMENT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 订单号时间格式，精确到毫秒。
     */
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS", Locale.ROOT);

    /**
     * 序列键保留天数，跨天后自动释放历史键。
     */
    private static final long SEQUENCE_KEY_TTL_DAYS = 2L;

    /**
     * Spring 字符串 Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建 Redis 分布式订单号生成服务。
     *
     * @param stringRedisTemplate Spring 字符串 Redis 模板
     */
    public RedisOrderNoGeneratorImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成分布式支付订单号。
     *
     * @param businessPrefix 业务前缀，例如 PA 表示收单支付，PO 表示代付
     * @return 支付订单号
     */
    @Override
    public String nextOrderNo(String businessPrefix) {
        String prefix = normalizePrefix(businessPrefix);
        LocalDateTime now = LocalDateTime.now(PAYMENT_ZONE_ID);
        String sequenceKey = RedisKeyConstants.ORDER_NO_PREFIX
                + prefix + RedisKeyConstants.SEPARATOR
                + LocalDate.now(PAYMENT_ZONE_ID);
        Long sequence = stringRedisTemplate.opsForValue().increment(sequenceKey);
        if (sequence != null && sequence == 1L) {
            stringRedisTemplate.expire(sequenceKey, SEQUENCE_KEY_TTL_DAYS, TimeUnit.DAYS);
        }
        return prefix + now.format(ORDER_TIME_FORMATTER) + String.format("%06d", sequence == null ? 0 : sequence);
    }

    /**
     * 标准化业务前缀。
     *
     * @param businessPrefix 原始业务前缀
     * @return 标准化后的业务前缀
     */
    private String normalizePrefix(String businessPrefix) {
        if (!StringUtils.hasText(businessPrefix)) {
            return "PA";
        }
        return businessPrefix.trim().toUpperCase(Locale.ROOT);
    }
}
