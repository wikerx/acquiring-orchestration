package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * OpenAPI 请求上下文访问器。
 * <p>
 * 统一读取当前线程绑定请求中的开放接口请求头上下文，避免业务服务直接依赖 Servlet 细节。
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
