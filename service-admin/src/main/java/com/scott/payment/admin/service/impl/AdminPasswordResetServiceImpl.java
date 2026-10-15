package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetConfirmRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetConfirmResponse;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetEmailVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetMfaVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequestResponse;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetVerifyResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.constant.SystemConfigKeys;
import com.scott.payment.admin.entity.SysPasswordResetRequestDO;
import com.scott.payment.admin.mapper.SysPasswordResetRequestMapper;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.admin.service.AdminPasswordResetService;
import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.auth.PasswordPolicy;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.db.auth.service.AccountPasswordHistoryService;
import com.scott.payment.component.db.auth.support.MfaSecretCrypto;
import com.scott.payment.component.db.auth.support.TotpUtils;
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
import java.time.Instant;
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
 * @classname : AdminPasswordResetServiceImpl
 * @date : 2026-10-14 00:00
 * @email : scott_x@163.com
 * @description : 管理后台找回密码服务实现；以虚拟请求、哈希验证码、一次性阶段令牌、MFA 防重放和事务内会话撤销收敛匿名改密风险。
 * @status : create
 */
@Slf4j
@Service
public class AdminPasswordResetServiceImpl implements AdminPasswordResetService {

    /** 重置申请已创建，等待校验邮件验证码的持久化状态。 */
    private static final String STATE_PENDING_EMAIL = "PENDING_EMAIL";
    /** 邮件验证码已通过，等待校验管理员 TOTP 的持久化状态。 */
    private static final String STATE_PENDING_MFA = "PENDING_MFA";
    /** 身份验证已完成，允许在短期令牌有效期内提交新密码的状态。 */
    private static final String STATE_READY = "READY";
    /** 密码已更新且旧会话已注销的终态。 */
    private static final String STATE_COMPLETED = "COMPLETED";
    /** 邮件验证码或操作令牌超过有效期后的终态。 */
    private static final String STATE_EXPIRED = "EXPIRED";
    /** 邮件验证码失败次数达到上限后的锁定终态。 */
    private static final String STATE_LOCKED = "LOCKED";
    /** 账号或 MFA 状态不允许继续自助找回时的阻断终态。 */
    private static final String STATE_BLOCKED = "BLOCKED";
    /** 邮件验证完成后仍需校验 MFA 的下一步标识。 */
    private static final String NEXT_MFA = "MFA";
    /** 身份验证完成后允许设置新密码的下一步标识。 */
    private static final String NEXT_RESET_PASSWORD = "RESET_PASSWORD";
    /** 无法自助恢复、需要其他管理员介入的下一步标识。 */
    private static final String NEXT_ADMIN_SUPPORT = "ADMIN_SUPPORT";
    /** 统一申请响应，避免通过返回内容探测账号是否存在或具备找回资格。 */
    private static final String GENERIC_REQUEST_MESSAGE = "If the account is eligible, a verification email has been sent.";
    /** 邮件验证码有效期，单位为秒，当前为 30 分钟。 */
    private static final int EMAIL_CODE_TTL_SECONDS = 30 * 60;
    /** MFA 阶段令牌和最终重置令牌有效期，单位为秒。 */
    private static final int ACTION_TOKEN_TTL_SECONDS = 300;
    /** 开发阶段允许充分手工验收；单次申请连续失败 50 次后仍会立即锁定。 */
    private static final int MAX_EMAIL_VERIFY_ATTEMPTS = 50;
    /** MFA 最大连续失败次数，达到上限后阻断本次流程并触发 MFA 锁定。 */
    private static final int MAX_MFA_VERIFY_ATTEMPTS = 5;
    /** 开发阶段同一应用、同一账号指纹每小时最多申请 50 次；虚拟申请同样计数。 */
    private static final int ACCOUNT_REQUESTS_PER_HOUR = 50;
    /** 开发阶段同一应用、同一客户端 IP 每小时最多申请 200 次。 */
    private static final int IP_REQUESTS_PER_HOUR = 200;
    /** 开发阶段同一账号连续申请邮件验证码的最短间隔为 6 秒。 */
    private static final int RESEND_COOLDOWN_SECONDS = 6;
    /** TOTP 动态验证码的标准时间步长，单位为秒。 */
    private static final int MFA_PERIOD_SECONDS = 30;
    /** TOTP 校验允许前后偏移的时间步数量，用于容忍有限时钟误差。 */
    private static final int MFA_WINDOW = 1;
    /** 审计记录允许保存的 User-Agent 最大字符数。 */
    private static final int MAX_USER_AGENT_LENGTH = 500;
    /** 邮件验证码长度，单位为字符。 */
    private static final int EMAIL_CODE_LENGTH = 6;
    /** 邮件验证码数字字符池；排除容易与字母混淆的 0 和 1。 */
    private static final String EMAIL_CODE_DIGITS = "23456789";
    /** 邮件验证码小写字母字符池；排除容易与数字 1 混淆的小写 l。 */
    private static final String EMAIL_CODE_LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    /** 邮件验证码大写字母字符池；排除容易与 0、1 混淆的 O 和 I。 */
    private static final String EMAIL_CODE_UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    /** 在保证至少包含数字、大小写字母后，其余随机位置使用的完整字符池。 */
    private static final String EMAIL_CODE_CHARACTERS = EMAIL_CODE_DIGITS
            + EMAIL_CODE_LOWERCASE + EMAIL_CODE_UPPERCASE;
    /** Redis 中同账号重发冷却窗口的限流作用域。 */
    private static final String RATE_SCOPE_COOLDOWN = "adminPasswordResetCooldown";
    /** Redis 中同账号小时申请次数的限流作用域。 */
    private static final String RATE_SCOPE_ACCOUNT = "adminPasswordResetAccount";
    /** Redis 中同客户端 IP 小时申请次数的限流作用域。 */
    private static final String RATE_SCOPE_IP = "adminPasswordResetIp";
    /** 邮件验证码验证阶段按 IP 单独限流，避免申请额度掩盖后续爆破。 */
    private static final String RATE_SCOPE_EMAIL_VERIFY_IP = "adminPasswordResetEmailVerifyIp";
    /** MFA 验证阶段按 IP 单独限流。 */
    private static final String RATE_SCOPE_MFA_VERIFY_IP = "adminPasswordResetMfaVerifyIp";
    /** 最终改密阶段按 IP 单独限流。 */
    private static final String RATE_SCOPE_CONFIRM_IP = "adminPasswordResetConfirmIp";
    /** 用于生成和打乱一次性邮件验证码的密码学安全随机数源。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SystemAuthService systemAuthService;
    private final SysAppMapper appMapper;
    private final SysAccountMapper accountMapper;
    private final SysAccountMfaMapper mfaMapper;
    private final SysLoginSessionMapper sessionMapper;
    private final SysPasswordResetRequestMapper resetRequestMapper;
    private final AdminEmailService emailService;
    private final RedisSlidingWindowRateLimiter rateLimiter;
    private final AccountPasswordHistoryService passwordHistoryService;
    private final AdminConfigService configService;

    /** 创建找回密码服务并注入认证、账号、MFA、会话、请求和邮件能力。 */
    public AdminPasswordResetServiceImpl(SystemAuthService systemAuthService,
                                         SysAppMapper appMapper,
                                         SysAccountMapper accountMapper,
                                         SysAccountMfaMapper mfaMapper,
                                         SysLoginSessionMapper sessionMapper,
                                         SysPasswordResetRequestMapper resetRequestMapper,
                                         AdminEmailService emailService,
                                         RedisSlidingWindowRateLimiter rateLimiter,
                                         AccountPasswordHistoryService passwordHistoryService,
                                         AdminConfigService configService) {
        this.systemAuthService = systemAuthService;
        this.appMapper = appMapper;
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
     * 创建找回密码申请。图形验证码先行消费；无效、停用、锁定或邮箱未验证账号均落虚拟请求并返回相同结构。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public PasswordResetRequestResponse requestReset(PasswordResetRequest request, String clientIp, String userAgent) {
        String ip = normalizeIp(clientIp);
        systemAuthService.verifyLoginCaptcha(AuthConstants.APP_ADMIN, request.getVerifyCodeId(), request.getVerifyCode(), ip);
        SysAppDO app = requireAdminApp();
        LocalDateTime now = LocalDateTime.now();
        String normalizedAccount = normalizeAccount(request.getLoginAccount());
        String accountFingerprint = sha256(normalizedAccount);
        enforceRequestRateLimit(app.getId(), accountFingerprint, ip, now);

        SysAccountDO account = accountMapper.selectOne(Wrappers.<SysAccountDO>lambdaQuery()
                .eq(SysAccountDO::getAppId, app.getId())
                .eq(SysAccountDO::getLoginAccount, normalizedAccount)
                .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        SysAccountDO eligibleAccount = eligible(account) ? account : null;
        return createResetRequest(app, eligibleAccount, accountFingerprint, ip, userAgent,
                normalizeLocale(request.getLocale()), now, false);
    }

    /** 管理员操作只签发重置邮件，不修改密码、不产生临时明文密码，也不提前注销现有会话。 */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public PasswordResetRequestResponse requestResetForAccount(Long accountId,
                                                               String locale,
                                                               String clientIp,
                                                               String userAgent) {
        SysAppDO app = requireAdminApp();
        SysAccountDO account = accountId == null ? null : accountMapper.selectById(accountId);
        if (!eligible(account) || !Objects.equals(account.getAppId(), app.getId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "account must be enabled with a verified email before password reset");
        }
        LocalDateTime now = LocalDateTime.now();
        String ip = normalizeIp(clientIp);
        String accountFingerprint = sha256(normalizeAccount(account.getLoginAccount()));
        enforceRequestRateLimit(app.getId(), accountFingerprint, ip, now);
        return createResetRequest(app, account, accountFingerprint, ip, userAgent,
                normalizeLocale(locale), now, true);
    }

    /** 统一持久化真实或虚拟重置请求，保证匿名和后台发起流程使用同一状态机。 */
    private PasswordResetRequestResponse createResetRequest(SysAppDO app,
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

        if (eligibleAccount != null) {
            if (requireEmailPrepared) {
                sendVerificationEmail(eligibleAccount, row, emailCode, row.getLocale());
            } else {
                sendVerificationEmailAfterCommit(eligibleAccount, row, emailCode, row.getLocale());
            }
        }
        PasswordResetRequestResponse response = new PasswordResetRequestResponse();
        response.setRequestId(row.getRequestNo());
        response.setExpireSeconds(EMAIL_CODE_TTL_SECONDS);
        response.setMessage(GENERIC_REQUEST_MESSAGE);
        return response;
    }

    /** 校验邮件验证码，成功后根据 MFA 状态签发短期阶段令牌或重置令牌。 */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = PasswordResetVerificationException.class)
    public PasswordResetVerifyResponse verifyEmail(PasswordResetEmailVerifyRequest request, String clientIp) {
        enforceStageIpRateLimit(RATE_SCOPE_EMAIL_VERIFY_IP, clientIp);
        SysPasswordResetRequestDO row = requireLockedRequest(request.getRequestId());
        LocalDateTime now = LocalDateTime.now();
        if (!STATE_PENDING_EMAIL.equals(row.getState()) || expired(row.getEmailExpireAt(), now)) {
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
        boolean matches = PasswordHashUtils.matches(request.getEmailCode(), row.getEmailCodeSalt(), row.getEmailCodeHash());
        SysAccountDO account = row.getAccountId() == null ? null : accountMapper.selectById(row.getAccountId());
        if (!matches || !eligible(account) || !Objects.equals(account.getAppId(), row.getAppId())) {
            if (row.getEmailVerifyCount() >= MAX_EMAIL_VERIFY_ATTEMPTS) {
                row.setState(STATE_LOCKED);
            }
            row.setUpdatedAt(now);
            resetRequestMapper.updateById(row);
            throw verificationFailed();
        }

        row.setEmailVerifiedAt(now);
        SysAccountMfaDO mfa = loadMfa(row.getAppId(), account.getId());
        if (mfa != null && (AuthConstants.MFA_STATUS_LOCKED.equals(mfa.getMfaStatus())
                || AuthConstants.MFA_STATUS_RESET_REQUIRED.equals(mfa.getMfaStatus()))) {
            row.setState(STATE_BLOCKED);
            row.setUpdatedAt(now);
            resetRequestMapper.updateById(row);
            return verifyResponse(NEXT_ADMIN_SUPPORT, null, 0,
                    "Self-service recovery is unavailable. Contact another administrator.");
        }
        if (mfa != null && AuthConstants.MFA_STATUS_ENABLED.equals(mfa.getMfaStatus())
                && StringUtils.hasText(mfa.getSecretCipher())) {
            String stageToken = LoginTokenUtils.generateToken();
            row.setStageTokenHash(LoginTokenUtils.hashToken(stageToken));
            row.setStageTokenExpireAt(now.plusSeconds(ACTION_TOKEN_TTL_SECONDS));
            row.setState(STATE_PENDING_MFA);
            row.setUpdatedAt(now);
            resetRequestMapper.updateById(row);
            return verifyResponse(NEXT_MFA, stageToken, ACTION_TOKEN_TTL_SECONDS,
                    "Authenticator verification is required.");
        }
        return prepareResetToken(row, now);
    }

    /** 校验 TOTP，拒绝过期、重放或锁定验证码，并在成功后签发一次性重置令牌。 */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = PasswordResetVerificationException.class)
    public PasswordResetVerifyResponse verifyMfa(PasswordResetMfaVerifyRequest request, String clientIp) {
        enforceStageIpRateLimit(RATE_SCOPE_MFA_VERIFY_IP, clientIp);
        SysPasswordResetRequestDO row = requireLockedRequest(request.getRequestId());
        LocalDateTime now = LocalDateTime.now();
        if (!STATE_PENDING_MFA.equals(row.getState())
                || expired(row.getStageTokenExpireAt(), now)
                || !tokenMatches(request.getActionToken(), row.getStageTokenHash())) {
            markExpiredIfNecessary(row, now);
            throw verificationFailed();
        }
        SysAccountDO account = row.getAccountId() == null ? null : accountMapper.selectById(row.getAccountId());
        SysAccountMfaDO mfa = account == null ? null : loadMfa(row.getAppId(), account.getId());
        if (!eligible(account) || mfa == null || !AuthConstants.MFA_STATUS_ENABLED.equals(mfa.getMfaStatus())
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

    /** 原子消费重置令牌，禁止复用当前密码，更新 PBKDF2 哈希并注销全部会话。 */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = PasswordResetVerificationException.class)
    public PasswordResetConfirmResponse confirm(PasswordResetConfirmRequest request, String clientIp) {
        enforceStageIpRateLimit(RATE_SCOPE_CONFIRM_IP, clientIp);
        SysPasswordResetRequestDO row = requireLockedRequest(request.getRequestId());
        LocalDateTime now = LocalDateTime.now();
        if (!STATE_READY.equals(row.getState())
                || expired(row.getResetTokenExpireAt(), now)
                || !tokenMatches(request.getResetToken(), row.getResetTokenHash())) {
            markExpiredIfNecessary(row, now);
            throw verificationFailed();
        }
        SysAccountDO account = row.getAccountId() == null ? null : accountMapper.selectByIdForUpdate(row.getAccountId());
        if (!eligible(account) || !Objects.equals(account.getAppId(), row.getAppId())) {
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

    private void enforceRequestRateLimit(Long appId, String accountFingerprint, String ip, LocalDateTime now) {
        String accountIdentity = appId + ":" + accountFingerprint;
        enforceDistributedRateLimit(RATE_SCOPE_COOLDOWN, accountIdentity, 1,
                Duration.ofSeconds(RESEND_COOLDOWN_SECONDS));
        enforceDistributedRateLimit(RATE_SCOPE_ACCOUNT, accountIdentity, ACCOUNT_REQUESTS_PER_HOUR,
                Duration.ofHours(1));
        enforceDistributedRateLimit(RATE_SCOPE_IP, appId + ":" + ip, IP_REQUESTS_PER_HOUR,
                Duration.ofHours(1));

        Long cooldownCount = resetRequestMapper.selectCount(Wrappers.<SysPasswordResetRequestDO>lambdaQuery()
                .eq(SysPasswordResetRequestDO::getAppId, appId)
                .eq(SysPasswordResetRequestDO::getAccountFingerprint, accountFingerprint)
                .ge(SysPasswordResetRequestDO::getCreatedAt, now.minusSeconds(RESEND_COOLDOWN_SECONDS)));
        Long accountCount = resetRequestMapper.selectCount(Wrappers.<SysPasswordResetRequestDO>lambdaQuery()
                .eq(SysPasswordResetRequestDO::getAppId, appId)
                .eq(SysPasswordResetRequestDO::getAccountFingerprint, accountFingerprint)
                .ge(SysPasswordResetRequestDO::getCreatedAt, now.minusHours(1)));
        Long ipCount = resetRequestMapper.selectCount(Wrappers.<SysPasswordResetRequestDO>lambdaQuery()
                .eq(SysPasswordResetRequestDO::getAppId, appId)
                .eq(SysPasswordResetRequestDO::getRequestIp, ip)
                .ge(SysPasswordResetRequestDO::getCreatedAt, now.minusHours(1)));
        if (positive(cooldownCount) || value(accountCount) >= ACCOUNT_REQUESTS_PER_HOUR || value(ipCount) >= IP_REQUESTS_PER_HOUR) {
            throw new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS.getCode(), "Too many requests. Please retry later.");
        }
    }

    /**
     * 开发环境阶段限流沿用每 IP 每小时 200 次，但邮件、MFA 和最终确认使用独立作用域，互不挤占额度。
     */
    private void enforceStageIpRateLimit(String scope, String clientIp) {
        enforceDistributedRateLimit(scope, normalizeIp(clientIp), IP_REQUESTS_PER_HOUR, Duration.ofHours(1));
    }

    /** Redis 提供跨实例原子判定，数据库计数继续作为审计型第二层保护。 */
    private void enforceDistributedRateLimit(String scope, String identity, int limit, Duration window) {
        SlidingWindowRateLimitResult result = rateLimiter.tryAcquire(scope, identity, limit, window);
        if (!result.allowed()) {
            throw new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS.getCode(),
                    "Too many requests. Please retry later.");
        }
    }

    private SysPasswordResetRequestDO requireLockedRequest(String requestId) {
        SysPasswordResetRequestDO row = resetRequestMapper.selectByRequestNoForUpdate(requestId);
        if (row == null) {
            PasswordHashUtils.hashPassword("000000", PasswordHashUtils.generateSalt());
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

    private PasswordResetVerifyResponse verifyResponse(String nextStep, String token, int expireSeconds, String message) {
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

    private boolean eligible(SysAccountDO account) {
        return account != null
                && Objects.equals(account.getStatus(), AuthConstants.ENABLED)
                && !Objects.equals(account.getLocked(), AuthConstants.ENABLED)
                && Objects.equals(account.getDeleted(), AuthConstants.NOT_DELETED)
                && StringUtils.hasText(account.getEmail())
                && account.getEmailVerifiedAt() != null;
    }

    private void markExpiredIfNecessary(SysPasswordResetRequestDO row, LocalDateTime now) {
        if (!STATE_COMPLETED.equals(row.getState()) && !STATE_BLOCKED.equals(row.getState()) && !STATE_LOCKED.equals(row.getState())) {
            row.setState(STATE_EXPIRED);
            row.setUpdatedAt(now);
            resetRequestMapper.updateById(row);
        }
    }

    private void sendVerificationEmailAfterCommit(SysAccountDO account,
                                                  SysPasswordResetRequestDO row,
                                                  String emailCode,
                                                  String locale) {
        afterCommit(() -> {
            try {
                sendVerificationEmail(account, row, emailCode, locale);
            } catch (RuntimeException exception) {
                log.warn("admin password reset verification email preparation failed, accountId: {}, exceptionType: {}",
                        account.getId(), exception.getClass().getSimpleName());
            }
        });
    }

    /** 创建验证码邮件任务；后台人工发起时 CLOSED 状态会同步返回失败。 */
    private void sendVerificationEmail(SysAccountDO account,
                                       SysPasswordResetRequestDO row,
                                       String emailCode,
                                       String locale) {
        EmailSendRequest email = baseEmail(account, row, "ADMIN_PASSWORD_RESET_VERIFY", "PASSWORD_RESET_VERIFY", locale);
        Map<String, Object> variables = new HashMap<>();
        variables.put("systemName", "Vexra Admin");
        variables.put("userName", account.getLoginAccount());
        variables.put("verifyCode", emailCode);
        variables.put("expireMinutes", EMAIL_CODE_TTL_SECONDS / 60);
        variables.put("verificationUrl", passwordResetUrl(row.getRequestNo()));
        variables.put("requestTime", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(row.getCreatedAt()));
        variables.put("securityNotice", verificationSecurityNotice(locale));
        email.setVariables(variables);
        EmailSendResult result = emailService.sendByTemplate(email);
        if (result == null || Objects.equals(result.getSendStatus(), EmailDeliveryStatus.CLOSED.getCode())
                || Objects.equals(result.getSendStatus(), EmailDeliveryStatus.CANCELLED.getCode())) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "password reset email could not be prepared");
        }
    }

    private void sendSuccessEmailAfterCommit(SysAccountDO account, SysPasswordResetRequestDO row, LocalDateTime now) {
        afterCommit(() -> {
            EmailSendRequest email = baseEmail(account, row, "ADMIN_PASSWORD_RESET_SUCCESS", "PASSWORD_RESET_SUCCESS",
                    normalizeLocale(row.getLocale()));
            Map<String, Object> variables = new HashMap<>();
            variables.put("systemName", "Vexra Admin");
            variables.put("userName", account.getLoginAccount());
            variables.put("operationTime", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(now));
            variables.put("securityNotice", successSecurityNotice(row.getLocale()));
            email.setVariables(variables);
            sendEmailSafely(email, "password reset success", account.getId());
        });
    }

    private EmailSendRequest baseEmail(SysAccountDO account,
                                       SysPasswordResetRequestDO row,
                                       String templateCode,
                                       String sceneCode,
                                       String locale) {
        EmailSendRequest email = new EmailSendRequest();
        email.setAppCode(AuthConstants.APP_ADMIN);
        email.setTemplateCode(templateCode);
        email.setSceneCode(sceneCode);
        email.setLocale(locale);
        email.setToEmails(List.of(account.getEmail()));
        email.setBizType("PASSWORD_RESET");
        email.setBizNo(row.getRequestNo());
        return email;
    }

    /** 构造带请求号的管理端找回密码入口，管理员发起和登录页发起使用同一页面。 */
    private String passwordResetUrl(String requestNo) {
        Map<String, String> values = configService.enabledConfigValues(Set.of(SystemConfigKeys.ADMIN_FRONTEND_BASE_URL));
        String baseUrl = values.get(SystemConfigKeys.ADMIN_FRONTEND_BASE_URL);
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = "http://127.0.0.1:5173";
        }
        return UriComponentsBuilder.fromUriString(baseUrl.replaceAll("/+$", ""))
                .path("/forgot-password")
                .queryParam("requestId", requestNo)
                .build()
                .encode()
                .toUriString();
    }

    private void sendEmailSafely(EmailSendRequest email, String purpose, Long accountId) {
        try {
            emailService.sendByTemplate(email);
        } catch (RuntimeException exception) {
            log.warn("admin {} email enqueue failed, accountId: {}, exceptionType: {}",
                    purpose, accountId, exception.getClass().getSimpleName());
        }
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

    private boolean tokenMatches(String rawToken, String expectedHash) {
        if (!StringUtils.hasText(rawToken) || !StringUtils.hasText(expectedHash)) {
            return false;
        }
        byte[] actual = LoginTokenUtils.hashToken(rawToken).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
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

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "account fingerprint can not be calculated");
        }
    }

    private PasswordResetVerificationException verificationFailed() {
        return new PasswordResetVerificationException();
    }

    private boolean expired(LocalDateTime expireAt, LocalDateTime now) {
        return expireAt == null || !expireAt.isAfter(now);
    }

    private String normalizeAccount(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String value) {
        return StringUtils.hasText(value) ? truncate(value.trim(), 64) : "unknown";
    }

    private String normalizeLocale(String value) {
        return "en-US".equals(value) ? "en-US" : "zh-CN";
    }

    private String verificationSecurityNotice(String locale) {
        return "en-US".equals(normalizeLocale(locale))
                ? "If you did not request this, ignore this email and contact another administrator."
                : "如非本人操作，请忽略本邮件并联系其他管理员。";
    }

    private String successSecurityNotice(String locale) {
        return "en-US".equals(normalizeLocale(locale))
                ? "All previous sessions were signed out. Contact another administrator immediately if this was not you."
                : "所有旧会话均已退出。如非本人操作，请立即联系其他管理员。";
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

    /**
     * 表示需要向匿名调用方返回统一提示、但必须提交失败计数或终态变更的验证失败。
     * 非该类型的运行时异常仍按事务默认规则回滚，避免基础设施异常提交不完整数据。
     */
    private static final class PasswordResetVerificationException extends ServiceException {

        private static final long serialVersionUID = 1L;

        private PasswordResetVerificationException() {
            super(ApiResultEnum.UNAUTHORIZED.getCode(), "Verification failed or expired.");
        }
    }
}
