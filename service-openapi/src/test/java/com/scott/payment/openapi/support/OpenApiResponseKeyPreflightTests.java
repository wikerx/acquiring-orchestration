package com.scott.payment.openapi.support;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.openapi.service.MerchantSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiResponseKeyPreflightTests
 * @date : 2026-10-26 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 响应公钥前置门禁测试，确保密钥问题在调用支付核心之前失败。
 * @status : create
 */
class OpenApiResponseKeyPreflightTests {

    /** 验证通过后必须把同一把响应公钥绑定到请求上下文，供响应加密直接复用。 */
    @Test
    void shouldBindValidatedResponsePublicKeyToRequest() throws Exception {
        Fixture fixture = fixture();
        PublicKey publicKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        when(fixture.merchantSecurityService().getMerchantResponsePublicKey("200001")).thenReturn(publicKey);

        assertThat(fixture.interceptor().preHandle(
                fixture.request(), new MockHttpServletResponse(), fixture.handlerMethod())).isTrue();

        assertThat(fixture.request().getAttribute(OpenApiRequestAttributes.RESPONSE_PUBLIC_KEY))
                .isSameAs(publicKey);
    }

    /** 已认证商户缺少响应公钥时返回 C2002，不能继续进入业务控制器。 */
    @Test
    void shouldRejectMissingMerchantResponseKey() throws Exception {
        Fixture fixture = fixture();
        when(fixture.merchantSecurityService().getMerchantResponsePublicKey("200001"))
                // 密钥查询层已经掌握“配置缺失”事实，必须使用专用结构化码，禁止让调用方从旧 F409 猜测。
                .thenThrow(new ApiException(
                        OpenApiResultCode.MERCHANT_RESPONSE_KEY_MISSING, "merchantResponseKey"));

        assertThatThrownBy(() -> fixture.interceptor().preHandle(
                fixture.request(), new MockHttpServletResponse(), fixture.handlerMethod()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo(OpenApiResultCode.MERCHANT_RESPONSE_KEY_MISSING.getCode()));
    }

    /** 密钥存储或解析异常属于平台故障，公开响应不得伪装成商户配置缺失。 */
    @Test
    void shouldMapUnexpectedKeyLookupFailureToSystemError() throws Exception {
        Fixture fixture = fixture();
        when(fixture.merchantSecurityService().getMerchantResponsePublicKey("200001"))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> fixture.interceptor().preHandle(
                fixture.request(), new MockHttpServletResponse(), fixture.handlerMethod()))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo(OpenApiResultCode.SYSTEM_ERROR.getCode()))
                .hasMessage(OpenApiResultCode.SYSTEM_ERROR.getMessage());
    }

    private Fixture fixture() throws Exception {
        OpenApiRequestHeaderExtractor headerExtractor = mock(OpenApiRequestHeaderExtractor.class);
        OpenApiDiagnosticLogSupport diagnosticLogSupport = mock(OpenApiDiagnosticLogSupport.class);
        MerchantSecurityService merchantSecurityService = mock(MerchantSecurityService.class);
        OpenApiRequestHeaderDTO header = new OpenApiRequestHeaderDTO();
        header.setMerchantId("200001");
        when(headerExtractor.extract(any(HttpServletRequest.class), any(String[].class))).thenReturn(header);
        OpenApiHeaderInterceptor interceptor = new OpenApiHeaderInterceptor(
                headerExtractor, merchantSecurityService, diagnosticLogSupport);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/rest/payment/v1/payment");
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(),
                TestController.class.getDeclaredMethod("handle"));
        return new Fixture(interceptor, merchantSecurityService, request, handlerMethod);
    }

    private record Fixture(OpenApiHeaderInterceptor interceptor,
                           MerchantSecurityService merchantSecurityService,
                           MockHttpServletRequest request,
                           HandlerMethod handlerMethod) {
    }

    private static final class TestController {

        @VerificationAndProcessing(requiredHeaders = {"authorization"})
        public void handle() {
        }
    }
}
