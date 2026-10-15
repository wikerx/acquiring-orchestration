package com.scott.payment.admin.application.auth;

import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetConfirmRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetConfirmResponse;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequestResponse;
import com.scott.payment.admin.service.AdminPasswordResetService;
import com.scott.payment.component.core.security.GatewayIngressSignature;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证找回密码 HTTP 编排只使用网关覆盖写入的可信客户端 IP。 */
@ExtendWith(MockitoExtension.class)
class AdminPasswordResetApplicationServiceTest {

    @Mock
    private AdminPasswordResetService passwordResetService;
    @Mock
    private HttpServletRequest servletRequest;

    private AdminPasswordResetApplicationService applicationService;

    @BeforeEach
    void setUp() {
        applicationService = new AdminPasswordResetApplicationService(passwordResetService);
    }

    @Test
    void shouldUseGatewayClientIpAndIgnoreForwardedFor() {
        PasswordResetRequest request = new PasswordResetRequest();
        PasswordResetRequestResponse expected = new PasswordResetRequestResponse();
        when(servletRequest.getHeader(GatewayIngressSignature.HEADER_CLIENT_IP)).thenReturn("203.0.113.10");
        when(servletRequest.getHeader("User-Agent")).thenReturn("JUnit browser");
        when(passwordResetService.requestReset(request, "203.0.113.10", "JUnit browser")).thenReturn(expected);

        PasswordResetRequestResponse actual = applicationService.requestReset(request, servletRequest);

        assertThat(actual).isSameAs(expected);
        verify(passwordResetService).requestReset(request, "203.0.113.10", "JUnit browser");
        verify(servletRequest, never()).getHeader("X-Forwarded-For");
    }

    @Test
    void shouldFallBackToRemoteAddressWhenGatewayHeaderIsInvalid() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        PasswordResetConfirmResponse expected = new PasswordResetConfirmResponse();
        when(servletRequest.getHeader(GatewayIngressSignature.HEADER_CLIENT_IP)).thenReturn("attacker.example");
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(passwordResetService.confirm(request, "127.0.0.1")).thenReturn(expected);

        PasswordResetConfirmResponse actual = applicationService.confirm(request, servletRequest);

        assertThat(actual).isSameAs(expected);
        verify(passwordResetService).confirm(request, "127.0.0.1");
    }
}
