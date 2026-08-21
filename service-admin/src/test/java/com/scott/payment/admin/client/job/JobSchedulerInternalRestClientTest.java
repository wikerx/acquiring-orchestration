package com.scott.payment.admin.client.job;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.admin.config.JobSchedulerClientProperties;
import com.scott.payment.component.core.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerInternalRestClientTest
 * @date : 2026-08-21 08:30
 * @email : scott_x@163.com
 * @description : 校验管理端调度内部客户端不会通过异常链或日志 Throwable 泄露下游响应正文。
 * @status : create
 */
class JobSchedulerInternalRestClientTest {

    /** 下游错误正文只用于 HTTP 状态判断，不得进入管理端异常链或日志 Throwable。 */
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
            JobSchedulerInternalRestClient client = new JobSchedulerInternalRestClient(
                    restTemplate, new JobSchedulerClientProperties(), discoveryClient);

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
}
