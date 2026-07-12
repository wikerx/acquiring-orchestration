package com.scott.payment.component.web.internal;

import com.alibaba.fastjson2.JSON;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalServiceAuthInterceptor
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 内部服务接口签名拦截器，为 /internal/** 接口提供服务间 HMAC 鉴权边界。
 * @status : create
 */
public class InternalServiceAuthInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 内部服务签名配置。
     */
    private final InternalServiceAuthProperties properties;

    /**
     * 创建内部服务签名拦截器。
     *
     * @param properties 内部服务签名配置
     */
    public InternalServiceAuthInterceptor(InternalServiceAuthProperties properties) {
        this.properties = properties;
    }

    /**
     * 请求进入内部接口前校验调用方、时间窗、随机串和签名。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  MVC 处理器
     * @return true 表示放行
     * @throws IOException 写错误响应失败
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!properties.isEnabled() || isWhitelisted(request.getRequestURI())) {
            return true;
        }
        String caller = request.getHeader(InternalServiceSignature.HEADER_CALLER);
        String timestampText = request.getHeader(InternalServiceSignature.HEADER_TIMESTAMP);
        String nonce = request.getHeader(InternalServiceSignature.HEADER_NONCE);
        String signature = request.getHeader(InternalServiceSignature.HEADER_SIGNATURE);
        if (!StringUtils.hasText(caller)
                || !StringUtils.hasText(timestampText)
                || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature)) {
            writeError(response, ApiResultEnum.UNAUTHORIZED.getCode(), "internal service signature headers are required");
            return false;
        }
        long timestamp = parseTimestamp(timestampText, response);
        if (timestamp < 0) {
            return false;
        }
        if (isExpired(timestamp)) {
            writeError(response, ApiResultEnum.UNAUTHORIZED.getCode(), "internal service signature timestamp is expired");
            return false;
        }
        String expectedSignature = InternalServiceSignature.sign(
                request.getMethod(),
                request.getRequestURI(),
                timestamp,
                nonce,
                caller,
                properties.getSecret()
        );
        if (!InternalServiceSignature.matches(expectedSignature, signature)) {
            writeError(response, ApiResultEnum.UNAUTHORIZED.getCode(), "internal service signature is invalid");
            return false;
        }
        return true;
    }

    private boolean isWhitelisted(String requestPath) {
        return properties.getWhitelist().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
    }

    private boolean isExpired(long timestamp) {
        Duration allowedClockSkew = properties.getAllowedClockSkew();
        long skewMillis = allowedClockSkew == null ? Duration.ofMinutes(5).toMillis() : allowedClockSkew.toMillis();
        return Math.abs(InternalServiceSignature.currentTimeMillis() - timestamp) > skewMillis;
    }

    private long parseTimestamp(String timestampText, HttpServletResponse response) throws IOException {
        try {
            return Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            writeError(response, ApiResultEnum.UNAUTHORIZED.getCode(), "internal service signature timestamp is invalid");
            return -1;
        }
    }

    private void writeError(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(CommonResult.error(code, message)));
    }
}
