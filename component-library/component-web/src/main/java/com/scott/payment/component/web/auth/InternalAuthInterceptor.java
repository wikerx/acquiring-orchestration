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
 * 内部管理类接口登录态与权限拦截器
 */
public class InternalAuthInterceptor implements HandlerInterceptor {

    /**
     * Bearer 令牌前缀。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 路径匹配器。
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final String appCode;
    private final InternalAuthChecker authChecker;
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
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (isWhitelisted(request.getRequestURI())) {
            return true;
        }
        String authorization = BrowserSessionCookieSupport.resolveAuthorization(appCode, request);
        if (!StringUtils.hasText(authorization)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResultEnum.AUTHORIZATION_HEADER_MISSING);
            return false;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResultEnum.UNAUTHORIZED);
            return false;
        }
        if (!BrowserSessionCookieSupport.isCsrfValid(appCode, request)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    ApiResultEnum.FORBIDDEN.getCode(), "csrf token is invalid");
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
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        InternalAuthContextHolder.clear();
    }

    /**
     * 判断内部管理接口路径是否配置为免认证。
     * <p>
     * 命中后将跳过 token 和权限校验，因此白名单只能配置健康检查等无业务数据入口。
     * </p>
     *
     * @param requestPath 当前请求路径
     * @return 命中任一 Ant 路径规则时返回 {@code true}
     */
    private boolean isWhitelisted(String requestPath) {
        return whitelistPatterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
    }

    /**
     * 解析处理器声明的权限编码，方法级声明优先于类级声明。
     *
     * @param handler Spring MVC 处理器
     * @return 权限编码；非控制器方法或未声明权限时返回 {@code null}
     */
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

    /**
     * 将统一接口结果枚举写为内部管理接口错误响应。
     *
     * @param response HTTP 响应
     * @param httpStatus HTTP 状态码
     * @param result 统一接口结果
     * @throws IOException 响应写入失败时抛出
     */
    private void writeError(HttpServletResponse response, int httpStatus, ApiResultEnum result) throws IOException {
        writeError(response, httpStatus, result.getCode(), result.getMessage());
    }

    /**
     * 使用受控错误码和安全文案写入 JSON 错误响应。
     *
     * @param response HTTP 响应
     * @param httpStatus HTTP 状态码
     * @param code 统一错误码
     * @param message 安全错误描述
     * @throws IOException 响应写入失败时抛出
     */
    private void writeError(HttpServletResponse response, int httpStatus, String code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(CommonResult.error(code, message)));
    }
}
