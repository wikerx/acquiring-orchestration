package com.scott.payment.admin.client.settlement;

import com.scott.payment.admin.config.SettlementInternalClientProperties;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Admin 结算客户端对完整目标和请求体摘要签名。 */
class SettlementInternalRestClientTest {

    @Test
    void detailAndCommandShouldUseVersionedSignedInternalResources() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        SettlementInternalClientProperties properties = new SettlementInternalClientProperties();
        properties.setInternalSecret("unit-test-settlement-secret");
        when(loadBalanced.exchange(any(URI.class), any(HttpMethod.class),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(
                        CommonResult.success(new BatchDetailResponse()))));
        SettlementInternalRestClient client = new SettlementInternalRestClient(
                direct, loadBalanced, properties);

        assertThat(client.detail("SB20260826-00000001")).isNotNull();

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalanced).exchange(uriCaptor.capture(), eq(HttpMethod.GET),
                entityCaptor.capture(), eq(String.class));
        URI uri = uriCaptor.getValue();
        HttpEntity<String> entity = entityCaptor.getValue();
        long timestamp = Long.parseLong(
                entity.getHeaders().getFirst(InternalServiceSignature.HEADER_TIMESTAMP));
        String nonce = entity.getHeaders().getFirst(InternalServiceSignature.HEADER_NONCE);
        String expected = InternalServiceSignature.sign(HttpMethod.GET.name(),
                InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp, nonce, properties.getInternalCaller(),
                InternalServiceSignature.payloadSha256((String) null), properties.getInternalSecret());
        assertThat(uri.getPath()).isEqualTo(
                "/internal/settlement/v1/batches/SB20260826-00000001");
        assertThat(entity.getHeaders().getFirst(InternalServiceSignature.HEADER_SIGNATURE))
                .isEqualTo(expected);
    }

    @Test
    void reverseShouldSignTrustedOperatorBody() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        SettlementInternalClientProperties properties = new SettlementInternalClientProperties();
        properties.setInternalSecret("unit-test-settlement-secret");
        when(loadBalanced.exchange(any(URI.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(CommonResult.success(
                        new com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandResponse()))));
        SettlementInternalRestClient client = new SettlementInternalRestClient(
                direct, loadBalanced, properties);
        InternalBatchCommandRequest request = new InternalBatchCommandRequest();
        request.setRequestKey("REQ-1");
        request.setExpectedVersion(3L);
        request.setReason("approved");
        request.setOperator("admin-account:88/Settlement Operator");

        client.reverse("SB20260826-00000001", request);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalanced).exchange(uriCaptor.capture(), eq(HttpMethod.POST),
                entityCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().getPath()).endsWith("/SB20260826-00000001/reverse");
        assertThat(entityCaptor.getValue().getBody())
                .contains("\"operator\":\"admin-account:88/Settlement Operator\"");
        assertThat(entityCaptor.getValue().getHeaders()
                .getFirst(InternalServiceSignature.HEADER_SIGNATURE)).isNotBlank();
    }
}
