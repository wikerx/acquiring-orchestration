package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.auth.PasswordPolicy;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
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
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.AccountActivationCompleteRequest;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.AccountActivationCompleteResponse;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.EmailVerificationVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.EmailVerificationVerifyResponse;
import com.scott.payment.merchant.service.MerchantConfigService;
import com.scott.payment.merchant.service.MerchantEmailVerificationService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService.MerchantEmailSendCommand;
import com.scott.payment.merchant.service.MerchantTemplateEmailService.MerchantEmailSendResult;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantEmailVerificationServiceImpl
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 商户账号激活和邮箱验证状态机；以租户绑定、行锁、哈希验证码和单次短期令牌阻止越权与重放。
 * @status : create
 */
@Service
public class MerchantEmailVerificationServiceImpl implements MerchantEmailVerificationService {

    private static final String STATE_PENDING = "PENDING";
    private static final String STATE_VERIFIED = "VERIFIED";
    private static final String STATE_COMPLETED = "COMPLETED";
    private static final String STATE_EXPIRED = "EXPIRED";
    private static final String STATE_LOCKED = "LOCKED";
    private static final String STATE_SUPERSEDED = "SUPERSEDED";
    private static final String STATE_BLOCKED = "BLOCKED";
    private static final String NEXT_SET_PASSWORD = "SET_PASSWORD";
    private static final String NEXT_COMPLETED = "COMPLETED";
    private static final int EMAIL_CODE_TTL_SECONDS = 30 * 60;
    private static final int ACTION_TOKEN_TTL_SECONDS = 5 * 60;
    private static final int MAX_VERIFY_ATTEMPTS = 50;
    private static final int ACCOUNT_REQUESTS_PER_HOUR = 50;
    private static final int IP_REQUESTS_PER_HOUR = 200;
    private static final int RESEND_COOLDOWN_SECONDS = 6;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final String RATE_SCOPE_COOLDOWN = "merchantEmailVerificationCooldown";
    private static final String RATE_SCOPE_ACCOUNT = "merchantEmailVerificationAccount";
    private static final String RATE_SCOPE_IP = "merchantEmailVerificationIp";
    private static final String RATE_SCOPE_VERIFY_IP = "merchantEmailVerificationVerifyIp";
    private static final String TEMPLATE_ACTIVATION = "MERCHANT_ACCOUNT_ACTIVATION";
    private static final String TEMPLATE_EMAIL_VERIFICATION = "MERCHANT_EMAIL_VERIFICATION";
    private static final String FRONTEND_BASE_URL_KEY = "platform.merchant.frontend-base-url";
    private static final String CODE_DIGITS = "23456789";
    private static final String CODE_LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    private static final String CODE_UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String CODE_CHARACTERS = CODE_DIGITS + CODE_LOWERCASE + CODE_UPPERCASE;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter EMAIL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysAppMapper appMapper;
    private final SysAccountMapper accountMapper;
    private final SysUserMapper userMapper;
    private final SysLoginSessionMapper sessionMapper;
    private final SysEmailVerificationRequestMapper requestMapper;
    private final MerchantTemplateEmailService emailService;
    private final MerchantConfigService configService;
    private final RedisSlidingWindowRateLimiter rateLimiter;
    private final AccountPasswordHistoryService passwordHistoryService;

    /** 创建商户邮箱验证服务。 */
    public MerchantEmailVerificationServiceImpl(SysAppMapper appMapper,
                                                SysAccountMapper accountMapper,
                                                SysUserMapper userMapper,
                                                SysLoginSessionMapper sessionMapper,
                                                SysEmailVerificationRequestMapper requestMapper,
                                                MerchantTemplateEmailService emailService,
                                                MerchantConfigService configService,
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

    /** 创建最新邮箱验证请求，并废止同账号尚未完成的旧请求。 */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long accountId, String purpose, String locale, String requestIp, String userAgent,
                      boolean enforceRateLimit) {
        SysAppDO app = requireMerchantApp();
        SysAccountDO account = requireAccount(app.getId(), accountId);
        validateIssuePurpose(account, purpose);
        String targetEmail = verificationTargetEmail(account, purpose);
        LocalDateTime now = LocalDateTime.now();
        String normalizedIp = normalizeIp(requestIp);
        if (enforceRateLimit) {
            enforceIssueRateLimit(app.getId(), account.getMerchantId(), account.getId(), normalizedIp, now);
        }
        requestMapper.update(null, Wrappers.<SysEmailVerificationRequestDO>lambdaUpdate()
                .eq(SysEmailVerificationRequestDO::getAppId, app.getId())
                .eq(SysEmailVerificationRequestDO::getMerchantId, account.getMerchantId())
                .eq(SysEmailVerificationRequestDO::getAccountId, account.getId())
                .in(SysEmailVerificationRequestDO::getState, STATE_PENDING, STATE_VERIFIED)
                .set(SysEmailVerificationRequestDO::getState, STATE_SUPERSEDED)
                .set(SysEmailVerificationRequestDO::getUpdatedAt, now));
        String code = generateEmailCode();
        String salt = PasswordHashUtils.generateSalt();
        SysEmailVerificationRequestDO row = new SysEmailVerificationRequestDO();
        row.setRequestNo(LoginTokenUtils.generateToken());
        row.setAppId(app.getId());
        row.setMerchantId(account.getMerchantId());
        row.setAccountId(account.getId());
        row.setPurpose(purpose);
        row.setEmailFingerprint(emailFingerprint(targetEmail));
        row.setCodeSalt(salt);
        row.setCodeHash(PasswordHashUtils.hashPassword(code, salt));
        row.setExpireAt(now.plusSeconds(EMAIL_CODE_TTL_SECONDS));
        row.setVerifyCount(0);
        row.setState(STATE_PENDING);
        row.setRequestIp(normalizedIp);
        row.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
        row.setLocale(normalizeLocale(locale));
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        requestMapper.insert(row);
        sendVerificationEmailAfterCommit(account, row, code, targetEmail);
    }

    /** 校验验证码；激活用途签发五分钟首次设密令牌，其余用途直接完成邮箱确认。 */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = EmailVerificationException.class)
    public EmailVerificationVerifyResponse verify(EmailVerificationVerifyRequest request, String clientIp) {
        enforceDistributedRateLimit(RATE_SCOPE_VERIFY_IP, normalizeIp(clientIp),
                IP_REQUESTS_PER_HOUR, Duration.ofHours(1));
        SysEmailVerificationRequestDO row = requireLockedRequest(request.getRequestId());
        LocalDateTime now = LocalDateTime.now();
        if (!STATE_PENDING.equals(row.getState())) {
            throw verificationFailed();
        }
        if (expired(row.getExpireAt(), now)) {
            markExpiredIfNecessary(row, now);
            throw verificationFailed();
        }
        if (defaultInt(row.getVerifyCount()) >= MAX_VERIFY_ATTEMPTS) {
            row.setState(STATE_LOCKED);
            row.setUpdatedAt(now);
            requestMapper.updateById(row);
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
            return response(NEXT_SET_PASSWORD, actionToken, ACTION_TOKEN_TTL_SECONDS,
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
        return response(NEXT_COMPLETED, null, 0, "Email verification completed.");
    }

    /** 原子消费短期令牌，写入首次密码并激活账号。 */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = EmailVerificationException.class)
    public AccountActivationCompleteResponse completeActivation(AccountActivationCompleteRequest request, String clientIp) {
        enforceDistributedRateLimit(RATE_SCOPE_VERIFY_IP, normalizeIp(clientIp),
                IP_REQUESTS_PER_HOUR, Duration.ofHours(1));
        SysEmailVerificationRequestDO row = requireLockedRequest(request.getRequestId());
        LocalDateTime now = LocalDateTime.now();
        if (!PURPOSE_ACCOUNT_ACTIVATION.equals(row.getPurpose())
                || !STATE_VERIFIED.equals(row.getState())) {
            throw verificationFailed();
        }
        if (expired(row.getActionTokenExpireAt(), now)) {
            markExpiredIfNecessary(row, now);
            throw verificationFailed();
        }
        if (!tokenMatches(request.getActionToken(), row.getActionTokenHash())) {
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
            if (!StringUtils.hasText(account.getPendingEmail()) || Objects.equals(account.getEmail(), account.getPendingEmail())) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "pending email is missing");
            }
        } else if (account.getEmailVerifiedAt() != null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "account email is already verified");
        }
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

    private SysAccountDO requireAccount(Long appId, Long accountId) {
        SysAccountDO account = accountId == null ? null : accountMapper.selectOne(Wrappers.<SysAccountDO>lambdaQuery()
                .eq(SysAccountDO::getId, accountId)
                .eq(SysAccountDO::getAppId, appId)
                .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "merchant account is unavailable");
        }
        return account;
    }

    private boolean validTarget(SysEmailVerificationRequestDO row, SysAccountDO account) {
        return account != null
                && Objects.equals(account.getAppId(), row.getAppId())
                && Objects.equals(account.getMerchantId(), row.getMerchantId())
                && Objects.equals(account.getDeleted(), AuthConstants.NOT_DELETED)
                && StringUtils.hasText(verificationTargetEmail(account, row.getPurpose()))
                && Objects.equals(emailFingerprint(verificationTargetEmail(account, row.getPurpose())), row.getEmailFingerprint());
    }

    private String verificationTargetEmail(SysAccountDO account, String purpose) {
        return PURPOSE_EMAIL_CHANGE.equals(purpose) ? account.getPendingEmail() : account.getEmail();
    }

    private SysEmailVerificationRequestDO requireLockedRequest(String requestId) {
        SysEmailVerificationRequestDO row = requestMapper.selectByRequestNoForUpdate(requestId);
        if (row == null) {
            PasswordHashUtils.hashPassword("Aa2Bb3", PasswordHashUtils.generateSalt());
            throw verificationFailed();
        }
        return row;
    }

    private void enforceIssueRateLimit(Long appId, String merchantId, Long accountId,
                                       String ip, LocalDateTime now) {
        String identity = appId + ":" + merchantId + ":" + accountId;
        enforceDistributedRateLimit(RATE_SCOPE_COOLDOWN, identity, 1, Duration.ofSeconds(RESEND_COOLDOWN_SECONDS));
        enforceDistributedRateLimit(RATE_SCOPE_ACCOUNT, identity, ACCOUNT_REQUESTS_PER_HOUR, Duration.ofHours(1));
        enforceDistributedRateLimit(RATE_SCOPE_IP, appId + ":" + ip, IP_REQUESTS_PER_HOUR, Duration.ofHours(1));
        Long cooldown = requestMapper.selectCount(Wrappers.<SysEmailVerificationRequestDO>lambdaQuery()
                .eq(SysEmailVerificationRequestDO::getAppId, appId)
                .eq(SysEmailVerificationRequestDO::getMerchantId, merchantId)
                .eq(SysEmailVerificationRequestDO::getAccountId, accountId)
                .ge(SysEmailVerificationRequestDO::getCreatedAt, now.minusSeconds(RESEND_COOLDOWN_SECONDS)));
        if (value(cooldown) > 0) {
            throw tooManyRequests();
        }
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
                                                  String code,
                                                  String targetEmail) {
        afterCommit(() -> sendVerificationEmail(account, row, code, targetEmail));
    }

    private void sendVerificationEmail(SysAccountDO account,
                                       SysEmailVerificationRequestDO row,
                                       String code,
                                       String targetEmail) {
        boolean activation = PURPOSE_ACCOUNT_ACTIVATION.equals(row.getPurpose());
        Map<String, Object> variables = new LinkedHashMap<>();
        SysUserDO user = userMapper.selectById(account.getUserId());
        variables.put("systemName", "Vexra Merchant");
        variables.put("userName", user != null && StringUtils.hasText(user.getRealName())
                ? user.getRealName() : displayLoginAccount(account));
        variables.put("loginAccount", displayLoginAccount(account));
        variables.put("merchantId", account.getMerchantId());
        variables.put("verificationCode", code);
        variables.put("expireMinutes", EMAIL_CODE_TTL_SECONDS / 60);
        variables.put("verificationUrl", verificationUrl(row.getRequestNo()));
        variables.put("requestTime", EMAIL_TIME_FORMATTER.format(row.getCreatedAt()));
        variables.put("securityNotice", "en-US".equals(row.getLocale())
                ? "If you did not request this change, ignore this email and contact your merchant administrator."
                : "如非本人或商户管理员发起，请忽略本邮件并联系商户管理员。");
        MerchantEmailSendResult result = emailService.sendByTemplate(new MerchantEmailSendCommand(
                AuthConstants.APP_MERCHANT, account.getMerchantId(), account.getMerchantId(), account.getMerchantId(),
                activation ? TEMPLATE_ACTIVATION : TEMPLATE_EMAIL_VERIFICATION,
                activation ? "ACCOUNT_ACTIVATION" : "EMAIL_VERIFICATION",
                row.getLocale(), List.of(targetEmail), variables, row.getPurpose(), row.getRequestNo()));
        if (result == null || Objects.equals(result.sendStatus(), EmailDeliveryStatus.CLOSED.getCode())
                || Objects.equals(result.sendStatus(), EmailDeliveryStatus.CANCELLED.getCode())) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "email verification message could not be prepared");
        }
    }

    private String verificationUrl(String requestNo) {
        String baseUrl = configService.enabledConfigValue(FRONTEND_BASE_URL_KEY)
                .filter(StringUtils::hasText).orElse("http://127.0.0.1:5174");
        return UriComponentsBuilder.fromUriString(baseUrl.replaceAll("/+$", ""))
                .path("/account-verification").queryParam("requestId", requestNo)
                .build().encode().toUriString();
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

    private EmailVerificationVerifyResponse response(String nextStep, String token, int ttl, String message) {
        EmailVerificationVerifyResponse response = new EmailVerificationVerifyResponse();
        response.setNextStep(nextStep);
        response.setActionToken(token);
        response.setExpireSeconds(ttl);
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
            char item = code[index];
            code[index] = code[swapIndex];
            code[swapIndex] = item;
        }
        return new String(code);
    }

    private char randomCharacter(String source) {
        return source.charAt(SECURE_RANDOM.nextInt(source.length()));
    }

    private String emailFingerprint(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                            .getBytes(StandardCharsets.UTF_8));
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

    private boolean tokenMatches(String token, String hash) {
        return StringUtils.hasText(token) && StringUtils.hasText(hash)
                && MessageDigest.isEqual(LoginTokenUtils.hashToken(token).getBytes(StandardCharsets.UTF_8),
                hash.getBytes(StandardCharsets.UTF_8));
    }

    private void markExpiredIfNecessary(SysEmailVerificationRequestDO row, LocalDateTime now) {
        if (STATE_PENDING.equals(row.getState()) || STATE_VERIFIED.equals(row.getState())) {
            row.setState(STATE_EXPIRED);
            row.setUpdatedAt(now);
            requestMapper.updateById(row);
        }
    }

    private boolean expired(LocalDateTime expireAt, LocalDateTime now) {
        return expireAt == null || !expireAt.isAfter(now);
    }

    private String displayLoginAccount(SysAccountDO account) {
        String suffix = "_" + account.getMerchantId();
        return StringUtils.hasText(account.getLoginAccount()) && account.getLoginAccount().endsWith(suffix)
                ? account.getLoginAccount().substring(0, account.getLoginAccount().length() - suffix.length())
                : account.getLoginAccount();
    }

    private String normalizeIp(String value) {
        return StringUtils.hasText(value) ? truncate(value.trim(), 64) : "unknown";
    }

    private String normalizeLocale(String value) {
        return "en-US".equals(value) ? "en-US" : "zh-CN";
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

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private EmailVerificationException verificationFailed() {
        return new EmailVerificationException();
    }

    /** 验证失败时保留计数或终态，同时统一对外错误。 */
    private static final class EmailVerificationException extends ServiceException {
        private static final long serialVersionUID = 1L;

        private EmailVerificationException() {
            super(ApiResultEnum.UNAUTHORIZED.getCode(), "Verification failed or expired.");
        }
    }
}
