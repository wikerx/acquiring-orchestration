package com.scott.payment.merchant.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysEmailVerificationRequestDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysEmailVerificationRequestMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.auth.service.AccountPasswordHistoryService;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.AccountActivationCompleteRequest;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.EmailVerificationVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.EmailVerificationVerifyResponse;
import com.scott.payment.merchant.service.MerchantConfigService;
import com.scott.payment.merchant.service.MerchantEmailVerificationService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService.MerchantEmailSendResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantEmailVerificationServiceImplTest
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 商户账号激活和邮箱验证状态机测试，覆盖租户边界、失败锁定和令牌单次消费。
 * @status : create
 */
class MerchantEmailVerificationServiceImplTest {

    private final SysAppMapper appMapper = mock(SysAppMapper.class);
    private final SysAccountMapper accountMapper = mock(SysAccountMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysLoginSessionMapper sessionMapper = mock(SysLoginSessionMapper.class);
    private final SysEmailVerificationRequestMapper requestMapper = mock(SysEmailVerificationRequestMapper.class);
    private final MerchantTemplateEmailService emailService = mock(MerchantTemplateEmailService.class);
    private final MerchantConfigService configService = mock(MerchantConfigService.class);
    private final RedisSlidingWindowRateLimiter rateLimiter = mock(RedisSlidingWindowRateLimiter.class);
    private final AccountPasswordHistoryService passwordHistoryService = mock(AccountPasswordHistoryService.class);

    private MerchantEmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "merchant-email-verification-test"),
                SysEmailVerificationRequestDO.class);
        reset(appMapper, accountMapper, userMapper, sessionMapper, requestMapper,
                emailService, configService, rateLimiter, passwordHistoryService);
        service = new MerchantEmailVerificationServiceImpl(
                appMapper, accountMapper, userMapper, sessionMapper, requestMapper,
                emailService, configService, rateLimiter, passwordHistoryService);
        SysAppDO app = new SysAppDO();
        app.setId(2L);
        app.setAppCode(AuthConstants.APP_MERCHANT);
        app.setStatus(AuthConstants.ENABLED);
        when(appMapper.selectOne(any())).thenReturn(app);
        when(rateLimiter.tryAcquire(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(SlidingWindowRateLimitResult.permitted());
        when(configService.enabledConfigValue(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void shouldReportFailureWhenVerificationEmailCannotBePrepared() {
        SysAccountDO account = activeAccount("M10000001");
        account.setEmailVerifiedAt(null);
        when(accountMapper.selectOne(any())).thenReturn(account);
        when(emailService.sendByTemplate(any())).thenReturn(
                new MerchantEmailSendResult(301L, EmailDeliveryStatus.CLOSED.getCode()));

        assertThatThrownBy(() -> service.issue(
                101L,
                MerchantEmailVerificationService.PURPOSE_EMAIL_VERIFICATION,
                "en-US",
                "127.0.0.1",
                "JUnit",
                false))
                .hasMessage("email verification message could not be prepared");

        verify(requestMapper).insert(any(SysEmailVerificationRequestDO.class));
        verify(emailService).sendByTemplate(any());
    }

    @Test
    void shouldRejectVerificationWhenRequestAndAccountMerchantDiffer() {
        SysEmailVerificationRequestDO row = pendingRequest(
                "merchant-boundary", MerchantEmailVerificationService.PURPOSE_EMAIL_VERIFICATION,
                "M10000001", "scott@example.com", "aB2cD3", 0);
        when(requestMapper.selectByRequestNoForUpdate("merchant-boundary")).thenReturn(row);
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(activeAccount("M20000002"));

        EmailVerificationVerifyRequest request = verifyRequest("merchant-boundary", "aB2cD3");

        assertThatThrownBy(() -> service.verify(request, "127.0.0.1"))
                .hasMessage("Verification failed or expired.");
        assertThat(row.getVerifyCount()).isEqualTo(1);
        assertThat(row.getState()).isEqualTo("PENDING");
        verify(requestMapper).updateById(row);
    }

    @Test
    void shouldLockEmailCodeAtAttemptLimit() {
        SysEmailVerificationRequestDO row = pendingRequest(
                "verification-lock", MerchantEmailVerificationService.PURPOSE_EMAIL_VERIFICATION,
                "M10000001", "scott@example.com", "aB2cD3", 49);
        when(requestMapper.selectByRequestNoForUpdate("verification-lock")).thenReturn(row);
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(activeAccount("M10000001"));

        assertThatThrownBy(() -> service.verify(verifyRequest("verification-lock", "xY9zQ8"), "127.0.0.1"))
                .hasMessage("Verification failed or expired.");
        assertThat(row.getVerifyCount()).isEqualTo(50);
        assertThat(row.getState()).isEqualTo("LOCKED");
        verify(requestMapper).updateById(row);
    }

    @Test
    void shouldNotExpireVerifiedActivationWhenEmailStageIsReplayed() {
        SysEmailVerificationRequestDO row = pendingRequest(
                "advanced-activation", MerchantEmailVerificationService.PURPOSE_ACCOUNT_ACTIVATION,
                "M10000001", "scott@example.com", "aB2cD3", 1);
        row.setState("VERIFIED");
        row.setActionTokenHash(LoginTokenUtils.hashToken("valid-action-token"));
        row.setActionTokenExpireAt(LocalDateTime.now().plusMinutes(5));
        when(requestMapper.selectByRequestNoForUpdate("advanced-activation")).thenReturn(row);

        assertThatThrownBy(() -> service.verify(
                verifyRequest("advanced-activation", "wrong-code"), "127.0.0.1"))
                .hasMessage("Verification failed or expired.");

        assertThat(row.getState()).isEqualTo("VERIFIED");
        assertThat(row.getActionTokenHash()).isEqualTo(LoginTokenUtils.hashToken("valid-action-token"));
    }

    @Test
    void shouldSwitchPendingEmailOnlyAfterCorrectVerification() {
        SysEmailVerificationRequestDO row = pendingRequest(
                "email-change", MerchantEmailVerificationService.PURPOSE_EMAIL_CHANGE,
                "M10000001", "new@example.com", "aB2cD3", 0);
        when(requestMapper.selectByRequestNoForUpdate("email-change")).thenReturn(row);
        SysAccountDO account = activeAccount("M10000001");
        account.setPendingEmail("new@example.com");
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(account);
        SysUserDO user = new SysUserDO();
        user.setId(201L);
        user.setEmail("scott@example.com");
        when(userMapper.selectById(201L)).thenReturn(user);

        EmailVerificationVerifyResponse response = service.verify(
                verifyRequest("email-change", "aB2cD3"), "127.0.0.1");

        assertThat(response.getNextStep()).isEqualTo("COMPLETED");
        assertThat(account.getEmail()).isEqualTo("new@example.com");
        assertThat(account.getPendingEmail()).isNull();
        assertThat(account.getEmailVerifiedAt()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(row.getState()).isEqualTo("COMPLETED");
        verify(userMapper).updateById(user);
        verify(accountMapper).updateById(account);
    }

    @Test
    void shouldRejectStaleEmailChangeAfterPendingAddressIsCancelled() {
        SysEmailVerificationRequestDO row = pendingRequest(
                "cancelled-email-change", MerchantEmailVerificationService.PURPOSE_EMAIL_CHANGE,
                "M10000001", "old-pending@example.com", "aB2cD3", 0);
        when(requestMapper.selectByRequestNoForUpdate("cancelled-email-change")).thenReturn(row);
        SysAccountDO account = activeAccount("M10000001");
        account.setPendingEmail(null);
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(account);

        assertThatThrownBy(() -> service.verify(
                verifyRequest("cancelled-email-change", "aB2cD3"), "127.0.0.1"))
                .hasMessage("Verification failed or expired.");

        assertThat(account.getEmail()).isEqualTo("scott@example.com");
        assertThat(account.getPendingEmail()).isNull();
        assertThat(row.getState()).isEqualTo("PENDING");
    }

    @Test
    void shouldConsumeActivationTokenOnceAndRevokeExistingSessions() {
        String actionToken = "single-use-activation-token";
        SysEmailVerificationRequestDO row = pendingRequest(
                "activation", MerchantEmailVerificationService.PURPOSE_ACCOUNT_ACTIVATION,
                "M10000001", "scott@example.com", "aB2cD3", 1);
        row.setState("VERIFIED");
        row.setActionTokenHash(LoginTokenUtils.hashToken(actionToken));
        row.setActionTokenExpireAt(LocalDateTime.now().plusMinutes(5));
        when(requestMapper.selectByRequestNoForUpdate("activation")).thenReturn(row);
        SysAccountDO account = activeAccount("M10000001");
        account.setActivationStatus(AuthConstants.ACTIVATION_STATUS_PENDING);
        account.setEmailVerifiedAt(null);
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(account);

        AccountActivationCompleteRequest request = new AccountActivationCompleteRequest();
        request.setRequestId("activation");
        request.setActionToken(actionToken);
        request.setNewPassword("SecurePass1!");

        service.completeActivation(request, "127.0.0.1");

        assertThat(account.getActivationStatus()).isEqualTo(AuthConstants.ACTIVATION_STATUS_ACTIVE);
        assertThat(account.getActivatedAt()).isNotNull();
        assertThat(account.getEmailVerifiedAt()).isNotNull();
        assertThat(row.getState()).isEqualTo("COMPLETED");
        assertThat(row.getActionTokenHash()).isNull();
        verify(passwordHistoryService).assertNotRecentlyUsed(account, "SecurePass1!");
        verify(passwordHistoryService).recordCurrentPassword(any(SysAccountDO.class), any(LocalDateTime.class));
        verify(sessionMapper).logoutAllActive(2L, 101L, row.getCompletedAt());

        assertThatThrownBy(() -> service.completeActivation(request, "127.0.0.1"))
                .hasMessage("Verification failed or expired.");
        verify(accountMapper, times(1)).updateById(account);
    }

    @Test
    void shouldKeepActivationRequestUsableAfterWrongActionToken() {
        String actionToken = "still-valid-action-token";
        SysEmailVerificationRequestDO row = pendingRequest(
                "activation-wrong-token", MerchantEmailVerificationService.PURPOSE_ACCOUNT_ACTIVATION,
                "M10000001", "scott@example.com", "aB2cD3", 1);
        row.setState("VERIFIED");
        row.setActionTokenHash(LoginTokenUtils.hashToken(actionToken));
        row.setActionTokenExpireAt(LocalDateTime.now().plusMinutes(5));
        when(requestMapper.selectByRequestNoForUpdate("activation-wrong-token")).thenReturn(row);
        SysAccountDO account = activeAccount("M10000001");
        account.setActivationStatus(AuthConstants.ACTIVATION_STATUS_PENDING);
        account.setEmailVerifiedAt(null);
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(account);

        AccountActivationCompleteRequest wrongRequest = new AccountActivationCompleteRequest();
        wrongRequest.setRequestId("activation-wrong-token");
        wrongRequest.setActionToken("wrong-token");
        wrongRequest.setNewPassword("SecurePass1!");
        assertThatThrownBy(() -> service.completeActivation(wrongRequest, "127.0.0.1"))
                .hasMessage("Verification failed or expired.");
        assertThat(row.getState()).isEqualTo("VERIFIED");

        AccountActivationCompleteRequest correctRequest = new AccountActivationCompleteRequest();
        correctRequest.setRequestId("activation-wrong-token");
        correctRequest.setActionToken(actionToken);
        correctRequest.setNewPassword("SecurePass1!");
        service.completeActivation(correctRequest, "127.0.0.1");

        assertThat(row.getState()).isEqualTo("COMPLETED");
        assertThat(account.getActivationStatus()).isEqualTo(AuthConstants.ACTIVATION_STATUS_ACTIVE);
    }

    private EmailVerificationVerifyRequest verifyRequest(String requestId, String code) {
        EmailVerificationVerifyRequest request = new EmailVerificationVerifyRequest();
        request.setRequestId(requestId);
        request.setEmailCode(code);
        return request;
    }

    private SysEmailVerificationRequestDO pendingRequest(String requestId,
                                                         String purpose,
                                                         String merchantId,
                                                         String targetEmail,
                                                         String code,
                                                         int attempts) {
        String salt = PasswordHashUtils.generateSalt();
        SysEmailVerificationRequestDO row = new SysEmailVerificationRequestDO();
        row.setRequestNo(requestId);
        row.setAppId(2L);
        row.setMerchantId(merchantId);
        row.setAccountId(101L);
        row.setPurpose(purpose);
        row.setEmailFingerprint(sha256(targetEmail));
        row.setCodeSalt(salt);
        row.setCodeHash(PasswordHashUtils.hashPassword(code, salt));
        row.setExpireAt(LocalDateTime.now().plusMinutes(30));
        row.setVerifyCount(attempts);
        row.setState("PENDING");
        return row;
    }

    private SysAccountDO activeAccount(String merchantId) {
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

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
