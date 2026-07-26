package com.scott.payment.component.web.trace;

import com.scott.payment.component.core.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceIdRestTemplateInterceptorTest
 * @date : 未确认
 * @email : scott_x@163.com
 * @description : TraceIdRestTemplateInterceptorTest 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class TraceIdRestTemplateInterceptorTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldPropagateExistingTraceId() throws IOException {
        TraceContext.setTraceId("trace-123");
        TraceIdRestTemplateInterceptor interceptor = new TraceIdRestTemplateInterceptor();
        MockClientHttpRequest request = new MockClientHttpRequest();

        interceptor.intercept(request, new byte[0], captureOnlyExecution());

        assertThat(request.getHeaders().getFirst(TraceContext.TRACE_ID_HEADER)).isEqualTo("trace-123");
    }

    @Test
    void shouldCreateTraceIdWhenContextIsEmpty() throws IOException {
        TraceIdRestTemplateInterceptor interceptor = new TraceIdRestTemplateInterceptor();
        MockClientHttpRequest request = new MockClientHttpRequest();

        interceptor.intercept(request, new byte[0], captureOnlyExecution());

        String traceId = request.getHeaders().getFirst(TraceContext.TRACE_ID_HEADER);
        assertThat(traceId).hasSize(32);
        assertThat(traceId).isEqualTo(TraceContext.getTraceId());
    }

    @Test
    void shouldAppendInterceptorThroughCustomizer() {
        TraceIdRestTemplateInterceptor interceptor = new TraceIdRestTemplateInterceptor();
        TraceIdRestTemplateCustomizer customizer = new TraceIdRestTemplateCustomizer(interceptor);
        RestTemplate restTemplate = new RestTemplate();

        customizer.customize(restTemplate);

        assertThat(restTemplate.getInterceptors()).contains(interceptor);
    }

    private ClientHttpRequestExecution captureOnlyExecution() {
        return (HttpRequest request, byte[] body) -> mock(ClientHttpResponse.class);
    }
}
