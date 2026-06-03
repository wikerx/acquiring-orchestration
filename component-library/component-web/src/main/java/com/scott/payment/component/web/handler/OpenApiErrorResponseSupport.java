package com.scott.payment.component.web.handler;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiErrorResponseSupport
 * @date : 2026-06-03 23:04
 * @email : scott_x@163.com
 * @description : 开放接口兜底错误响应辅助类
 * @status : create
 */
final class OpenApiErrorResponseSupport {

    /**
     * 开放 API 路径前缀，商户开放接口命名空间下的请求需要先完成 Authorization 校验。
     */
    private static final String OPEN_API_REST_PREFIX = "/api/rest/";

    /**
     * 标准授权请求头名称。
     */
    private static final String HEADER_AUTHORIZATION = "authorization";

    private OpenApiErrorResponseSupport() {
    }

    /**
     * 根据开放 API 路径和授权头状态构建路由未命中的错误响应。
     *
     * @param request     HTTP 请求
     * @param requestPath 原始请求路径
     * @return 开放 API 未授权或资源不存在响应
     */
    static CommonResult<Void> routeNotFound(HttpServletRequest request, String requestPath) {
        if (isOpenApiRestRequest(requestPath) && !hasAuthorization(request)) {
            return CommonResult.error(ApiResultEnum.AUTHORIZATION_HEADER_MISSING);
        }
        return CommonResult.error(ApiResultEnum.NOT_FOUND);
    }

    /**
     * 获取容器转发到 /error 前的原始请求地址。
     *
     * @param request HTTP 请求
     * @return 原始请求地址，缺失时回退到当前请求地址
     */
    static String resolveOriginalRequestUri(HttpServletRequest request) {
        Object requestUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (requestUri instanceof String originalRequestUri) {
            return originalRequestUri;
        }
        return request.getRequestURI();
    }

    /**
     * 判断请求路径是否属于商户开放 API REST 命名空间。
     *
     * @param requestPath 请求路径
     * @return true 表示请求路径以 /api/rest/ 开头
     */
    static boolean isOpenApiRestRequest(String requestPath) {
        String normalizedPath = normalizeRequestPath(requestPath);
        return normalizedPath != null && normalizedPath.startsWith(OPEN_API_REST_PREFIX);
    }

    /**
     * 判断请求是否携带授权头。
     *
     * @param request HTTP 请求
     * @return true 表示存在 Authorization 请求头
     */
    static boolean hasAuthorization(HttpServletRequest request) {
        String authorization = request.getHeader(HEADER_AUTHORIZATION);
        return authorization != null && !authorization.isBlank();
    }

    /**
     * 规范化请求路径，兼容 Spring 静态资源异常里不带前导斜杠的 resourcePath。
     *
     * @param requestPath 请求路径
     * @return 带前导斜杠的请求路径
     */
    private static String normalizeRequestPath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return null;
        }
        return requestPath.startsWith("/") ? requestPath : "/" + requestPath;
    }
}
