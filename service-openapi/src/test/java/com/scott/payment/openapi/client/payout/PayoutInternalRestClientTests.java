package com.scott.payment.openapi.client.payout;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientRequestDTO;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientResponseDTO;
import com.scott.payment.openapi.config.PayoutClientProperties;
import com.scott.payment.openapi.result.OpenApiContextualException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 OpenAPI 调用代付核心时的统一响应解包、业务码保留和 unknown 结果处理。
 */
class PayoutInternalRestClientTests {

    /**
     * service-payout 创建接口固定服务地址。
     */
    private static final String PAYOUT_CREATE_URL = "http://service-payout/internal/payout/create";

    /**
     * 直连客户端，本合同中固定服务名不会选择该客户端。
     */
    private RestTemplate directRestTemplate;

    /**
     * 服务发现客户端，用于模拟 service-payout 返回。
     */
    private RestTemplate loadBalancedRestTemplate;

    /**
     * 被测代付内部客户端。
     */
    private PayoutInternalRestClient payoutInternalRestClient;

    /**
     * 初始化内部签名配置和两个 REST 客户端替身。
     */
    @BeforeEach
    void setUp() {
        directRestTemplate = mock(RestTemplate.class);
        loadBalancedRestTemplate = mock(RestTemplate.class);
        PayoutClientProperties properties = new PayoutClientProperties();
        properties.setInternalCaller("service-openapi");
        properties.setInternalSecret("0123456789abcdef0123456789abcdef");
        payoutInternalRestClient = new PayoutInternalRestClient(
                directRestTemplate, loadBalancedRestTemplate, properties);
    }

    /**
     * 代付核心返回成功统一响应时应解包平台单号和当前状态。
     */
    @Test
    void shouldUnwrapSuccessfulPayoutResponse() {
        when(loadBalancedRestTemplate.postForEntity(
                eq(PAYOUT_CREATE_URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("""
                        {"code":"T200","message":"Success","data":{"payoutOrderNo":"PO202611010001","merchantOrderNo":"MO202611010001","status":"RECEIVED"}}
                        """));

        PayoutCreateClientResponseDTO response = payoutInternalRestClient.createPayout(request());

        assertThat(response.getPayoutOrderNo()).isEqualTo("PO202611010001");
        assertThat(response.getMerchantOrderNo()).isEqualTo("MO202611010001");
        assertThat(response.getStatus()).isEqualTo("RECEIVED");
    }

    /**
     * 代付核心返回明确业务失败时应保留下游业务码，避免误报为系统 unknown。
     */
    @Test
    void shouldPreserveExplicitPayoutBusinessFailure() {
        HttpClientErrorException responseException = HttpClientErrorException.create(
                HttpStatus.CONFLICT,
                "Conflict",
                HttpHeaders.EMPTY,
                "{\"code\":\"F520001\",\"message\":\"payout idempotency conflict\",\"data\":null}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        when(loadBalancedRestTemplate.postForEntity(
                eq(PAYOUT_CREATE_URL), any(HttpEntity.class), eq(String.class)))
                .thenThrow(responseException);

        assertThatThrownBy(() -> payoutInternalRestClient.createPayout(request()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception).isNotInstanceOf(OpenApiContextualException.class);
                    assertThat(exception.getCode()).isEqualTo("F520001");
                });
    }

    /**
     * 创建代付超时无法确认是否已被核心受理时必须返回平台 unknown 证据。
     */
    @Test
    void shouldMarkPayoutTimeoutAsUncertain() {
        when(loadBalancedRestTemplate.postForEntity(
                eq(PAYOUT_CREATE_URL), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("simulated timeout"));

        assertThatThrownBy(() -> payoutInternalRestClient.createPayout(request()))
                .isInstanceOfSatisfying(OpenApiContextualException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("F2000");
                    assertThat(exception.getErrorContext().requestSent()).isNull();
                    assertThat(exception.getErrorContext().outcomeUncertain()).isTrue();
                });
    }

    /**
     * 构造不包含收款账户信息的最小代付内部请求。
     *
     * @return 可用于内部客户端合同测试的创建请求
     */
    private PayoutCreateClientRequestDTO request() {
        PayoutCreateClientRequestDTO requestDTO = new PayoutCreateClientRequestDTO();
        requestDTO.setMerchantId("200001");
        requestDTO.setMerchantOrderNo("MO202611010001");
        requestDTO.setAmount(new BigDecimal("89.59"));
        requestDTO.setCurrency("AUD");
        requestDTO.setTransactionDateTime(LocalDateTime.of(2026, 11, 1, 5, 30));
        requestDTO.setRequestFingerprint("payout-request-fingerprint");
        return requestDTO;
    }
}
