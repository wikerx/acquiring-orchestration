package com.scott.payment.admin.client.clearing;

import com.scott.payment.admin.config.ClearingInternalClientProperties;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.DetailResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalReserveAdjustmentSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalTierPeriodReplaySubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplayResponse;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
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
    void detailShouldBindEncodedShardTimeQueryIntoHmacSignature() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        ClearingInternalClientProperties properties = new ClearingInternalClientProperties();
        properties.setInternalCaller("service-admin-test");
        properties.setInternalSecret("unit-test-clearing-secret");
        when(loadBalanced.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(CommonResult.success(new DetailResponse()))));
        ClearingInternalRestClient client = new ClearingInternalRestClient(direct, loadBalanced, properties);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 25, 9, 0);

        assertThat(client.detail("TX-1", transactionTime)).isNotNull();

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalanced).exchange(
                uriCaptor.capture(), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class));
        URI uri = uriCaptor.getValue();
        HttpEntity<String> entity = entityCaptor.getValue();
        long timestamp = Long.parseLong(entity.getHeaders().getFirst(InternalServiceSignature.HEADER_TIMESTAMP));
        String nonce = entity.getHeaders().getFirst(InternalServiceSignature.HEADER_NONCE);
        String expected = InternalServiceSignature.sign(
                HttpMethod.GET.name(), InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp, nonce, properties.getInternalCaller(),
                InternalServiceSignature.payloadSha256((String) null), properties.getInternalSecret());
        String pathOnly = InternalServiceSignature.sign(
                HttpMethod.GET.name(), uri.getRawPath(), timestamp, nonce, properties.getInternalCaller(),
                InternalServiceSignature.payloadSha256((String) null), properties.getInternalSecret());

        assertThat(uri.getPath()).isEqualTo("/internal/clearing/v1/transactions/TX-1");
        assertThat(uri.getRawQuery()).contains("transactionDateTime=");
        assertThat(entity.getHeaders().getFirst(InternalServiceSignature.HEADER_SIGNATURE))
                .isEqualTo(expected)
                .isNotEqualTo(pathOnly);
    }

    @Test
    void reserveAdjustmentSubmitShouldUseDedicatedSignedInternalResource() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        ClearingInternalClientProperties properties = new ClearingInternalClientProperties();
        properties.setInternalCaller("service-admin-test");
        properties.setInternalSecret("unit-test-clearing-secret");
        when(loadBalanced.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(
                        CommonResult.success(new ReserveAdjustmentResponse()))));
        ClearingInternalRestClient client = new ClearingInternalRestClient(direct, loadBalanced, properties);
        InternalReserveAdjustmentSubmitRequest request = new InternalReserveAdjustmentSubmitRequest();
        request.setRequestKey("REQ-1");
        request.setSubmitOperator("admin-account:88/Clearing Operator");

        assertThat(client.submitReserveAdjustment(request)).isNotNull();

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalanced).exchange(
                uriCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().getPath()).isEqualTo("/internal/clearing/v1/reserve-adjustments");
        assertThat(entityCaptor.getValue().getBody())
                .contains("\"requestKey\":\"REQ-1\"")
                .contains("\"submitOperator\":\"admin-account:88/Clearing Operator\"");
        assertThat(entityCaptor.getValue().getHeaders()
                .getFirst(InternalServiceSignature.HEADER_SIGNATURE)).isNotBlank();
    }

    @Test
    void tierPeriodReplaySubmitShouldUseDedicatedSignedInternalResource() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        ClearingInternalClientProperties properties = new ClearingInternalClientProperties();
        properties.setInternalCaller("service-admin-test");
        properties.setInternalSecret("unit-test-clearing-secret");
        when(loadBalanced.exchange(any(URI.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(
                        CommonResult.success(new TierPeriodReplayResponse()))));
        ClearingInternalRestClient client = new ClearingInternalRestClient(direct, loadBalanced, properties);
        InternalTierPeriodReplaySubmitRequest request = new InternalTierPeriodReplaySubmitRequest();
        request.setRequestKey("REQ-TIER-1");
        request.setMerchantId("M-1");
        request.setPeriodKey("202608");
        request.setSubmitOperator("admin-account:88/Clearing Operator");

        assertThat(client.submitTierPeriodReplay(request)).isNotNull();

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalanced).exchange(
                uriCaptor.capture(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().getPath())
                .isEqualTo("/internal/clearing/v1/tier-period-replays");
        assertThat(entityCaptor.getValue().getBody())
                .contains("\"requestKey\":\"REQ-TIER-1\"")
                .contains("\"submitOperator\":\"admin-account:88/Clearing Operator\"");
        assertThat(entityCaptor.getValue().getHeaders()
                .getFirst(InternalServiceSignature.HEADER_SIGNATURE)).isNotBlank();
    }
}
