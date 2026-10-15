package com.scott.payment.admin.application.auth;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 管理端客户端 IP 解析器。只接受网关覆盖写入的专用请求头，不信任调用方可直接伪造的 X-Forwarded-For。
 */
public final class AdminClientIpResolver {

    private static final String UNKNOWN_IP = "unknown";

    private AdminClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String gatewayClientIp = normalize(request.getHeader(GatewayIngressSignature.HEADER_CLIENT_IP));
        if (gatewayClientIp != null) {
            return gatewayClientIp;
        }
        String remoteAddress = normalize(request.getRemoteAddr());
        return remoteAddress == null ? UNKNOWN_IP : remoteAddress;
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
