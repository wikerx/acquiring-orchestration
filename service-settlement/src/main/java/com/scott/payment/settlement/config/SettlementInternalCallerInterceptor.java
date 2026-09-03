package com.scott.payment.settlement.config;

import com.alibaba.fastjson2.JSON;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementInternalCallerInterceptor
 * @date : 2026-08-26 21:10
 * @email : scott_x@163.com
 * @description : 在 HMAC 验证后把结算管理内部接口严格限制给 service-admin，未知调用方默认拒绝。
 * @status : create
 */
public class SettlementInternalCallerInterceptor implements HandlerInterceptor {

    /**
     * {@code ADMIN_CALLER}常量，统一 {@code SettlementInternalCallerInterceptor} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String ADMIN_CALLER = "service-admin";

    /**
     * 在 HMAC 和 nonce 校验通过后进一步限制固定调用方为 service-admin。
     *
     * @param request 已通过通用内部鉴权的请求
     * @param response 内部接口响应
     * @param handler 当前 Spring MVC 处理器
     * @return service-admin 调用返回 true，其它调用方写入 401 并返回 false
     * @throws IOException 写入拒绝响应失败时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if (ADMIN_CALLER.equals(request.getHeader(InternalServiceSignature.HEADER_CALLER))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(CommonResult.error(
                ApiResultEnum.UNAUTHORIZED.getCode(), "internal settlement caller is not allowed")));
        return false;
    }
}
