package com.scott.payment.openapi.support;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.component.web.gateway.GatewayIngressAuthFilter;
import com.scott.payment.openapi.dto.body.PayoutCreateRequestDTO;
import com.scott.payment.openapi.dto.body.reference.IpLookupRequestDTO;
import com.scott.payment.openapi.dto.body.reference.CardBinLookupRequestDTO;
import com.scott.payment.openapi.vo.reference.CardBinLookupVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiDiagnosticLogSupportTests
 * @date : 2026-08-03
 * @email : scott_x@163.com
 * @description : 验证商户 OpenAPI 诊断摘要不会泄露代付收款账户等敏感明文。
 * @status : create
 */
class OpenApiDiagnosticLogSupportTests {

    @Test
    void shouldDigestIpHeadersInHeaderSummary() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GatewayIngressSignature.HEADER_CLIENT_IP, "203.0.113.8");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.2");
        request.addHeader("X-Real-IP", "192.0.2.9");
        request.setAttribute(GatewayIngressAuthFilter.GATEWAY_CLIENT_IP_AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);

        String summary = new OpenApiDiagnosticLogSupport().headerSummary(request);

        assertThat(summary)
                .contains("trustedClientIpDigest")
                .doesNotContain("gatewayClientIpDigest")
                .doesNotContain("xForwardedForDigest")
                .doesNotContain("xRealIpDigest")
                .doesNotContain("203.0.113.8")
                .doesNotContain("198.51.100.7")
                .doesNotContain("10.0.0.2")
                .doesNotContain("192.0.2.9");
    }

    @Test
    void shouldMaskPayoutReceiverAccountNumberInPlainRequestSummary() {
        PayoutCreateRequestDTO request = new PayoutCreateRequestDTO();
        request.setMerchantOrderNo("PAYOUT-LOG-MASK-001");
        request.setReceiverAccountNo("6222021234567890123");

        String summary = new OpenApiDiagnosticLogSupport().plainRequestSummary(request);

        assertThat(summary)
                .doesNotContain("6222021234567890123")
                .contains("6222******0123")
                .contains("PAYOUT-LOG-MASK-001");
    }

    @Test
    void shouldMaskReferenceLookupValuesInPlainSummaries() {
        IpLookupRequestDTO ipRequest = new IpLookupRequestDTO();
        ipRequest.setIpAddress("2001:4860:4860::8888");
        CardBinLookupVO cardBinResponse = new CardBinLookupVO();
        cardBinResponse.setMatched(true);
        cardBinResponse.setCardBin("411111");

        OpenApiDiagnosticLogSupport support = new OpenApiDiagnosticLogSupport();
        String requestSummary = support.plainRequestSummary(ipRequest);
        String responseSummary = support.plainResponseSummary(cardBinResponse);

        assertThat(requestSummary)
                .contains("\"ipAddress\":\"***\"")
                .doesNotContain("2001:4860:4860::8888");
        assertThat(responseSummary)
                .contains("\"cardBin\":\"***\"")
                .doesNotContain("411111");
    }

    @Test
    void shouldNotLogLongCardBinInput() {
        CardBinLookupRequestDTO request = new CardBinLookupRequestDTO();
        request.setCardBin("4111111234567890");

        String summary = new OpenApiDiagnosticLogSupport().plainRequestSummary(request);

        assertThat(summary).contains("\"cardBin\":\"***\"")
                .doesNotContain("4111111234567890");
    }
}
