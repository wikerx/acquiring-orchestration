package com.scott.payment.openapi.support;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenAPI 响应加密出口与商户响应审计发布器的接线测试。
 */
class OpenApiResponseBodyAdviceTests {

    /**
     * 支付创建响应完成加密后应返回密文 data，并发布不含完整密文的审计命令。
     */
    @Test
    void shouldPublishMaskedAuditAfterEncryptingPaymentResponse() {
        String encryptedData = "12345678.encrypted.response.payload.87654321";
        PublicKey responsePublicKey = mock(PublicKey.class);
        OpenApiPayloadCrypto payloadCrypto = mock(OpenApiPayloadCrypto.class);
        when(payloadCrypto.encrypt(anyString(), same(responsePublicKey))).thenReturn(encryptedData);
        MerchantApiResponseAuditPublisher auditPublisher = mock(MerchantApiResponseAuditPublisher.class);
        OpenApiDiagnosticLogSupport diagnosticLogSupport = mock(OpenApiDiagnosticLogSupport.class);
        when(diagnosticLogSupport.plainResponseSummary(org.mockito.ArgumentMatchers.any()))
                .thenReturn("plain-summary");
        when(diagnosticLogSupport.cipherResponseSummary(encryptedData)).thenReturn("cipher-summary");
        OpenApiResponseBodyAdvice advice = new OpenApiResponseBodyAdvice(
                payloadCrypto, auditPublisher, diagnosticLogSupport);

        MockHttpServletRequest servletRequest = request(responsePublicKey);
        Object output = advice.beforeBodyWrite(
                CommonResult.success("A0000", "Success", paymentResponse()),
                null,
                null,
                null,
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(output).isInstanceOfSatisfying(CommonResult.class, result -> {
            assertThat(result.getCode()).isEqualTo("A0000");
            assertThat(result.getMessage()).isEqualTo("Success");
            assertThat(result.getData()).isEqualTo(encryptedData);
        });
        ArgumentCaptor<TransactionMerchantApiResponseLogUpdateClientRequestDTO> captor =
                ArgumentCaptor.forClass(TransactionMerchantApiResponseLogUpdateClientRequestDTO.class);
        verify(auditPublisher).publish(captor.capture());
        assertThat(captor.getValue())
                .extracting(TransactionMerchantApiResponseLogUpdateClientRequestDTO::getTransactionId,
                        TransactionMerchantApiResponseLogUpdateClientRequestDTO::getRequestId,
                        TransactionMerchantApiResponseLogUpdateClientRequestDTO::getTransactionDateTime,
                        TransactionMerchantApiResponseLogUpdateClientRequestDTO::getResponseCipherMasked)
                .containsExactly("TX-RESPONSE-AUDIT-001", "ORDER-RESPONSE-AUDIT-001",
                        LocalDateTime.of(2026, 11, 3, 10, 30, 0, 123_000_000),
                        "12345678***87654321");
        assertThat(captor.getValue().getResponseCipherDigest()).matches("[0-9a-f]{64}");
        assertThat(captor.getValue().getResponsePlainJsonMasked()).doesNotContain(encryptedData);
    }

    /**
     * 创建携带安全拦截器可信上下文的 Servlet 请求。
     *
     * @param responsePublicKey 已通过预检的商户响应公钥
     * @return 可供响应 Advice 使用的请求
     */
    private MockHttpServletRequest request(PublicKey responsePublicKey) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/rest/payment/v1/payment");
        OpenApiRequestHeaderDTO header = new OpenApiRequestHeaderDTO();
        header.setMerchantId("M000001");
        request.setAttribute(OpenApiRequestAttributes.REQUEST_HEADER, header);
        request.setAttribute(OpenApiRequestAttributes.RESPONSE_PUBLIC_KEY, responsePublicKey);
        return request;
    }

    /**
     * 创建包含真实交易身份和商户请求号的支付响应。
     *
     * @return 支付创建响应投影
     */
    private PaymentCreateVO paymentResponse() {
        PaymentCreateVO.TransactionInfoVO transactionInfo = new PaymentCreateVO.TransactionInfoVO();
        transactionInfo.setTransactionId("TX-RESPONSE-AUDIT-001");
        transactionInfo.setTransactionDateTime(OffsetDateTime.of(
                2026, 11, 3, 10, 30, 0, 123_000_000, ZoneOffset.ofHours(8)));
        PaymentCreateVO.OrderInfoVO orderInfo = new PaymentCreateVO.OrderInfoVO();
        orderInfo.setOrderId("ORDER-RESPONSE-AUDIT-001");
        PaymentCreateVO response = new PaymentCreateVO();
        response.setTransactionInfo(transactionInfo);
        response.setOrderInfo(orderInfo);
        return response;
    }
}
