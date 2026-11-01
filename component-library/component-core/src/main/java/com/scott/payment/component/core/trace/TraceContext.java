package com.scott.payment.component.core.trace;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 线程级链路追踪上下文，用于在网关、OpenAPI、支付服务和日志切面之间传递 traceId。
 */
public final class TraceContext {

    /**
     * 链路追踪请求头名称，网关、服务和日志 MDC 可使用该字段串联一次请求的全链路日志。
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 日志 MDC 中的 traceId 字段名，需与 logback-spring.xml 中的 %X{traceId} 保持一致。
     */
    public static final String MDC_TRACE_ID_KEY = "traceId";


    private static final int TRACE_ID_LENGTH = 32;


    private static final int MAX_ACCEPTED_TRACE_ID_LENGTH = 64;


    private static final char[] HEX = "0123456789abcdef".toCharArray();


    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 当前线程的 traceId 存储，适用于 Servlet 同步请求链路，线程复用前必须调用 clear 清理。
     */
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    /**
     * 禁止实例化线程级链路追踪上下文工具类。
     */
    private TraceContext() {
    }

    /**
     * 写入当前线程的链路追踪号。
     *
     * @param traceId 链路追踪号
     */
    public static void setTraceId(String traceId) {
        if (!hasText(traceId)) {
            clear();
            return;
        }
        TRACE_ID.set(traceId);
        MDC.put(MDC_TRACE_ID_KEY, traceId);
    }

    /**
     * 获取当前线程的链路追踪号。
     *
     * @return 链路追踪号，未设置时返回 null
     */
    public static String getTraceId() {
        return TRACE_ID.get();
    }

    /**
     * 获取当前 traceId，没有绑定时生成新的 traceId 并写入当前线程和 MDC。
     *
     * @return 当前线程可用的 traceId
     */
    public static String getOrCreateTraceId() {
        String traceId = getTraceId();
        if (hasText(traceId)) {
            return traceId;
        }
        traceId = newTraceId();
        setTraceId(traceId);
        return traceId;
    }

    /**
     * 判断外部传入的 traceId 是否可作为内部链路号继续使用。
     *
     * @param traceId 外部请求头中的 traceId
     * @return 合法时返回 true
     */
    public static boolean isValidTraceId(String traceId) {
        if (!hasText(traceId)) {
            return false;
        }
        String trimmed = traceId.trim();
        if (trimmed.length() > MAX_ACCEPTED_TRACE_ID_LENGTH) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            boolean legal = ch >= '0' && ch <= '9'
                    || ch >= 'a' && ch <= 'z'
                    || ch >= 'A' && ch <= 'Z'
                    || ch == '-' || ch == '_';
            if (!legal) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从外部候选值解析 traceId，非法或缺失时生成新的内部 traceId。
     *
     * @param candidate 外部候选 traceId
     * @return 可绑定到内部链路的 traceId
     */
    public static String resolveOrCreate(String candidate) {
        if (isValidTraceId(candidate)) {
            return candidate.trim();
        }
        return newTraceId();
    }

    /**
     * 生成 32 位十六进制 traceId，不携带业务含义和敏感信息。
     *
     * @return traceId
     */
    public static String newTraceId() {
        byte[] bytes = new byte[TRACE_ID_LENGTH / 2];
        RANDOM.nextBytes(bytes);
        char[] chars = new char[TRACE_ID_LENGTH];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(chars).toLowerCase(Locale.ROOT);
    }

    /**
     * 清理当前线程链路追踪号，避免线程池复用时串号。
     */
    public static void clear() {
        TRACE_ID.remove();
        MDC.remove(MDC_TRACE_ID_KEY);
    }

    /**
     * 判断链路号候选值是否包含非空白字符。
     *
     * @param value 待检查文本
     * @return 包含有效字符时返回 true
     */
    private static boolean hasText(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
