package com.scott.payment.openapi.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.exception.RateLimitExceededException;
import com.scott.payment.component.web.handler.GlobalExceptionHandler;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessDeniedException;
import com.scott.payment.openapi.security.MerchantIpWhitelistDenyReason;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAPI IP 准入拒绝响应和诊断日志契约测试。
 */
class OpenApiDiagnosticExceptionAdviceTests {

    /**
     * 商户 OpenAPI 限流必须同时返回 A4001、HTTP 429 和动态 Retry-After。
     */
    @Test
    void shouldReturnStandardRateLimitResponse() {
        OpenApiDiagnosticExceptionAdvice advice = newAdvice();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/rest/payment/v1/payment");

        ResponseEntity<CommonResult<Void>> response = advice.handleRateLimitExceeded(
                new RateLimitExceededException(17L), request);

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("17");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("A4001");
        assertThat(response.getBody().getMessage()).isEqualTo(
                "Too many requests. Wait as instructed before retrying.");
    }

    /**
     * 方法错误切换为五位业务码时仍保留本版本原有 HTTP 200 契约。
     */
    @Test
    void shouldMapMethodNotAllowedBeforeControllerInvocation() {
        OpenApiDiagnosticExceptionAdvice advice = newAdvice();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/rest/payment/v1/payment");

        ResponseEntity<CommonResult<Void>> response = advice.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("GET"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("A1006");
    }

    /**
     * Hosted Checkout JSON 接口使用付款人文案，不能返回内部或商户诊断说明。
     */
    @Test
    void shouldUsePayerContractForHostedCheckoutJsonApi() {
        OpenApiDiagnosticExceptionAdvice advice = newAdvice();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/checkout/api/v1/session");
        MerchantIpWhitelistAccessDeniedException exception = new MerchantIpWhitelistAccessDeniedException(
                MerchantIpWhitelistDenyReason.IP_NOT_ALLOWED, "203.0.113.10");

        ResponseEntity<CommonResult<Void>> response = advice.handleApiException(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("A3003");
        assertThat(response.getBody().getMessage())
                .isEqualTo("This operation is unavailable. Contact the merchant.");
    }

    /**
     * Hosted Checkout JSON 缺参异常使用 PAYER 版 A1002，不向付款人暴露字段路径。
     */
    @Test
    void shouldUsePayerMessageForHostedCheckoutMissingParameter() {
        OpenApiDiagnosticExceptionAdvice advice = newAdvice();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/checkout/api/v1/session/query");

        ResponseEntity<CommonResult<Void>> response = advice.handleMissingParameterException(
                new MissingServletRequestParameterException("sessionId", "String"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("A1002");
        assertThat(response.getBody().getMessage())
                .isEqualTo("Complete the required information and submit again.");
        assertThat(response.getBody().getMessage()).doesNotContain("sessionId");
    }

    /**
     * 商户 OpenAPI 缺参响应应指出受控字段名，且不能拼接请求值或异常原文。
     */
    @Test
    void shouldExposeSafeMissingFieldToMerchant() {
        OpenApiDiagnosticExceptionAdvice advice = newAdvice();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/rest/payment/v1/payment");

        ResponseEntity<CommonResult<Void>> response = advice.handleMissingParameterException(
                new MissingServletRequestParameterException("orderInfo.orderId", "String"), request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("A1002");
        assertThat(response.getBody().getMessage())
                .isEqualTo("Required request parameter is missing: orderInfo.orderId.");
    }

    /**
     * Bean Validation 只公开 BindingResult 的字段路径，不公开 rejectedValue 或校验异常原文。
     */
    @Test
    void shouldExposeSafeValidationFieldWithoutRejectedValue() throws Exception {
        OpenApiDiagnosticExceptionAdvice advice = newAdvice();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/rest/payment/v1/payment");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError(
                "request", "orderInfo.orderId", "sensitive-rejected-value", false,
                null, null, "must match internal detail"));
        Method method = OpenApiDiagnosticExceptionAdviceTests.class
                .getDeclaredMethod("validatedArgument", Object.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(method, 0), bindingResult);

        ResponseEntity<CommonResult<Void>> response = advice.handleValidationException(exception, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("A1001");
        assertThat(response.getBody().getMessage())
                .isEqualTo("Invalid request parameter: orderInfo.orderId. Check the API requirements.")
                .doesNotContain("sensitive-rejected-value", "internal detail");
    }

    /**
     * 类型转换异常仅公开受控字段路径，不回显原始参数值或底层转换异常。
     */
    @Test
    void shouldExposeSafeTypeMismatchFieldWithoutRejectedValue() throws Exception {
        OpenApiDiagnosticExceptionAdvice advice = newAdvice();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/rest/payment/v1/payment");
        Method method = OpenApiDiagnosticExceptionAdviceTests.class
                .getDeclaredMethod("validatedArgument", Object.class);
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "sensitive-request-value", Long.class, "orderInfo.amount",
                new MethodParameter(method, 0), new IllegalArgumentException("internal conversion detail"));

        ResponseEntity<CommonResult<Void>> response = advice.handleValidationException(exception, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("A1001");
        assertThat(response.getBody().getMessage())
                .isEqualTo("Invalid request parameter: orderInfo.amount. Check the API requirements.")
                .doesNotContain("sensitive-request-value", "internal conversion detail");
    }

    /**
     * 3DS HTML bridge 和渠道回调保持既有协议，不能被 Checkout 的 PAYER 规则误覆盖。
     */
    @Test
    void shouldPreserveLegacyContractForBridgeAndChannelCallback() {
        OpenApiDiagnosticExceptionAdvice advice = newAdvice();
        // 旧命名空间隔离测试必须显式构造旧内部异常，不能复用已迁移为 A3003 的准入异常。
        ApiException exception = new ApiException("F403", "Forbidden");

        ResponseEntity<CommonResult<Void>> bridgeResponse = advice.handleApiException(
                exception, new MockHttpServletRequest("POST", "/checkout/api/v1/3ds/bridge"));
        ResponseEntity<CommonResult<Void>> callbackResponse = advice.handleApiException(
                exception, new MockHttpServletRequest("POST", "/channel/v1/callbacks/mpgs"));

        assertThat(bridgeResponse.getBody()).isNotNull();
        assertThat(bridgeResponse.getBody().getCode()).isEqualTo("F403");
        assertThat(bridgeResponse.getBody().getMessage()).isEqualTo("Forbidden");
        assertThat(callbackResponse.getBody()).isNotNull();
        assertThat(callbackResponse.getBody().getCode()).isEqualTo("F403");
        assertThat(callbackResponse.getBody().getMessage()).isEqualTo("Forbidden");
    }

    /**
     * 验证异常终止日志只记录客户端 IP 摘要，并保留稳定错误上下文供排查。
     */
    @Test
    void shouldKeepErrorEnvelopeAndLogNormalizedRequestIp() {
        OpenApiDiagnosticExceptionAdvice advice = newAdvice();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rest/payment/v1/payment");
        MerchantIpWhitelistAccessDeniedException exception = new MerchantIpWhitelistAccessDeniedException(
                MerchantIpWhitelistDenyReason.IP_NOT_ALLOWED,
                "203.0.113.10"
        );
        Logger logger = (Logger) LoggerFactory.getLogger(OpenApiDiagnosticExceptionAdvice.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ResponseEntity<CommonResult<Void>> response = advice.handleApiException(exception, request);
            CommonResult<Void> result = response.getBody();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("A3003");
            assertThat(result.getMessage())
                    .isEqualTo("This request source is not allowed. Check the access settings.");
            assertThat(result.getData()).isNull();
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("event: OPENAPI_REQUEST_ABORTED")
                            .contains("requestIpDigest: 631f08140b24b727")
                            .doesNotContain("requestIp: 203.0.113.10"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private OpenApiDiagnosticExceptionAdvice newAdvice() {
        return new OpenApiDiagnosticExceptionAdvice(
                new OpenApiDiagnosticLogSupport(),
                new GlobalExceptionHandler());
    }

    @SuppressWarnings("unused")
    private void validatedArgument(Object request) {
        // 仅用于构造 Spring MethodParameter，测试不会执行该方法。
    }
}
