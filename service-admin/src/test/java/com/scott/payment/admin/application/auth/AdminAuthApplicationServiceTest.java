package com.scott.payment.admin.application.auth;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthAccountDTO;
import com.scott.payment.component.db.auth.dto.AuthLoginRequest;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.dto.AuthRegisterRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendResponse;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthApplicationServiceTest {

    @Mock
    private SystemAuthService systemAuthService;

    @Mock
    private HttpServletRequest servletRequest;

    private AdminAuthApplicationService adminAuthApplicationService;

    @BeforeEach
    void setUp() {
        adminAuthApplicationService = new AdminAuthApplicationService(systemAuthService);
    }

    @Test
    void shouldDelegateRegisterToSystemAuthService() {
        AuthRegisterRequest request = new AuthRegisterRequest();
        AuthAccountDTO expected = new AuthAccountDTO();
        when(systemAuthService.register(AuthConstants.APP_ADMIN, request)).thenReturn(expected);

        AuthAccountDTO actual = adminAuthApplicationService.register(request);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).register(AuthConstants.APP_ADMIN, request);
    }

    @Test
    void shouldUseForwardedIpWhenSendingVerifyCode() {
        AuthVerifyCodeSendRequest request = new AuthVerifyCodeSendRequest();
        AuthVerifyCodeSendResponse expected = new AuthVerifyCodeSendResponse();
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("1.1.1.1, 2.2.2.2");
        when(systemAuthService.sendLoginVerifyCode(AuthConstants.APP_ADMIN, request, "1.1.1.1"))
                .thenReturn(expected);

        AuthVerifyCodeSendResponse actual = adminAuthApplicationService.sendVerifyCode(request, servletRequest);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).sendLoginVerifyCode(AuthConstants.APP_ADMIN, request, "1.1.1.1");
    }

    @Test
    void shouldUseRemoteAddressAndUserAgentWhenLoggingIn() {
        AuthLoginRequest request = new AuthLoginRequest();
        AuthLoginResponse expected = new AuthLoginResponse();
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getHeader("User-Agent")).thenReturn("admin-browser");
        when(systemAuthService.login(AuthConstants.APP_ADMIN, request, "127.0.0.1", "admin-browser"))
                .thenReturn(expected);

        AuthLoginResponse actual = adminAuthApplicationService.login(request, servletRequest);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).login(AuthConstants.APP_ADMIN, request, "127.0.0.1", "admin-browser");
    }

    @Test
    void shouldDelegateCurrentUserAndLogout() {
        AuthLoginResponse expected = new AuthLoginResponse();
        when(systemAuthService.currentUser(AuthConstants.APP_ADMIN, "Bearer token")).thenReturn(expected);

        AuthLoginResponse actual = adminAuthApplicationService.currentUser("Bearer token");
        adminAuthApplicationService.logout("Bearer token");

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).currentUser(AuthConstants.APP_ADMIN, "Bearer token");
        verify(systemAuthService).logout(AuthConstants.APP_ADMIN, "Bearer token");
    }
}
