package com.scott.payment.component.web.handler;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.RequestDispatcher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : UnifiedErrorControllerTest
 * @date : 2026-06-03 22:55
 * @email : scott_x@163.com
 * @description : 统一兜底错误响应控制器测试
 * @status : create
 */
class UnifiedErrorControllerTest {

    /**
     * 商户请求不存在的开放 API 且没有携带 Authorization 时，应返回认证缺失，而不是内部错误。
     */
    @Test
    void shouldReturnAuthorizationRequiredWhenOpenApiPathMissingAuthorization() {
        UnifiedErrorController controller = new UnifiedErrorController();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/rest/asd/asd");

        CommonResult<Void> result = controller.handleError(request);

        assertThat(result.getCode()).isEqualTo(ApiResultEnum.AUTHORIZATION_HEADER_MISSING.getCode());
        assertThat(result.getMessage()).isEqualTo(ApiResultEnum.AUTHORIZATION_HEADER_MISSING.getMessage());
    }

    /**
     * 商户请求不存在的开放 API 但已携带 Authorization 时，应按真实路由不存在返回 404。
     */
    @Test
    void shouldReturnNotFoundWhenOpenApiPathHasAuthorizationButRouteMissing() {
        UnifiedErrorController controller = new UnifiedErrorController();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.addHeader("authorization", "Bearer sample.jwt.token");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/rest/asd/asd");

        CommonResult<Void> result = controller.handleError(request);

        assertThat(result.getCode()).isEqualTo(ApiResultEnum.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo(ApiResultEnum.NOT_FOUND.getMessage());
    }
}
