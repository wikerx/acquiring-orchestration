package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.auth.PasswordPolicy;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
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
import com.scott.payment.component.db.auth.support.MfaSecretCrypto;
import com.scott.payment.component.db.auth.support.TotpUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetConfirmRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetConfirmResponse;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetEmailVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetMfaVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetRequestResponse;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetVerifyResponse;
import com.scott.payment.merchant.service.MerchantConfigService;
import com.scott.payment.merchant.service.MerchantPasswordResetService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService.MerchantEmailSendCommand;
import com.scott.payment.merchant.service.MerchantTemplateEmailService.MerchantEmailSendResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantPasswordResetServiceImpl
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 商户系统找回密码服务实现；以商户号和登录账号联合定位账号，并以虚拟请求防止账号枚举。
 * @status : create
 */
@Service
@Slf4j
public class MerchantPasswordResetServiceImpl implements MerchantPasswordResetService {

    /** 固定不存在的账号主键，用于让虚拟请求执行与真实请求相同的账号查询。 */
    private static final long VIRTUAL_ACCOUNT_ID = -1L;

    private static final String STATE_PENDING_EMAIL = "PENDING_EMAIL";
    private static final String STATE_PENDING_MFA = "PENDING_MFA";
    private static final String STATE_READY = "READY";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_EXPIRED = "EXPIRED";
    private static final String STATE_LOCKED = "LOCKED";
    private static final String STATE_BLOCKED = "BLOCKED";
    private static final String NEXT_MFA = "MFA";
    private static final String NEXT_RESET_PASSWORD = "RESET_PASSWORD";
    private static final String NEXT_MERCHANT_SUPPORT = "MERCHANT_SUPPORT";
    private static final String GENERIC_REQUEST_MESSAGE = "If the account is eligible, a verification email has been sent.";
    private static final int EMAIL_CODE_TTL_SECONDS = 30 * 60;
    private static final int ACTION_TOKEN_TTL_SECONDS = 5 * 60;
    private static final int MAX_EMAIL_VERIFY_ATTEMPTS = 50;
    private static final int MAX_MFA_VERIFY_ATTEMPTS = 5;
    private static final int ACCOUNT_REQUESTS_PER_HOUR = 50;
    private static final int IP_REQUESTS_PER_HOUR = 200;
    private static final int RESEND_COOLDOWN_SECONDS = 6;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final int EMAIL_CODE_LENGTH = 6;
    private static final int MFA_PERIOD_SECONDS = 30;
    private static final int MFA_WINDOW = 1;
    private static final String EMAIL_CODE_DIGITS = "23456789";
    private static final String EMAIL_CODE_LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    private static final String EMAIL_CODE_UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String EMAIL_CODE_CHARACTERS = EMAIL_CODE_DIGITS
            + EMAIL_CODE_LOWERCASE + EMAIL_CODE_UPPERCASE;
    private static final String RATE_SCOPE_COOLDOWN = "merchantPasswordResetCooldown";
    private static final String RATE_SCOPE_ACCOUNT = "merchantPasswordResetAccount";
    private static final String RATE_SCOPE_IP = "merchantPasswordResetIp";
    private static final String RATE_SCOPE_EMAIL_VERIFY_IP = "merchantPasswordResetEmailVerifyIp";
    private static final String RATE_SCOPE_MFA_VERIFY_IP = "merchantPasswordResetMfaVerifyIp";
    private static final String RATE_SCOPE_CONFIRM_IP = "merchantPasswordResetConfirmIp";
    private static final String FRONTEND_BASE_URL_KEY = "platform.merchant.frontend-base-url";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SystemAuthService systemAuthService;
    private final SysAppMapper appMapper;
    private final SysMerchantUserMapper merchantUserMapper;
    private final SysAccountMapper accountMapper;
    private final SysAccountMfaMapper mfaMapper;
    private final SysLoginSessionMapper sessionMapper;
    private final SysPasswordResetRequestMapper resetRequestMapper;
    private final MerchantTemplateEmailService emailService;
    private final RedisSlidingWindowRateLimiter rateLimiter;
    private final AccountPasswordHistoryService passwordHistoryService;
    private final MerchantConfigService configService;

    /**
     * 创建商户找回密码服务。
     */
    public MerchantPasswordResetServiceImpl(SystemAuthService systemAuthService,
                                            SysAppMapper appMapper,
                                            SysMerchantUserMapper merchantUserMapper,
                                            SysAccountMapper accountMapper,
                                            SysAccountMfaMapper mfaMapper,
                                            SysLoginSessionMapper sessionMapper,
                                            SysPasswordResetRequestMapper resetRequestMapper,
                                            MerchantTemplateEmailService emailService,
                                            RedisSlidingWindowRateLimiter rateLimiter,
                                            AccountPasswordHistoryService passwordHistoryService,
                                            MerchantConfigService configService) {
        this.systemAuthService = systemAuthService;
        this.appMapper = appMapper;
        this.merchantUserMapper = merchantUserMapper;
        this.accountMapper = accountMapper;
        this.mfaMapper = mfaMapper;
        this.sessionMapper = sessionMapper;
        this.resetRequestMapper = resetRequestMapper;
        this.emailService = emailService;
        this.rateLimiter = rateLimiter;
        this.passwordHistoryService = passwordHistoryService;
        this.configService = configService;
    }

    /**
     * 创建找回密码申请。图形验证码先行消费，账号不合格时仍创建不可用的虚拟请求。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public PasswordResetRequestResponse requestReset(PasswordResetRequest request, String clientIp, String userAgent) {
        String ip = normalizeIp(clientIp);
        systemAuthService.verifyLoginCaptcha(
                AuthConstants.APP_MERCHANT, request.getVerifyCodeId(), request.getVerifyCode(), ip);
        SysAppDO app = requireMerchantApp();
        LocalDateTime now = LocalDateTime.now();
        String merchantId = normalizeMerchantId(request.getMerchantId());
        String loginAccount = normalizeLoginAccount(request.getLoginAccount());
        String accountFingerprint = sha256(merchantId + "\n" + loginAccount);
        enforceRequestRateLimit(app.getId(), merchantId, accountFingerprint, ip, now);

        SysAccountDO account = findEligibleAccount(app.getId(), merchantId, loginAccount);
        return createResetRequest(app, merchantId, account, accountFingerprint, ip, userAgent,
                normalizeLocale(request.getLocale()), now, false);
    }

    /**
     * 商户管理员发起员工密码重置。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public PasswordResetRequestResponse requestResetForAccount(Long accountId,
                                                               String locale,
                                                               String clientIp,
                                                               String userAgent) {
        SysAppDO app = requireMerchantApp();
        InternalAuthAccount operator = InternalAuthContextHolder.get();
        if (operator == null || !StringUtils.hasText(operator.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant login is required");
        }
        SysAccountDO account = accountId == null ? null : accountMapper.selectById(accountId);
        if (!eligible(account)
                || !Objects.equals(account.getAppId(), app.getId())
                || !Objects.equals(account.getMerchantId(), operator.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "account must belong to the current merchant and have a verified email");
        }
        LocalDateTime now = LocalDateTime.now();
        String ip = normalizeIp(clientIp);
        String fingerprint = sha256(account.getMerchantId() + "\n" + normalizeLoginAccount(account.getLoginAccount()));
        enforceRequestRateLimit(app.getId(), account.getMerchantId(), fingerprint, ip, now);
        return createResetRequest(app, account.getMerchantId(), account, fingerprint, ip, userAgent,
                normalizeLocale(locale), now, true);
    }

    /**
     * 校验邮件验证码。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = PasswordResetVerificationException.class)
    public PasswordResetVerifyResponse verifyEmail(PasswordResetEmailVerifyRequest request, String clientIp) {
        enforceStageIpRateLimit(RATE_SCOPE_EMAIL_VERIFY_IP, clientIp);
        SysPasswordResetRequestDO row = requireLockedRequest(request.getRequestId());
        LocalDateTime now = LocalDateTime.now();
        if (!STATE_PENDING_EMAIL.equals(row.getState())) {
            throw verificationFailed();
        }
        if (expired(row.getEmailExpireAt(), now)) {
            markExpiredIfNecessary(row, now);
            throw verificationFailed();
        }
        if (defaultInt(row.getEmailVerifyCount()) >= MAX_EMAIL_VERIFY_ATTEMPTS) {
            row.setState(STATE_LOCKED);
            row.setUpdatedAt(now);
            resetRequestMapper.updateById(row);
            throw verificationFailed();
        }
        row.setEmailVerifyCount(defaultInt(row.getEmailVerifyCount()) + 1);
        boolean matches = PasswordHashUtils.matches(
                request.getEmailCode(), row.getEmailCodeSalt(), row.getEmailCodeHash());
        SysAccountDO account = row.getAccountId() == null ? null : accountMapper.selectById(row.getAccountId());
        if (!matches || !validTarget(row, account)) {
            if (row.getEmailVerifyCount() >= MAX_EMAIL_VERIFY_ATTEMPTS) {
                row.setState(STATE_LOCKED);
            }
            row.setUpdatedAt(now);
            resetRequestMapper.updateById(row);
            throw verificationFailed();
        }
        row.setEmailVerifiedAt(now);
        SysAccountMfaDO mfa = loadMfa(row.getAppId(), account.getId());
        if (mfa == null
                || !AuthConstants.MFA_STATUS_ENABLED.equals(mfa.getMfaStatus())
                || !StringUtils.hasText(mfa.getSecretCipher())) {
            row.setState(STATE_BLOCKED);
            row.setUpdatedAt(now);
            resetRequestMapper.updateById(row);
            return verifyResponse(NEXT_MERCHANT_SUPPORT, null, 0,
                    "Self-service recovery requires an active authenticator. Contact your merchant administrator.");
        }
        String stageToken = LoginTokenUtils.generateToken();
        row.setStageTokenHash(LoginTokenUtils.hashToken(stageToken));
        row.setStageTokenExpireAt(now.plusSeconds(ACTION_TOKEN_TTL_SECONDS));
        row.setState(STATE_PENDING_MFA);
        row.setUpdatedAt(now);
        resetRequestMapper.updateById(row);
        return verifyResponse(NEXT_MFA, stageToken, ACTION_TOKEN_TTL_SECONDS,
                "Authenticator verification is required.");
    }

    /**
     * 校验商户账号 TOTP。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = PasswordResetVerificationException.class)
    public PasswordResetVerifyResponse verifyMfa(PasswordResetMfaVerifyRequest request, String clientIp) {
        enforceStageIpRateLimit(RATE_SCOPE_MFA_VERIFY_IP, clientIp);
        SysPasswordResetRequestDO row = requireLockedRequest(request.getRequestId());
        LocalDateTime now = LocalDateTime.now();
        if (!STATE_PENDING_MFA.equals(row.getState())) {
            throw verificationFailed();
        }
        if (expired(row.getStageTokenExpireAt(), now)) {
            markExpiredIfNecessary(row, now);
            throw verificationFailed();
        }
        if (!tokenMatches(request.getActionToken(), row.getStageTokenHash())) {
            throw verificationFailed();
        }
        SysAccountDO account = row.getAccountId() == null ? null : accountMapper.selectById(row.getAccountId());
        SysAccountMfaDO mfa = account == null ? null : loadMfa(row.getAppId(), account.getId());
        if (!validTarget(row, account) || mfa == null
                || !AuthConstants.MFA_STATUS_ENABLED.equals(mfa.getMfaStatus())
                || !StringUtils.hasText(mfa.getSecretCipher())) {
            row.setState(STATE_BLOCKED);
            row.setUpdatedAt(now);
            resetRequestMapper.updateById(row);
            throw verificationFailed();
        }
        Long timeStep = TotpUtils.verify(MfaSecretCrypto.decrypt(mfa.getSecretCipher()), request.getTotpCode(),
                Instant.now(), MFA_PERIOD_SECONDS, MFA_WINDOW);
        if (timeStep == null || (mfa.getLastSuccessTimeStep() != null && timeStep <= mfa.getLastSuccessTimeStep())) {
            int failedCount = defaultInt(mfa.getFailedVerifyCount()) + 1;
            mfa.setFailedVerifyCount(failedCount);
            if (failedCount >= MAX_MFA_VERIFY_ATTEMPTS) {
                mfa.setMfaStatus(AuthConstants.MFA_STATUS_LOCKED);
                mfa.setLockedUntil(now.plusMinutes(15));
                row.setState(STATE_BLOCKED);
            }
            mfa.setUpdatedAt(now);
            mfaMapper.updateById(mfa);
            row.setUpdatedAt(now);
            resetRequestMapper.updateById(row);
            throw verificationFailed();
        }
        mfa.setLastSuccessTimeStep(timeStep);
        mfa.setLastVerifyTime(now);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setUpdatedAt(now);
        mfaMapper.updateById(mfa);
        row.setMfaVerifiedAt(now);
        row.setStageTokenHash(null);
        row.setStageTokenExpireAt(null);
        return prepareResetToken(row, now);
    }

    /**
     * 原子消费重置令牌并更新密码。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = PasswordResetVerificationException.class)
    public PasswordResetConfirmResponse confirm(PasswordResetConfirmRequest request, String clientIp) {
        enforceStageIpRateLimit(RATE_SCOPE_CONFIRM_IP, clientIp);
        SysPasswordResetRequestDO row = requireLockedRequest(request.getRequestId());
        LocalDateTime now = LocalDateTime.now();
        if (!STATE_READY.equals(row.getState())) {
            throw verificationFailed();
        }
        if (expired(row.getResetTokenExpireAt(), now)) {
            markExpiredIfNecessary(row, now);
            throw verificationFailed();
        }
        if (!tokenMatches(request.getResetToken(), row.getResetTokenHash())) {
            throw verificationFailed();
        }
        SysAccountDO account = row.getAccountId() == null ? null : accountMapper.selectByIdForUpdate(row.getAccountId());
        if (!validTarget(row, account)) {
            row.setState(STATE_BLOCKED);
            row.setUpdatedAt(now);
            resetRequestMapper.updateById(row);
            throw verificationFailed();
        }
        if (!PasswordPolicy.isValid(request.getNewPassword())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "new password must be 12-64 characters and include uppercase, lowercase, number and special character");
        }
        passwordHistoryService.assertNotRecentlyUsed(account, request.getNewPassword());
        passwordHistoryService.recordCurrentPassword(account, now);
        String salt = PasswordHashUtils.generateSalt();
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(request.getNewPassword(), salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setPasswordExpired(AuthConstants.DISABLED);
        account.setPasswordUpdatedAt(now);
        account.setFailedLoginCount(0);
        account.setUpdatedAt(now);
        account.setUpdatedBy(account.getId());
        accountMapper.updateById(account);
        sessionMapper.logoutAllActive(row.getAppId(), account.getId(), now);
        row.setState(STATE_COMPLETED);
        row.setCompletedAt(now);
        row.setResetTokenHash(null);
        row.setResetTokenExpireAt(null);
        row.setUpdatedAt(now);
        resetRequestMapper.updateById(row);
        sendSuccessEmailAfterCommit(account, row, now);
        PasswordResetConfirmResponse response = new PasswordResetConfirmResponse();
        response.setMessage("Password reset completed. Sign in again with the new password.");
        return response;
    }

    private PasswordResetRequestResponse createResetRequest(SysAppDO app,
                                                            String merchantId,
                                                            SysAccountDO eligibleAccount,
                                                            String accountFingerprint,
                                                            String ip,
                                                            String userAgent,
                                                            String locale,
                                                            LocalDateTime now,
                                                            boolean requireEmailPrepared) {
        String emailCode = generateEmailCode();
        String emailCodeSalt = PasswordHashUtils.generateSalt();
        SysPasswordResetRequestDO row = new SysPasswordResetRequestDO();
        row.setRequestNo(LoginTokenUtils.generateToken());
        row.setAppId(app.getId());
        row.setMerchantId(merchantId);
        row.setAccountId(eligibleAccount == null ? null : eligibleAccount.getId());
        row.setAccountFingerprint(accountFingerprint);
        row.setEmailCodeSalt(emailCodeSalt);
        row.setEmailCodeHash(PasswordHashUtils.hashPassword(emailCode, emailCodeSalt));
        row.setEmailExpireAt(now.plusSeconds(EMAIL_CODE_TTL_SECONDS));
        row.setEmailVerifyCount(0);
        row.setState(STATE_PENDING_EMAIL);
        row.setRequestIp(ip);
        row.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
        row.setLocale(locale);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        resetRequestMapper.insert(row);
        if (requireEmailPrepared) {
            sendVerificationEmail(eligibleAccount, row, emailCode);
        } else {
            // 匿名请求无论账号是否存在都执行完整邮件准备链路，避免响应耗时泄露账号状态。
            sendVerificationEmailAfterCommit(eligibleAccount, row, emailCode);
        }
        PasswordResetRequestResponse response = new PasswordResetRequestResponse();
        response.setRequestId(row.getRequestNo());
        response.setExpireSeconds(EMAIL_CODE_TTL_SECONDS);
        response.setMessage(GENERIC_REQUEST_MESSAGE);
        return response;
    }

    private SysAccountDO findEligibleAccount(Long appId, String merchantId, String loginAccount) {
        SysMerchantUserDO merchantUser = merchantUserMapper.selectOne(Wrappers.<SysMerchantUserDO>lambdaQuery()
                .eq(SysMerchantUserDO::getMerchantId, merchantId)
                .eq(SysMerchantUserDO::getLoginAccount, loginAccount)
                .eq(SysMerchantUserDO::getStatus, AuthConstants.ENABLED)
                .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        Long accountId = merchantUser == null || merchantUser.getAccountId() == null
                ? VIRTUAL_ACCOUNT_ID : merchantUser.getAccountId();
        SysAccountDO account = accountMapper.selectById(accountId);
        return eligible(account)
                && Objects.equals(account.getAppId(), appId)
                && Objects.equals(account.getMerchantId(), merchantId)
                ? account : null;
    }

    private boolean eligible(SysAccountDO account) {
        return account != null
                && Objects.equals(account.getStatus(), AuthConstants.ENABLED)
                && !Objects.equals(account.getLocked(), AuthConstants.ENABLED)
                && Objects.equals(account.getDeleted(), AuthConstants.NOT_DELETED)
                && AuthConstants.ACTIVATION_STATUS_ACTIVE.equals(account.getActivationStatus())
                && StringUtils.hasText(account.getEmail())
                && account.getEmailVerifiedAt() != null;
    }

    private SysAppDO requireMerchantApp() {
        SysAppDO app = appMapper.selectOne(Wrappers.<SysAppDO>lambdaQuery()
                .eq(SysAppDO::getAppCode, AuthConstants.APP_MERCHANT)
                .eq(SysAppDO::getStatus, AuthConstants.ENABLED)
                .eq(SysAppDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (app == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "merchant application is unavailable");
        }
        return app;
    }

    private void enforceRequestRateLimit(Long appId,
                                         String merchantId,
                                         String accountFingerprint,
                                         String ip,
                                         LocalDateTime now) {
        String accountIdentity = appId + ":" + merchantId + ":" + accountFingerprint;
        enforceDistributedRateLimit(RATE_SCOPE_COOLDOWN, accountIdentity, 1,
                Duration.ofSeconds(RESEND_COOLDOWN_SECONDS));
        enforceDistributedRateLimit(RATE_SCOPE_ACCOUNT, accountIdentity, ACCOUNT_REQUESTS_PER_HOUR,
                Duration.ofHours(1));
        enforceDistributedRateLimit(RATE_SCOPE_IP, appId + ":" + ip, IP_REQUESTS_PER_HOUR,
                Duration.ofHours(1));
        Long cooldownCount = resetRequestMapper.selectCount(Wrappers.<SysPasswordResetRequestDO>lambdaQuery()
                .eq(SysPasswordResetRequestDO::getAppId, appId)
                .eq(SysPasswordResetRequestDO::getMerchantId, merchantId)
                .eq(SysPasswordResetRequestDO::getAccountFingerprint, accountFingerprint)
                .ge(SysPasswordResetRequestDO::getCreatedAt, now.minusSeconds(RESEND_COOLDOWN_SECONDS)));
        Long accountCount = resetRequestMapper.selectCount(Wrappers.<SysPasswordResetRequestDO>lambdaQuery()
                .eq(SysPasswordResetRequestDO::getAppId, appId)
                .eq(SysPasswordResetRequestDO::getMerchantId, merchantId)
                .eq(SysPasswordResetRequestDO::getAccountFingerprint, accountFingerprint)
                .ge(SysPasswordResetRequestDO::getCreatedAt, now.minusHours(1)));
        Long ipCount = resetRequestMapper.selectCount(Wrappers.<SysPasswordResetRequestDO>lambdaQuery()
                .eq(SysPasswordResetRequestDO::getAppId, appId)
                .eq(SysPasswordResetRequestDO::getRequestIp, ip)
                .ge(SysPasswordResetRequestDO::getCreatedAt, now.minusHours(1)));
        if (positive(cooldownCount)
                || value(accountCount) >= ACCOUNT_REQUESTS_PER_HOUR
                || value(ipCount) >= IP_REQUESTS_PER_HOUR) {
            throw new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS.getCode(),
                    "Too many requests. Please retry later.");
        }
    }

    private void enforceDistributedRateLimit(String scope, String identity, int limit, Duration window) {
        SlidingWindowRateLimitResult result = rateLimiter.tryAcquire(scope, identity, limit, window);
        if (!result.allowed()) {
            throw new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS.getCode(),
                    "Too many requests. Please retry later.");
        }
    }

    private void enforceStageIpRateLimit(String scope, String clientIp) {
        enforceDistributedRateLimit(scope, normalizeIp(clientIp), IP_REQUESTS_PER_HOUR, Duration.ofHours(1));
    }

    private void sendVerificationEmailAfterCommit(SysAccountDO account,
                                                  SysPasswordResetRequestDO row,
                                                  String emailCode) {
        afterCommit(() -> {
            try {
                sendVerificationEmail(account, row, emailCode);
            } catch (RuntimeException exception) {
                log.warn("merchant password reset email preparation failed, accountId: {}, exceptionType: {}",
                        account == null ? null : account.getId(), exception.getClass().getSimpleName());
            }
        });
    }

    private void sendVerificationEmail(SysAccountDO account,
                                       SysPasswordResetRequestDO row,
                                       String emailCode) {
        boolean suppressDelivery = account == null;
        MerchantEmailSendCommand command = new MerchantEmailSendCommand(
                AuthConstants.APP_MERCHANT,
                row.getMerchantId(),
                row.getMerchantId(),
                row.getMerchantId(),
                "MERCHANT_PASSWORD_RESET_VERIFY",
                "PASSWORD_RESET_VERIFY",
                normalizeLocale(row.getLocale()),
                suppressDelivery ? List.of() : List.of(account.getEmail()),
                verificationEmailVariables(account, row, emailCode),
                "PASSWORD_RESET",
                row.getRequestNo()
        );
        MerchantEmailSendResult result = suppressDelivery
                ? emailService.prepareSuppressedByTemplate(command)
                : emailService.sendByTemplate(command);
        if (suppressDelivery) {
            return;
        }
        if (result == null
                || Objects.equals(result.sendStatus(), EmailDeliveryStatus.CLOSED.getCode())
                || Objects.equals(result.sendStatus(), EmailDeliveryStatus.CANCELLED.getCode())) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "password reset email could not be prepared");
        }
    }

    private void sendSuccessEmailAfterCommit(SysAccountDO account,
                                             SysPasswordResetRequestDO row,
                                             LocalDateTime now) {
        afterCommit(() -> {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("systemName", "Vexra Merchant");
            variables.put("userName", displayLoginAccount(account));
            variables.put("merchantId", row.getMerchantId());
            variables.put("operationTime", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(now));
            variables.put("securityNotice", "en-US".equals(normalizeLocale(row.getLocale()))
                    ? "All previous sessions were signed out. Contact your merchant administrator if this was not you."
                    : "所有旧会话均已退出。如非本人操作，请立即联系商户管理员。");
            try {
                emailService.sendByTemplate(new MerchantEmailSendCommand(
                        AuthConstants.APP_MERCHANT, row.getMerchantId(), row.getMerchantId(), row.getMerchantId(),
                        "MERCHANT_PASSWORD_RESET_SUCCESS", "PASSWORD_RESET_SUCCESS",
                        normalizeLocale(row.getLocale()), List.of(account.getEmail()), variables,
                        "PASSWORD_RESET", row.getRequestNo()));
            } catch (RuntimeException exception) {
                log.warn("merchant password reset success email preparation failed, accountId: {}, exceptionType: {}",
                        account.getId(), exception.getClass().getSimpleName());
            }
        });
    }

    private SysPasswordResetRequestDO requireLockedRequest(String requestId) {
        SysPasswordResetRequestDO row = resetRequestMapper.selectByRequestNoForUpdate(requestId);
        if (row == null) {
            PasswordHashUtils.hashPassword("Aa2Bb3", PasswordHashUtils.generateSalt());
            throw verificationFailed();
        }
        return row;
    }

    private PasswordResetVerifyResponse prepareResetToken(SysPasswordResetRequestDO row, LocalDateTime now) {
        String resetToken = LoginTokenUtils.generateToken();
        row.setResetTokenHash(LoginTokenUtils.hashToken(resetToken));
        row.setResetTokenExpireAt(now.plusSeconds(ACTION_TOKEN_TTL_SECONDS));
        row.setStageTokenHash(null);
        row.setStageTokenExpireAt(null);
        row.setState(STATE_READY);
        row.setUpdatedAt(now);
        resetRequestMapper.updateById(row);
        return verifyResponse(NEXT_RESET_PASSWORD, resetToken, ACTION_TOKEN_TTL_SECONDS,
                "Identity verified. Set a new password within five minutes.");
    }

    private PasswordResetVerifyResponse verifyResponse(String nextStep, String token,
                                                       int expireSeconds, String message) {
        PasswordResetVerifyResponse response = new PasswordResetVerifyResponse();
        response.setNextStep(nextStep);
        response.setActionToken(token);
        response.setExpireSeconds(expireSeconds);
        response.setMessage(message);
        return response;
    }

    private SysAccountMfaDO loadMfa(Long appId, Long accountId) {
        return mfaMapper.selectOne(Wrappers.<SysAccountMfaDO>lambdaQuery()
                .eq(SysAccountMfaDO::getAppId, appId)
                .eq(SysAccountMfaDO::getAccountId, accountId)
                .eq(SysAccountMfaDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
    }

    private boolean validTarget(SysPasswordResetRequestDO row, SysAccountDO account) {
        return eligible(account)
                && Objects.equals(account.getAppId(), row.getAppId())
                && Objects.equals(account.getMerchantId(), row.getMerchantId());
    }

    private void markExpiredIfNecessary(SysPasswordResetRequestDO row, LocalDateTime now) {
        if (!STATE_COMPLETED.equals(row.getState())
                && !STATE_BLOCKED.equals(row.getState())
                && !STATE_LOCKED.equals(row.getState())) {
            row.setState(STATE_EXPIRED);
            row.setUpdatedAt(now);
            resetRequestMapper.updateById(row);
        }
    }

    private Map<String, Object> verificationEmailVariables(SysAccountDO account,
                                                           SysPasswordResetRequestDO row,
                                                           String emailCode) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("systemName", "Vexra Merchant");
        variables.put("userName", account == null
                ? ("en-US".equals(normalizeLocale(row.getLocale())) ? "Merchant user" : "商户用户")
                : displayLoginAccount(account));
        variables.put("merchantId", row.getMerchantId());
        variables.put("verifyCode", emailCode);
        variables.put("expireMinutes", EMAIL_CODE_TTL_SECONDS / 60);
        variables.put("verificationUrl", passwordResetUrl(row.getRequestNo()));
        variables.put("requestTime", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(row.getCreatedAt()));
        variables.put("securityNotice", "en-US".equals(normalizeLocale(row.getLocale()))
                ? "If you did not request this, ignore this email and contact your merchant administrator."
                : "如非本人操作，请忽略本邮件并联系商户管理员。");
        return variables;
    }

    private String passwordResetUrl(String requestNo) {
        String baseUrl = configService.enabledConfigValue(FRONTEND_BASE_URL_KEY)
                .filter(StringUtils::hasText)
                .orElse("http://127.0.0.1:5174");
        return UriComponentsBuilder.fromUriString(baseUrl.replaceAll("/+$", ""))
                .path("/forgot-password")
                .queryParam("requestId", requestNo)
                .build()
                .encode()
                .toUriString();
    }

    private void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private String generateEmailCode() {
        char[] code = new char[EMAIL_CODE_LENGTH];
        code[0] = randomCharacter(EMAIL_CODE_DIGITS);
        code[1] = randomCharacter(EMAIL_CODE_LOWERCASE);
        code[2] = randomCharacter(EMAIL_CODE_UPPERCASE);
        for (int index = 3; index < code.length; index++) {
            code[index] = randomCharacter(EMAIL_CODE_CHARACTERS);
        }
        for (int index = code.length - 1; index > 0; index--) {
            int swapIndex = SECURE_RANDOM.nextInt(index + 1);
            char current = code[index];
            code[index] = code[swapIndex];
            code[swapIndex] = current;
        }
        return new String(code);
    }

    private char randomCharacter(String candidates) {
        return candidates.charAt(SECURE_RANDOM.nextInt(candidates.length()));
    }

    private boolean tokenMatches(String rawToken, String expectedHash) {
        if (!StringUtils.hasText(rawToken) || !StringUtils.hasText(expectedHash)) {
            return false;
        }
        return MessageDigest.isEqual(
                LoginTokenUtils.hashToken(rawToken).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    private boolean expired(LocalDateTime expireAt, LocalDateTime now) {
        return expireAt == null || !expireAt.isAfter(now);
    }

    private PasswordResetVerificationException verificationFailed() {
        return new PasswordResetVerificationException();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "account fingerprint can not be calculated");
        }
    }

    private String displayLoginAccount(SysAccountDO account) {
        String suffix = "_" + account.getMerchantId();
        return StringUtils.hasText(account.getLoginAccount()) && account.getLoginAccount().endsWith(suffix)
                ? account.getLoginAccount().substring(0, account.getLoginAccount().length() - suffix.length())
                : account.getLoginAccount();
    }

    private String normalizeMerchantId(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeLoginAccount(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeLocale(String value) {
        return "en-US".equals(value) ? "en-US" : "zh-CN";
    }

    private String normalizeIp(String value) {
        return StringUtils.hasText(value) ? truncate(value, 64) : "unknown";
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private long value(Long count) {
        return count == null ? 0L : count;
    }

    private boolean positive(Long count) {
        return value(count) > 0L;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    /** 验证失败需要提交失败计数或终态，再向匿名调用方返回统一错误。 */
    private static final class PasswordResetVerificationException extends ServiceException {
        private static final long serialVersionUID = 1L;

        private PasswordResetVerificationException() {
            super(ApiResultEnum.UNAUTHORIZED.getCode(), "Verification failed or expired.");
        }
    }
}
