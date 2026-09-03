package com.scott.payment.admin.client.settlement;

import com.scott.payment.admin.config.SettlementInternalClientProperties;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCommandResponse;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;

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
 * @classname : SettlementInternalRestClientTest
 * @date : 2026-09-01 23:20
 * @email : scott_x@163.com
 * @description : 验证 Admin 结算内部客户端的命令边界、完整目标签名和请求体摘要签名
 * @status : create
 */
class SettlementInternalRestClientTest {

    @Test
    void queryMethodsMustNotBeExposedByTheSettlementCommandClient() {
        assertThat(java.util.Arrays.stream(SettlementInternalClient.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)).doesNotContain("search", "detail");
    }

    @Test
    void cancellationShouldSignCompleteTrustedOperatorSnapshot() {
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
        request.setRequestKey("CANCEL-REQ-1");
        request.setExpectedVersion(3L);
        request.setReason("cancel before posting");
        request.setOperatorId(88L);
        request.setOperatorName("Settlement Operator");
        request.setRoleSnapshot("SETTLEMENT_OPERATOR");
        request.setClientIp("10.0.0.8");
        request.setUserAgent("JUnit Admin");
        request.setOperationTime(java.time.LocalDateTime.of(2026, 8, 31, 18, 0));

        client.cancel("SB20260826-00000001", request);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalanced).exchange(uriCaptor.capture(), eq(HttpMethod.POST),
                entityCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().getPath()).isEqualTo(
                "/internal/settlement/v1/batches/SB20260826-00000001/cancel");
        assertThat(entityCaptor.getValue().getBody()).contains(
                "\"requestKey\":\"CANCEL-REQ-1\"",
                "\"operatorId\":88",
                "\"roleSnapshot\":\"SETTLEMENT_OPERATOR\"",
                "\"clientIp\":\"10.0.0.8\"",
                "\"userAgent\":\"JUnit Admin\"",
                "\"operationTime\":\"2026-08-31 18:00:00\"");
        assertThat(entityCaptor.getValue().getHeaders()
                .getFirst(InternalServiceSignature.HEADER_SIGNATURE)).isNotBlank();
    }

    @Test
    void reversalSubmitShouldSignTrustedOperatorBody() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        SettlementInternalClientProperties properties = new SettlementInternalClientProperties();
        properties.setInternalSecret("unit-test-settlement-secret");
        when(loadBalanced.exchange(any(URI.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(CommonResult.success(
                        new com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalCommandResponse()))));
        SettlementInternalRestClient client = new SettlementInternalRestClient(
                direct, loadBalanced, properties);
        InternalReversalSubmitRequest request = new InternalReversalSubmitRequest();
        request.setRequestKey("REQ-1");
        request.setOriginalBatchNo("SB20260826-00000001");
        request.setExpectedBatchVersion(3L);
        request.setReason("approved");
        request.setOperatorId(88L);
        request.setOperatorName("Settlement Operator");

        client.submitReversal(request);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalanced).exchange(uriCaptor.capture(), eq(HttpMethod.POST),
                entityCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().getPath()).isEqualTo(
                "/internal/settlement/v1/reversal-orders");
        assertThat(entityCaptor.getValue().getBody())
                .contains("\"operatorId\":88", "\"operatorName\":\"Settlement Operator\"");
        assertThat(entityCaptor.getValue().getHeaders()
                .getFirst(InternalServiceSignature.HEADER_SIGNATURE)).isNotBlank();
    }

    @Test
    void reviewDecisionShouldUseVersionedSignedInternalResource() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        SettlementInternalClientProperties properties = new SettlementInternalClientProperties();
        properties.setInternalSecret("unit-test-settlement-secret");
        when(loadBalanced.exchange(any(URI.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(JsonUtils.toJsonString(
                        CommonResult.success(new ReviewCommandResponse()))));
        SettlementInternalRestClient client = new SettlementInternalRestClient(
                direct, loadBalanced, properties);
        InternalReviewDecisionRequest request = new InternalReviewDecisionRequest();
        request.setRequestKey("DECIDE-1");
        request.setExpectedVersion(3L);
        request.setDecision("APPROVE");
        request.setComment("checked");
        request.setOperatorId(99L);
        request.setOperatorName("Checker");
        request.setRoleSnapshot("SETTLEMENT_CHECKER");
        request.setClientIp("10.0.0.2");
        request.setUserAgent("JUnit");
        request.setOperationTime(java.time.LocalDateTime.of(2026, 8, 31, 9, 50));

        client.decideReview("SO20260831-00000001", request);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> entityCaptor =
                (ArgumentCaptor<HttpEntity<String>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(HttpEntity.class);
        verify(loadBalanced).exchange(uriCaptor.capture(), eq(HttpMethod.POST),
                entityCaptor.capture(), eq(String.class));
        assertThat(uriCaptor.getValue().getPath()).isEqualTo(
                "/internal/settlement/v1/reviews/SO20260831-00000001/decisions");
        assertThat(entityCaptor.getValue().getBody()).contains(
                "\"decision\":\"APPROVE\"", "\"operatorId\":99", "\"expectedVersion\":3");
        assertThat(entityCaptor.getValue().getHeaders()
                .getFirst(InternalServiceSignature.HEADER_SIGNATURE)).isNotBlank();
    }

    @Test
    void unauthorizedResponseShouldBeReportedAsInternalAuthenticationFailure() {
        RestTemplate direct = mock(RestTemplate.class);
        RestTemplate loadBalanced = mock(RestTemplate.class);
        SettlementInternalClientProperties properties = new SettlementInternalClientProperties();
        properties.setInternalSecret("unit-test-settlement-internal-secret-32-bytes");
        when(loadBalanced.exchange(any(URI.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class))).thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY,
                        new byte[0], StandardCharsets.UTF_8));
        SettlementInternalRestClient client = new SettlementInternalRestClient(
                direct, loadBalanced, properties);

        assertThatThrownBy(() -> client.submitReview(new InternalReviewSubmitRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("internal authentication failed");
    }
}
