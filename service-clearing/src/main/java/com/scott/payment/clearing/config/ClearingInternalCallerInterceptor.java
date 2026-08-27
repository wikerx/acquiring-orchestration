package com.scott.payment.clearing.config;

import com.alibaba.fastjson2.JSON;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** 在 HMAC 验证之后限制清分内部接口的调用方服务身份。 */
public class ClearingInternalCallerInterceptor implements HandlerInterceptor {

    private static final String ADMIN_CALLER = "service-admin";
    private static final String JOB_CALLER = "service-job";
    private static final String ADMIN_PATH = "/internal/clearing/v1/transactions/**";
    private static final String JOB_PATH = "/internal/clearing/v1/compensations/**";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final Set<String> allowedCallers;

    /** @param properties 清分内部调用方总白名单 */
    public ClearingInternalCallerInterceptor(ClearingProperties properties) {
        this.allowedCallers = Set.copyOf(properties.getInternalAllowedCallers());
    }

    /** 校验 HMAC 已认证 caller 是否同时具备当前清分内部路径的服务级权限。 */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String caller = request.getHeader(InternalServiceSignature.HEADER_CALLER);
        if (allowedCallers.contains(caller) && authorizedForPath(caller, request.getRequestURI())) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(CommonResult.error(
                ApiResultEnum.UNAUTHORIZED.getCode(), "internal clearing caller is not allowed")));
        return false;
    }

    /** 未知清分内部路径默认拒绝，避免未来新增接口自动继承过宽调用权限。 */
    private boolean authorizedForPath(String caller, String requestPath) {
        return ADMIN_CALLER.equals(caller) && PATH_MATCHER.match(ADMIN_PATH, requestPath)
                || JOB_CALLER.equals(caller) && PATH_MATCHER.match(JOB_PATH, requestPath);
    }
}
