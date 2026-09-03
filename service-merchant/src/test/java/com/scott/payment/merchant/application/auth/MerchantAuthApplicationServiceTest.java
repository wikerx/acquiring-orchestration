package com.scott.payment.merchant.application.auth;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthAccountDTO;
import com.scott.payment.component.db.auth.dto.AuthLoginRequest;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.dto.AuthMfaBindConfirmRequest;
import com.scott.payment.component.db.auth.dto.AuthMfaBindInfoResponse;
import com.scott.payment.component.db.auth.dto.AuthMfaVerifyRequest;
import com.scott.payment.component.db.auth.dto.AuthPasswordChangeRequest;
import com.scott.payment.component.db.auth.dto.AuthProfileUpdateRequest;
import com.scott.payment.component.db.auth.dto.AuthRegisterRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendResponse;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserMapper;
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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAuthApplicationServiceTest
 * @date : 2026-06-19 19:12
 * @email : scott_x@163.com
 * @description : 商户auth应用服务，位于 商户后台服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@ExtendWith(MockitoExtension.class)
class MerchantAuthApplicationServiceTest {

    @Mock
    private SystemAuthService systemAuthService;

    @Mock
    private SysAppMapper sysAppMapper;

    @Mock
    private SysAccountMapper sysAccountMapper;

    @Mock
    private SysMerchantUserMapper sysMerchantUserMapper;

    @Mock
    private Environment environment;

    @Mock
    private HttpServletRequest servletRequest;

    private MerchantAuthApplicationService merchantAuthApplicationService;

    @BeforeEach
    void setUp() {
        merchantAuthApplicationService = new MerchantAuthApplicationService(
                systemAuthService,
                sysAppMapper,
                sysAccountMapper,
                sysMerchantUserMapper,
                environment
        );
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
    void shouldDelegateMfaBindInfoToMerchantApp() {
        AuthMfaBindInfoResponse expected = new AuthMfaBindInfoResponse();
        when(systemAuthService.mfaBindInfo(AuthConstants.APP_MERCHANT, "ticket")).thenReturn(expected);

        AuthMfaBindInfoResponse actual = merchantAuthApplicationService.mfaBindInfo("ticket");

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).mfaBindInfo(AuthConstants.APP_MERCHANT, "ticket");
    }

    @Test
    void shouldDelegateMfaBindConfirmToMerchantApp() {
        AuthMfaBindConfirmRequest request = new AuthMfaBindConfirmRequest();
        AuthLoginResponse expected = new AuthLoginResponse();
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("8.8.8.8");
        when(servletRequest.getHeader("User-Agent")).thenReturn("merchant-browser");
        when(systemAuthService.mfaBindConfirm(AuthConstants.APP_MERCHANT, request, "8.8.8.8", "merchant-browser"))
                .thenReturn(expected);

        AuthLoginResponse actual = merchantAuthApplicationService.mfaBindConfirm(request, servletRequest);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).mfaBindConfirm(AuthConstants.APP_MERCHANT, request, "8.8.8.8", "merchant-browser");
    }

    @Test
    void shouldDelegateMfaVerifyToMerchantApp() {
        AuthMfaVerifyRequest request = new AuthMfaVerifyRequest();
        AuthLoginResponse expected = new AuthLoginResponse();
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.2");
        when(servletRequest.getHeader("User-Agent")).thenReturn("merchant-browser");
        when(systemAuthService.mfaVerify(AuthConstants.APP_MERCHANT, request, "127.0.0.2", "merchant-browser"))
                .thenReturn(expected);

        AuthLoginResponse actual = merchantAuthApplicationService.mfaVerify(request, servletRequest);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).mfaVerify(AuthConstants.APP_MERCHANT, request, "127.0.0.2", "merchant-browser");
    }

    @Test
    void shouldReturnDefaultLoginCredentialFromSeedAccount() {
        SysAppDO app = new SysAppDO();
        app.setId(2L);
        SysMerchantUserDO merchantUser = new SysMerchantUserDO();
        merchantUser.setMerchantId("200045");
        merchantUser.setLoginAccount("admin");
        merchantUser.setAccountId(10L);
        SysAccountDO account = new SysAccountDO();
        account.setAppId(2L);
        account.setMerchantId("200045");
        account.setLoginAccount("admin_200045");
        account.setStatus(AuthConstants.ENABLED);
        account.setDeleted(AuthConstants.NOT_DELETED);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);
        when(sysAppMapper.selectOne(any())).thenReturn(app);
        when(sysMerchantUserMapper.selectOne(any())).thenReturn(merchantUser);
        when(sysAccountMapper.selectById(10L)).thenReturn(account);

        MerchantDefaultLoginCredentialDTO actual = merchantAuthApplicationService.defaultLoginCredential();

        assertThat(actual.getMerchantId()).isEqualTo("200045");
        assertThat(actual.getLoginAccount()).isEqualTo("admin");
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

    @Test
    void shouldDelegateProfileUpdateAndPasswordChange() {
        AuthProfileUpdateRequest profileRequest = new AuthProfileUpdateRequest();
        AuthPasswordChangeRequest passwordRequest = new AuthPasswordChangeRequest();
        AuthLoginResponse expected = new AuthLoginResponse();
        when(systemAuthService.updateCurrentProfile(AuthConstants.APP_MERCHANT, "Bearer token", profileRequest))
                .thenReturn(expected);

        AuthLoginResponse actual = merchantAuthApplicationService.updateCurrentProfile("Bearer token", profileRequest);
        merchantAuthApplicationService.changeCurrentPassword("Bearer token", passwordRequest);

        assertThat(actual).isSameAs(expected);
        verify(systemAuthService).updateCurrentProfile(AuthConstants.APP_MERCHANT, "Bearer token", profileRequest);
        verify(systemAuthService).changeCurrentPassword(AuthConstants.APP_MERCHANT, "Bearer token", passwordRequest);
    }
}
