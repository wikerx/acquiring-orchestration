package com.scott.payment.component.web.version;

import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiVersionCondition
 * @date : 2026-05-28 18:16
 * @email : scott_x@163.com
 * @description : REST API 版本匹配条件
 * @status : create
 */
public class ApiVersionCondition implements RequestCondition<ApiVersionCondition> {

    /**
     * 当前控制器支持的 API 主版本号，例如 v1 对应 1、v2 对应 2。
     */
    private final int apiVersion;

    public ApiVersionCondition(int apiVersion) {
        this.apiVersion = apiVersion;
    }

    @Override
    public ApiVersionCondition combine(ApiVersionCondition other) {
        return other;
    }

    @Override
    public ApiVersionCondition getMatchingCondition(HttpServletRequest request) {
        Integer requestVersion = resolveRequestVersion(request);
        if (requestVersion == null || requestVersion < apiVersion) {
            return null;
        }
        return this;
    }

    @Override
    public int compareTo(ApiVersionCondition other, HttpServletRequest request) {
        return other.apiVersion - this.apiVersion;
    }

    @SuppressWarnings("unchecked")
    private Integer resolveRequestVersion(HttpServletRequest request) {
        Object attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attributes instanceof Map) {
            Object version = ((Map<String, Object>) attributes).get("version");
            if (version != null) {
                return parseVersion(String.valueOf(version));
            }
        }
        return resolveVersionFromUri(request);
    }

    private Integer resolveVersionFromUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        String[] segments = uri.split("/");
        for (int index = 0; index + 3 < segments.length; index++) {
            if ("api".equals(segments[index]) && "rest".equals(segments[index + 1])) {
                return parseVersion(segments[index + 3]);
            }
        }
        return null;
    }

    private Integer parseVersion(String version) {
        String value = version.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
