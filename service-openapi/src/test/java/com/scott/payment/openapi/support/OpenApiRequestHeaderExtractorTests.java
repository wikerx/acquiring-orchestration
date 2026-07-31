package com.scott.payment.openapi.support;

import com.scott.payment.component.security.jwt.JwtMerchantClaims;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessService;
import com.scott.payment.openapi.security.MerchantKeyProvider;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenAPI 请求头安全提取测试。
 */
class OpenApiRequestHeaderExtractorTests {

    @Test
    void shouldDeferIpWhitelistOnlyAfterResolvingTrustedClientIp() {
        MerchantJwtVerifier jwtVerifier = mock(MerchantJwtVerifier.class);
        MerchantKeyProvider keyProvider = mock(MerchantKeyProvider.class);
        OpenApiJwtReplayProtectionService replayProtectionService = mock(OpenApiJwtReplayProtectionService.class);
        MerchantIpWhitelistAccessService ipWhitelistAccessService = mock(MerchantIpWhitelistAccessService.class);
        SecurityInterceptEventRecorder eventRecorder = mock(SecurityInterceptEventRecorder.class);
        OpenApiDiagnosticLogSupport diagnosticLogSupport = mock(OpenApiDiagnosticLogSupport.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        JwtMerchantClaims claims = claims();

        when(request.getHeader("authorization")).thenReturn("Bearer signed-token");
        when(request.getRequestURI()).thenReturn("/api/rest/payment/v1/payment");
        when(jwtVerifier.peekMerchantId("signed-token")).thenReturn("200045");
        when(keyProvider.getMerchantKey("200045")).thenReturn("merchant-key");
        when(jwtVerifier.verify("signed-token", "merchant-key")).thenReturn(claims);
        when(ipWhitelistAccessService.resolveClientIp(request)).thenReturn("127.0.0.1");
        when(diagnosticLogSupport.jwtSummary(org.mockito.ArgumentMatchers.any())).thenReturn("{}");
        when(diagnosticLogSupport.headerSummary(request)).thenReturn("{}");
        OpenApiRequestHeaderExtractor extractor = new OpenApiRequestHeaderExtractor(
                jwtVerifier,
                keyProvider,
                replayProtectionService,
                ipWhitelistAccessService,
                eventRecorder,
                diagnosticLogSupport
        );

        OpenApiRequestHeaderDTO result = extractor.extract(
                request,
                new String[]{"authorization"},
                true
        );

        assertThat(result.getMerchantId()).isEqualTo("200045");
        assertThat(result.getClientIp()).isEqualTo("127.0.0.1");
        verify(ipWhitelistAccessService).resolveClientIp(request);
        verify(ipWhitelistAccessService, never()).checkAccess("200045", request);
        verify(replayProtectionService).checkAndMark("200045", "jwt-id", 2000L);
    }

    private JwtMerchantClaims claims() {
        JwtMerchantClaims claims = new JwtMerchantClaims();
        claims.setMerchantId("200045");
        claims.setJwtId("jwt-id");
        claims.setIssuedAt(1000L);
        claims.setExpiresAt(2000L);
        return claims;
    }
}
