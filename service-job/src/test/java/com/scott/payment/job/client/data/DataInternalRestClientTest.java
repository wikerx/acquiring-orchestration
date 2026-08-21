package com.scott.payment.job.client.data;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyDueClientRequestDTO;
import com.scott.payment.job.config.DataInternalClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataInternalRestClientTest
 * @date : 2026-08-21 08:25
 * @email : scott_x@163.com
 * @description : 校验商户通知补偿内部客户端不会通过异常 cause 泄露 service-data 响应正文。
 * @status : create
 */
class DataInternalRestClientTest {

    /** 下游错误正文只用于 HTTP 边界判断，不得继续进入异常链。 */
    @Test
    void shouldNotPropagateDownstreamResponseBodyInExceptionCause() {
        RestTemplate directRestTemplate = mock(RestTemplate.class);
        RestTemplate loadBalancedRestTemplate = mock(RestTemplate.class);
        DataInternalClientProperties properties = new DataInternalClientProperties();
        properties.setInternalCaller("service-job-test");
        properties.setInternalSecret("unit-test-internal-secret");
        HttpServerErrorException downstreamFailure = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "failure",
                null,
                "{\"cardNo\":\"4111111111111111\",\"secretKey\":\"plain-text-secret\"}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(loadBalancedRestTemplate.exchange(
                any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(downstreamFailure);
        DataInternalRestClient client = new DataInternalRestClient(
                directRestTemplate, loadBalancedRestTemplate, properties);
        DataMerchantNotificationNotifyDueClientRequestDTO request =
                new DataMerchantNotificationNotifyDueClientRequestDTO();

        assertThatThrownBy(() -> client.notifyDueMerchantNotifications(request))
                .isInstanceOfSatisfying(ServiceException.class, exception -> {
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage())
                            .doesNotContain("4111111111111111")
                            .doesNotContain("plain-text-secret");
                });
    }
}
