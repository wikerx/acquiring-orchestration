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

    /**
     * 创建 API 版本匹配条件。
     *
     * @param apiVersion 当前控制器支持的主版本号
     */
    public ApiVersionCondition(int apiVersion) {
        this.apiVersion = apiVersion;
    }

    /**
     * 合并版本匹配条件，优先使用更靠近处理方法的条件。
     *
     * @param other 其他版本条件
     * @return 合并后的版本条件
     */
    @Override
    public ApiVersionCondition combine(ApiVersionCondition other) {
        return other;
    }

    /**
     * 判断当前控制器版本是否能处理请求版本。
     * <p>
     * 请求版本大于控制器版本时允许匹配，用于实现 v2 请求自动降级到 v1 控制器。
     *
     * @param request HTTP 请求
     * @return 匹配成功返回当前条件，不匹配返回 null
     */
    @Override
    public ApiVersionCondition getMatchingCondition(HttpServletRequest request) {
        Integer requestVersion = resolveRequestVersion(request);
        if (requestVersion == null || requestVersion < apiVersion) {
            return null;
        }
        return this;
    }

    /**
     * 多个控制器同时匹配时选择版本号最高且不超过请求版本的控制器。
     *
     * @param other   其他版本条件
     * @param request HTTP 请求
     * @return 排序结果
     */
    @Override
    public int compareTo(ApiVersionCondition other, HttpServletRequest request) {
        return other.apiVersion - this.apiVersion;
    }

    private Integer resolveRequestVersion(HttpServletRequest request) {
        Object attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (attributes instanceof Map<?, ?> variables) {
            Object version = variables.get("version");
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

    /**
     * 解析parseversion，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 公共组件库 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param version version 输入值，参与 version 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private Integer parseVersion(String version) {
        String value = version.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
