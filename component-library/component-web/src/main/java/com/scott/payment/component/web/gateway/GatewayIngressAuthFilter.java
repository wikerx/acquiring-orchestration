package com.scott.payment.component.web.gateway;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.security.GatewayIngressSignature;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.LongSupplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayIngressAuthFilter
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : 收银台下游入口过滤器，在控制器前拒绝未经过 service-gateway 签发的公网业务请求。
 * @status : create
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class GatewayIngressAuthFilter extends OncePerRequestFilter {

    private static final int MAX_NONCE_LENGTH = 128;

    private final GatewayIngressAuthProperties properties;
    private final LongSupplier currentTimeMillis;

    /**
     * 创建生产环境入口验签过滤器。
     *
     * @param properties 受保护路径、时间窗和外部密钥配置
     */
    @Autowired
    public GatewayIngressAuthFilter(GatewayIngressAuthProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    GatewayIngressAuthFilter(GatewayIngressAuthProperties properties, LongSupplier currentTimeMillis) {
        this.properties = properties;
        this.currentTimeMillis = currentTimeMillis;
    }

    /**
     * 验证 Gateway 调用方、时间窗和 HMAC 后再交给业务控制器。
     *
     * @param request 当前 Servlet 请求
     * @param response 当前 Servlet 响应
     * @param filterChain 后续过滤器链
     * @throws ServletException 下游过滤器异常
     * @throws IOException 响应写入异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!GatewayIngressSignature.isConfiguredSecret(properties.getSecret())
                || properties.getAllowedClockSkewMillis() <= 0L) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    ApiResultEnum.NETWORK_BUSY.getCode(), "checkout gateway ingress is unavailable");
            return;
        }

        String caller = request.getHeader(GatewayIngressSignature.HEADER_CALLER);
        String timestampText = request.getHeader(GatewayIngressSignature.HEADER_TIMESTAMP);
        String nonce = request.getHeader(GatewayIngressSignature.HEADER_NONCE);
        String signature = request.getHeader(GatewayIngressSignature.HEADER_SIGNATURE);
        if (!GatewayIngressSignature.CALLER_SERVICE_GATEWAY.equals(caller)
                || !StringUtils.hasText(timestampText)
                || !StringUtils.hasText(nonce)
                || nonce.length() > MAX_NONCE_LENGTH
                || !StringUtils.hasText(signature)) {
            rejectUnauthorized(response);
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            rejectUnauthorized(response);
            return;
        }
        long now = currentTimeMillis.getAsLong();
        long skew = properties.getAllowedClockSkewMillis();
        if (timestamp < now - skew || timestamp > now + skew) {
            rejectUnauthorized(response);
            return;
        }

        String requestTarget = GatewayIngressSignature.requestTarget(request.getRequestURI(), request.getQueryString());
        String expected = GatewayIngressSignature.sign(
                request.getMethod(), requestTarget, timestamp, nonce, properties.getSecret());
        if (!GatewayIngressSignature.matches(expected, signature)) {
            rejectUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 仅对各服务显式配置的收银台入口执行验签。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !GatewayIngressSignature.isProtectedCheckoutPath(request.getRequestURI());
    }

    private void rejectUnauthorized(HttpServletResponse response) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                ApiResultEnum.UNAUTHORIZED.getCode(), "checkout requests must pass through service-gateway");
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null}");
    }
}
