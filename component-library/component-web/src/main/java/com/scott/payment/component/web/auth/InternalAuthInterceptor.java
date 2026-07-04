package com.scott.payment.component.web.auth;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthChecker;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalAuthInterceptor
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 内部管理类接口登录态与权限拦截器
 * @status : create
 */
public class InternalAuthInterceptor implements HandlerInterceptor {

    /**
     * Authorization 请求头名称。
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Bearer token 前缀。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 路径匹配器。
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private final String appCode;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final InternalAuthChecker authChecker;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final List<String> whitelistPatterns;

    /**
     * 创建内部管理接口鉴权拦截器。
     *
     * @param appCode           应用编码
     * @param authChecker       内部鉴权检查器
     * @param whitelistPatterns 白名单路径
     */
    public InternalAuthInterceptor(String appCode, InternalAuthChecker authChecker, List<String> whitelistPatterns) {
        this.appCode = appCode;
        this.authChecker = authChecker;
        this.whitelistPatterns = List.copyOf(whitelistPatterns);
    }

    /**
     * 请求进入控制器前校验登录态和权限。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @return true 表示放行
     * @throws IOException 写响应失败
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param response 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param handler 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     * @throws Exception 当下游调用、数据访问或业务校验失败时抛出。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (isWhitelisted(request.getRequestURI())) {
            return true;
        }
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResultEnum.AUTHORIZATION_HEADER_MISSING);
            return false;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResultEnum.UNAUTHORIZED);
            return false;
        }
        try {
            InternalAuthAccount account = authChecker.check(
                    appCode,
                    authorization,
                    request.getMethod(),
                    request.getRequestURI(),
                    requiredPermission(handler)
            );
            InternalAuthContextHolder.set(account);
            return true;
        } catch (ServiceException exception) {
            int httpStatus = ApiResultEnum.FORBIDDEN.getCode().equals(exception.getCode())
                    ? HttpServletResponse.SC_FORBIDDEN
                    : HttpServletResponse.SC_UNAUTHORIZED;
            writeError(response, httpStatus, exception.getCode(), exception.getMessage());
            return false;
        }
    }

    /**
     * 请求完成后清理线程上下文。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @param ex       请求异常
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param response 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param handler 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param ex 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        InternalAuthContextHolder.clear();
    }

    private boolean isWhitelisted(String requestPath) {
        return whitelistPatterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
    }

    private String requiredPermission(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }
        RequiresPermission methodPermission = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (methodPermission != null && StringUtils.hasText(methodPermission.value())) {
            return methodPermission.value();
        }
        RequiresPermission typePermission = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        return typePermission == null ? null : typePermission.value();
    }

    private void writeError(HttpServletResponse response, int httpStatus, ApiResultEnum result) throws IOException {
        writeError(response, httpStatus, result.getCode(), result.getMessage());
    }

    private void writeError(HttpServletResponse response, int httpStatus, String code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(CommonResult.error(code, message)));
    }
}
