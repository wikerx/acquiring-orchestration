package com.scott.payment.openapi.security;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.db.security.entity.SecurityInterceptEventDO;
import com.scott.payment.component.db.security.mapper.SecurityInterceptEventMapper;
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
 * @description : 安全拦截事件记录器，位于 service-openapi 安全层，只记录脱敏排查元数据，禁止保存请求体、JWT、Cookie、密钥或完整密文。
 * @status : create
 */
@Slf4j
@Service
public class SecurityInterceptEventRecorder {

    /**
     * OpenAPI 来源层。
     */
    public static final String SOURCE_OPENAPI = "OPENAPI";

    /**
     * 渠道回调来源层。
     */
    public static final String SOURCE_CHANNEL = "CHANNEL";

    /**
     * 阻断动作。
     */
    public static final String ACTION_BLOCK = "BLOCK";

    /**
     * 中风险。
     */
    public static final String RISK_MEDIUM = "MEDIUM";

    /**
     * 高风险。
     */
    public static final String RISK_HIGH = "HIGH";

    /**
     * 严重风险。
     */
    public static final String RISK_CRITICAL = "CRITICAL";

    private static final String SERVICE_NAME = "service-openapi";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final int MAX_PATH_LENGTH = 512;
    private static final int MAX_TEXT_LENGTH = 512;
    private static final int MAX_HEADER_SUMMARY_LENGTH = 1024;
    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final SecurityInterceptEventMapper eventMapper;

    /**
     * 创建安全拦截事件记录器。
     *
     * @param eventMapper 安全拦截事件 Mapper
     */
    public SecurityInterceptEventRecorder(SecurityInterceptEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    /**
     * 记录已被安全链路阻断的请求。
     *
     * @param request       HTTP 请求
     * @param sourceLayer   来源层级
     * @param eventType     事件类型
     * @param riskLevel     风险等级
     * @param merchantId    商户号，无法解析时为空
     * @param hitRuleCode   命中规则编码
     * @param reasonCode    原因码
     * @param reasonMessage 原因说明
     */
    public void recordBlocked(HttpServletRequest request,
                              String sourceLayer,
                              String eventType,
                              String riskLevel,
                              String merchantId,
                              String hitRuleCode,
                              String reasonCode,
                              String reasonMessage) {
        try {
            LocalDateTime now = LocalDateTime.now();
            SecurityInterceptEventDO row = new SecurityInterceptEventDO();
            row.setEventNo(generateEventNo(now));
            row.setEventTime(now);
            row.setSourceLayer(defaultIfBlank(sourceLayer, SOURCE_OPENAPI));
            row.setEventType(limit(defaultIfBlank(eventType, "SECURITY_INTERCEPT"), 64));
            row.setRiskLevel(defaultIfBlank(riskLevel, RISK_HIGH));
            row.setAction(ACTION_BLOCK);
            row.setMerchantId(limit(trimToNull(merchantId), 32));
            row.setClientIp(limit(resolveClientIp(request), 45));
            row.setRequestMethod(limit(request == null ? null : request.getMethod(), 16));
            row.setRequestPath(limit(resolveRequestPath(request), MAX_PATH_LENGTH));
            row.setTraceId(limit(resolveTraceId(request), 64));
            row.setRequestId(limit(resolveRequestId(request), 64));
            row.setUserAgent(limit(request == null ? null : request.getHeader(HEADER_USER_AGENT), MAX_TEXT_LENGTH));
            row.setReasonCode(limit(trimToNull(reasonCode), 64));
            row.setReasonMessage(limit(sanitizeText(reasonMessage), MAX_TEXT_LENGTH));
            row.setServiceName(SERVICE_NAME);
            row.setHitRuleCode(limit(trimToNull(hitRuleCode), 64));
            row.setHeaderSummary(limit(buildHeaderSummary(request), MAX_HEADER_SUMMARY_LENGTH));
            row.setProcessStatus(0);
            row.setGmtCreate(now);
            row.setGmtModified(now);
            eventMapper.insert(row);
        } catch (RuntimeException exception) {
            log.warn("安全拦截事件记录失败，不影响原始安全拦截结果，事件类型：{}，错误类型：{}",
                    eventType,
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 从业务异常中提取响应码作为原因码。
     *
     * @param exception 业务异常
     * @return 原因码
     */
    public String reasonCode(Throwable exception) {
        if (exception instanceof ServiceException serviceException) {
            return serviceException.getCode();
        }
        return exception == null ? null : exception.getClass().getSimpleName();
    }

    /**
     * 从异常中提取脱敏后的原因说明。
     *
     * @param exception 异常
     * @return 脱敏原因说明
     */
    public String reasonMessage(Throwable exception) {
        return exception == null ? null : exception.getMessage();
    }

    private String generateEventNo(LocalDateTime now) {
        return "SIE" + EVENT_TIME_FORMATTER.format(now) + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String gatewayClientIp = request.getHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP);
        if (StringUtils.hasText(gatewayClientIp)) {
            return gatewayClientIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveRequestPath(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getRequestURI();
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request == null ? null : request.getHeader(TraceContext.TRACE_ID_HEADER);
        if (StringUtils.hasText(traceId)) {
            return traceId.trim();
        }
        return TraceContext.getTraceId();
    }

    private String resolveRequestId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return trimToNull(request.getHeader(HEADER_REQUEST_ID));
    }

    private String buildHeaderSummary(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("authorizationPresent", StringUtils.hasText(request.getHeader("Authorization")) || StringUtils.hasText(request.getHeader("authorization")));
        putIfPresent(summary, "contentType", request.getContentType());
        putIfPresent(summary, "gatewayClientIp", request.getHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP));
        putIfPresent(summary, "traceId", request.getHeader(TraceContext.TRACE_ID_HEADER));
        putIfPresent(summary, "requestId", request.getHeader(HEADER_REQUEST_ID));
        putIfPresent(summary, "userAgent", request.getHeader(HEADER_USER_AGENT));
        putIfPresent(summary, "channelTimestampPresent", presentText(request.getHeader("X-Channel-Timestamp")));
        putIfPresent(summary, "channelNoncePresent", presentText(request.getHeader("X-Channel-Nonce")));
        putIfPresent(summary, "channelSignaturePresent", presentText(request.getHeader("X-Channel-Signature")));
        return sanitizeText(JsonUtils.toJsonString(summary));
    }

    private Boolean presentText(String value) {
        return StringUtils.hasText(value) ? Boolean.TRUE : null;
    }

    private void putIfPresent(Map<String, Object> summary, String key, Object value) {
        if (value instanceof String text && !StringUtils.hasText(text)) {
            return;
        }
        if (value != null) {
            summary.put(key, value);
        }
    }

    private String sanitizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return SensitiveDataMaskUtils.maskJson(value.trim());
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
