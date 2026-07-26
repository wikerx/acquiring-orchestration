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

    /**
     * 解析 resolve Request Version 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 解析或查询得到的业务值
     */
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

    /**
     * 解析 resolve Version From Uri 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 解析或查询得到的业务值
     */
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
     * 解析 parse Version 输入文本并转换为内部可校验的数据结构。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param version version 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析后的内部数据结构或业务值
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
