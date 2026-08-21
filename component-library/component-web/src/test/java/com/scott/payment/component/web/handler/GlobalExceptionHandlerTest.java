package com.scott.payment.component.web.handler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.exception.BizException;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.exception.TransactionDataUnavailableException;
import com.scott.payment.component.core.model.CommonResult;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    /** 所有 F500 异常都只向商户返回统一繁忙提示，不暴露内部失败详情。 */
    @Test
    void shouldReturnStandardMerchantMessageForAllF500Exceptions() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertStandardInternalError(handler.handleApiException(
                    new ApiException("F500", "sensitive api failure detail")));
            assertStandardInternalError(handler.handleServiceException(
                    new ServiceException("F500", "sensitive service failure detail")));
            assertStandardInternalError(handler.handleBizException(
                    new BizException("F500", "sensitive business failure detail")));

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .allMatch(message -> !message.contains("sensitive") && !message.contains("failure detail"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    /** ShardingSphere 包装路由异常后，Web 边界仍应返回明确的季度数据不可用错误。 */
    @Test
    void shouldReturnTransactionDataUnavailableForWrappedMissingNodeFailure() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException wrapped = new RuntimeException("jdbc wrapper",
                new TransactionDataUnavailableException("transaction_operation", "2026-Q2", "test-001"));

        CommonResult<Void> result = handler.handleException(wrapped);

        assertThat(result.getCode()).isEqualTo(ApiResultEnum.TRANSACTION_DATA_UNAVAILABLE.getCode());
        assertThat(result.getMessage()).isEqualTo(ApiResultEnum.TRANSACTION_DATA_UNAVAILABLE.getMessage());
    }

    /** 普通未捕获异常仍由 F500 兜底，避免错误扩大为所有数据库故障。 */
    @Test
    void shouldKeepGenericFailureForUnrelatedException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        CommonResult<Void> result = handler.handleException(new IllegalStateException("unrelated"));

        assertThat(result.getCode()).isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode());
    }

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

    /** 参数校验失败时只返回字段名和约束文案，不能暴露 rejected value。 */
    @Test
    void shouldNotExposeRejectedValueForMethodArgumentValidation() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        String sensitiveValue = "4111111111111111";
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError(
                "request", "cardNumber", sensitiveValue, false, null, null, "Card number format is invalid."));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        CommonResult<Void> result = handler.handleValidException(exception);

        assertThat(result.getMessage()).isEqualTo("cardNumber: Card number format is invalid.");
        assertThat(result.getMessage()).doesNotContain(sensitiveValue);
    }

    /** 请求正文无法解析时统一返回格式错误，日志与响应都不能包含原始 JSON 片段。 */
    @Test
    void shouldNotExposeUnreadableRequestBodyInResponseOrLog() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        String sensitiveBody = "{\"secretKey\":\"must-not-enter-response\"}";
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException(sensitiveBody);
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CommonResult<Void> result = handler.handleRequestParameterException(exception);

            assertThat(result.getMessage()).isEqualTo("Invalid request body format.");
            assertThat(result.getMessage()).doesNotContain("secretKey", "must-not-enter-response");
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .allMatch(message -> !message.contains("secretKey")
                            && !message.contains("must-not-enter-response"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void assertStandardInternalError(CommonResult<Void> result) {
        assertThat(result.getCode()).isEqualTo("F500");
        assertThat(result.getMessage()).isEqualTo("The system is busy; please try again later.");
        assertThat(result.getData()).isNull();
    }
}
