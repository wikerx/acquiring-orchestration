package com.scott.payment.component.core.trace;

import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceContext
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 线程级链路追踪上下文，用于在网关、OpenAPI、支付服务和日志切面之间传递 traceId。
 * @status : create
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

    /**
     * TRACE ID LENGTH，用于保存 Trace Context 中与 traceIDlength 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与日志 MDC 和 X-Trace-Id 请求头共同串联一次链路。
     * </p>
     */
    private static final int TRACE_ID_LENGTH = 32;

    /**
     * MAX ACCEPTED TRACE ID LENGTH，用于保存 Trace Context 中与 maxacceptedtraceIDlength 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与日志 MDC 和 X-Trace-Id 请求头共同串联一次链路。
     * </p>
     */
    private static final int MAX_ACCEPTED_TRACE_ID_LENGTH = 64;

    /**
     * HEX，用于保存 Trace Context 中与 hex 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * RANDOM，用于保存 Trace Context 中与 random 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 当前线程的 traceId 存储，适用于 Servlet 同步请求链路，线程复用前必须调用 clear 清理。
     */
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

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
     * 判断 has text 条件是否成立，用于控制 Trace Context 的后续分支。
     * <p>
     * 前置条件：调用方已准备 公共组件库 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 条件满足时返回 true，否则返回 false
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
