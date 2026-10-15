package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.constant.SystemConfigKeys;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.AccountActivationCompleteRequest;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.AccountActivationCompleteResponse;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.EmailVerificationVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.EmailVerificationVerifyResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.entity.SysEmailVerificationRequestDO;
import com.scott.payment.admin.mapper.SysEmailVerificationRequestMapper;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.admin.service.AdminEmailVerificationService;
import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.auth.PasswordPolicy;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.auth.service.AccountPasswordHistoryService;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailVerificationServiceImpl
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 管理账号邮箱验证服务实现；使用哈希验证码、短期一次性令牌、行锁状态机和双层限流完成账号激活及邮箱变更验证。
 * @status : create
 */
@Slf4j
@Service
public class AdminEmailVerificationServiceImpl implements AdminEmailVerificationService {

    private static final String STATE_PENDING = "PENDING";
    private static final String STATE_VERIFIED = "VERIFIED";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_EXPIRED = "EXPIRED";
    private static final String STATE_LOCKED = "LOCKED";
    private static final String STATE_SUPERSEDED = "SUPERSEDED";
    private static final String STATE_BLOCKED = "BLOCKED";
    private static final String NEXT_SET_PASSWORD = "SET_PASSWORD";
    private static final String NEXT_COMPLETED = "COMPLETED";
    /** 邮件验证码固定在 30 分钟后过期。 */
    private static final int EMAIL_CODE_TTL_SECONDS = 30 * 60;
    /** 邮箱验证通过后的首次设密令牌仅保留五分钟。 */
    private static final int ACTION_TOKEN_TTL_SECONDS = 5 * 60;
    /** 开发阶段允许充分手工验收，同时仍以高熵验证码和限流阻断自动爆破。 */
    private static final int MAX_VERIFY_ATTEMPTS = 50;
    private static final int ACCOUNT_REQUESTS_PER_HOUR = 50;
    private static final int IP_REQUESTS_PER_HOUR = 200;
    private static final int RESEND_COOLDOWN_SECONDS = 6;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final String RATE_SCOPE_COOLDOWN = "adminEmailVerificationCooldown";
    private static final String RATE_SCOPE_ACCOUNT = "adminEmailVerificationAccount";
    private static final String RATE_SCOPE_IP = "adminEmailVerificationIp";
    private static final String RATE_SCOPE_VERIFY_IP = "adminEmailVerificationVerifyIp";
    private static final String TEMPLATE_ACTIVATION = "ADMIN_ACCOUNT_ACTIVATION";
    private static final String TEMPLATE_EMAIL_VERIFICATION = "ADMIN_EMAIL_VERIFICATION";
    private static final DateTimeFormatter EMAIL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CODE_DIGITS = "23456789";
    private static final String CODE_LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    private static final String CODE_UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String CODE_CHARACTERS = CODE_DIGITS + CODE_LOWERCASE + CODE_UPPERCASE;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SysAppMapper appMapper;
    private final SysAccountMapper accountMapper;
    private final SysUserMapper userMapper;
    private final SysLoginSessionMapper sessionMapper;
    private final SysEmailVerificationRequestMapper requestMapper;
    private final AdminEmailService emailService;
    private final AdminConfigService configService;
    private final RedisSlidingWindowRateLimiter rateLimiter;
    private final AccountPasswordHistoryService passwordHistoryService;

    /** 创建邮箱验证服务并注入账号、请求、邮件和限流能力。 */
    public AdminEmailVerificationServiceImpl(SysAppMapper appMapper,
                                             SysAccountMapper accountMapper,
                                             SysUserMapper userMapper,
                                             SysLoginSessionMapper sessionMapper,
                                             SysEmailVerificationRequestMapper requestMapper,
                                             AdminEmailService emailService,
                                             AdminConfigService configService,
                                             RedisSlidingWindowRateLimiter rateLimiter,
                                             AccountPasswordHistoryService passwordHistoryService) {
        this.appMapper = appMapper;
        this.accountMapper = accountMapper;
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.requestMapper = requestMapper;
        this.emailService = emailService;
        this.configService = configService;
        this.rateLimiter = rateLimiter;
        this.passwordHistoryService = passwordHistoryService;
    }

    /**
     * 创建最新邮箱验证请求。创建新请求前废止同账号未完成请求，确保旧邮件不能在邮箱变更后继续生效。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long accountId, String purpose, String locale, String requestIp, String userAgent,
                      boolean enforceRateLimit) {
        SysAppDO app = requireAdminApp();
        SysAccountDO account = requireAccount(app.getId(), accountId);
        validateIssuePurpose(account, purpose);
        String targetEmail = verificationTargetEmail(account, purpose);
        LocalDateTime now = LocalDateTime.now();
        String normalizedIp = normalizeIp(requestIp);
        if (enforceRateLimit) {
            enforceIssueRateLimit(app.getId(), account.getId(), normalizedIp, now);
        }

        requestMapper.update(null, Wrappers.<SysEmailVerificationRequestDO>lambdaUpdate()
                .eq(SysEmailVerificationRequestDO::getAppId, app.getId())
                .eq(SysEmailVerificationRequestDO::getAccountId, account.getId())
                .in(SysEmailVerificationRequestDO::getState, STATE_PENDING, STATE_VERIFIED)
                .set(SysEmailVerificationRequestDO::getState, STATE_SUPERSEDED)
                .set(SysEmailVerificationRequestDO::getUpdatedAt, now));

        String emailCode = generateEmailCode();
        String salt = PasswordHashUtils.generateSalt();
        SysEmailVerificationRequestDO row = new SysEmailVerificationRequestDO();
        row.setRequestNo(LoginTokenUtils.generateToken());
        row.setAppId(app.getId());
        row.setAccountId(account.getId());
        row.setPurpose(purpose);
        row.setEmailFingerprint(emailFingerprint(targetEmail));
        row.setCodeSalt(salt);
        row.setCodeHash(PasswordHashUtils.hashPassword(emailCode, salt));
        row.setExpireAt(now.plusSeconds(EMAIL_CODE_TTL_SECONDS));
        row.setVerifyCount(0);
        row.setState(STATE_PENDING);
        row.setRequestIp(normalizedIp);
        row.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
        row.setLocale(normalizeLocale(locale));
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        requestMapper.insert(row);
        sendVerificationEmailAfterCommit(account, row, emailCode, targetEmail);
    }

    /** 校验一次性邮件验证码；普通验证直接完成，新账号激活则签发短期首次设密令牌。 */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = EmailVerificationException.class)
    public EmailVerificationVerifyResponse verify(EmailVerificationVerifyRequest request, String clientIp) {
        enforceVerifyRateLimit(normalizeIp(clientIp));
        SysEmailVerificationRequestDO row = requireLockedRequest(request.getRequestId());
        LocalDateTime now = LocalDateTime.now();
        if (!STATE_PENDING.equals(row.getState()) || expired(row.getExpireAt(), now)) {
            markExpiredIfNecessary(row, now);
            throw verificationFailed();
        }
        if (defaultInt(row.getVerifyCount()) >= MAX_VERIFY_ATTEMPTS) {
            markLocked(row, now);
            throw verificationFailed();
        }
        row.setVerifyCount(defaultInt(row.getVerifyCount()) + 1);
        SysAccountDO account = accountMapper.selectByIdForUpdate(row.getAccountId());
        boolean matches = PasswordHashUtils.matches(request.getEmailCode(), row.getCodeSalt(), row.getCodeHash());
        if (!matches || !validTarget(row, account)) {
            if (row.getVerifyCount() >= MAX_VERIFY_ATTEMPTS) {
                row.setState(STATE_LOCKED);
            }
            row.setUpdatedAt(now);
            requestMapper.updateById(row);
            throw verificationFailed();
        }

        row.setVerifiedAt(now);
        if (PURPOSE_ACCOUNT_ACTIVATION.equals(row.getPurpose())) {
            if (!AuthConstants.ACTIVATION_STATUS_PENDING.equals(account.getActivationStatus())) {
                row.setState(STATE_BLOCKED);
                row.setUpdatedAt(now);
                requestMapper.updateById(row);
                throw verificationFailed();
            }
            String actionToken = LoginTokenUtils.generateToken();
            row.setActionTokenHash(LoginTokenUtils.hashToken(actionToken));
            row.setActionTokenExpireAt(now.plusSeconds(ACTION_TOKEN_TTL_SECONDS));
            row.setState(STATE_VERIFIED);
            row.setUpdatedAt(now);
            requestMapper.updateById(row);
            return verifyResponse(NEXT_SET_PASSWORD, actionToken, ACTION_TOKEN_TTL_SECONDS,
                    "Email verified. Set the initial password within five minutes.");
        }

        if (PURPOSE_EMAIL_CHANGE.equals(row.getPurpose())) {
            String newEmail = account.getPendingEmail();
            account.setEmail(newEmail);
            account.setPendingEmail(null);
            SysUserDO user = userMapper.selectById(account.getUserId());
            if (user != null) {
                user.setEmail(newEmail);
                user.setUpdatedAt(now);
                user.setUpdatedBy(account.getId());
                userMapper.updateById(user);
            }
        }
        account.setEmailVerifiedAt(now);
        account.setUpdatedAt(now);
        accountMapper.updateById(account);
        row.setState(STATE_COMPLETED);
        row.setCompletedAt(now);
        row.setUpdatedAt(now);
        requestMapper.updateById(row);
        return verifyResponse(NEXT_COMPLETED, null, 0, "Email verification completed.");
    }

    /** 原子消费首次设密令牌并激活账号，随后注销该账号可能存在的全部旧会话。 */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = EmailVerificationException.class)
    public AccountActivationCompleteResponse completeActivation(AccountActivationCompleteRequest request, String clientIp) {
        enforceVerifyRateLimit(normalizeIp(clientIp));
        SysEmailVerificationRequestDO row = requireLockedRequest(request.getRequestId());
        LocalDateTime now = LocalDateTime.now();
        if (!PURPOSE_ACCOUNT_ACTIVATION.equals(row.getPurpose())
                || !STATE_VERIFIED.equals(row.getState())
                || expired(row.getActionTokenExpireAt(), now)
                || !tokenMatches(request.getActionToken(), row.getActionTokenHash())) {
            markExpiredIfNecessary(row, now);
            throw verificationFailed();
        }
        SysAccountDO account = accountMapper.selectByIdForUpdate(row.getAccountId());
        if (!validTarget(row, account)
                || !AuthConstants.ACTIVATION_STATUS_PENDING.equals(account.getActivationStatus())) {
            row.setState(STATE_BLOCKED);
            row.setUpdatedAt(now);
            requestMapper.updateById(row);
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
        account.setEmailVerifiedAt(now);
        account.setActivationStatus(AuthConstants.ACTIVATION_STATUS_ACTIVE);
        account.setActivatedAt(now);
        account.setFailedLoginCount(0);
        account.setLocked(AuthConstants.DISABLED);
        account.setLockedAt(null);
        account.setLockedReason(null);
        account.setUpdatedAt(now);
        accountMapper.updateById(account);
        sessionMapper.logoutAllActive(account.getAppId(), account.getId(), now);

        row.setState(STATE_COMPLETED);
        row.setCompletedAt(now);
        row.setActionTokenHash(null);
        row.setActionTokenExpireAt(null);
        row.setUpdatedAt(now);
        requestMapper.updateById(row);
        AccountActivationCompleteResponse response = new AccountActivationCompleteResponse();
        response.setMessage("Account activated. Sign in and complete MFA setup.");
        return response;
    }

    private void validateIssuePurpose(SysAccountDO account, String purpose) {
        if (!Set.of(PURPOSE_ACCOUNT_ACTIVATION, PURPOSE_EMAIL_VERIFICATION, PURPOSE_EMAIL_CHANGE).contains(purpose)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "email verification purpose is invalid");
        }
        if (!StringUtils.hasText(verificationTargetEmail(account, purpose))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "account email is missing");
        }
        if (PURPOSE_ACCOUNT_ACTIVATION.equals(purpose)) {
            if (!AuthConstants.ACTIVATION_STATUS_PENDING.equals(account.getActivationStatus())) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "account is already activated");
            }
            return;
        }
        if (AuthConstants.ACTIVATION_STATUS_PENDING.equals(account.getActivationStatus())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "pending account must use activation email");
        }
        if (PURPOSE_EMAIL_CHANGE.equals(purpose)) {
            if (!StringUtils.hasText(account.getPendingEmail())
                    || Objects.equals(account.getEmail(), account.getPendingEmail())) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "pending email is missing");
            }
            return;
        }
        if (account.getEmailVerifiedAt() != null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "account email is already verified");
        }
    }

    private boolean validTarget(SysEmailVerificationRequestDO row, SysAccountDO account) {
        return account != null
                && Objects.equals(account.getAppId(), row.getAppId())
                && Objects.equals(account.getDeleted(), AuthConstants.NOT_DELETED)
                && StringUtils.hasText(verificationTargetEmail(account, row.getPurpose()))
                && Objects.equals(emailFingerprint(verificationTargetEmail(account, row.getPurpose())),
                row.getEmailFingerprint());
    }

    /** 邮箱变更验证指向待确认邮箱，其余流程使用当前正式邮箱。 */
    private String verificationTargetEmail(SysAccountDO account, String purpose) {
        return PURPOSE_EMAIL_CHANGE.equals(purpose) ? account.getPendingEmail() : account.getEmail();
    }

    private SysAppDO requireAdminApp() {
        SysAppDO app = appMapper.selectOne(Wrappers.<SysAppDO>lambdaQuery()
                .eq(SysAppDO::getAppCode, AuthConstants.APP_ADMIN)
                .eq(SysAppDO::getStatus, AuthConstants.ENABLED)
                .eq(SysAppDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (app == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "admin application is unavailable");
        }
        return app;
    }

    private SysAccountDO requireAccount(Long appId, Long accountId) {
        SysAccountDO account = accountId == null ? null : accountMapper.selectOne(
                Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getId, accountId)
                        .eq(SysAccountDO::getAppId, appId)
                        .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED)
                        .last("LIMIT 1"));
        if (account == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "admin account is unavailable");
        }
        return account;
    }

    private SysEmailVerificationRequestDO requireLockedRequest(String requestId) {
        SysEmailVerificationRequestDO row = requestMapper.selectByRequestNoForUpdate(requestId);
        if (row == null) {
            PasswordHashUtils.hashPassword("Aa2Bb3", PasswordHashUtils.generateSalt());
            throw verificationFailed();
        }
        return row;
    }

    private void enforceIssueRateLimit(Long appId, Long accountId, String ip, LocalDateTime now) {
        String accountIdentity = appId + ":" + accountId;
        enforceDistributedRateLimit(RATE_SCOPE_COOLDOWN, accountIdentity, 1,
                Duration.ofSeconds(RESEND_COOLDOWN_SECONDS));
        enforceDistributedRateLimit(RATE_SCOPE_ACCOUNT, accountIdentity, ACCOUNT_REQUESTS_PER_HOUR,
                Duration.ofHours(1));
        enforceDistributedRateLimit(RATE_SCOPE_IP, appId + ":" + ip, IP_REQUESTS_PER_HOUR,
                Duration.ofHours(1));
        Long cooldownCount = requestMapper.selectCount(Wrappers.<SysEmailVerificationRequestDO>lambdaQuery()
                .eq(SysEmailVerificationRequestDO::getAppId, appId)
                .eq(SysEmailVerificationRequestDO::getAccountId, accountId)
                .ge(SysEmailVerificationRequestDO::getCreatedAt, now.minusSeconds(RESEND_COOLDOWN_SECONDS)));
        Long accountCount = requestMapper.selectCount(Wrappers.<SysEmailVerificationRequestDO>lambdaQuery()
                .eq(SysEmailVerificationRequestDO::getAppId, appId)
                .eq(SysEmailVerificationRequestDO::getAccountId, accountId)
                .ge(SysEmailVerificationRequestDO::getCreatedAt, now.minusHours(1)));
        Long ipCount = requestMapper.selectCount(Wrappers.<SysEmailVerificationRequestDO>lambdaQuery()
                .eq(SysEmailVerificationRequestDO::getAppId, appId)
                .eq(SysEmailVerificationRequestDO::getRequestIp, ip)
                .ge(SysEmailVerificationRequestDO::getCreatedAt, now.minusHours(1)));
        if (positive(cooldownCount) || value(accountCount) >= ACCOUNT_REQUESTS_PER_HOUR
                || value(ipCount) >= IP_REQUESTS_PER_HOUR) {
            throw tooManyRequests();
        }
    }

    private void enforceVerifyRateLimit(String ip) {
        enforceDistributedRateLimit(RATE_SCOPE_VERIFY_IP, ip, IP_REQUESTS_PER_HOUR, Duration.ofHours(1));
    }

    private void enforceDistributedRateLimit(String scope, String identity, int limit, Duration window) {
        SlidingWindowRateLimitResult result = rateLimiter.tryAcquire(scope, identity, limit, window);
        if (!result.allowed()) {
            throw tooManyRequests();
        }
    }

    private ServiceException tooManyRequests() {
        return new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS.getCode(),
                "Too many requests. Please retry later.");
    }

    private void sendVerificationEmailAfterCommit(SysAccountDO account,
                                                  SysEmailVerificationRequestDO row,
                                                  String emailCode,
                                                  String targetEmail) {
        Runnable task = () -> sendVerificationEmail(account, row, emailCode, targetEmail);
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

    private void sendVerificationEmail(SysAccountDO account,
                                       SysEmailVerificationRequestDO row,
                                       String emailCode,
                                       String targetEmail) {
        boolean activation = PURPOSE_ACCOUNT_ACTIVATION.equals(row.getPurpose());
        EmailSendRequest email = new EmailSendRequest();
        email.setAppCode(AuthConstants.APP_ADMIN);
        email.setTemplateCode(activation ? TEMPLATE_ACTIVATION : TEMPLATE_EMAIL_VERIFICATION);
        email.setSceneCode(activation ? "ACCOUNT_ACTIVATION" : "EMAIL_VERIFICATION");
        email.setLocale(row.getLocale());
        email.setToEmails(List.of(targetEmail));
        email.setBizType(row.getPurpose());
        email.setBizNo(row.getRequestNo());
        SysUserDO user = userMapper.selectById(account.getUserId());
        Map<String, Object> variables = new HashMap<>();
        variables.put("systemName", "Vexra Admin");
        variables.put("userName", user != null && StringUtils.hasText(user.getRealName())
                ? user.getRealName() : account.getLoginAccount());
        variables.put("loginAccount", account.getLoginAccount());
        variables.put("verificationCode", emailCode);
        variables.put("expireMinutes", EMAIL_CODE_TTL_SECONDS / 60);
        variables.put("verificationUrl", verificationUrl(row.getRequestNo()));
        variables.put("requestTime", EMAIL_TIME_FORMATTER.format(row.getCreatedAt()));
        variables.put("securityNotice", securityNotice(row.getLocale()));
        email.setVariables(variables);
        EmailSendResult result = emailService.sendByTemplate(email);
        if (result == null || Objects.equals(result.getSendStatus(), EmailDeliveryStatus.CLOSED.getCode())
                || Objects.equals(result.getSendStatus(), EmailDeliveryStatus.CANCELLED.getCode())) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "email verification message could not be prepared");
        }
    }

    private String verificationUrl(String requestNo) {
        Map<String, String> values = configService.enabledConfigValues(Set.of(SystemConfigKeys.ADMIN_FRONTEND_BASE_URL));
        String baseUrl = values.get(SystemConfigKeys.ADMIN_FRONTEND_BASE_URL);
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "http://127.0.0.1:5173";
        }
        return UriComponentsBuilder.fromUriString(baseUrl.replaceAll("/+$", ""))
                .path("/account-verification")
                .queryParam("requestId", requestNo)
                .build()
                .encode()
                .toUriString();
    }

    private String securityNotice(String locale) {
        return "en-US".equals(normalizeLocale(locale))
                ? "If you did not request this change, ignore this email and contact another administrator."
                : "如非本人或授权管理员发起，请忽略本邮件并联系其他管理员。";
    }

    private EmailVerificationVerifyResponse verifyResponse(String nextStep, String actionToken,
                                                           int expireSeconds, String message) {
        EmailVerificationVerifyResponse response = new EmailVerificationVerifyResponse();
        response.setNextStep(nextStep);
        response.setActionToken(actionToken);
        response.setExpireSeconds(expireSeconds);
        response.setMessage(message);
        return response;
    }

    private String generateEmailCode() {
        char[] code = new char[6];
        code[0] = randomCharacter(CODE_DIGITS);
        code[1] = randomCharacter(CODE_LOWERCASE);
        code[2] = randomCharacter(CODE_UPPERCASE);
        for (int index = 3; index < code.length; index++) {
            code[index] = randomCharacter(CODE_CHARACTERS);
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

    private String emailFingerprint(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "email fingerprint can not be calculated");
        }
    }

    private boolean tokenMatches(String token, String expectedHash) {
        return StringUtils.hasText(token) && StringUtils.hasText(expectedHash)
                && MessageDigest.isEqual(LoginTokenUtils.hashToken(token).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private void markExpiredIfNecessary(SysEmailVerificationRequestDO row, LocalDateTime now) {
        if (STATE_PENDING.equals(row.getState()) || STATE_VERIFIED.equals(row.getState())) {
            row.setState(STATE_EXPIRED);
            row.setUpdatedAt(now);
            requestMapper.updateById(row);
        }
    }

    private void markLocked(SysEmailVerificationRequestDO row, LocalDateTime now) {
        row.setState(STATE_LOCKED);
        row.setUpdatedAt(now);
        requestMapper.updateById(row);
    }

    private boolean expired(LocalDateTime expireAt, LocalDateTime now) {
        return expireAt == null || !expireAt.isAfter(now);
    }

    private String normalizeLocale(String locale) {
        return "en-US".equals(locale) ? "en-US" : "zh-CN";
    }

    private String normalizeIp(String ip) {
        return StringUtils.hasText(ip) ? truncate(ip, 64) : "unknown";
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long value(Long count) {
        return count == null ? 0L : count;
    }

    private boolean positive(Long count) {
        return value(count) > 0L;
    }

    private EmailVerificationException verificationFailed() {
        return new EmailVerificationException();
    }

    /** 验证失败必须提交失败次数或终态，同时向匿名调用方返回统一提示。 */
    private static final class EmailVerificationException extends ServiceException {
        private static final long serialVersionUID = 1L;

        private EmailVerificationException() {
            super(ApiResultEnum.UNAUTHORIZED.getCode(), "Verification failed or expired.");
        }
    }
}
