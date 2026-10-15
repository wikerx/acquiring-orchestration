package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import com.scott.payment.component.db.auth.entity.SysPasswordResetRequestDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserMapper;
import com.scott.payment.component.db.auth.mapper.SysPasswordResetRequestMapper;
import com.scott.payment.component.db.auth.service.AccountPasswordHistoryService;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetRequestResponse;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetConfirmRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetEmailVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetVerifyResponse;
import com.scott.payment.merchant.service.MerchantConfigService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantPasswordResetServiceImplTest
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 商户找回密码安全边界测试，重点验证商户号与登录账号的联合定位。
 * @status : create
 */
class MerchantPasswordResetServiceImplTest {

    private final SystemAuthService systemAuthService = mock(SystemAuthService.class);
    private final SysAppMapper appMapper = mock(SysAppMapper.class);
    private final SysMerchantUserMapper merchantUserMapper = mock(SysMerchantUserMapper.class);
    private final SysAccountMapper accountMapper = mock(SysAccountMapper.class);
    private final SysAccountMfaMapper mfaMapper = mock(SysAccountMfaMapper.class);
    private final SysLoginSessionMapper sessionMapper = mock(SysLoginSessionMapper.class);
    private final SysPasswordResetRequestMapper resetRequestMapper = mock(SysPasswordResetRequestMapper.class);
    private final MerchantTemplateEmailService emailService = mock(MerchantTemplateEmailService.class);
    private final RedisSlidingWindowRateLimiter rateLimiter = mock(RedisSlidingWindowRateLimiter.class);
    private final AccountPasswordHistoryService passwordHistoryService = mock(AccountPasswordHistoryService.class);
    private final MerchantConfigService configService = mock(MerchantConfigService.class);

    private MerchantPasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        reset(systemAuthService, appMapper, merchantUserMapper, accountMapper, mfaMapper,
                sessionMapper, resetRequestMapper, emailService, rateLimiter,
                passwordHistoryService, configService);
        service = new MerchantPasswordResetServiceImpl(
                systemAuthService,
                appMapper,
                merchantUserMapper,
                accountMapper,
                mfaMapper,
                sessionMapper,
                resetRequestMapper,
                emailService,
                rateLimiter,
                passwordHistoryService,
                configService
        );
        SysAppDO app = new SysAppDO();
        app.setId(2L);
        app.setAppCode(AuthConstants.APP_MERCHANT);
        app.setStatus(AuthConstants.ENABLED);
        when(appMapper.selectOne(any())).thenReturn(app);
        when(resetRequestMapper.selectCount(any())).thenReturn(0L);
        when(resetRequestMapper.insert(any(SysPasswordResetRequestDO.class))).thenAnswer(invocation -> {
            SysPasswordResetRequestDO row = invocation.getArgument(0);
            row.setId(1001L);
            return 1;
        });
        when(rateLimiter.tryAcquire(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(SlidingWindowRateLimitResult.permitted());
        when(configService.enabledConfigValue("platform.merchant.frontend-base-url"))
                .thenReturn(Optional.of("http://127.0.0.1:5174"));
    }

    @AfterEach
    void tearDown() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void shouldResolveAccountInsideRequestedMerchantBeforePreparingEmail() {
        SysMerchantUserDO merchantUser = new SysMerchantUserDO();
        merchantUser.setId(301L);
        merchantUser.setMerchantId("M10000001");
        merchantUser.setLoginAccount("scott");
        merchantUser.setAccountId(101L);
        merchantUser.setStatus(AuthConstants.ENABLED);
        merchantUser.setDeleted(AuthConstants.NOT_DELETED);
        when(merchantUserMapper.selectOne(any())).thenReturn(merchantUser);

        SysAccountDO account = new SysAccountDO();
        account.setId(101L);
        account.setAppId(2L);
        account.setUserId(201L);
        account.setMerchantId("M10000001");
        account.setLoginAccount("scott_M10000001");
        account.setEmail("scott@example.com");
        account.setEmailVerifiedAt(LocalDateTime.now().minusDays(1));
        account.setActivationStatus(AuthConstants.ACTIVATION_STATUS_ACTIVE);
        account.setStatus(AuthConstants.ENABLED);
        account.setLocked(AuthConstants.DISABLED);
        account.setDeleted(AuthConstants.NOT_DELETED);
        when(accountMapper.selectById(101L)).thenReturn(account);

        PasswordResetRequest request = new PasswordResetRequest();
        request.setMerchantId("M10000001");
        request.setLoginAccount("scott");
        request.setVerifyCodeId("12");
        request.setVerifyCode("ABCD");
        request.setLocale("zh-CN");

        PasswordResetRequestResponse response = service.requestReset(request, "127.0.0.1", "JUnit");

        assertThat(response.getRequestId()).isNotBlank();
        ArgumentCaptor<SysPasswordResetRequestDO> rowCaptor = ArgumentCaptor.forClass(SysPasswordResetRequestDO.class);
        verify(resetRequestMapper).insert(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getMerchantId()).isEqualTo("M10000001");
        assertThat(rowCaptor.getValue().getAccountId()).isEqualTo(101L);
        assertThat(rowCaptor.getValue().getAccountFingerprint()).doesNotContain("scott");
        verify(emailService).sendByTemplate(any());
        verify(systemAuthService).verifyLoginCaptcha(
                AuthConstants.APP_MERCHANT, "12", "ABCD", "127.0.0.1");
    }

    @Test
    void shouldCreateVirtualRequestWhenAccountBelongsToAnotherMerchant() {
        SysMerchantUserDO merchantUser = merchantUser("M10000001", 101L);
        when(merchantUserMapper.selectOne(any())).thenReturn(merchantUser);
        when(accountMapper.selectById(101L)).thenReturn(eligibleAccount("M20000002"));

        PasswordResetRequestResponse response = service.requestReset(resetRequest(), "127.0.0.1", "JUnit");

        assertThat(response.getRequestId()).isNotBlank();
        ArgumentCaptor<SysPasswordResetRequestDO> rowCaptor = ArgumentCaptor.forClass(SysPasswordResetRequestDO.class);
        verify(resetRequestMapper).insert(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getMerchantId()).isEqualTo("M10000001");
        assertThat(rowCaptor.getValue().getAccountId()).isNull();
        verify(emailService).prepareSuppressedByTemplate(any());
        verify(emailService, never()).sendByTemplate(any());
    }

    @Test
    void shouldBlockSelfServiceWhenMfaIsNotActivelyBound() {
        SysPasswordResetRequestDO row = pendingEmailRequest("request-mfa", "aB2cD3", 0);
        when(resetRequestMapper.selectByRequestNoForUpdate("request-mfa")).thenReturn(row);
        when(accountMapper.selectById(101L)).thenReturn(eligibleAccount("M10000001"));
        when(mfaMapper.selectOne(any())).thenReturn(null);

        PasswordResetEmailVerifyRequest request = new PasswordResetEmailVerifyRequest();
        request.setRequestId("request-mfa");
        request.setEmailCode("aB2cD3");
        PasswordResetVerifyResponse response = service.verifyEmail(request, "127.0.0.1");

        assertThat(response.getNextStep()).isEqualTo("MERCHANT_SUPPORT");
        assertThat(response.getActionToken()).isNull();
        assertThat(row.getState()).isEqualTo("BLOCKED");
        verify(resetRequestMapper).updateById(row);
    }

    @Test
    void shouldLockEmailVerificationAtAttemptLimit() {
        SysPasswordResetRequestDO row = pendingEmailRequest("request-lock", "aB2cD3", 49);
        when(resetRequestMapper.selectByRequestNoForUpdate("request-lock")).thenReturn(row);
        when(accountMapper.selectById(101L)).thenReturn(eligibleAccount("M10000001"));

        PasswordResetEmailVerifyRequest request = new PasswordResetEmailVerifyRequest();
        request.setRequestId("request-lock");
        request.setEmailCode("xY9zQ8");

        assertThatThrownBy(() -> service.verifyEmail(request, "127.0.0.1"))
                .hasMessage("Verification failed or expired.");
        assertThat(row.getEmailVerifyCount()).isEqualTo(50);
        assertThat(row.getState()).isEqualTo("LOCKED");
        verify(resetRequestMapper).updateById(row);
    }

    @Test
    void shouldNotExpireAdvancedRequestWhenEmailStageIsReplayed() {
        SysPasswordResetRequestDO row = pendingEmailRequest("advanced-request", "aB2cD3", 1);
        row.setState("READY");
        row.setResetTokenHash(LoginTokenUtils.hashToken("valid-reset-token"));
        row.setResetTokenExpireAt(LocalDateTime.now().plusMinutes(5));
        when(resetRequestMapper.selectByRequestNoForUpdate("advanced-request")).thenReturn(row);

        PasswordResetEmailVerifyRequest request = new PasswordResetEmailVerifyRequest();
        request.setRequestId("advanced-request");
        request.setEmailCode("wrong-code");
        assertThatThrownBy(() -> service.verifyEmail(request, "127.0.0.1"))
                .hasMessage("Verification failed or expired.");

        assertThat(row.getState()).isEqualTo("READY");
        assertThat(row.getResetTokenHash()).isEqualTo(LoginTokenUtils.hashToken("valid-reset-token"));
    }

    @Test
    void shouldConsumeResetTokenOnceAndRevokeExistingSessions() {
        String resetToken = "single-use-reset-token";
        SysPasswordResetRequestDO row = new SysPasswordResetRequestDO();
        row.setRequestNo("request-confirm");
        row.setAppId(2L);
        row.setMerchantId("M10000001");
        row.setAccountId(101L);
        row.setResetTokenHash(LoginTokenUtils.hashToken(resetToken));
        row.setResetTokenExpireAt(LocalDateTime.now().plusMinutes(5));
        row.setState("READY");
        row.setLocale("zh-CN");
        when(resetRequestMapper.selectByRequestNoForUpdate("request-confirm")).thenReturn(row);
        SysAccountDO account = eligibleAccount("M10000001");
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(account);

        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        request.setRequestId("request-confirm");
        request.setResetToken(resetToken);
        request.setNewPassword("SecurePass1!");

        service.confirm(request, "127.0.0.1");

        assertThat(row.getState()).isEqualTo("COMPLETED");
        assertThat(row.getResetTokenHash()).isNull();
        verify(passwordHistoryService).assertNotRecentlyUsed(account, "SecurePass1!");
        verify(passwordHistoryService).recordCurrentPassword(any(SysAccountDO.class), any(LocalDateTime.class));
        verify(sessionMapper).logoutAllActive(2L, 101L, row.getCompletedAt());

        assertThatThrownBy(() -> service.confirm(request, "127.0.0.1"))
                .hasMessage("Verification failed or expired.");
        verify(accountMapper, times(1)).updateById(account);
    }

    @Test
    void shouldKeepResetRequestUsableAfterWrongToken() {
        String resetToken = "still-valid-reset-token";
        SysPasswordResetRequestDO row = new SysPasswordResetRequestDO();
        row.setRequestNo("request-wrong-token");
        row.setAppId(2L);
        row.setMerchantId("M10000001");
        row.setAccountId(101L);
        row.setResetTokenHash(LoginTokenUtils.hashToken(resetToken));
        row.setResetTokenExpireAt(LocalDateTime.now().plusMinutes(5));
        row.setState("READY");
        row.setLocale("zh-CN");
        when(resetRequestMapper.selectByRequestNoForUpdate("request-wrong-token")).thenReturn(row);
        SysAccountDO account = eligibleAccount("M10000001");
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(account);

        PasswordResetConfirmRequest wrongRequest = new PasswordResetConfirmRequest();
        wrongRequest.setRequestId("request-wrong-token");
        wrongRequest.setResetToken("wrong-token");
        wrongRequest.setNewPassword("SecurePass1!");

        assertThatThrownBy(() -> service.confirm(wrongRequest, "127.0.0.1"))
                .hasMessage("Verification failed or expired.");
        assertThat(row.getState()).isEqualTo("READY");
        assertThat(row.getResetTokenHash()).isEqualTo(LoginTokenUtils.hashToken(resetToken));

        PasswordResetConfirmRequest correctRequest = new PasswordResetConfirmRequest();
        correctRequest.setRequestId("request-wrong-token");
        correctRequest.setResetToken(resetToken);
        correctRequest.setNewPassword("SecurePass1!");
        service.confirm(correctRequest, "127.0.0.1");

        assertThat(row.getState()).isEqualTo("COMPLETED");
        verify(accountMapper).updateById(account);
    }

    @Test
    void shouldRejectAdministratorResetForAccountOutsideCurrentMerchant() {
        InternalAuthAccount operator = new InternalAuthAccount();
        operator.setAppCode(AuthConstants.APP_MERCHANT);
        operator.setAppId(2L);
        operator.setAccountId(900L);
        operator.setMerchantId("M10000001");
        InternalAuthContextHolder.set(operator);
        when(accountMapper.selectById(101L)).thenReturn(eligibleAccount("M20000002"));

        assertThatThrownBy(() -> service.requestResetForAccount(
                101L, "zh-CN", "127.0.0.1", "JUnit"))
                .hasMessageContaining("current merchant");
        verify(resetRequestMapper, never()).insert(any(SysPasswordResetRequestDO.class));
        verify(emailService, never()).sendByTemplate(any());
    }

    private PasswordResetRequest resetRequest() {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setMerchantId("M10000001");
        request.setLoginAccount("scott");
        request.setVerifyCodeId("12");
        request.setVerifyCode("ABCD");
        request.setLocale("zh-CN");
        return request;
    }

    private SysMerchantUserDO merchantUser(String merchantId, Long accountId) {
        SysMerchantUserDO row = new SysMerchantUserDO();
        row.setId(301L);
        row.setMerchantId(merchantId);
        row.setLoginAccount("scott");
        row.setAccountId(accountId);
        row.setStatus(AuthConstants.ENABLED);
        row.setDeleted(AuthConstants.NOT_DELETED);
        return row;
    }

    private SysAccountDO eligibleAccount(String merchantId) {
        SysAccountDO row = new SysAccountDO();
        row.setId(101L);
        row.setAppId(2L);
        row.setUserId(201L);
        row.setMerchantId(merchantId);
        row.setLoginAccount("scott_" + merchantId);
        row.setEmail("scott@example.com");
        row.setEmailVerifiedAt(LocalDateTime.now().minusDays(1));
        row.setActivationStatus(AuthConstants.ACTIVATION_STATUS_ACTIVE);
        row.setStatus(AuthConstants.ENABLED);
        row.setLocked(AuthConstants.DISABLED);
        row.setDeleted(AuthConstants.NOT_DELETED);
        return row;
    }

    private SysPasswordResetRequestDO pendingEmailRequest(String requestId, String code, int attempts) {
        String salt = PasswordHashUtils.generateSalt();
        SysPasswordResetRequestDO row = new SysPasswordResetRequestDO();
        row.setRequestNo(requestId);
        row.setAppId(2L);
        row.setMerchantId("M10000001");
        row.setAccountId(101L);
        row.setEmailCodeSalt(salt);
        row.setEmailCodeHash(PasswordHashUtils.hashPassword(code, salt));
        row.setEmailExpireAt(LocalDateTime.now().plusMinutes(30));
        row.setEmailVerifyCount(attempts);
        row.setState("PENDING_EMAIL");
        return row;
    }
}
