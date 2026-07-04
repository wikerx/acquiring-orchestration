package com.scott.payment.component.redis.identity.impl;

import com.scott.payment.component.redis.constant.RedisKeyConstants;
import com.scott.payment.component.redis.identity.RedisOrderNoGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisOrderNoGeneratorImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis Order No Generator Impl，位于 component-library/component-redis 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param businessPrefix 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
