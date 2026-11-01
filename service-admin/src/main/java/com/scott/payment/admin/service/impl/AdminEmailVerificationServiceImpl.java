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
import com.scott.payment.component.db.auth.entity.SysEmailVerificationRequestDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysEmailVerificationRequestMapper;
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
 * 管理账号邮箱验证服务实现；使用哈希验证码、短期一次性令牌、行锁状态机和双层限流完成账号激活及邮箱变更验证。
 */
@Slf4j
@Service
public class AdminEmailVerificationServiceImpl implements AdminEmailVerificationService {

    /**
     * 状态等待常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String STATE_PENDING = "PENDING";
    /**
     * {@code STATE_VERIFIED}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String STATE_VERIFIED = "VERIFIED";
    /**
     * {@code STATE_COMPLETED}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String STATE_COMPLETED = "COMPLETED";
    /**
     * {@code STATE_EXPIRED}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String STATE_EXPIRED = "EXPIRED";
    /**
     * {@code STATE_LOCKED}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String STATE_LOCKED = "LOCKED";
    /**
     * {@code STATE_SUPERSEDED}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String STATE_SUPERSEDED = "SUPERSEDED";
    /**
     * 状态拦截常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String STATE_BLOCKED = "BLOCKED";
    /**
     * {@code NEXT_SET_PASSWORD}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；高敏感字段，禁止明文打印日志，禁止写入异常消息。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String NEXT_SET_PASSWORD = "SET_PASSWORD";
    /**
     * {@code NEXT_COMPLETED}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String NEXT_COMPLETED = "COMPLETED";
    /**
     * 邮件验证码固定在 30 分钟后过期。
     */
    private static final int EMAIL_CODE_TTL_SECONDS = 30 * 60;
    /**
     * 邮箱验证通过后的首次设密令牌仅保留五分钟。
     */
    private static final int ACTION_TOKEN_TTL_SECONDS = 5 * 60;
    /**
     * 开发阶段允许充分手工验收，同时仍以高熵验证码和限流阻断自动爆破。
     */
    private static final int MAX_VERIFY_ATTEMPTS = 50;
    /**
     * {@code ACCOUNT_REQUESTS_PER_HOUR}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：请求链路、回调链路或跨服务调用上下文。
     * </p>
     */
    private static final int ACCOUNT_REQUESTS_PER_HOUR = 50;
    /**
     * {@code IP_REQUESTS_PER_HOUR}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：请求链路、回调链路或跨服务调用上下文。
     * </p>
     */
    private static final int IP_REQUESTS_PER_HOUR = 200;
    /**
     * {@code RESEND_COOLDOWN_SECONDS}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int RESEND_COOLDOWN_SECONDS = 6;
    /**
     * {@code MAX_USER_AGENT_LENGTH}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MAX_USER_AGENT_LENGTH = 500;
    /**
     * {@code RATE_SCOPE_COOLDOWN}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RATE_SCOPE_COOLDOWN = "adminEmailVerificationCooldown";
    /**
     * 汇率范围账号常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RATE_SCOPE_ACCOUNT = "adminEmailVerificationAccount";
    /**
     * {@code RATE_SCOPE_IP}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RATE_SCOPE_IP = "adminEmailVerificationIp";
    /**
     * {@code RATE_SCOPE_VERIFY_IP}常量，统一 {@code AdminEmailVerificationServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RATE_SCOPE_VERIFY_IP = "adminEmailVerificationVerifyIp";
    /**
     * {@code TEMPLATE_ACTIVATION}，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String TEMPLATE_ACTIVATION = "ADMIN_ACCOUNT_ACTIVATION";
    /**
     * {@code TEMPLATE_EMAIL_VERIFICATION}，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String TEMPLATE_EMAIL_VERIFICATION = "ADMIN_EMAIL_VERIFICATION";
    /**
     * 邮件时间格式化器，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter EMAIL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /**
     * {@code CODE_DIGITS}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CODE_DIGITS = "23456789";
    /**
     * {@code CODE_LOWERCASE}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CODE_LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    /**
     * {@code CODE_UPPERCASE}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CODE_UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    /**
     * {@code CODE_CHARACTERS}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CODE_CHARACTERS = CODE_DIGITS + CODE_LOWERCASE + CODE_UPPERCASE;
    /**
     * 密码学安全随机数生成器，用于生成一次性 AES 密钥和 GCM IV。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
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

    /**
     * 创建邮箱验证服务并注入账号、请求、邮件和限流能力。
     */
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

    /**
     * 校验一次性邮件验证码；普通验证直接完成，新账号激活则签发短期首次设密令牌。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = EmailVerificationException.class)
    public EmailVerificationVerifyResponse verify(EmailVerificationVerifyRequest request, String clientIp) {
        enforceVerifyRateLimit(normalizeIp(clientIp));
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

    /**
     * 原子消费首次设密令牌并激活账号，随后注销该账号可能存在的全部旧会话。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class, noRollbackFor = EmailVerificationException.class)
    public AccountActivationCompleteResponse completeActivation(AccountActivationCompleteRequest request, String clientIp) {
        enforceVerifyRateLimit(normalizeIp(clientIp));
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

    /**
     * 校验验证码签发用途受支持。
     */
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

    /**
     * 校验并返回允许的状态目标值。
     */
    private boolean validTarget(SysEmailVerificationRequestDO row, SysAccountDO account) {
        return account != null
                && Objects.equals(account.getAppId(), row.getAppId())
                && Objects.equals(account.getDeleted(), AuthConstants.NOT_DELETED)
                && StringUtils.hasText(verificationTargetEmail(account, row.getPurpose()))
                && Objects.equals(emailFingerprint(verificationTargetEmail(account, row.getPurpose())),
                row.getEmailFingerprint());
    }

    /**
     * 邮箱变更验证指向待确认邮箱，其余流程使用当前正式邮箱。
     */
    private String verificationTargetEmail(SysAccountDO account, String purpose) {
        return PURPOSE_EMAIL_CHANGE.equals(purpose) ? account.getPendingEmail() : account.getEmail();
    }

    /**
     * 查询并校验已启用的管理端应用。
     */
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

    /**
     * 校验并获取账户。
     */
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

    /**
     * 加锁查询邮箱验证请求，并校验请求存在。
     */
    private SysEmailVerificationRequestDO requireLockedRequest(String requestId) {
        SysEmailVerificationRequestDO row = requestMapper.selectByRequestNoForUpdate(requestId);
        if (row == null) {
            PasswordHashUtils.hashPassword("Aa2Bb3", PasswordHashUtils.generateSalt());
            throw verificationFailed();
        }
        return row;
    }

    /**
     * 执行验证码签发接口限流。
     */
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

    /**
     * 执行验证码校验接口限流。
     */
    private void enforceVerifyRateLimit(String ip) {
        enforceDistributedRateLimit(RATE_SCOPE_VERIFY_IP, ip, IP_REQUESTS_PER_HOUR, Duration.ofHours(1));
    }

    /**
     * 使用分布式滑动窗口执行邮箱验证码接口限流。
     */
    private void enforceDistributedRateLimit(String scope, String identity, int limit, Duration window) {
        SlidingWindowRateLimitResult result = rateLimiter.tryAcquire(scope, identity, limit, window);
        if (!result.allowed()) {
            throw tooManyRequests();
        }
    }

    /**
     * 构建请求频率超限异常。
     */
    private ServiceException tooManyRequests() {
        return new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS.getCode(),
                "Too many requests. Please retry later.");
    }

    /**
     * 在事务提交后发送验证码邮件。
     */
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
            /**
             * 在当前事务成功提交后执行延迟动作。
             */
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    /**
     * 发送账户验证码邮件。
     */
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

    /**
     * 构建邮件验证码确认地址。
     */
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

    /**
     * 构建账户安全通知邮件内容。
     */
    private String securityNotice(String locale) {
        return "en-US".equals(normalizeLocale(locale))
                ? "If you did not request this change, ignore this email and contact another administrator."
                : "如非本人或授权管理员发起，请忽略本邮件并联系其他管理员。";
    }

    /**
     * 构建验证码校验响应。
     */
    private EmailVerificationVerifyResponse verifyResponse(String nextStep, String actionToken,
                                                           int expireSeconds, String message) {
        EmailVerificationVerifyResponse response = new EmailVerificationVerifyResponse();
        response.setNextStep(nextStep);
        response.setActionToken(actionToken);
        response.setExpireSeconds(expireSeconds);
        response.setMessage(message);
        return response;
    }

    /**
     * 生成邮件验证码并限制字符范围。
     */
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

    /**
     * 生成验证码允许字符集中的随机字符。
     */
    private char randomCharacter(String candidates) {
        return candidates.charAt(SECURE_RANDOM.nextInt(candidates.length()));
    }

    /**
     * 计算邮件发送内容指纹用于幂等控制。
     */
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

    /**
     * 使用常量时间比较安全令牌摘要。
     */
    private boolean tokenMatches(String token, String expectedHash) {
        return StringUtils.hasText(token) && StringUtils.hasText(expectedHash)
                && MessageDigest.isEqual(LoginTokenUtils.hashToken(token).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将仍处于待验证或已验证状态的过期请求标记为已过期。
     */
    private void markExpiredIfNecessary(SysEmailVerificationRequestDO row, LocalDateTime now) {
        if (STATE_PENDING.equals(row.getState()) || STATE_VERIFIED.equals(row.getState())) {
            row.setState(STATE_EXPIRED);
            row.setUpdatedAt(now);
            requestMapper.updateById(row);
        }
    }

    /**
     * 将邮箱验证请求标记为锁定并更新修改时间。
     */
    private void markLocked(SysEmailVerificationRequestDO row, LocalDateTime now) {
        row.setState(STATE_LOCKED);
        row.setUpdatedAt(now);
        requestMapper.updateById(row);
    }

    /**
     * 判断安全令牌是否已过期。
     */
    private boolean expired(LocalDateTime expireAt, LocalDateTime now) {
        return expireAt == null || !expireAt.isAfter(now);
    }

    /**
     * 规范化邮件语言标签，未指定时使用管理端默认语言。
     */
    private String normalizeLocale(String locale) {
        return "en-US".equals(locale) ? "en-US" : "zh-CN";
    }

    /**
     * 规范化部署节点 IP。
     */
    private String normalizeIp(String ip) {
        return StringUtils.hasText(ip) ? truncate(ip, 64) : "unknown";
    }

    /**
     * 按持久化字段上限截断文本。
     */
    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    /**
     * 将可为空的整数转换为非空计数值。
     */
    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 将可为空的数据库计数转换为非空长整型值。
     */
    private long value(Long count) {
        return count == null ? 0L : count;
    }

    /**
     * 校验并返回大于零的数值。
     */
    private boolean positive(Long count) {
        return value(count) > 0L;
    }

    /**
     * 构建验证码校验失败异常。
     */
    private EmailVerificationException verificationFailed() {
        return new EmailVerificationException();
    }

    /**
     * 验证失败必须提交失败次数或终态，同时向匿名调用方返回统一提示。
     */
    private static final class EmailVerificationException extends ServiceException {
        private static final long serialVersionUID = 1L;

        /**
         * 阻止仅提供静态能力的类型被实例化。
         */
        private EmailVerificationException() {
            super(ApiResultEnum.UNAUTHORIZED.getCode(), "Verification failed or expired.");
        }
    }
}
