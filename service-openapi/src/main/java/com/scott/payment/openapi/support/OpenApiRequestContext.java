package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestContext
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 请求上下文读取器，位于 service-openapi 支撑层，统一获取已验签的商户头信息和商户号。
 * @status : create
 */
@Component
public class OpenApiRequestContext {

    /**
     * 获取当前请求中的开放接口请求头上下文。
     *
     * @return 开放接口请求头对象
     */
    public OpenApiRequestHeaderDTO getRequiredHeader() {
        HttpServletRequest request = currentRequest();
        OpenApiRequestHeaderDTO headerDTO = (OpenApiRequestHeaderDTO) request.getAttribute(OpenApiRequestAttributes.REQUEST_HEADER);
        if (headerDTO == null) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "openapi request header context missing");
        }
        return headerDTO;
    }

    /**
     * 获取当前请求中的商户号。
     *
     * @return 商户号
     */
    public String getRequiredMerchantId() {
        String merchantId = getRequiredHeader().getMerchantId();
        if (merchantId == null || merchantId.isBlank()) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "openapi merchantId missing");
        }
        return merchantId;
    }

    /**
     * 获取当前线程绑定的请求对象。
     *
     * @return 当前 HTTP 请求
     */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest();
    }
}
