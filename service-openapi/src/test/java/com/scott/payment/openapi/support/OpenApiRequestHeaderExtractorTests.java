package com.scott.payment.openapi.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.component.security.jwt.JwtMerchantClaims;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.exception.RateLimitExceededException;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessService;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessDeniedException;
import com.scott.payment.openapi.security.MerchantIpWhitelistDenyReason;
import com.scott.payment.openapi.security.MerchantKeyProvider;
import com.scott.payment.openapi.security.OpenApiMerchantRateLimitService;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestHeaderExtractorTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : OpenAPI 请求头安全提取测试。
 * @status : create
 */
class OpenApiRequestHeaderExtractorTests {

    @Test
    void shouldRejectRateLimitedMerchantBeforeWritingReplayToken() {
        MerchantJwtVerifier jwtVerifier = mock(MerchantJwtVerifier.class);
        MerchantKeyProvider keyProvider = mock(MerchantKeyProvider.class);
        OpenApiJwtReplayProtectionService replayProtectionService = mock(OpenApiJwtReplayProtectionService.class);
        MerchantIpWhitelistAccessService ipWhitelistAccessService = mock(MerchantIpWhitelistAccessService.class);
        OpenApiMerchantRateLimitService rateLimitService = mock(OpenApiMerchantRateLimitService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        JwtMerchantClaims claims = claims();
        when(request.getHeader("authorization")).thenReturn("Bearer signed-token");
        when(request.getRequestURI()).thenReturn("/api/rest/iso/v1/countries/query");
        when(jwtVerifier.peekMerchantId("signed-token")).thenReturn("200045");
        when(keyProvider.getMerchantKey("200045")).thenReturn("merchant-key");
        when(jwtVerifier.verify("signed-token", "merchant-key")).thenReturn(claims);
        when(ipWhitelistAccessService.checkAccess("200045", request)).thenReturn("127.0.0.1");
        doThrow(new RateLimitExceededException(3L)).when(rateLimitService).check("200045");
        OpenApiRequestHeaderExtractor extractor = new OpenApiRequestHeaderExtractor(
                jwtVerifier,
                keyProvider,
                replayProtectionService,
                ipWhitelistAccessService,
                rateLimitService,
                mock(SecurityInterceptEventRecorder.class),
                mock(OpenApiDiagnosticLogSupport.class)
        );

        assertThatThrownBy(() -> extractor.extract(request, new String[]{"authorization"}))
                .isInstanceOf(RateLimitExceededException.class);
        verify(replayProtectionService, never()).checkAndMark("200045", "jwt-id", 2000L);
    }

    @Test
    void shouldRequireJsonContentTypeWhenDeclared() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("authorization")).thenReturn("Bearer signed-token");

        OpenApiRequestHeaderExtractor extractor = extractor();

        assertThatThrownBy(() -> extractor.extract(
                request,
                new String[]{"authorization", "content-type"}
        ))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.PARAM_MISSING.getCode());
    }

    @Test
    void shouldRejectNonJsonContentType() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("authorization")).thenReturn("Bearer signed-token");
        when(request.getHeader("content-type")).thenReturn("text/plain");

        OpenApiRequestHeaderExtractor extractor = extractor();

        assertThatThrownBy(() -> extractor.extract(
                request,
                new String[]{"authorization", "content-type"}
        ))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.PARAM_INVALID.getCode());
    }

    @Test
    void shouldEnforceIpWhitelistBeforeRateLimitAndReplayProtection() {
        MerchantJwtVerifier jwtVerifier = mock(MerchantJwtVerifier.class);
        MerchantKeyProvider keyProvider = mock(MerchantKeyProvider.class);
        OpenApiJwtReplayProtectionService replayProtectionService = mock(OpenApiJwtReplayProtectionService.class);
        MerchantIpWhitelistAccessService ipWhitelistAccessService = mock(MerchantIpWhitelistAccessService.class);
        SecurityInterceptEventRecorder eventRecorder = mock(SecurityInterceptEventRecorder.class);
        OpenApiDiagnosticLogSupport diagnosticLogSupport = mock(OpenApiDiagnosticLogSupport.class);
        OpenApiMerchantRateLimitService rateLimitService = mock(OpenApiMerchantRateLimitService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        JwtMerchantClaims claims = claims();

        when(request.getHeader("authorization")).thenReturn("Bearer signed-token");
        when(request.getHeader("content-type")).thenReturn("application/json;charset=UTF-8");
        when(request.getRequestURI()).thenReturn("/api/rest/payment/v1/payment");
        when(jwtVerifier.peekMerchantId("signed-token")).thenReturn("200045");
        when(keyProvider.getMerchantKey("200045")).thenReturn("merchant-key");
        when(jwtVerifier.verify("signed-token", "merchant-key")).thenReturn(claims);
        when(ipWhitelistAccessService.checkAccess("200045", request)).thenReturn("127.0.0.1");
        when(diagnosticLogSupport.jwtSummary(org.mockito.ArgumentMatchers.any())).thenReturn("{}");
        when(diagnosticLogSupport.headerSummary(request)).thenReturn("{}");
        OpenApiRequestHeaderExtractor extractor = new OpenApiRequestHeaderExtractor(
                jwtVerifier,
                keyProvider,
                replayProtectionService,
                ipWhitelistAccessService,
                rateLimitService,
                eventRecorder,
                diagnosticLogSupport
        );

        OpenApiRequestHeaderDTO result = extractor.extract(
                request,
                new String[]{"authorization", "content-type"}
        );

        assertThat(result.getMerchantId()).isEqualTo("200045");
        assertThat(result.getClientIp()).isEqualTo("127.0.0.1");
        var order = inOrder(ipWhitelistAccessService, rateLimitService, replayProtectionService);
        order.verify(ipWhitelistAccessService).checkAccess("200045", request);
        order.verify(rateLimitService).check("200045");
        order.verify(replayProtectionService).checkAndMark("200045", "jwt-id", 2000L);
    }

    @Test
    void shouldLogNormalizedRequestIpWhenWhitelistRejectsRequest() {
        MerchantJwtVerifier jwtVerifier = mock(MerchantJwtVerifier.class);
        MerchantKeyProvider keyProvider = mock(MerchantKeyProvider.class);
        MerchantIpWhitelistAccessService ipWhitelistAccessService = mock(MerchantIpWhitelistAccessService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("authorization")).thenReturn("Bearer signed-token");
        when(request.getRequestURI()).thenReturn("/api/rest/payment/v1/payment");
        when(jwtVerifier.peekMerchantId("signed-token")).thenReturn("200045");
        when(keyProvider.getMerchantKey("200045")).thenReturn("merchant-key");
        when(jwtVerifier.verify("signed-token", "merchant-key")).thenReturn(claims());
        doThrow(new MerchantIpWhitelistAccessDeniedException(
                MerchantIpWhitelistDenyReason.IP_NOT_ALLOWED,
                "203.0.113.10"
        )).when(ipWhitelistAccessService).checkAccess("200045", request);
        OpenApiRequestHeaderExtractor extractor = new OpenApiRequestHeaderExtractor(
                jwtVerifier,
                keyProvider,
                mock(OpenApiJwtReplayProtectionService.class),
                ipWhitelistAccessService,
                mock(OpenApiMerchantRateLimitService.class),
                mock(SecurityInterceptEventRecorder.class),
                mock(OpenApiDiagnosticLogSupport.class)
        );

        Logger logger = (Logger) LoggerFactory.getLogger(OpenApiRequestHeaderExtractor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> extractor.extract(request, new String[]{"authorization"}))
                    .isInstanceOf(MerchantIpWhitelistAccessDeniedException.class);

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("event: OPENAPI_SECURITY_CHECK_END")
                            .contains("requestIp: 203.0.113.10"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private OpenApiRequestHeaderExtractor extractor() {
        return new OpenApiRequestHeaderExtractor(
                mock(MerchantJwtVerifier.class),
                mock(MerchantKeyProvider.class),
                mock(OpenApiJwtReplayProtectionService.class),
                mock(MerchantIpWhitelistAccessService.class),
                mock(OpenApiMerchantRateLimitService.class),
                mock(SecurityInterceptEventRecorder.class),
                mock(OpenApiDiagnosticLogSupport.class)
        );
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
