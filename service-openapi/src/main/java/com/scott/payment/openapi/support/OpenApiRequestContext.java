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
 * @description : 商户 OpenAPIOpen Api Request Context，位于 service-openapi 的支撑组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class OpenApiRequestContext {

    /**
     * 获取当前请求中的开放接口请求头上下文。
     *
     * @return 开放接口请求头对象
     */
    /**
     * 获取商户 OpenAPI明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 获取商户 OpenAPI明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
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
