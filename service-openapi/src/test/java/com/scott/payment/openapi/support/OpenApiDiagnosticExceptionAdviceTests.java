package com.scott.payment.openapi.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessDeniedException;
import com.scott.payment.openapi.security.MerchantIpWhitelistDenyReason;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiDiagnosticExceptionAdviceTests
 * @date : 2026-10-17 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI IP 准入拒绝响应和诊断日志契约测试。
 * @status : create
 */
class OpenApiDiagnosticExceptionAdviceTests {

    @Test
    void shouldKeepErrorEnvelopeAndLogNormalizedRequestIp() {
        OpenApiDiagnosticExceptionAdvice advice = new OpenApiDiagnosticExceptionAdvice(
                mock(OpenApiDiagnosticLogSupport.class)
        );
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
            CommonResult<Void> result = advice.handleApiException(exception, request);

            assertThat(result.getCode()).isEqualTo("F403");
            assertThat(result.getMessage())
                    .isEqualTo("Forbidden:merchant ip not allowed, requestIp: 203.0.113.10");
            assertThat(result.getData()).isNull();
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("event: OPENAPI_REQUEST_ABORTED")
                            .contains("requestIp: 203.0.113.10"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
