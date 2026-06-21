package com.scott.payment.merchant.application.auth;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthAccountDTO;
import com.scott.payment.component.db.auth.dto.AuthLoginRequest;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.dto.AuthRegisterRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendResponse;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.merchant.dto.MerchantDefaultLoginCredentialDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantAuthApplicationServiceTest {

    @Mock
    private SystemAuthService systemAuthService;

    @Mock
    private SysAppMapper sysAppMapper;

    @Mock
    private SysAccountMapper sysAccountMapper;

    @Mock
    private Environment environment;

    @Mock
    private HttpServletRequest servletRequest;

    private MerchantAuthApplicationService merchantAuthApplicationService;

    @BeforeEach
    void setUp() {
        merchantAuthApplicationService = new MerchantAuthApplicationService(systemAuthService, sysAppMapper, sysAccountMapper, environment);
    }

    @Test
    void shouldDelegateRegisterToSystemAuthService() {
        AuthRegisterRequest request = new AuthRegisterRequest();
        AuthAccountDTO expected = new AuthAccountDTO();
        when(systemAuthService.register(AuthConstants.APP_MERCHANT, request)).thenReturn(expected);

        AuthAccountDTO actual = merchantAuthApplicationService.register(request);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).register(AuthConstants.APP_MERCHANT, request);
    }

    @Test
    void shouldUseForwardedIpWhenSendingVerifyCode() {
        AuthVerifyCodeSendRequest request = new AuthVerifyCodeSendRequest();
        AuthVerifyCodeSendResponse expected = new AuthVerifyCodeSendResponse();
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("1.1.1.1, 2.2.2.2");
        when(systemAuthService.sendLoginVerifyCode(AuthConstants.APP_MERCHANT, request, "1.1.1.1"))
                .thenReturn(expected);

        AuthVerifyCodeSendResponse actual = merchantAuthApplicationService.sendVerifyCode(request, servletRequest);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).sendLoginVerifyCode(AuthConstants.APP_MERCHANT, request, "1.1.1.1");
    }

    @Test
    void shouldUseRemoteAddressAndUserAgentWhenLoggingIn() {
        AuthLoginRequest request = new AuthLoginRequest();
        AuthLoginResponse expected = new AuthLoginResponse();
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getHeader("User-Agent")).thenReturn("merchant-browser");
        when(systemAuthService.login(AuthConstants.APP_MERCHANT, request, "127.0.0.1", "merchant-browser"))
                .thenReturn(expected);

        AuthLoginResponse actual = merchantAuthApplicationService.login(request, servletRequest);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).login(AuthConstants.APP_MERCHANT, request, "127.0.0.1", "merchant-browser");
    }

    @Test
    void shouldReturnDefaultLoginCredentialFromSeedAccount() {
        SysAppDO app = new SysAppDO();
        app.setId(2L);
        SysAccountDO account = new SysAccountDO();
        account.setMerchantId("200045");
        account.setLoginAccount("merchant");
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        when(sysAppMapper.selectOne(any())).thenReturn(app);
        when(sysAccountMapper.selectOne(any())).thenReturn(account);

        MerchantDefaultLoginCredentialDTO actual = merchantAuthApplicationService.defaultLoginCredential();

        assertThat(actual.getMerchantId()).isEqualTo("200045");
        assertThat(actual.getLoginAccount()).isEqualTo("merchant");
        assertThat(actual.getPassword()).isEqualTo("Merchant@123456");
    }

    @Test
    void shouldHideDefaultLoginCredentialOutsideLocalProfiles() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        MerchantDefaultLoginCredentialDTO actual = merchantAuthApplicationService.defaultLoginCredential();

        assertThat(actual.getMerchantId()).isNull();
        assertThat(actual.getLoginAccount()).isNull();
        assertThat(actual.getPassword()).isNull();
    }

    @Test
    void shouldDelegateCurrentUserAndLogout() {
        AuthLoginResponse expected = new AuthLoginResponse();
        when(systemAuthService.currentUser(AuthConstants.APP_MERCHANT, "Bearer token")).thenReturn(expected);

        AuthLoginResponse actual = merchantAuthApplicationService.currentUser("Bearer token");
        merchantAuthApplicationService.logout("Bearer token");

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).currentUser(AuthConstants.APP_MERCHANT, "Bearer token");
        verify(systemAuthService).logout(AuthConstants.APP_MERCHANT, "Bearer token");
    }
}
