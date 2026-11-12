package com.scott.payment.admin.client.job;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.admin.config.JobSchedulerClientProperties;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogResponse;
import com.scott.payment.admin.dto.monitor.JobExecutionTrendResponse;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 校验管理端调度内部客户端不会通过异常链或日志 Throwable 泄露下游响应正文。
 */
class JobSchedulerInternalRestClientTest {

    private static final String INTERNAL_SECRET = "unit-test-admin-job-secret-at-least-32-bytes";

    /**
     * Cookie 会话登录时，将 HttpOnly 会话中的令牌恢复为下游可校验的 Bearer 凭据。
     */
    @Test
    void shouldForwardCookieSessionAsBearerAuthorization() {
        RestTemplate restTemplate = successfulRestTemplate();
        JobSchedulerInternalRestClient client = client(restTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/monitor/job/handlers");
        request.setCookies(new Cookie("VEXRA_ADMIN_SESSION", "cookie-session-token"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            client.listHandlers();

            assertThat(capturedRequest(restTemplate).getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer cookie-session-token");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /**
     * 显式 Bearer 请求头优先于同一请求中的浏览器会话 Cookie，保持非浏览器调用兼容性。
     */
    @Test
    void shouldPreferExplicitBearerAuthorizationOverCookieSession() {
        RestTemplate restTemplate = successfulRestTemplate();
        JobSchedulerInternalRestClient client = client(restTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/monitor/job/handlers");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer explicit-session-token");
        request.setCookies(new Cookie("VEXRA_ADMIN_SESSION", "cookie-session-token"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        try {
            client.listHandlers();

            assertThat(capturedRequest(restTemplate).getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer explicit-session-token");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /**
     * 后台线程没有 Servlet 请求上下文时不伪造操作人身份，仍由下游鉴权失败关闭。
     */
    @Test
    void shouldNotAddAuthorizationWithoutRequestContext() {
        RestTemplate restTemplate = successfulRestTemplate();
        JobSchedulerInternalRestClient client = client(restTemplate);
        RequestContextHolder.resetRequestAttributes();

        client.listHandlers();

        assertThat(capturedRequest(restTemplate).getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    /**
     * 后台导出线程必须使用专用 HMAC 内部路径，不依赖浏览器管理员会话。
     */
    @Test
    void shouldUseHmacOnlyRunLogExportPathWithoutBrowserAuthorization() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(CommonResult.success(
                        PageResult.of(0L, 1L, 500L, List.<JobRunLogResponse>of())))));
        JobSchedulerInternalRestClient client = client(restTemplate);
        RequestContextHolder.resetRequestAttributes();

        client.pageRunLogsForExport(new JobRunLogQueryRequest());

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<HttpEntity<Object>> entityCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                uriCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().getPath()).isEqualTo("/internal/job/logs/export/search");
        assertThat(entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(entityCaptor.getValue().getHeaders().getFirst("X-Internal-Caller")).isNotBlank();
        assertThat(entityCaptor.getValue().getHeaders().getFirst("X-Internal-Signature")).isNotBlank();
    }

    /**
     * 最近七日趋势必须通过受保护的 Job 内部 GET 入口读取真实聚合结果。
     */
    @Test
    void shouldRequestExecutionTrendFromJobInternalEndpoint() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        JobExecutionTrendResponse response = new JobExecutionTrendResponse();
        response.setStartDate(LocalDate.of(2026, 11, 5));
        response.setEndDate(LocalDate.of(2026, 11, 11));
        when(restTemplate.exchange(
                any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(CommonResult.success(response))));
        JobSchedulerInternalRestClient client = client(restTemplate);

        JobExecutionTrendResponse actual = client.executionTrend();

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).exchange(
                uriCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        assertThat(uriCaptor.getValue().getPath()).isEqualTo("/internal/job/logs/trend");
        assertThat(actual.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(actual.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 11));
    }

    /**
     * 下游错误正文只用于 HTTP 状态判断，不得进入管理端异常链或日志 Throwable。
     */
    @Test
    void shouldNotPropagateDownstreamResponseBodyInExceptionOrLogThrowable() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        ServiceInstance serviceInstance = mock(ServiceInstance.class);
        when(serviceInstance.getUri()).thenReturn(URI.create("http://127.0.0.1:18089"));
        when(discoveryClient.getInstances("service-job")).thenReturn(List.of(serviceInstance));

        HttpServerErrorException downstreamFailure = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "failure",
                null,
                "{\"cardNo\":\"4111111111111111\",\"secretKey\":\"plain-text-secret\"}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(restTemplate.exchange(
                any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(downstreamFailure);

        Logger logger = (Logger) LoggerFactory.getLogger(JobSchedulerInternalRestClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            JobSchedulerClientProperties properties = new JobSchedulerClientProperties();
            properties.setInternalSecret(INTERNAL_SECRET);
            JobSchedulerInternalRestClient client = new JobSchedulerInternalRestClient(
                    restTemplate, properties, discoveryClient);

            assertThatThrownBy(client::listHandlers)
                    .isInstanceOfSatisfying(ApiException.class, exception -> {
                        assertThat(exception.getCause()).isNull();
                        assertThat(exception.getMessage())
                                .doesNotContain("4111111111111111")
                                .doesNotContain("plain-text-secret");
                    });

            assertThat(appender.list).isNotEmpty();
            assertThat(appender.list)
                    .allSatisfy(event -> {
                        assertThat(event.getFormattedMessage())
                                .doesNotContain("4111111111111111")
                                .doesNotContain("plain-text-secret");
                        assertThat(event.getThrowableProxy()).isNull();
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    /**
     * 创建返回空处理器列表的 RestTemplate，用于只验证客户端请求头组装行为。
     *
     * @return 已配置成功响应的 RestTemplate mock
     */
    private RestTemplate successfulRestTemplate() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.exchange(
                any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(CommonResult.success(List.of()))));
        return restTemplate;
    }

    /**
     * 创建使用固定强密钥和单个 Job 实例的待测客户端。
     *
     * @param restTemplate 用于捕获请求的 RestTemplate
     * @return 调度内部客户端
     */
    private JobSchedulerInternalRestClient client(RestTemplate restTemplate) {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        ServiceInstance serviceInstance = mock(ServiceInstance.class);
        when(serviceInstance.getUri()).thenReturn(URI.create("http://127.0.0.1:18089"));
        when(discoveryClient.getInstances("service-job")).thenReturn(List.of(serviceInstance));

        JobSchedulerClientProperties properties = new JobSchedulerClientProperties();
        properties.setInternalSecret(INTERNAL_SECRET);
        return new JobSchedulerInternalRestClient(restTemplate, properties, discoveryClient);
    }

    /**
     * 捕获客户端发送给 Job 服务的 GET 请求实体。
     *
     * @param restTemplate 已执行请求的 RestTemplate mock
     * @return 被发送的请求实体
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpEntity<Object> capturedRequest(RestTemplate restTemplate) {
        ArgumentCaptor<HttpEntity<Object>> entityCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                any(URI.class), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class));
        return entityCaptor.getValue();
    }
}
