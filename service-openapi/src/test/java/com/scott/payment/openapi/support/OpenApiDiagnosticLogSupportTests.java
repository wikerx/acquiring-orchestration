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
 * 验证商户 OpenAPI 诊断摘要只保留不可逆元数据，不泄露解密后的业务字段。
 */
class OpenApiDiagnosticLogSupportTests {

    @Test
    void shouldDigestIpHeadersInHeaderSummary() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GatewayIngressSignature.HEADER_CLIENT_IP, "203.0.113.8");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.2");
        request.addHeader("X-Real-IP", "192.0.2.9");
        request.addHeader("Origin", "https://merchant.example/checkout?token=origin-secret");
        request.addHeader("Referer", "https://merchant.example/pay?session=referer-secret");
        request.addHeader("User-Agent", "merchant-browser-sensitive-marker");
        request.addHeader("X-Request-Id", "merchant-request-sensitive-marker");
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
                .doesNotContain("192.0.2.9")
                .doesNotContain("origin-secret")
                .doesNotContain("referer-secret")
                .doesNotContain("merchant-browser-sensitive-marker")
                .doesNotContain("merchant-request-sensitive-marker")
                .contains("originDigest")
                .contains("refererDigest")
                .contains("userAgentDigest")
                .contains("xRequestIdDigest");
    }

    @Test
    void shouldOnlyExposeMetadataForDecryptedPayoutRequest() {
        PayoutCreateRequestDTO request = new PayoutCreateRequestDTO();
        request.setMerchantOrderNo("PAYOUT-LOG-MASK-001");
        request.setReceiverAccountNo("6222021234567890123");

        String summary = new OpenApiDiagnosticLogSupport().plainRequestSummary(request);

        assertThat(summary)
                .doesNotContain("6222021234567890123")
                .doesNotContain("PAYOUT-LOG-MASK-001")
                .contains("\"payloadType\":\"PayoutCreateRequestDTO\"")
                .contains("\"jsonLength\":")
                .contains("\"jsonDigest\":");
    }

    @Test
    void shouldOnlyExposeMetadataForReferenceRequestAndResponse() {
        IpLookupRequestDTO ipRequest = new IpLookupRequestDTO();
        ipRequest.setIpAddress("2001:4860:4860::8888");
        CardBinLookupVO cardBinResponse = new CardBinLookupVO();
        cardBinResponse.setMatched(true);
        cardBinResponse.setCardBin("411111");

        OpenApiDiagnosticLogSupport support = new OpenApiDiagnosticLogSupport();
        String requestSummary = support.plainRequestSummary(ipRequest);
        String responseSummary = support.plainResponseSummary(cardBinResponse);

        assertThat(requestSummary)
                .contains("\"payloadType\":\"IpLookupRequestDTO\"")
                .doesNotContain("2001:4860:4860::8888");
        assertThat(responseSummary)
                .contains("\"payloadType\":\"CardBinLookupVO\"")
                .doesNotContain("411111");
    }

    @Test
    void shouldNotLogLongCardBinInput() {
        CardBinLookupRequestDTO request = new CardBinLookupRequestDTO();
        request.setCardBin("4111111234567890");

        String summary = new OpenApiDiagnosticLogSupport().plainRequestSummary(request);

        assertThat(summary).contains("\"payloadType\":\"CardBinLookupRequestDTO\"")
                .doesNotContain("4111111234567890");
    }
}
