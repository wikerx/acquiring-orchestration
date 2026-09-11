package com.scott.payment.component.web.gateway;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    /**
     * {@code MAX_NONCE_LENGTH}常量，统一 {@code GatewayIngressAuthFilter} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MAX_NONCE_LENGTH = 128;

    private final GatewayIngressAuthProperties properties;
    private final InternalRequestReplayGuard replayGuard;
    private final LongSupplier currentTimeMillis;

    /** 入口验签成功后写入的可信请求属性。 */
    public static final String GATEWAY_AUTHENTICATED_ATTRIBUTE =
            GatewayIngressAuthFilter.class.getName() + ".AUTHENTICATED";

    /**
     * 创建生产环境入口验签过滤器。
     *
     * @param properties 受保护路径、时间窗和外部密钥配置
     */
    @Autowired
    public GatewayIngressAuthFilter(GatewayIngressAuthProperties properties,
                                    ObjectProvider<InternalRequestReplayGuard> replayGuardProvider) {
        this(properties, replayGuardProvider.getIfAvailable(), System::currentTimeMillis);
    }

    GatewayIngressAuthFilter(GatewayIngressAuthProperties properties,
                             InternalRequestReplayGuard replayGuard,
                             LongSupplier currentTimeMillis) {
        this.properties = properties;
        this.replayGuard = replayGuard;
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
        String bodySha256Header = request.getHeader(GatewayIngressSignature.HEADER_BODY_SHA256);
        String clientIp = request.getHeader(GatewayIngressSignature.HEADER_CLIENT_IP);
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
        String actualBodySha256 = resolveBodySha256(request);
        if (!verifySignature(request, requestTarget, timestamp, nonce,
                bodySha256Header, actualBodySha256, clientIp, signature)) {
            rejectUnauthorized(response);
            return;
        }
        if (properties.isReplayProtectionRequired()) {
            if (replayGuard == null) {
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        ApiResultEnum.NETWORK_BUSY.getCode(), "gateway replay protection is unavailable");
                return;
            }
            try {
                Duration nonceTtl = Duration.ofMillis(Math.max(skew * 2L, 1L));
                if (!replayGuard.tryAcquire(GatewayIngressSignature.CALLER_SERVICE_GATEWAY, nonce, nonceTtl)) {
                    rejectUnauthorized(response);
                    return;
                }
            } catch (RuntimeException exception) {
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        ApiResultEnum.NETWORK_BUSY.getCode(), "gateway replay protection is unavailable");
                return;
            }
        }
        request.setAttribute(GATEWAY_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        filterChain.doFilter(request, response);
    }

    /** 判断当前请求是否已通过 Gateway 入口验签。 */
    public static boolean isGatewayAuthenticated(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(GATEWAY_AUTHENTICATED_ATTRIBUTE));
    }

    /** 仅对各服务显式配置的收银台入口执行验签。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !GatewayIngressSignature.isProtectedCheckoutPath(request.getRequestURI());
    }

    private boolean verifySignature(HttpServletRequest request,
                                    String requestTarget,
                                    long timestamp,
                                    String nonce,
                                    String bodySha256Header,
                                    String actualBodySha256,
                                    String clientIp,
                                    String signature) {
        if (StringUtils.hasText(bodySha256Header)) {
            if (!GatewayIngressSignature.matches(actualBodySha256, bodySha256Header.trim())) {
                return false;
            }
            String expected = GatewayIngressSignature.sign(
                    request.getMethod(), requestTarget, timestamp, nonce,
                    actualBodySha256, clientIp, properties.getSecret());
            return GatewayIngressSignature.matches(expected, signature);
        }
        if (!properties.isAcceptLegacySignature()) {
            return false;
        }
        String legacyExpected = GatewayIngressSignature.sign(
                request.getMethod(), requestTarget, timestamp, nonce, properties.getSecret());
        return GatewayIngressSignature.matches(legacyExpected, signature);
    }

    private String resolveBodySha256(HttpServletRequest request) {
        Object digest = request.getAttribute(GatewayIngressRequestBodyFilter.BODY_SHA256_ATTRIBUTE);
        return digest instanceof String value && StringUtils.hasText(value)
                ? value : GatewayIngressSignature.EMPTY_BODY_SHA256;
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
