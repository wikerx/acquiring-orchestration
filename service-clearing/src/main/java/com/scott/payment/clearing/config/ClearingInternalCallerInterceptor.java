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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingInternalCallerInterceptor
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 在 HMAC 验证之后限制清分内部接口的调用方服务身份。
 * @status : update
 */
public class ClearingInternalCallerInterceptor implements HandlerInterceptor {

    /**
     * {@code ADMIN_CALLER}常量，统一 {@code ClearingInternalCallerInterceptor} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String ADMIN_CALLER = "service-admin";
    /**
     * {@code JOB_CALLER}常量，统一 {@code ClearingInternalCallerInterceptor} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String JOB_CALLER = "service-job";
    /**
     * {@code ADMIN_PATH}，表示接口路径、资源路径或路由匹配路径。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String ADMIN_PATH = "/internal/clearing/v1/transactions/**";
    /**
     * {@code JOB_PATH}，表示接口路径、资源路径或路由匹配路径。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String JOB_PATH = "/internal/clearing/v1/compensations/**";
    /**
     * {@code PATH_MATCHER}，表示接口路径、资源路径或路由匹配路径。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final Set<String> allowedCallers;

    /** @param properties 清分内部调用方总白名单 */
    public ClearingInternalCallerInterceptor(ClearingProperties properties) {
        this.allowedCallers = Set.copyOf(properties.getInternalAllowedCallers());
    }

    /**
     * 校验 HMAC 已认证 caller 是否同时具备当前清分内部路径的服务级权限。
     *
     * @param request 已由内部鉴权拦截器校验并写入 caller 的请求
     * @param response 内部接口响应
     * @param handler 当前 Spring MVC 处理器
     * @return caller 与路径权限匹配时返回 true，否则直接写入拒绝响应并返回 false
     * @throws IOException 写入拒绝响应失败时抛出
     */
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

    /**
     * 未知清分内部路径默认拒绝，避免未来新增接口自动继承过宽调用权限。
     *
     * @param caller HMAC 验证通过的固定服务身份
     * @param requestPath 当前内部请求路径
     * @return 调用方被显式授权访问该路径时返回 true
     */
    private boolean authorizedForPath(String caller, String requestPath) {
        return ADMIN_CALLER.equals(caller) && PATH_MATCHER.match(ADMIN_PATH, requestPath)
                || JOB_CALLER.equals(caller) && PATH_MATCHER.match(JOB_PATH, requestPath);
    }
}
