package com.scott.payment.job.client.admin;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Request;
import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Response;
import com.scott.payment.job.client.admin.dto.OperationalWorkItemSyncClientDTOs;
import com.scott.payment.job.config.AdminInternalClientProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * service-admin 汇率波动评估内部客户端契约测试。
 */
class AdminInternalRestClientTest {

    /**
     * 签名必须同时绑定固定路径和最终 JSON 正文，避免业务参数被替换。
     */
    @Test
    void shouldBindPathAndRequestBodyIntoSignature() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        AdminInternalClientProperties properties = properties();
        Response downstream = new Response();
        downstream.setRequestKey("EXCHANGE_RATE_VOLATILITY_MONITOR:run-1");
        downstream.setEvaluatedRuleCount(2);
        when(loadBalanced.exchange(
                any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(CommonResult.success(downstream))));
        AdminInternalRestClient client = new AdminInternalRestClient(direct, loadBalanced, properties);
        Request request = request();

        Response response = client.evaluateExchangeRateAlerts(request);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalanced).exchange(
                uriCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        URI uri = uriCaptor.getValue();
        HttpEntity<String> entity = entityCaptor.getValue();
        long timestamp = Long.parseLong(
                entity.getHeaders().getFirst(InternalServiceSignature.HEADER_TIMESTAMP));
        String nonce = entity.getHeaders().getFirst(InternalServiceSignature.HEADER_NONCE);
        String expected = InternalServiceSignature.sign(
                HttpMethod.POST.name(),
                uri.getRawPath(),
                timestamp,
                nonce,
                properties.getInternalCaller(),
                InternalServiceSignature.payloadSha256(entity.getBody()),
                properties.getInternalSecret());

        assertThat(response.getEvaluatedRuleCount()).isEqualTo(2);
        assertThat(uri.getPath()).isEqualTo("/internal/admin/exchange/alerts/evaluate");
        assertThat(entity.getBody()).contains("EXCHANGE_RATE_VOLATILITY_MONITOR:run-1");
        assertThat(entity.getHeaders().getFirst(InternalServiceSignature.HEADER_CALLER))
                .isEqualTo("service-job");
        assertThat(entity.getHeaders().getFirst(InternalServiceSignature.HEADER_SIGNATURE))
                .isEqualTo(expected);
    }

    /**
     * 下游错误正文可能包含敏感信息，不得进入跨服务异常消息或 cause。
     */
    @Test
    void shouldNotPropagateDownstreamResponseBody() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        HttpServerErrorException downstreamFailure = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "failure",
                null,
                "{\"secret\":\"plain-text-secret\",\"cardNo\":\"4111111111111111\"}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(loadBalanced.exchange(
                any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(downstreamFailure);
        AdminInternalRestClient client = new AdminInternalRestClient(direct, loadBalanced, properties());

        assertThatThrownBy(() -> client.evaluateExchangeRateAlerts(request()))
                .isInstanceOfSatisfying(ServiceException.class, exception -> {
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage())
                            .doesNotContain("plain-text-secret")
                            .doesNotContain("4111111111111111");
                });
    }

    /** 统一待办同步复用同一 HMAC 契约，并使用独立的内部路径。 */
    @Test
    void shouldSynchronizeOperationalWorkItemsWithSignedRequest() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        OperationalWorkItemSyncClientDTOs.Response downstream =
                new OperationalWorkItemSyncClientDTOs.Response();
        downstream.setCreatedCount(3);
        when(loadBalanced.exchange(
                any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(CommonResult.success(downstream))));
        AdminInternalRestClient client = new AdminInternalRestClient(direct, loadBalanced, properties());
        OperationalWorkItemSyncClientDTOs.Request request =
                new OperationalWorkItemSyncClientDTOs.Request();
        request.setRequestKey("OPERATIONAL_WORK_ITEM_SYNC:run-2");
        request.setSyncTime(LocalDateTime.of(2026, 11, 5, 9, 30));

        OperationalWorkItemSyncClientDTOs.Response response =
                client.synchronizeOperationalWorkItems(request);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalanced).exchange(
                uriCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        assertThat(response.getCreatedCount()).isEqualTo(3);
        assertThat(uriCaptor.getValue().getPath())
                .isEqualTo("/internal/admin/operations/work-items/sync");
        assertThat(entityCaptor.getValue().getBody())
                .contains("OPERATIONAL_WORK_ITEM_SYNC:run-2");
        assertThat(entityCaptor.getValue().getHeaders()
                .getFirst(InternalServiceSignature.HEADER_SIGNATURE)).isNotBlank();
    }

    private AdminInternalClientProperties properties() {
        AdminInternalClientProperties properties = new AdminInternalClientProperties();
        properties.setInternalCaller("service-job");
        properties.setInternalSecret("unit-test-internal-secret");
        return properties;
    }

    private Request request() {
        Request request = new Request();
        request.setRequestKey("EXCHANGE_RATE_VOLATILITY_MONITOR:run-1");
        request.setEvaluationTime(LocalDateTime.of(2026, 11, 2, 10, 0));
        return request;
    }
}
