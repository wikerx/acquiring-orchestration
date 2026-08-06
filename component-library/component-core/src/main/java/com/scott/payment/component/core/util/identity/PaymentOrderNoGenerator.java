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
 * @description : 支付平台订单号生成工具，按 UTC+8 毫秒时间生成可解析的交易标识和内部业务编号。
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
     * 格式：业务前缀 + UTC+8 时间戳 + 四位序列，例如 OP202605312040001230001。
     * 对外可见的平台 transactionId 必须使用 {@link #nextTransactionId(LocalDateTime)}，
     * 不携带 TX 等内部业务前缀。
     * 生产高并发环境如果要求跨机房严格单调，可在此工具基础上替换为 Redis/号段/雪花算法实现。
     *
     * @param businessPrefix 业务前缀，例如 PA 表示收单支付，PO 表示代付
     * @return 支付平台内部订单号
     */
    public static String nextOrderNo(String businessPrefix) {
        return nextOrderNo(businessPrefix, Clock.system(PAYMENT_ZONE_ID));
    }

    /**
     * 按指定业务时间生成支付平台内部订单号。
     * <p>
     * 时间片用于排序、审计和缺少显式上下文的内部恢复；在线查询仍必须显式传递
     * transaction_date_time，不以编号解析代替分片键。
     *
     * @param businessPrefix   业务前缀，例如 OP 表示内部交易关联动作
     * @param businessDateTime 业务时间，对应交易表 transaction_date_time
     * @return 支付平台内部订单号
     */
    public static String nextOrderNo(String businessPrefix, LocalDateTime businessDateTime) {
        Objects.requireNonNull(businessDateTime, "businessDateTime can not be null");
        String prefix = normalizePrefix(businessPrefix);
        int sequence = SEQUENCE.updateAndGet(current -> current >= MAX_SEQUENCE ? 0 : current + 1);
        return prefix + businessDateTime.format(ORDER_TIME_FORMATTER) + String.format("%04d", sequence);
    }

    /**
     * 按指定业务时间生成无业务前缀的平台交易 ID。
     * <p>
     * 对外可见的收单 transactionId 不携带 TX 等内部前缀，但仍保留
     * yyyyMMddHHmmssSSS 时间片，供排序、审计和内部异常恢复使用。
     *
     * @param businessDateTime 业务时间，对应交易表 transaction_date_time
     * @return 无前缀平台交易 ID
     */
    public static String nextTransactionId(LocalDateTime businessDateTime) {
        Objects.requireNonNull(businessDateTime, "businessDateTime can not be null");
        int sequence = SEQUENCE.updateAndGet(current -> current >= MAX_SEQUENCE ? 0 : current + 1);
        return businessDateTime.format(ORDER_TIME_FORMATTER) + String.format("%04d", sequence);
    }

    /**
     * 按当前支付业务时区生成无业务前缀的平台交易 ID。
     *
     * @return 无前缀平台交易 ID
     */
    public static String nextTransactionId() {
        return nextTransactionId(LocalDateTime.now(Clock.system(PAYMENT_ZONE_ID)));
    }

    /**
     * 按指定时钟生成支付平台内部订单号，主要用于单元测试固定时间。
     *
     * @param businessPrefix 业务前缀，例如 PA 表示收单支付，PO 表示代付
     * @param clock          业务时钟
     * @return 支付平台内部订单号
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
