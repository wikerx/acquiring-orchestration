package com.scott.payment.component.web.trace;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.component.core.trace.TraceContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HttpTrafficLoggingFilterTest
 * @date : 2026-07-26 16:50
 * @email : scott_x@163.com
 * @description : HTTP 请求响应摘要日志过滤器测试，验证通用链路日志可见且不会泄露 OpenAPI 密文、认证头、卡号或安全码。
 * @status : create
 */
class HttpTrafficLoggingFilterTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldLogMaskedTrafficSummaryAndCopyResponseBody() throws ServletException, IOException {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpTrafficLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Level originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        try {
            TraceContext.setTraceId("trace-test-001");
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rest/payment/v1/payment");
            request.setContentType("application/json");
            request.setCharacterEncoding("UTF-8");
            request.setContent("""
                    {"data":"abcdefghijklmnopqrstuvwxyz","authorization":"Bearer secret-token","cardNo":"5387380678556554","securityCode":"123"}
                    """.getBytes());
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain(new HttpServlet() {
                @Override
                public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws IOException {
                    servletRequest.getInputStream().readAllBytes();
                    servletResponse.setContentType("application/json");
                    servletResponse.getWriter().write("""
                            {"code":"T200","data":"encrypted-response","cardNo":"5387380678556554","securityCode":"123"}
                            """);
                }
            });

            new HttpTrafficLoggingFilter().doFilter(request, response, chain);

            assertThat(response.getContentAsString()).contains("\"code\":\"T200\"");
            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(logs).contains("event: HTTP_TRAFFIC_SUMMARY");
            assertThat(logs).contains("traceId: trace-test-001");
            assertThat(logs).contains("requestDigest:");
            assertThat(logs).contains("requestSummary:");
            assertThat(logs).contains("538738******6554");
            assertThat(logs).contains("securityCode\":\"***");
            assertThat(logs).doesNotContain("5387380678556554");
            assertThat(logs).doesNotContain("secret-token");
            assertThat(logs).doesNotContain("abcdefghijklmnopqrstuvwxyz");
            assertThat(logs).doesNotContain("encrypted-response");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }
    }
}
