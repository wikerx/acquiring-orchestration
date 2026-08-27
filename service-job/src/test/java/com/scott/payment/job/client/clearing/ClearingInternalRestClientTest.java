package com.scott.payment.job.client.clearing;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Request;
import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Response;
import com.scott.payment.job.config.ClearingInternalClientProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClearingInternalRestClientTest {

    @Test
    void scanShouldBindCallerPathAndJsonBodyIntoHmacSignature() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        ClearingInternalClientProperties properties = new ClearingInternalClientProperties();
        properties.setInternalCaller("service-job-test");
        properties.setInternalSecret("unit-test-clearing-secret");
        Response downstream = new Response();
        downstream.setScannedCount(3);
        when(loadBalanced.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(CommonResult.success(downstream))));
        ClearingInternalRestClient client = new ClearingInternalRestClient(direct, loadBalanced, properties);
        Request request = new Request();
        request.setMode("DRY_RUN");
        request.setBeginTime(LocalDateTime.of(2026, 8, 26, 10, 0));
        request.setEndTime(LocalDateTime.of(2026, 8, 26, 10, 15));
        request.setLimit(200);

        assertThat(client.scan(request).getScannedCount()).isEqualTo(3);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalanced).exchange(
                uriCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        URI uri = uriCaptor.getValue();
        HttpEntity<String> entity = entityCaptor.getValue();
        long timestamp = Long.parseLong(entity.getHeaders().getFirst(InternalServiceSignature.HEADER_TIMESTAMP));
        String nonce = entity.getHeaders().getFirst(InternalServiceSignature.HEADER_NONCE);
        String expected = InternalServiceSignature.sign(
                HttpMethod.POST.name(), uri.getRawPath(), timestamp, nonce,
                properties.getInternalCaller(), InternalServiceSignature.payloadSha256(entity.getBody()),
                properties.getInternalSecret());

        assertThat(uri.getPath()).isEqualTo("/internal/clearing/v1/compensations/scan");
        assertThat(entity.getHeaders().getFirst(InternalServiceSignature.HEADER_CALLER))
                .isEqualTo("service-job-test");
        assertThat(entity.getHeaders().getFirst(InternalServiceSignature.HEADER_SIGNATURE)).isEqualTo(expected);
        assertThat(entity.getBody()).contains("\"mode\":\"DRY_RUN\"", "\"limit\":200");
    }
}
