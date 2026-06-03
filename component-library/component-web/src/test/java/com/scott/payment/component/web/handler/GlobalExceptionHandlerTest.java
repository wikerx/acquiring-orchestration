package com.scott.payment.component.web.handler;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalExceptionHandlerTest
 * @date : 2026-06-03 23:04
 * @email : scott_x@163.com
 * @description : 全局异常处理器测试
 * @status : create
 */
class GlobalExceptionHandlerTest {

    /**
     * Spring Boot 3 将未命中的浏览器请求包装为 NoResourceFoundException 时，开放 API 未带授权头应返回认证缺失。
     */
    @Test
    void shouldReturnAuthorizationRequiredWhenNoResourceFoundForOpenApiWithoutAuthorization() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rest/123/123asd/sdasd");
        NoResourceFoundException exception = new NoResourceFoundException(
                HttpMethod.GET,
                "api/rest/123/123asd/sdasd"
        );

        CommonResult<Void> result = handler.handleRouteNotFoundException(exception, request);

        assertThat(result.getCode()).isEqualTo(ApiResultEnum.AUTHORIZATION_HEADER_MISSING.getCode());
        assertThat(result.getMessage()).isEqualTo(ApiResultEnum.AUTHORIZATION_HEADER_MISSING.getMessage());
    }

    /**
     * 已携带授权头但路由确实不存在时，应返回资源不存在，便于商户区分鉴权缺失和地址错误。
     */
    @Test
    void shouldReturnNotFoundWhenNoResourceFoundForOpenApiWithAuthorization() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/rest/123/123asd/sdasd");
        request.addHeader("authorization", "Bearer sample.jwt.token");
        NoResourceFoundException exception = new NoResourceFoundException(
                HttpMethod.GET,
                "api/rest/123/123asd/sdasd"
        );

        CommonResult<Void> result = handler.handleRouteNotFoundException(exception, request);

        assertThat(result.getCode()).isEqualTo(ApiResultEnum.NOT_FOUND.getCode());
        assertThat(result.getMessage()).isEqualTo(ApiResultEnum.NOT_FOUND.getMessage());
    }
}
