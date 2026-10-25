package com.scott.payment.component.web.gateway;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TrustedClientIpResolver
 * @date : 2026-10-25 00:00
 * @email : scott_x@163.com
 * @description : Servlet 服务统一可信客户端 IP 解析器；仅在 Gateway HMAC 验签成功后读取签名绑定的专用头，否则回退 TCP 对端地址。
 * @status : create
 */
public final class TrustedClientIpResolver {

    /** 无法获得规范 IP 时使用的非地址占位符。 */
    public static final String UNKNOWN_IP = "unknown";

    private TrustedClientIpResolver() {
    }

    /**
     * 解析可信客户端 IP。
     *
     * @param request 当前 Servlet 请求
     * @return 规范 IPv4/IPv6；没有有效地址时返回 null
     */
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if (GatewayIngressAuthFilter.isGatewayClientIpAuthenticated(request)) {
            return normalize(request.getHeader(GatewayIngressSignature.HEADER_CLIENT_IP));
        }
        return normalize(request.getRemoteAddr());
    }

    /** @return 可信客户端 IP；无法解析时返回 {@value #UNKNOWN_IP} */
    public static String resolveOrUnknown(HttpServletRequest request) {
        String resolved = resolve(request);
        return resolved == null ? UNKNOWN_IP : resolved;
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return IpAddressNormalizer.normalizeExact(value).ipValue();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
