package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.AccountActivationCompleteRequest;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.EmailVerificationVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.EmailVerificationVerifyResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.admin.service.AdminEmailVerificationService;
import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysEmailVerificationRequestDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysEmailVerificationRequestMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.auth.service.AccountPasswordHistoryService;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 管理账号激活与邮箱验证状态机的安全回归测试。 */
class AdminEmailVerificationServiceImplTest {

    private final SysAppMapper appMapper = mock(SysAppMapper.class);
    private final SysAccountMapper accountMapper = mock(SysAccountMapper.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysLoginSessionMapper sessionMapper = mock(SysLoginSessionMapper.class);
    private final SysEmailVerificationRequestMapper requestMapper = mock(SysEmailVerificationRequestMapper.class);
    private final AdminEmailService emailService = mock(AdminEmailService.class);
    private final AdminConfigService configService = mock(AdminConfigService.class);
    private final RedisSlidingWindowRateLimiter rateLimiter = mock(RedisSlidingWindowRateLimiter.class);
    private final AccountPasswordHistoryService passwordHistoryService = mock(AccountPasswordHistoryService.class);

    private AdminEmailVerificationServiceImpl service;
    private SysAccountDO account;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, SysEmailVerificationRequestDO.class);
        service = new AdminEmailVerificationServiceImpl(
                appMapper, accountMapper, userMapper, sessionMapper, requestMapper,
                emailService, configService, rateLimiter, passwordHistoryService
        );
        SysAppDO app = new SysAppDO();
        app.setId(1L);
        app.setAppCode(AuthConstants.APP_ADMIN);
        app.setStatus(AuthConstants.ENABLED);
        app.setDeleted(AuthConstants.NOT_DELETED);
        when(appMapper.selectOne(any())).thenReturn(app);

        account = account("admin@example.com", AuthConstants.ACTIVATION_STATUS_PENDING);
        when(accountMapper.selectOne(any())).thenReturn(account);
        when(accountMapper.selectById(101L)).thenReturn(account);
        when(accountMapper.selectByIdForUpdate(101L)).thenReturn(account);
        SysUserDO user = new SysUserDO();
        user.setId(201L);
        user.setRealName("管理员");
        user.setLanguage("zh-CN");
        when(userMapper.selectById(201L)).thenReturn(user);
        when(requestMapper.selectCount(any())).thenReturn(0L);
        when(rateLimiter.tryAcquire(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(SlidingWindowRateLimitResult.permitted());
        when(configService.enabledConfigValues(any())).thenReturn(Map.of());
        EmailSendResult pending = new EmailSendResult();
        pending.setRecordId(9001L);
        pending.setSendStatus(EmailDeliveryStatus.PENDING.getCode());
        when(emailService.sendByTemplate(any())).thenReturn(pending);
        when(requestMapper.insert(any(SysEmailVerificationRequestDO.class))).thenAnswer(invocation -> {
            SysEmailVerificationRequestDO row = invocation.getArgument(0);
            row.setId(1001L);
            return 1;
        });
    }

    @Test
    void shouldIssueMixedCaseCodeValidForThirtyMinutesWithoutDescribingComposition() {
        service.issue(101L, AdminEmailVerificationService.PURPOSE_ACCOUNT_ACTIVATION,
                "zh-CN", "127.0.0.1", "JUnit", false);

        ArgumentCaptor<SysEmailVerificationRequestDO> rowCaptor =
                ArgumentCaptor.forClass(SysEmailVerificationRequestDO.class);
        verify(requestMapper).insert(rowCaptor.capture());
        SysEmailVerificationRequestDO row = rowCaptor.getValue();
        assertThat(Duration.between(row.getCreatedAt(), row.getExpireAt())).isEqualTo(Duration.ofMinutes(30));
        assertThat(row.getCodeHash()).isNotBlank();
        assertThat(row.getEmailFingerprint()).isEqualTo(fingerprint(account.getEmail()));

        ArgumentCaptor<EmailSendRequest> emailCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailService).sendByTemplate(emailCaptor.capture());
        EmailSendRequest email = emailCaptor.getValue();
        assertThat(email.getTemplateCode()).isEqualTo("ADMIN_ACCOUNT_ACTIVATION");
        assertThat(email.getVariables().get("expireMinutes")).isEqualTo(30);
        assertThat(String.valueOf(email.getVariables().get("securityNotice")))
                .doesNotContain("大写", "小写", "区分大小写");
        String code = String.valueOf(email.getVariables().get("verificationCode"));
        assertThat(code).hasSize(6).containsPattern("[2-9]").containsPattern("[a-z]")
                .containsPattern("[A-Z]").matches("^[A-Za-z0-9]{6}$");
        assertThat(PasswordHashUtils.matches(code, row.getCodeSalt(), row.getCodeHash())).isTrue();
    }

    @Test
    void shouldUseStandardTemplateForManualEmailVerification() {
        account.setActivationStatus(AuthConstants.ACTIVATION_STATUS_ACTIVE);

        service.issue(101L, AdminEmailVerificationService.PURPOSE_EMAIL_VERIFICATION,
                "zh-CN", "127.0.0.1", "JUnit", false);

        ArgumentCaptor<SysEmailVerificationRequestDO> rowCaptor =
                ArgumentCaptor.forClass(SysEmailVerificationRequestDO.class);
        verify(requestMapper).insert(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getPurpose())
                .isEqualTo(AdminEmailVerificationService.PURPOSE_EMAIL_VERIFICATION);

        ArgumentCaptor<EmailSendRequest> emailCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailService).sendByTemplate(emailCaptor.capture());
        assertThat(emailCaptor.getValue().getTemplateCode()).isEqualTo("ADMIN_EMAIL_VERIFICATION");
        assertThat(emailCaptor.getValue().getSceneCode()).isEqualTo("EMAIL_VERIFICATION");
    }

    @Test
    void shouldPromotePendingEmailOnlyAfterCodeVerification() {
        account.setActivationStatus(AuthConstants.ACTIVATION_STATUS_ACTIVE);
        account.setEmailVerifiedAt(LocalDateTime.now().minusDays(1));
        account.setPendingEmail("new-admin@example.com");

        service.issue(101L, AdminEmailVerificationService.PURPOSE_EMAIL_CHANGE,
                "zh-CN", "127.0.0.1", "JUnit", false);

        ArgumentCaptor<SysEmailVerificationRequestDO> rowCaptor =
                ArgumentCaptor.forClass(SysEmailVerificationRequestDO.class);
        verify(requestMapper).insert(rowCaptor.capture());
        ArgumentCaptor<EmailSendRequest> emailCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailService).sendByTemplate(emailCaptor.capture());
        String code = String.valueOf(emailCaptor.getValue().getVariables().get("verificationCode"));
        SysEmailVerificationRequestDO row = rowCaptor.getValue();
        when(requestMapper.selectByRequestNoForUpdate(row.getRequestNo())).thenReturn(row);

        EmailVerificationVerifyResponse response = service.verify(
                verificationRequest(row.getRequestNo(), code), "127.0.0.1");

        assertThat(response.getNextStep()).isEqualTo("COMPLETED");
        assertThat(account.getEmail()).isEqualTo("new-admin@example.com");
        assertThat(account.getPendingEmail()).isNull();
        assertThat(account.getEmailVerifiedAt()).isNotNull();
        ArgumentCaptor<SysUserDO> userCaptor = ArgumentCaptor.forClass(SysUserDO.class);
        verify(userMapper).updateById(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new-admin@example.com");
    }

    @Test
    void shouldTreatVerificationCodeAsCaseSensitive() {
        SysEmailVerificationRequestDO row = pendingRequest("aB3xY7");
        when(requestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);

        assertThatThrownBy(() -> service.verify(verifyRequest("Ab3xY7"), "127.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Verification failed or expired.");

        assertThat(row.getVerifyCount()).isEqualTo(1);
        assertThat(row.getState()).isEqualTo("PENDING");
        verify(requestMapper).updateById(row);
    }

    @Test
    void shouldNotExpireVerifiedActivationWhenEmailStageIsReplayed() {
        SysEmailVerificationRequestDO row = pendingRequest("aB3xY7");
        row.setState("VERIFIED");
        row.setActionTokenHash(LoginTokenUtils.hashToken("valid-action-token"));
        row.setActionTokenExpireAt(LocalDateTime.now().plusMinutes(5));
        when(requestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);

        assertThatThrownBy(() -> service.verify(verifyRequest("wrong-code"), "127.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Verification failed or expired.");

        assertThat(row.getState()).isEqualTo("VERIFIED");
        assertThat(row.getActionTokenHash()).isEqualTo(LoginTokenUtils.hashToken("valid-action-token"));
    }

    @Test
    void shouldExpireCodeAfterThirtyMinutes() {
        SysEmailVerificationRequestDO row = pendingRequest("aB3xY7");
        row.setExpireAt(LocalDateTime.now().minusSeconds(1));
        when(requestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);

        assertThatThrownBy(() -> service.verify(verifyRequest("aB3xY7"), "127.0.0.1"))
                .isInstanceOf(ServiceException.class);

        assertThat(row.getState()).isEqualTo("EXPIRED");
        verify(accountMapper, never()).updateById(any(SysAccountDO.class));
    }

    @Test
    void shouldRejectOldRequestAfterEmailChanges() {
        SysEmailVerificationRequestDO row = pendingRequest("aB3xY7");
        row.setEmailFingerprint(fingerprint("old@example.com"));
        when(requestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);

        assertThatThrownBy(() -> service.verify(verifyRequest("aB3xY7"), "127.0.0.1"))
                .isInstanceOf(ServiceException.class);

        assertThat(row.getVerifyCount()).isEqualTo(1);
        assertThat(account.getEmailVerifiedAt()).isNull();
        verify(accountMapper, never()).updateById(any(SysAccountDO.class));
    }

    @Test
    void shouldActivateOnceAndRejectActionTokenReplay() {
        SysEmailVerificationRequestDO row = pendingRequest("aB3xY7");
        when(requestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);

        EmailVerificationVerifyResponse verified = service.verify(verifyRequest("aB3xY7"), "127.0.0.1");
        assertThat(verified.getNextStep()).isEqualTo("SET_PASSWORD");
        assertThat(verified.getExpireSeconds()).isEqualTo(300);
        assertThat(verified.getActionToken()).isNotBlank();

        AccountActivationCompleteRequest complete = new AccountActivationCompleteRequest();
        complete.setRequestId("request-1");
        complete.setActionToken(verified.getActionToken());
        complete.setNewPassword("NewAdminAccess@456");
        service.completeActivation(complete, "127.0.0.1");

        assertThat(row.getState()).isEqualTo("COMPLETED");
        assertThat(row.getActionTokenHash()).isNull();
        assertThat(account.getActivationStatus()).isEqualTo(AuthConstants.ACTIVATION_STATUS_ACTIVE);
        assertThat(account.getEmailVerifiedAt()).isNotNull();
        verify(passwordHistoryService).assertNotRecentlyUsed(account, "NewAdminAccess@456");
        verify(passwordHistoryService).recordCurrentPassword(org.mockito.ArgumentMatchers.eq(account),
                any(LocalDateTime.class));
        verify(sessionMapper).logoutAllActive(any(), any(), any(LocalDateTime.class));

        assertThatThrownBy(() -> service.completeActivation(complete, "127.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Verification failed or expired.");
        verify(sessionMapper, times(1)).logoutAllActive(any(), any(), any(LocalDateTime.class));
    }

    @Test
    void shouldKeepActivationRequestUsableAfterWrongActionToken() {
        SysEmailVerificationRequestDO row = pendingRequest("aB3xY7");
        when(requestMapper.selectByRequestNoForUpdate("request-1")).thenReturn(row);
        EmailVerificationVerifyResponse verified = service.verify(verifyRequest("aB3xY7"), "127.0.0.1");

        AccountActivationCompleteRequest wrong = new AccountActivationCompleteRequest();
        wrong.setRequestId("request-1");
        wrong.setActionToken("wrong-token");
        wrong.setNewPassword("NewAdminAccess@456");
        assertThatThrownBy(() -> service.completeActivation(wrong, "127.0.0.1"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Verification failed or expired.");
        assertThat(row.getState()).isEqualTo("VERIFIED");

        AccountActivationCompleteRequest correct = new AccountActivationCompleteRequest();
        correct.setRequestId("request-1");
        correct.setActionToken(verified.getActionToken());
        correct.setNewPassword("NewAdminAccess@456");
        service.completeActivation(correct, "127.0.0.1");

        assertThat(row.getState()).isEqualTo("COMPLETED");
        assertThat(account.getActivationStatus()).isEqualTo(AuthConstants.ACTIVATION_STATUS_ACTIVE);
    }

    @Test
    void shouldRejectManualResendWhenDistributedRateLimitDenies() {
        when(rateLimiter.tryAcquire(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(SlidingWindowRateLimitResult.rejected(6));

        assertThatThrownBy(() -> service.issue(101L,
                AdminEmailVerificationService.PURPOSE_ACCOUNT_ACTIVATION,
                "zh-CN", "127.0.0.1", "JUnit", true))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Too many requests. Please retry later.");

        verify(requestMapper, never()).insert(any(SysEmailVerificationRequestDO.class));
        verify(emailService, never()).sendByTemplate(any());
    }

    private SysAccountDO account(String email, String activationStatus) {
        SysAccountDO value = new SysAccountDO();
        value.setId(101L);
        value.setAppId(1L);
        value.setUserId(201L);
        value.setLoginAccount("admin");
        value.setEmail(email);
        value.setActivationStatus(activationStatus);
        value.setStatus(AuthConstants.ENABLED);
        value.setLocked(AuthConstants.DISABLED);
        value.setDeleted(AuthConstants.NOT_DELETED);
        return value;
    }

    private SysEmailVerificationRequestDO pendingRequest(String code) {
        String salt = PasswordHashUtils.generateSalt();
        SysEmailVerificationRequestDO row = new SysEmailVerificationRequestDO();
        row.setId(1001L);
        row.setRequestNo("request-1");
        row.setAppId(1L);
        row.setAccountId(101L);
        row.setPurpose(AdminEmailVerificationService.PURPOSE_ACCOUNT_ACTIVATION);
        row.setEmailFingerprint(fingerprint(account.getEmail()));
        row.setCodeSalt(salt);
        row.setCodeHash(PasswordHashUtils.hashPassword(code, salt));
        row.setExpireAt(LocalDateTime.now().plusMinutes(30));
        row.setVerifyCount(0);
        row.setState("PENDING");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        return row;
    }

    private EmailVerificationVerifyRequest verifyRequest(String code) {
        return verificationRequest("request-1", code);
    }

    private EmailVerificationVerifyRequest verificationRequest(String requestId, String code) {
        EmailVerificationVerifyRequest request = new EmailVerificationVerifyRequest();
        request.setRequestId(requestId);
        request.setEmailCode(code);
        return request;
    }

    private String fingerprint(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
