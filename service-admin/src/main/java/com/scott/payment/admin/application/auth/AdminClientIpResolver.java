package com.scott.payment.admin.application.auth;

import com.scott.payment.component.web.gateway.TrustedClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 管理端客户端 IP 解析器。只接受网关覆盖写入的专用请求头，不信任调用方可直接伪造的 X-Forwarded-For。
 */
public final class AdminClientIpResolver {

    private AdminClientIpResolver() {
    }

    /** 解析经过 Gateway HMAC 认证的客户端 IP，直连时只使用 TCP 对端地址。 */
    public static String resolve(HttpServletRequest request) {
        return TrustedClientIpResolver.resolveOrUnknown(request);
    }
}
