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
 * 收银台下游入口过滤器，在控制器前拒绝未经过 service-gateway 签发的公网业务请求。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class GatewayIngressAuthFilter extends OncePerRequestFilter {


    private static final int MAX_NONCE_LENGTH = 128;

    private final GatewayIngressAuthProperties properties;
    private final InternalRequestReplayGuard replayGuard;
    private final LongSupplier currentTimeMillis;

    /**
     * 入口验签成功后写入的可信请求属性。
     */
    public static final String GATEWAY_AUTHENTICATED_ATTRIBUTE =
            GatewayIngressAuthFilter.class.getName() + ".AUTHENTICATED";

    /**
     * v2 入口验签成功后写入，表示客户端 IP 已包含在 HMAC 规范文本中。
     */
    public static final String GATEWAY_CLIENT_IP_AUTHENTICATED_ATTRIBUTE =
            GatewayIngressAuthFilter.class.getName() + ".CLIENT_IP_AUTHENTICATED";

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

    /**
     * 创建可注入防重放存储和时钟的入口验签过滤器，供隔离测试使用。
     *
     * @param properties Gateway 入口验签配置
     * @param replayGuard nonce 原子防重放存储
     * @param currentTimeMillis 当前毫秒时间提供器
     */
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
        if (StringUtils.hasText(bodySha256Header)) {
            request.setAttribute(GATEWAY_CLIENT_IP_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 判断当前请求是否已通过 Gateway 入口验签。
     */
    public static boolean isGatewayAuthenticated(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(GATEWAY_AUTHENTICATED_ATTRIBUTE));
    }

    /**
     * 判断客户端 IP 是否已经由 Gateway v2 HMAC 绑定，旧签名通过时仍返回 false。
     */
    public static boolean isGatewayClientIpAuthenticated(HttpServletRequest request) {
        return request != null
                && Boolean.TRUE.equals(request.getAttribute(GATEWAY_CLIENT_IP_AUTHENTICATED_ATTRIBUTE));
    }

    /**
     * 仅对各服务显式配置的收银台入口执行验签。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !GatewayIngressSignature.isProtectedCheckoutPath(path)
                && !(properties.isProtectPortalPaths() && GatewayIngressSignature.isProtectedPortalPath(path));
    }

    /**
     * 校验正文摘要绑定的 v2 签名，并仅在显式兼容配置开启时接受旧签名。
     *
     * @param request 当前请求
     * @param requestTarget 原始请求目标
     * @param timestamp 签名时间戳
     * @param nonce 单次请求随机串
     * @param bodySha256Header Gateway 声明的正文摘要
     * @param actualBodySha256 下游根据缓存正文计算的摘要
     * @param clientIp Gateway 解析并签名的客户端 IP
     * @param signature 请求签名
     * @return 签名和正文摘要均通过时返回 true
     */
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

    /**
     * 读取正文缓存过滤器计算的摘要；空正文使用协议固定摘要。
     *
     * @param request 当前请求
     * @return 小写十六进制 SHA-256 摘要
     */
    private String resolveBodySha256(HttpServletRequest request) {
        Object digest = request.getAttribute(GatewayIngressRequestBodyFilter.BODY_SHA256_ATTRIBUTE);
        return digest instanceof String value && StringUtils.hasText(value)
                ? value : GatewayIngressSignature.EMPTY_BODY_SHA256;
    }

    /**
     * 写入未经过可信 Gateway 的统一未授权响应。
     *
     * @param response HTTP 响应
     * @throws IOException 响应写入失败时抛出
     */
    private void rejectUnauthorized(HttpServletResponse response) throws IOException {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                ApiResultEnum.UNAUTHORIZED.getCode(), "checkout requests must pass through service-gateway");
    }

    /**
     * 使用受控错误码和文案写入 Gateway 入口 JSON 错误响应。
     *
     * @param response HTTP 响应
     * @param status HTTP 状态码
     * @param code 统一错误码
     * @param message 安全错误描述
     * @throws IOException 响应写入失败时抛出
     */
    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null}");
    }
}
