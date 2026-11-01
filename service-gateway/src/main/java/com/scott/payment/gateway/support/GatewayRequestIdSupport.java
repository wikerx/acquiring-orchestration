package com.scott.payment.gateway.support;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

/**
 * 校验供 Gateway 审计日志使用的外部 X-Request-Id；只允许短业务标识，防止日志注入和无界字段。
 */
public final class GatewayRequestIdSupport {

    /**
     * SDK 与 Gateway 约定的单次 HTTP 尝试标识 Header。
     */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * 非法或缺失请求号的固定日志占位值，避免记录未经校验的外部输入。
     */
    private static final String UNKNOWN_REQUEST_ID = "unknown";

    /**
     * 日志可接受的最大请求号长度，与当前 UUID 及平台短标识兼容。
     */
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private GatewayRequestIdSupport() {
    }

    /**
     * 从请求头提取可安全写入单行日志的 requestId。
     *
     * @param headers Gateway 请求头
     * @return 合法请求号；缺失、超长或包含特殊字符时返回 unknown
     */
    public static String resolveForLogging(HttpHeaders headers) {
        String candidate = headers == null ? null : headers.getFirst(REQUEST_ID_HEADER);
        if (!StringUtils.hasText(candidate)) {
            return UNKNOWN_REQUEST_ID;
        }
        String requestId = candidate.trim();
        if (requestId.length() > MAX_REQUEST_ID_LENGTH) {
            return UNKNOWN_REQUEST_ID;
        }
        for (int index = 0; index < requestId.length(); index++) {
            char current = requestId.charAt(index);
            boolean allowed = current >= '0' && current <= '9'
                    || current >= 'a' && current <= 'z'
                    || current >= 'A' && current <= 'Z'
                    || current == '-'
                    || current == '_';
            if (!allowed) {
                return UNKNOWN_REQUEST_ID;
            }
        }
        return requestId;
    }
}
