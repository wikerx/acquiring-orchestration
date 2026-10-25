package com.scott.payment.merchant.application.auth;

import com.scott.payment.component.web.gateway.TrustedClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;

/** 商户端客户端 IP 解析器，只信任网关覆盖写入的专用请求头。 */
public final class MerchantClientIpResolver {

    private MerchantClientIpResolver() {
    }

    /** 解析可信客户端 IP；无有效值时返回 unknown。 */
    public static String resolve(HttpServletRequest request) {
        return TrustedClientIpResolver.resolveOrUnknown(request);
    }
}
