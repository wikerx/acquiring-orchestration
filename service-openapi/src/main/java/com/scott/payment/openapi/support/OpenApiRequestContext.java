package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;


@Component
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestContext
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : OpenApiRequestContext Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
