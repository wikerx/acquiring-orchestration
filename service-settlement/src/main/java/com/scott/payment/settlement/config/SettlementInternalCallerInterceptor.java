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

    private static final String ADMIN_CALLER = "service-admin";

    /** @return service-admin 调用返回 true，其它调用方返回401 */
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
