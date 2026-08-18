package com.scott.payment.openapi.security;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.SecurityInterceptAuditMessage;
import com.scott.payment.component.mq.publisher.IndependentReliableMqPublisher;
import com.scott.payment.component.mq.properties.SecurityAuditMqProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SecurityInterceptEventRecorder
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 安全拦截审计生产端，只发布脱敏排查元数据给 service-data，禁止传输请求体、JWT、Cookie、密钥或完整密文
 * @status : create
 */
@Slf4j
@Service
public class SecurityInterceptEventRecorder {

    /** OpenAPI 来源层。 */
    public static final String SOURCE_OPENAPI = "OPENAPI";

    /** 渠道回调来源层。 */
    public static final String SOURCE_CHANNEL = "CHANNEL";

    /** 阻断处置动作。 */
    public static final String ACTION_BLOCK = "BLOCK";

    /** 中风险。 */
    public static final String RISK_MEDIUM = "MEDIUM";

    /** 高风险。 */
    public static final String RISK_HIGH = "HIGH";

    /** 严重风险。 */
    public static final String RISK_CRITICAL = "CRITICAL";

    /** 安全事件生产服务名。 */
    private static final String SERVICE_NAME = "service-openapi";

    /** User-Agent 请求头名。 */
    private static final String HEADER_USER_AGENT = "User-Agent";

    /** 请求级业务标识头名。 */
    private static final String HEADER_REQUEST_ID = "X-Request-Id";

    /** 请求路径最大字符数。 */
    private static final int MAX_PATH_LENGTH = 512;

    /** 普通审计文本最大字符数。 */
    private static final int MAX_TEXT_LENGTH = 512;

    /** 脱敏请求头摘要最大字符数。 */
    private static final int MAX_HEADER_SUMMARY_LENGTH = 1024;

    /** 安全事件号中的毫秒时间格式。 */
    private static final DateTimeFormatter EVENT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** MQ 消息发布器。 */
    private final IndependentReliableMqPublisher mqPublisher;

    /** 安全审计 MQ 开关与消费参数。 */
    private final SecurityAuditMqProperties properties;

    /**
     * 创建安全拦截审计生产端。
     *
     * @param mqPublisher 独立事务可靠消息发布器
     * @param properties 安全审计 MQ 配置
     */
    public SecurityInterceptEventRecorder(IndependentReliableMqPublisher mqPublisher,
                                          SecurityAuditMqProperties properties) {
        this.mqPublisher = mqPublisher;
        this.properties = properties;
    }

    /**
     * 将已被安全链路阻断的请求转换为脱敏审计消息。
     * <p>
     * 审计发布失败不得改变原始安全拦截结果；请求体、认证头原文和密钥材料
     * 不得进入 MQ 消息、Redis 或应用日志。
     * </p>
     *
     * @param request HTTP 请求
     * @param sourceLayer 来源层级
     * @param eventType 事件类型
     * @param riskLevel 风险等级
     * @param merchantId 商户号，无法解析时为空
     * @param hitRuleCode 命中规则编码
     * @param reasonCode 原因码
     * @param reasonMessage 原因说明，发布前必须脱敏和截断
     */
    public void recordBlocked(HttpServletRequest request,
                              String sourceLayer,
                              String eventType,
                              String riskLevel,
                              String merchantId,
                              String hitRuleCode,
                              String reasonCode,
                              String reasonMessage) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            mqPublisher.publish(
                    MqTopic.SECURITY_INTERCEPT_AUDIT,
                    MqTag.SECURITY_INTERCEPT_AUDIT,
                    buildMessage(request, sourceLayer, eventType, riskLevel,
                            merchantId, hitRuleCode, reasonCode, reasonMessage)
            );
        } catch (RuntimeException exception) {
            log.warn("event: SECURITY_AUDIT_PUBLISH_FAILED eventType: {} exceptionType: {}",
                    eventType,
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 从业务异常中提取稳定原因码。
     *
     * @param exception 业务异常
     * @return 业务错误码或异常类型名
     */
    public String reasonCode(Throwable exception) {
        if (exception instanceof ServiceException serviceException) {
            return serviceException.getCode();
        }
        return exception == null ? null : exception.getClass().getSimpleName();
    }

    /**
     * 从异常中提取待脱敏的原因说明。
     *
     * @param exception 异常
     * @return 原始原因说明；仅允许交给 recordBlocked 脱敏后记录
     */
    public String reasonMessage(Throwable exception) {
        return exception == null ? null : exception.getMessage();
    }

    /**
     * 构造不含认证凭据和请求体的审计消息。
     *
     * @return 可安全发布的安全拦截审计消息
     */
    private SecurityInterceptAuditMessage buildMessage(HttpServletRequest request,
                                                       String sourceLayer,
                                                       String eventType,
                                                       String riskLevel,
                                                       String merchantId,
                                                       String hitRuleCode,
                                                       String reasonCode,
                                                       String reasonMessage) {
        LocalDateTime now = LocalDateTime.now();
        SecurityInterceptAuditMessage message = new SecurityInterceptAuditMessage();
        message.setEventNo(generateEventNo(now));
        message.setEventTime(now);
        message.setSourceLayer(defaultIfBlank(sourceLayer, SOURCE_OPENAPI));
        message.setEventType(limit(defaultIfBlank(eventType, "SECURITY_INTERCEPT"), 64));
        message.setRiskLevel(defaultIfBlank(riskLevel, RISK_HIGH));
        message.setAction(ACTION_BLOCK);
        message.setMerchantId(limit(trimToNull(merchantId), 32));
        message.setClientIp(limit(resolveClientIp(request), 45));
        message.setRequestMethod(limit(request == null ? null : request.getMethod(), 16));
        message.setRequestPath(limit(resolveRequestPath(request), MAX_PATH_LENGTH));
        message.setTraceId(limit(resolveTraceId(request), 64));
        message.setRequestId(limit(resolveRequestId(request), 64));
        message.setUserAgent(limit(request == null ? null : request.getHeader(HEADER_USER_AGENT), MAX_TEXT_LENGTH));
        message.setReasonCode(limit(trimToNull(reasonCode), 64));
        message.setReasonMessage(limit(sanitizeText(reasonMessage), MAX_TEXT_LENGTH));
        message.setServiceName(SERVICE_NAME);
        message.setHitRuleCode(limit(trimToNull(hitRuleCode), 64));
        message.setHeaderSummary(limit(buildHeaderSummary(request), MAX_HEADER_SUMMARY_LENGTH));
        return message;
    }

    /** 生成包含毫秒时间与随机后缀的安全事件号。 */
    private String generateEventNo(LocalDateTime now) {
        return "SIE" + EVENT_TIME_FORMATTER.format(now)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /** 优先读取网关写入的可信客户端 IP，未提供时回退到连接地址。 */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String gatewayClientIp = request.getHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP);
        return StringUtils.hasText(gatewayClientIp) ? gatewayClientIp.trim() : request.getRemoteAddr();
    }

    /** 返回不含查询参数的请求路径。 */
    private String resolveRequestPath(HttpServletRequest request) {
        return request == null ? null : request.getRequestURI();
    }

    /** 优先读取请求头 traceId，未提供时使用当前链路上下文。 */
    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request == null ? null : request.getHeader(TraceContext.TRACE_ID_HEADER);
        return StringUtils.hasText(traceId) ? traceId.trim() : TraceContext.getTraceId();
    }

    /** 解析可选的请求级业务标识。 */
    private String resolveRequestId(HttpServletRequest request) {
        return request == null ? null : trimToNull(request.getHeader(HEADER_REQUEST_ID));
    }

    /**
     * 生成请求头存在性与非敏感值摘要。
     * <p>Authorization 只记录是否存在，不读取或序列化原文。</p>
     */
    private String buildHeaderSummary(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("authorizationPresent",
                StringUtils.hasText(request.getHeader("Authorization"))
                        || StringUtils.hasText(request.getHeader("authorization")));
        putIfPresent(summary, "contentType", request.getContentType());
        putIfPresent(summary, "gatewayClientIp",
                request.getHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP));
        putIfPresent(summary, "traceId", request.getHeader(TraceContext.TRACE_ID_HEADER));
        putIfPresent(summary, "requestId", request.getHeader(HEADER_REQUEST_ID));
        putIfPresent(summary, "userAgent", request.getHeader(HEADER_USER_AGENT));
        putIfPresent(summary, "channelTimestampPresent", presentText(request.getHeader("X-Channel-Timestamp")));
        putIfPresent(summary, "channelNoncePresent", presentText(request.getHeader("X-Channel-Nonce")));
        putIfPresent(summary, "channelSignaturePresent", presentText(request.getHeader("X-Channel-Signature")));
        return sanitizeText(JsonUtils.toJsonString(summary));
    }

    /** 有文本时返回 true，缺失时不在摘要中写入字段。 */
    private Boolean presentText(String value) {
        return StringUtils.hasText(value) ? Boolean.TRUE : null;
    }

    /** 只将非空摘要值写入有序结构。 */
    private void putIfPresent(Map<String, Object> summary, String key, Object value) {
        if (value instanceof String text && !StringUtils.hasText(text)) {
            return;
        }
        if (value != null) {
            summary.put(key, value);
        }
    }

    /** 使用公共脱敏规则处理可能含敏感字段的文本。 */
    private String sanitizeText(String value) {
        return StringUtils.hasText(value) ? SensitiveDataMaskUtils.maskJsonSafely(value.trim()) : null;
    }

    /** 将空白文本替换为默认值。 */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    /** 去除文本首尾空白，空白值转换为 null。 */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 按数据库字段上限截断文本，避免审计写入影响安全链路。 */
    private String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
