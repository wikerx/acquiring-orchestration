package com.scott.payment.job.client.payment;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.job.config.PaymentInternalClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalRestClientTest
 * @date : 2026-08-20 21:20
 * @email : scott_x@163.com
 * @description : 校验支付超时关单内部客户端的查询参数、服务发现路由和 HMAC 路径签名契约
 * @status : create
 */
@Slf4j
class PaymentInternalRestClientTest {

    /** limit 必须同时进入查询参数和内部签名原文，防止内部请求参数被替换。 */
    @Test
    void shouldSignTimeoutCloseRequestWithUriPathOnly() {
        log.info("用例开始：校验超时关单内部请求携带 limit 查询参数且签名绑定完整请求目标");
        RestTemplate directRestTemplate = mock(RestTemplate.class);
        RestTemplate loadBalancedRestTemplate = mock(RestTemplate.class);
        PaymentInternalClientProperties properties = new PaymentInternalClientProperties();
        properties.setInternalCaller("service-job-test");
        properties.setInternalSecret("unit-test-internal-secret");
        when(loadBalancedRestTemplate.exchange(
                any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(CommonResult.success(7))));
        PaymentInternalRestClient client = new PaymentInternalRestClient(
                directRestTemplate, loadBalancedRestTemplate, properties);

        int expiredCount = client.expireDueCheckoutSessions(120);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalancedRestTemplate).exchange(
                uriCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        URI uri = uriCaptor.getValue();
        HttpEntity<String> entity = entityCaptor.getValue();
        long timestamp = Long.parseLong(entity.getHeaders().getFirst(InternalServiceSignature.HEADER_TIMESTAMP));
        String nonce = entity.getHeaders().getFirst(InternalServiceSignature.HEADER_NONCE);
        String actualSignature = entity.getHeaders().getFirst(InternalServiceSignature.HEADER_SIGNATURE);
        String bodyDigest = InternalServiceSignature.payloadSha256(entity.getBody());
        String queryBoundSignature = InternalServiceSignature.sign(
                HttpMethod.POST.name(), uri.getRawPath() + "?" + uri.getRawQuery(), timestamp, nonce,
                properties.getInternalCaller(), bodyDigest, properties.getInternalSecret());
        String pathOnlySignature = InternalServiceSignature.sign(
                HttpMethod.POST.name(), uri.getRawPath(), timestamp, nonce,
                properties.getInternalCaller(), bodyDigest, properties.getInternalSecret());

        assertThat(expiredCount).isEqualTo(7);
        assertThat(uri.getPath()).isEqualTo("/internal/payment/checkout/session/expire-due");
        assertThat(uri.getRawQuery()).isEqualTo("limit=120");
        assertThat(entity.getHeaders().getFirst(InternalServiceSignature.HEADER_CALLER))
                .isEqualTo("service-job-test");
        assertThat(actualSignature).isEqualTo(queryBoundSignature).isNotEqualTo(pathOnlySignature);
        log.info("用例结果：limit=120 已进入查询参数和签名原文");
    }

    /** 下游错误正文可能包含敏感字段，不得挂到跨服务异常 cause。 */
    @Test
    void shouldNotPropagateDownstreamResponseBodyInExceptionCause() {
        RestTemplate directRestTemplate = mock(RestTemplate.class);
        RestTemplate loadBalancedRestTemplate = mock(RestTemplate.class);
        PaymentInternalClientProperties properties = new PaymentInternalClientProperties();
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
        PaymentInternalRestClient client = new PaymentInternalRestClient(
                directRestTemplate, loadBalancedRestTemplate, properties);

        assertThatThrownBy(() -> client.expireDueCheckoutSessions(100))
                .isInstanceOfSatisfying(ServiceException.class, exception -> {
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage())
                            .doesNotContain("4111111111111111")
                            .doesNotContain("plain-text-secret");
                });
    }
}
