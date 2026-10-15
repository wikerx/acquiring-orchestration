package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetConfirmRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetEmailVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetMfaVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequestResponse;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetVerifyResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysPasswordResetRequestDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysPasswordResetRequestMapper;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.db.auth.service.AccountPasswordHistoryService;
import com.scott.payment.component.db.auth.support.MfaSecretCrypto;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPasswordResetServiceImplTest
 * @date : 2026-10-14 00:00
 * @email : scott_x@163.com
 * @description : 管理后台找回密码安全边界测试，验证账号枚举防护与验证码邮件触发规则。
 * @status : create
 */
@Slf4j
class AdminPasswordResetServiceImplTest {

    private final SystemAuthService systemAuthService = mock(SystemAuthService.class);
    private final SysAppMapper appMapper = mock(SysAppMapper.class);
    private final SysAccountMapper accountMapper = mock(SysAccountMapper.class);
    private final SysAccountMfaMapper mfaMapper = mock(SysAccountMfaMapper.class);
    private final SysLoginSessionMapper sessionMapper = mock(SysLoginSessionMapper.class);
    private final SysPasswordResetRequestMapper resetRequestMapper = mock(SysPasswordResetRequestMapper.class);
    private final AdminEmailService emailService = mock(AdminEmailService.class);
    private final RedisSlidingWindowRateLimiter rateLimiter = mock(RedisSlidingWindowRateLimiter.class);
    private final AccountPasswordHistoryService passwordHistoryService = mock(AccountPasswordHistoryService.class);
    private final AdminConfigService configService = mock(AdminConfigService.class);

    private static final String CURRENT_PASSWORD = "CurrentAdmin@123";
    private static final String NEW_PASSWORD = "NewAdminAccess@456";

    private AdminPasswordResetServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminPasswordResetServiceImpl(
                systemAuthService,
                appMapper,
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
        app.setId(1L);
        app.setAppCode("ADMIN");
        app.setStatus(1);
        when(appMapper.selectOne(any())).thenReturn(app);
        when(resetRequestMapper.selectCount(any())).thenReturn(0L);
        when(rateLimiter.tryAcquire(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(SlidingWindowRateLimitResult.permitted());
        when(configService.enabledConfigValues(any())).thenReturn(Map.of(
                "ADMIN_FRONTEND_BASE_URL", "http://127.0.0.1:5173"));
        when(emailService.sendByTemplate(any())).thenReturn(pendingEmailResult());
        when(resetRequestMapper.insert(any(SysPasswordResetRequestDO.class))).thenAnswer(invocation -> {
            SysPasswordResetRequestDO row = invocation.getArgument(0);
            row.setId(1001L);
            return 1;
        });
    }

    @Test
    void shouldLetAdministratorSendOneTimeResetLinkWithoutChangingPassword() {
        SysAccountDO account = eligibleAccountWithPassword(CURRENT_PASSWORD);
        String originalHash = account.getPasswordHash();
        when(accountMapper.selectById(101L)).thenReturn(account);

        PasswordResetRequestResponse response = service.requestResetForAccount(
                101L, "zh-CN", "127.0.0.1", "JUnit");

        assertThat(response.getRequestId()).isNotBlank();
        assertThat(account.getPasswordHash()).isEqualTo(originalHash);
        ArgumentCaptor<EmailSendRequest> emailCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailService).sendByTemplate(emailCaptor.capture());
        assertThat(emailCaptor.getValue().getVariables()).doesNotContainKey("temporaryPassword");
        assertThat(emailCaptor.getValue().getVariables().get("verificationUrl").toString())
                .contains("/forgot-password?requestId=");
        verify(accountMapper, never()).updateById(any(SysAccountDO.class));
        verify(sessionMapper, never()).logoutAllActive(any(), any(), any());
    }

    @Test
    void shouldSendVerificationEmailOnlyForEligibleAccount() {
        log.info("测试找回密码申请，关键输入: 已启用账号、可信邮箱、有效图形验证码");
        SysAccountDO account = eligibleAccount();
        when(accountMapper.selectOne(any())).thenReturn(account);

        PasswordResetRequestResponse response = service.requestReset(request(), "127.0.0.1", "JUnit");

        assertThat(response.getRequestId()).isNotBlank();
        assertThat(response.getExpireSeconds()).isEqualTo(1800);
        verify(systemAuthService).verifyLoginCaptcha("ADMIN", "12", "ABCD", "127.0.0.1");
        ArgumentCaptor<SysPasswordResetRequestDO> resetRequestCaptor =
                ArgumentCaptor.forClass(SysPasswordResetRequestDO.class);
        verify(resetRequestMapper).insert(resetRequestCaptor.capture());
        assertThat(Duration.between(
                resetRequestCaptor.getValue().getCreatedAt(),
                resetRequestCaptor.getValue().getEmailExpireAt()
        )).isEqualTo(Duration.ofMinutes(30));
        ArgumentCaptor<EmailSendRequest> emailCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailService).sendByTemplate(emailCaptor.capture());
        assertThat(emailCaptor.getValue().getLocale()).isEqualTo("zh-CN");
        assertThat(emailCaptor.getValue().getVariables().get("expireMinutes")).isEqualTo(30);
        assertThat(emailCaptor.getValue().getVariables().get("securityNotice"))
                .isEqualTo("如非本人操作，请忽略本邮件并联系其他管理员。");
        String emailCode = String.valueOf(emailCaptor.getValue().getVariables().get("verifyCode"));
        assertThat(emailCode)
                .hasSize(6)
                .containsPattern("[0-9]")
                .containsPattern("[a-z]")
                .containsPattern("[A-Z]")
                .matches("^[A-Za-z0-9]{6}$");
        log.info("找回密码申请验证完成，结果: 请求结构固定且已触发验证码邮件");
    }

    @Test
    void shouldGenerateReadableMixedCaseEmailCodes() {
        log.info("测试邮件验证码字符规则，关键输入: 连续生成 32 个验证码");
        when(accountMapper.selectOne(any())).thenReturn(eligibleAccount());

        for (int index = 0; index < 32; index++) {
            service.requestReset(request(), "127.0.0.1", "JUnit");
        }

        ArgumentCaptor<EmailSendRequest> emailCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailService, times(32)).sendByTemplate(emailCaptor.capture());
        assertThat(emailCaptor.getAllValues())
                .extracting(email -> String.valueOf(email.getVariables().get("verifyCode")))
                .allSatisfy(emailCode -> assertThat(emailCode)
                        .hasSize(6)
                        .containsPattern("[2-9]")
                        .containsPattern("[a-km-z]")
                        .containsPattern("[A-HJ-NP-Z]")
                        .doesNotContain("0", "O", "1", "I", "l")
                        .matches("^[2-9A-HJ-NP-Za-km-z]{6}$"));
        log.info("邮件验证码字符规则验证完成，结果: 每个验证码均包含三类字符且不含易混淆字符");
    }

    @Test
    void shouldReturnSameShapeWithoutEmailForUnknownAccount() {
        log.info("测试账号枚举防护，关键输入: 不存在的登录账号");
        when(accountMapper.selectOne(any())).thenReturn(null);

        PasswordResetRequestResponse unknown = service.requestReset(request(), "127.0.0.1", "JUnit");

        assertThat(unknown.getRequestId()).isNotBlank();
        assertThat(unknown.getExpireSeconds()).isEqualTo(1800);
        assertThat(unknown.getMessage()).isEqualTo("If the account is eligible, a verification email has been sent.");
        verify(emailService, never()).sendByTemplate(any());
        verify(resetRequestMapper).insert(any(SysPasswordResetRequestDO.class));
        log.info("账号枚举防护验证完成，结果: 虚拟请求已持久化且未发送邮件");
    }

    @Test
    void shouldRejectWhenDistributedRateLimitDeniesRequest() {
        when(rateLimiter.tryAcquire(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(SlidingWindowRateLimitResult.rejected(60));

        assertThatThrownBy(() -> service.requestReset(request(), "127.0.0.1", "JUnit"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Too many requests. Please retry later.");

        verify(resetRequestMapper, never()).insert(any(SysPasswordResetRequestDO.class));
        verify(emailService, never()).sendByTemplate(any());
    }

    @Test
    void shouldLockRequestAfterFiftiethInvalidEmailCode() {
        SysPasswordResetRequestDO row = pendingEmailRequest("123456");
        row.setEmailVerifyCount(49);
        when(resetRequestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);
        when(accountMapper.selectById(101L)).thenReturn(eligibleAccount());

        assertThatThrownBy(() -> service.verifyEmail(emailVerifyRequest("654321"), "127.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Verification failed or expired.");

        assertThat(row.getEmailVerifyCount()).isEqualTo(50);
        assertThat(row.getState()).isEqualTo("LOCKED");
        verify(resetRequestMapper).updateById(row);
    }

    @Test
    void shouldRequireExistingEnabledMfaAfterEmailVerification() {
        SysPasswordResetRequestDO row = pendingEmailRequest("aB3xY7");
        SysAccountDO account = eligibleAccount();
        SysAccountMfaDO mfa = enabledMfa("JBSWY3DPEHPK3PXP");
        when(resetRequestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);
        when(accountMapper.selectById(101L)).thenReturn(account);
        when(mfaMapper.selectOne(any())).thenReturn(mfa);

        PasswordResetVerifyResponse response = service.verifyEmail(emailVerifyRequest("aB3xY7"), "127.0.0.1");

        assertThat(response.getNextStep()).isEqualTo("MFA");
        assertThat(response.getActionToken()).isNotBlank();
        assertThat(row.getState()).isEqualTo("PENDING_MFA");
        assertThat(row.getStageTokenHash()).isEqualTo(LoginTokenUtils.hashToken(response.getActionToken()));
        assertThat(row.getResetTokenHash()).isNull();
    }

    @Test
    void shouldTreatEmailVerificationCodeAsCaseSensitive() {
        SysPasswordResetRequestDO row = pendingEmailRequest("aB3xY7");
        when(resetRequestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);
        when(accountMapper.selectById(101L)).thenReturn(eligibleAccount());

        assertThatThrownBy(() -> service.verifyEmail(emailVerifyRequest("Ab3xY7"), "127.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Verification failed or expired.");

        assertThat(row.getEmailVerifyCount()).isEqualTo(1);
        assertThat(row.getState()).isEqualTo("PENDING_EMAIL");
        verify(resetRequestMapper).updateById(row);
        verify(rateLimiter).tryAcquire(org.mockito.ArgumentMatchers.eq("adminPasswordResetEmailVerifyIp"),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"), anyInt(), any(Duration.class));
    }

    @Test
    void shouldCommitDeliberateVerificationFailureButRollbackUnexpectedException() throws Exception {
        log.info("测试验证码失败事务边界，关键输入: 错误邮件验证码与非预期运行时异常");
        SysPasswordResetRequestDO row = pendingEmailRequest("aB3xY7");
        when(resetRequestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);
        when(accountMapper.selectById(101L)).thenReturn(eligibleAccount());

        Throwable verificationFailure = catchThrowable(
                () -> service.verifyEmail(emailVerifyRequest("xY7aB3"), "127.0.0.1")
        );
        Method verifyEmail = AdminPasswordResetServiceImpl.class.getMethod(
                "verifyEmail", PasswordResetEmailVerifyRequest.class, String.class
        );
        TransactionAttribute attribute = new AnnotationTransactionAttributeSource()
                .getTransactionAttribute(verifyEmail, AdminPasswordResetServiceImpl.class);

        assertThat(verificationFailure).isInstanceOf(ServiceException.class);
        assertThat(attribute).isNotNull();
        assertThat(attribute.rollbackOn(verificationFailure)).isFalse();
        assertThat(attribute.rollbackOn(new IllegalStateException("database unavailable"))).isTrue();
        assertThat(row.getEmailVerifyCount()).isEqualTo(1);
        verify(resetRequestMapper).updateById(row);
        log.info("验证码失败事务边界验证完成，结果: 业务失败提交计数且非预期异常保持回滚");
    }

    @Test
    void shouldRejectReplayedTotpTimeStep() throws Exception {
        byte[] secretBytes = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        long timeStep = Instant.now().getEpochSecond() / 30;
        String actionToken = "mfa-action-token";
        SysPasswordResetRequestDO row = pendingMfaRequest(actionToken);
        SysAccountMfaDO mfa = enabledMfa(secret);
        mfa.setLastSuccessTimeStep(timeStep);
        when(resetRequestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);
        when(accountMapper.selectById(101L)).thenReturn(eligibleAccount());
        when(mfaMapper.selectOne(any())).thenReturn(mfa);

        PasswordResetMfaVerifyRequest request = new PasswordResetMfaVerifyRequest();
        request.setRequestId("request-1");
        request.setActionToken(actionToken);
        request.setTotpCode(generateTotp(secretBytes, timeStep));

        assertThatThrownBy(() -> service.verifyMfa(request, "127.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Verification failed or expired.");

        assertThat(mfa.getFailedVerifyCount()).isEqualTo(1);
        assertThat(mfa.getLastSuccessTimeStep()).isEqualTo(timeStep);
        assertThat(row.getState()).isEqualTo("PENDING_MFA");
        verify(mfaMapper).updateById(mfa);
        verify(rateLimiter).tryAcquire(org.mockito.ArgumentMatchers.eq("adminPasswordResetMfaVerifyIp"),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"), anyInt(), any(Duration.class));
    }

    @Test
    void shouldNotExpireAdvancedRequestWhenEmailStageIsReplayed() {
        SysPasswordResetRequestDO row = readyRequest("valid-reset-token");
        when(resetRequestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);

        assertThatThrownBy(() -> service.verifyEmail(emailVerifyRequest("wrong-code"), "127.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Verification failed or expired.");

        assertThat(row.getState()).isEqualTo("READY");
        assertThat(row.getResetTokenHash()).isEqualTo(LoginTokenUtils.hashToken("valid-reset-token"));
    }

    @Test
    void shouldConsumeResetTokenOnceAndRevokeEverySession() {
        String resetToken = "one-time-reset-token";
        SysPasswordResetRequestDO row = readyRequest(resetToken);
        SysAccountDO account = eligibleAccountWithPassword(CURRENT_PASSWORD);
        when(resetRequestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(account);

        service.confirm(confirmRequest(resetToken, NEW_PASSWORD), "127.0.0.1");

        assertThat(row.getState()).isEqualTo("COMPLETED");
        assertThat(row.getResetTokenHash()).isNull();
        assertThat(PasswordHashUtils.matches(NEW_PASSWORD, account.getPasswordSalt(), account.getPasswordHash())).isTrue();
        verify(passwordHistoryService).assertNotRecentlyUsed(account, NEW_PASSWORD);
        verify(passwordHistoryService).recordCurrentPassword(org.mockito.ArgumentMatchers.eq(account),
                any(LocalDateTime.class));
        verify(sessionMapper).logoutAllActive(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(101L), any(LocalDateTime.class));
        verify(emailService).sendByTemplate(any());
        verify(rateLimiter).tryAcquire(org.mockito.ArgumentMatchers.eq("adminPasswordResetConfirmIp"),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"), anyInt(), any(Duration.class));

        assertThatThrownBy(() -> service.confirm(confirmRequest(resetToken, NEW_PASSWORD), "127.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Verification failed or expired.");
        verify(sessionMapper, times(1)).logoutAllActive(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(101L), any(LocalDateTime.class));
    }

    @Test
    void shouldKeepResetRequestUsableAfterWrongToken() {
        String resetToken = "still-valid-reset-token";
        SysPasswordResetRequestDO row = readyRequest(resetToken);
        SysAccountDO account = eligibleAccountWithPassword(CURRENT_PASSWORD);
        when(resetRequestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(account);

        assertThatThrownBy(() -> service.confirm(
                confirmRequest("wrong-token", NEW_PASSWORD), "127.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Verification failed or expired.");
        assertThat(row.getState()).isEqualTo("READY");
        assertThat(row.getResetTokenHash()).isEqualTo(LoginTokenUtils.hashToken(resetToken));

        service.confirm(confirmRequest(resetToken, NEW_PASSWORD), "127.0.0.1");

        assertThat(row.getState()).isEqualTo("COMPLETED");
        verify(accountMapper).updateById(account);
    }

    @Test
    void shouldRejectRecentlyUsedPasswordWithoutConsumingResetToken() {
        String resetToken = "retryable-reset-token";
        SysPasswordResetRequestDO row = readyRequest(resetToken);
        SysAccountDO account = eligibleAccountWithPassword(CURRENT_PASSWORD);
        when(resetRequestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(account);
        doThrow(new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "new password was used recently"))
                .when(passwordHistoryService).assertNotRecentlyUsed(account, CURRENT_PASSWORD);

        assertThatThrownBy(() -> service.confirm(confirmRequest(resetToken, CURRENT_PASSWORD), "127.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("new password was used recently");

        assertThat(row.getState()).isEqualTo("READY");
        assertThat(row.getResetTokenHash()).isEqualTo(LoginTokenUtils.hashToken(resetToken));
        verify(accountMapper, never()).updateById(any(SysAccountDO.class));
        verify(sessionMapper, never()).logoutAllActive(any(), any(), any());
    }

    private PasswordResetRequest request() {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setLoginAccount("admin");
        request.setVerifyCodeId("12");
        request.setVerifyCode("ABCD");
        request.setLocale("zh-CN");
        return request;
    }

    private SysAccountDO eligibleAccount() {
        SysAccountDO account = new SysAccountDO();
        account.setId(101L);
        account.setAppId(1L);
        account.setUserId(201L);
        account.setLoginAccount("admin");
        account.setEmail("admin@example.com");
        account.setEmailVerifiedAt(LocalDateTime.now().minusDays(1));
        account.setStatus(1);
        account.setLocked(0);
        account.setDeleted(0L);
        return account;
    }

    private SysAccountDO eligibleAccountWithPassword(String password) {
        SysAccountDO account = eligibleAccount();
        String salt = PasswordHashUtils.generateSalt();
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(password, salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        return account;
    }

    private SysPasswordResetRequestDO pendingEmailRequest(String emailCode) {
        String salt = PasswordHashUtils.generateSalt();
        SysPasswordResetRequestDO row = baseRequest();
        row.setState("PENDING_EMAIL");
        row.setEmailCodeSalt(salt);
        row.setEmailCodeHash(PasswordHashUtils.hashPassword(emailCode, salt));
        row.setEmailExpireAt(LocalDateTime.now().plusMinutes(30));
        row.setEmailVerifyCount(0);
        return row;
    }

    private SysPasswordResetRequestDO pendingMfaRequest(String actionToken) {
        SysPasswordResetRequestDO row = baseRequest();
        row.setState("PENDING_MFA");
        row.setStageTokenHash(LoginTokenUtils.hashToken(actionToken));
        row.setStageTokenExpireAt(LocalDateTime.now().plusMinutes(5));
        return row;
    }

    private SysPasswordResetRequestDO readyRequest(String resetToken) {
        SysPasswordResetRequestDO row = baseRequest();
        row.setState("READY");
        row.setResetTokenHash(LoginTokenUtils.hashToken(resetToken));
        row.setResetTokenExpireAt(LocalDateTime.now().plusMinutes(5));
        return row;
    }

    private SysPasswordResetRequestDO baseRequest() {
        SysPasswordResetRequestDO row = new SysPasswordResetRequestDO();
        row.setId(1001L);
        row.setRequestNo("request-1");
        row.setAppId(1L);
        row.setAccountId(101L);
        row.setRequestIp("127.0.0.1");
        row.setLocale("zh-CN");
        row.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        row.setUpdatedAt(LocalDateTime.now().minusMinutes(1));
        return row;
    }

    private PasswordResetEmailVerifyRequest emailVerifyRequest(String emailCode) {
        PasswordResetEmailVerifyRequest request = new PasswordResetEmailVerifyRequest();
        request.setRequestId("request-1");
        request.setEmailCode(emailCode);
        return request;
    }

    private PasswordResetConfirmRequest confirmRequest(String resetToken, String newPassword) {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        request.setRequestId("request-1");
        request.setResetToken(resetToken);
        request.setNewPassword(newPassword);
        return request;
    }

    private SysAccountMfaDO enabledMfa(String secret) {
        SysAccountMfaDO mfa = new SysAccountMfaDO();
        mfa.setId(301L);
        mfa.setAppId(1L);
        mfa.setAccountId(101L);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_ENABLED);
        mfa.setSecretCipher(MfaSecretCrypto.encrypt(secret));
        mfa.setFailedVerifyCount(0);
        mfa.setDeleted(AuthConstants.NOT_DELETED);
        return mfa;
    }

    private EmailSendResult pendingEmailResult() {
        EmailSendResult result = new EmailSendResult();
        result.setRecordId(9001L);
        result.setSendStatus(EmailDeliveryStatus.PENDING.getCode());
        return result;
    }

    private String generateTotp(byte[] secret, long step) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
    }
}
