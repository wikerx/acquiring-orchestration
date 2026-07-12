package com.scott.payment.component.redis.identity.impl;

import com.scott.payment.component.redis.constant.RedisKeyConstants;
import com.scott.payment.component.redis.identity.RedisIdentityService;
import com.scott.payment.component.redis.identity.RedisOrderNoGenerator;
import com.scott.payment.component.redis.support.RedisKeySupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisIdentityServiceImpl
 * @date : 2026-05-31 22:08
 * @email : scott_x@163.com
 * @description : Redis 分布式业务标识生成服务实现
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisIdentityServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Redis Identity Service Impl，位于 component-library/component-redis 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisIdentityServiceImpl implements RedisIdentityService {

    /**
     * 支付业务统一时区。
     */
    private static final ZoneId PAYMENT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 日期格式，用于按天隔离 Redis 计数器。
     */
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    /**
     * 6 位系统追踪号最大值。
     */
    private static final long MAX_STAN_VALUE = 999_999L;

    /**
     * 6 位系统追踪号最小值。
     */
    private static final long MIN_STAN_VALUE = 1L;

    /**
     * 6 位数字格式。
     */
    private static final String SIX_DIGIT_FORMAT = "%06d";

    /**
     * Redis 分布式订单号生成器。
     */
    private final RedisOrderNoGenerator redisOrderNoGenerator;

    /**
     * Spring 字符串 Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建 Redis 分布式业务标识生成服务。
     *
     * @param redisOrderNoGenerator Redis 分布式订单号生成器
     * @param stringRedisTemplate   Spring 字符串 Redis 模板
     */
    public RedisIdentityServiceImpl(RedisOrderNoGenerator redisOrderNoGenerator,
                                    StringRedisTemplate stringRedisTemplate) {
        this.redisOrderNoGenerator = redisOrderNoGenerator;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成平台业务标识。
     *
     * @param businessPrefix 业务前缀，例如 PA 表示收单支付
     * @return 平台业务标识
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param businessPrefix 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public String nextIdentityId(String businessPrefix) {
        return redisOrderNoGenerator.nextOrderNo(businessPrefix);
    }

    /**
     * 生成每日递增的 6 位系统追踪号。
     *
     * @param institutionCode 机构号或通道机构编码
     * @return 6 位系统追踪号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param institutionCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public String nextDailyStan(String institutionCode) {
        RedisKeySupport.requireKey(institutionCode);
        String counterKey = buildStanKey(institutionCode);
        Long sequence = stringRedisTemplate.opsForValue().increment(counterKey);
        if (sequence != null && sequence == MIN_STAN_VALUE) {
            stringRedisTemplate.expire(counterKey, ttlToTomorrow());
        }
        if (sequence != null && sequence > MAX_STAN_VALUE) {
            stringRedisTemplate.opsForValue().set(counterKey, String.valueOf(MIN_STAN_VALUE), ttlToTomorrow());
            sequence = MIN_STAN_VALUE;
        }
        return String.format(SIX_DIGIT_FORMAT, sequence == null ? MIN_STAN_VALUE : sequence);
    }

    /**
     * 生成按卡品牌和机构号隔离的每日 6 位系统追踪号。
     *
     * @param cardBrand       卡品牌
     * @param institutionCode 机构号或通道机构编码
     * @return 6 位系统追踪号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param cardBrand 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param institutionCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public String nextDailyStan(String cardBrand, String institutionCode) {
        String businessKey = StringUtils.hasText(cardBrand)
                ? cardBrand.trim().toUpperCase(Locale.ROOT) + RedisKeyConstants.SEPARATOR + institutionCode
                : institutionCode;
        return nextDailyStan(businessKey);
    }

    /**
     * 生成每日递减的 6 位系统追踪号。
     *
     * @param businessKey 业务隔离键
     * @return 6 位系统追踪号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param businessKey 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public String nextDailyDecrementStan(String businessKey) {
        RedisKeySupport.requireKey(businessKey);
        String counterKey = buildStanKey("decrement" + RedisKeyConstants.SEPARATOR + businessKey);
        Boolean initialized = stringRedisTemplate.opsForValue()
                .setIfAbsent(counterKey, String.valueOf(MAX_STAN_VALUE), ttlToTomorrow());
        if (Boolean.TRUE.equals(initialized)) {
            return String.format(SIX_DIGIT_FORMAT, MAX_STAN_VALUE);
        }
        Long sequence = stringRedisTemplate.opsForValue().decrement(counterKey);
        if (sequence == null || sequence < MIN_STAN_VALUE) {
            stringRedisTemplate.opsForValue().set(counterKey, String.valueOf(MAX_STAN_VALUE), ttlToTomorrow());
            sequence = MAX_STAN_VALUE;
        }
        return String.format(SIX_DIGIT_FORMAT, sequence);
    }

    /**
     * 构建系统追踪号 Redis Key。
     *
     * @param businessKey 业务隔离键
     * @return Redis Key
     */
    private String buildStanKey(String businessKey) {
        String day = LocalDate.now(PAYMENT_ZONE_ID).format(DAY_FORMATTER);
        return RedisKeyConstants.STAN_PREFIX + businessKey + RedisKeyConstants.SEPARATOR + day;
    }

    /**
     * 计算到明天零点后的过期时间。
     *
     * @return 到明天零点后的过期时间
     */
    private Duration ttlToTomorrow() {
        LocalDateTime now = LocalDateTime.now(PAYMENT_ZONE_ID);
        LocalDateTime tomorrowStart = LocalDate.now(PAYMENT_ZONE_ID).plusDays(1L).atTime(LocalTime.MIN);
        return Duration.between(now, tomorrowStart).plusMinutes(5L);
    }
}
