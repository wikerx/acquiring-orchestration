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
 * 在 HMAC 验证后把结算管理内部接口严格限制给 service-admin，未知调用方默认拒绝。
 */
public class SettlementInternalCallerInterceptor implements HandlerInterceptor {


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
