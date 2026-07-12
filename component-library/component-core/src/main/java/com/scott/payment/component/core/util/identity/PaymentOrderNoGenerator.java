package com.scott.payment.component.core.util.identity;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentOrderNoGenerator
 * @date : 2026-05-31 20:40
 * @email : scott_x@163.com
 * @description : 支付订单号生成工具
 * @status : create
 */
public final class PaymentOrderNoGenerator {

    /**
     * 支付系统统一业务时区，当前数据库和业务时间统一按 UTC+8 处理。
     */
    private static final ZoneId PAYMENT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 订单号时间格式，精确到毫秒，便于按时间排序和排查请求链路。
     */
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS", Locale.ROOT);

    /**
     * 单 JVM 内的自增序列，避免同一毫秒内高并发生成相同订单号。
     */
    private static final AtomicInteger SEQUENCE = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000));

    /**
     * 序列最大值，超过后回绕，配合毫秒时间和随机启动值保证开发、测试场景足够稳定。
     */
    private static final int MAX_SEQUENCE = 9999;

    private PaymentOrderNoGenerator() {
    }

    /**
     * 生成支付平台内部订单号。
     * <p>
     * 格式：业务前缀 + UTC+8 时间戳 + 四位序列，例如 PA202605312040001230001。
     * 生产高并发环境如果要求跨机房严格单调，可在此工具基础上替换为 Redis/号段/雪花算法实现。
     *
     * @param businessPrefix 业务前缀，例如 PA 表示收单支付，PO 表示代付
     * @return 支付平台内部订单号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param businessPrefix 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String nextOrderNo(String businessPrefix) {
        return nextOrderNo(businessPrefix, Clock.system(PAYMENT_ZONE_ID));
    }

    /**
     * 按指定时钟生成支付平台内部订单号，主要用于单元测试固定时间。
     *
     * @param businessPrefix 业务前缀，例如 PA 表示收单支付，PO 表示代付
     * @param clock          业务时钟
     * @return 支付平台内部订单号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param businessPrefix 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param clock 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String nextOrderNo(String businessPrefix, Clock clock) {
        Objects.requireNonNull(clock, "clock can not be null");
        String prefix = normalizePrefix(businessPrefix);
        String timePart = LocalDateTime.now(clock).format(ORDER_TIME_FORMATTER);
        int sequence = SEQUENCE.updateAndGet(current -> current >= MAX_SEQUENCE ? 0 : current + 1);
        return prefix + timePart + String.format("%04d", sequence);
    }

    /**
     * 标准化业务前缀，避免空前缀或小写前缀造成订单号不可读。
     *
     * @param businessPrefix 原始业务前缀
     * @return 标准化后的业务前缀
     */
    private static String normalizePrefix(String businessPrefix) {
        if (businessPrefix == null || businessPrefix.isBlank()) {
            return "PA";
        }
        return businessPrefix.trim().toUpperCase(Locale.ROOT);
    }
}
