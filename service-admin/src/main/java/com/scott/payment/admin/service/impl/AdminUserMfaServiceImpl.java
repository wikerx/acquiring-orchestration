package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.constant.SystemConfigKeys;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaActionRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaExemptRequest;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogQuery;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaLogResponse;
import com.scott.payment.admin.dto.AdminUserMfaDTOs.UserMfaStatusResponse;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.admin.service.AdminUserMfaService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaLogDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaTokenDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaLogMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaTokenMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.support.MfaSecretCrypto;
import com.scott.payment.component.db.auth.support.TotpUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserMfaServiceImpl
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : admin用户MFA服务实现，位于 运营后台服务，执行该业务的规则校验和数据读写，并保持现有事务与异常边界。
 * @status : create
 */
@Slf4j
@Service
public class AdminUserMfaServiceImpl implements AdminUserMfaService {

    /**
     * 未删除标识。
     */
    private static final long NOT_DELETED = 0L;

    /**
     * 未退出状态。
     */
    private static final int NOT_LOGOUT = 0;

    /**
     * 已退出状态。
     */
    private static final int LOGOUT = 1;

    /**
     * MFA 操作结果：成功。
     */
    private static final String RESULT_SUCCESS = "SUCCESS";

    /**
     * MFA 操作结果：失败。
     */
    private static final String RESULT_FAILED = "FAILED";

    /**
     * 绑定邮件模板编码。
     */
    private static final String TEMPLATE_BIND_NOTICE = "ADMIN_MFA_BIND_NOTICE";

    /**
     * 启用邮件模板编码。
     */
    private static final String TEMPLATE_ENABLED_NOTICE = "ADMIN_MFA_ENABLED_NOTICE";

    /**
     * 重置邮件模板编码。
     */
    private static final String TEMPLATE_RESET_NOTICE = "ADMIN_MFA_RESET_NOTICE";

    /**
     * 停用邮件模板编码。
     */
    private static final String TEMPLATE_DISABLED_NOTICE = "ADMIN_MFA_DISABLED_NOTICE";

    /**
     * 豁免邮件模板编码。
     */
    private static final String TEMPLATE_EXEMPT_NOTICE = "ADMIN_MFA_EXEMPT_NOTICE";

    /**
     * MFA 登录票据类型。
     */
    private static final String MFA_TOKEN_TYPE_LOGIN = "LOGIN_MFA";

    private final SysAppMapper sysAppMapper;
    private final SysAccountMapper sysAccountMapper;
    private final SysAccountMfaMapper sysAccountMfaMapper;
    private final SysAccountMfaTokenMapper sysAccountMfaTokenMapper;
    private final SysAccountMfaLogMapper sysAccountMfaLogMapper;
    private final SysLoginSessionMapper sysLoginSessionMapper;
    private final AdminEmailService adminEmailService;
    private final AdminConfigService adminConfigService;

    /**
     * 创建后台用户 MFA 管理服务。
     *
     * @param sysAppMapper             应用 Mapper
     * @param sysAccountMapper         账号 Mapper
     * @param sysAccountMfaMapper      MFA 配置 Mapper
     * @param sysAccountMfaTokenMapper MFA 票据 Mapper
     * @param sysAccountMfaLogMapper   MFA 日志 Mapper
     * @param sysLoginSessionMapper    登录会话 Mapper
     * @param adminEmailService        邮件服务
     * @param adminConfigService       系统参数服务
     */
    public AdminUserMfaServiceImpl(SysAppMapper sysAppMapper,
                                   SysAccountMapper sysAccountMapper,
                                   SysAccountMfaMapper sysAccountMfaMapper,
                                   SysAccountMfaTokenMapper sysAccountMfaTokenMapper,
                                   SysAccountMfaLogMapper sysAccountMfaLogMapper,
                                   SysLoginSessionMapper sysLoginSessionMapper,
                                   AdminEmailService adminEmailService,
                                   AdminConfigService adminConfigService) {
        this.sysAppMapper = sysAppMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysAccountMfaMapper = sysAccountMfaMapper;
        this.sysAccountMfaTokenMapper = sysAccountMfaTokenMapper;
        this.sysAccountMfaLogMapper = sysAccountMfaLogMapper;
        this.sysLoginSessionMapper = sysLoginSessionMapper;
        this.adminEmailService = adminEmailService;
        this.adminConfigService = adminConfigService;
    }

    /**
     * 强制后台用户启用 TOTP MFA，生成加密待绑定密钥并注销既有会话。
     *
     * <p>旧正式密钥和未完成票据会失效；新密钥仅以密文持久化，
     * 操作同时写入 MFA 审计并发送启用、绑定通知。</p>
     *
     * @param request 目标账号和操作原因
     * @return 更新后的 MFA 策略与绑定状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public UserMfaStatusResponse requireMfa(UserMfaActionRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_PENDING_BIND);
        mfa.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        mfa.setPendingSecretCipher(MfaSecretCrypto.encrypt(TotpUtils.generateBase32Secret()));
        mfa.setSecretCipher(null);
        mfa.setIssuer("Acquiring Admin");
        mfa.setAccountLabel(account.getLoginAccount());
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setExemptReason(null);
        mfa.setExemptUntil(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentOperatorId());
        sysAccountMfaMapper.updateById(mfa);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordLog(app, account, mfa, "REQUIRE", RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), clientIpFallback(), null);
        sendNotice(account, TEMPLATE_ENABLED_NOTICE, request.getReason(), null);
        sendNotice(account, TEMPLATE_BIND_NOTICE, request.getReason(), null);
        return toStatusResponse(account, mfa);
    }

    /**
     * 重置其他后台用户的 TOTP MFA，清除旧密钥并生成新的加密待绑定密钥。
     *
     * <p>禁止操作者重置自身 MFA；未完成票据和现有登录会话会统一失效。</p>
     *
     * @param request 目标账号和重置原因
     * @return 进入 RESET_REQUIRED 状态的 MFA 信息
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public UserMfaStatusResponse resetMfa(UserMfaActionRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        assertNotSelf(account.getId(), "不能重置当前登录账号自己的 MFA");
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_RESET_REQUIRED);
        mfa.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        mfa.setSecretCipher(null);
        mfa.setPendingSecretCipher(MfaSecretCrypto.encrypt(TotpUtils.generateBase32Secret()));
        mfa.setIssuer("Acquiring Admin");
        mfa.setAccountLabel(account.getLoginAccount());
        mfa.setResetTime(now);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setLastSuccessTimeStep(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentOperatorId());
        sysAccountMfaMapper.updateById(mfa);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordLog(app, account, mfa, "RESET", RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), clientIpFallback(), null);
        sendNotice(account, TEMPLATE_RESET_NOTICE, request.getReason(), null);
        sendNotice(account, TEMPLATE_BIND_NOTICE, request.getReason(), null);
        return toStatusResponse(account, mfa);
    }

    /**
     * 为其他后台用户配置有期限的 MFA 豁免，并清除现有及待绑定密钥。
     *
     * <p>豁免会注销现有会话并失效未完成票据，豁免原因和截止时间写入审计。</p>
     *
     * @param request 目标账号、豁免原因和截止时间
     * @return 更新后的豁免状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public UserMfaStatusResponse exemptMfa(UserMfaExemptRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        assertNotSelf(account.getId(), "不能豁免当前登录账号自己的 MFA");
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_EXEMPT);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_EXEMPT);
        mfa.setSecretCipher(null);
        mfa.setPendingSecretCipher(null);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setExemptReason(normalize(request.getReason()));
        mfa.setExemptUntil(request.getExemptUntil());
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentOperatorId());
        sysAccountMfaMapper.updateById(mfa);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordLog(app, account, mfa, "EXEMPT", RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), clientIpFallback(), null);
        sendNotice(account, TEMPLATE_EXEMPT_NOTICE, request.getReason(), request.getExemptUntil());
        return toStatusResponse(account, mfa);
    }

    /**
     * 停用其他后台用户 MFA，删除密钥引用并注销现有会话。
     *
     * <p>禁止操作者停用自身 MFA，避免绕过当前管理会话的二次认证约束。</p>
     *
     * @param request 目标账号和停用原因
     * @return 更新后的未启用状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public UserMfaStatusResponse disableMfa(UserMfaActionRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        assertNotSelf(account.getId(), "不能停用当前登录账号自己的 MFA");
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_OPTIONAL);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_NOT_ENABLED);
        mfa.setSecretCipher(null);
        mfa.setPendingSecretCipher(null);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentOperatorId());
        sysAccountMfaMapper.updateById(mfa);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordLog(app, account, mfa, "DISABLE", RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), clientIpFallback(), null);
        sendNotice(account, TEMPLATE_DISABLED_NOTICE, request.getReason(), null);
        return toStatusResponse(account, mfa);
    }

    /**
     * 清零 MFA 失败次数和锁定截止时间，并注销现有会话使解锁状态重新生效。
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 解锁后的后台账号 MFA 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public UserMfaStatusResponse unlockMfa(UserMfaActionRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        if (AuthConstants.MFA_STATUS_LOCKED.equals(mfa.getMfaStatus())) {
            mfa.setMfaStatus(StringUtils.hasText(mfa.getSecretCipher())
                    ? AuthConstants.MFA_STATUS_ENABLED
                    : AuthConstants.MFA_STATUS_PENDING_BIND);
        }
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentOperatorId());
        sysAccountMfaMapper.updateById(mfa);
        recordLog(app, account, mfa, "UNLOCK", RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), clientIpFallback(), null);
        return toStatusResponse(account, mfa);
    }

    /**
     * 为待绑定或需重绑用户重新发送 MFA 绑定邮件。
     *
     * <p>缺少待绑定密钥时生成新的加密密钥，并使旧绑定票据失效；
     * 任何密钥明文均不得进入日志或邮件审计正文。</p>
     *
     * @param request 目标账号和重发原因
     * @return 当前 MFA 绑定状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public UserMfaStatusResponse resendBindMail(UserMfaActionRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        if (!AuthConstants.MFA_STATUS_PENDING_BIND.equals(mfa.getMfaStatus())
                && !AuthConstants.MFA_STATUS_RESET_REQUIRED.equals(mfa.getMfaStatus())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "MFA 绑定邮件只能对待绑定或需重绑用户重发");
        }
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        if (!StringUtils.hasText(mfa.getPendingSecretCipher())) {
            mfa.setPendingSecretCipher(MfaSecretCrypto.encrypt(TotpUtils.generateBase32Secret()));
        }
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentOperatorId());
        sysAccountMfaMapper.updateById(mfa);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        recordLog(app, account, mfa, "RESEND_BIND_MAIL", RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), clientIpFallback(), null);
        sendNotice(account, TEMPLATE_BIND_NOTICE, request.getReason(), null);
        return toStatusResponse(account, mfa);
    }

    /**
     * 从只读数据源分页查询 MFA 管理审计日志。
     *
     * @param query 账号、动作、结果、时间范围和分页条件
     * @return 按事件时间倒序的 MFA 日志分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<UserMfaLogResponse> pageLogs(UserMfaLogQuery query) {
        UserMfaLogQuery condition = query == null ? new UserMfaLogQuery() : query;
        SysAppDO app = getAdminApp();
        Page<SysAccountMfaLogDO> page = sysAccountMfaLogMapper.selectPage(
                new Page<>(condition.safePageNo(), condition.safePageSize()),
                Wrappers.<SysAccountMfaLogDO>lambdaQuery()
                        .eq(SysAccountMfaLogDO::getAppId, app.getId())
                        .eq(condition.getAccountId() != null, SysAccountMfaLogDO::getAccountId, condition.getAccountId())
                        .eq(StringUtils.hasText(condition.getActionType()), SysAccountMfaLogDO::getActionType, normalize(condition.getActionType()))
                        .eq(StringUtils.hasText(condition.getResult()), SysAccountMfaLogDO::getResult, normalize(condition.getResult()))
                        .ge(condition.getBeginTime() != null, SysAccountMfaLogDO::getEventTime, condition.getBeginTime())
                        .le(condition.getEndTime() != null, SysAccountMfaLogDO::getEventTime, condition.getEndTime())
                        .orderByDesc(SysAccountMfaLogDO::getEventTime)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream()
                .filter(row -> !StringUtils.hasText(condition.getLoginAccount())
                        || accountLoginAccount(row.getAccountId()).contains(normalize(condition.getLoginAccount())))
                .map(this::toLogResponse)
                .toList());
    }

    /**
     * 查询未删除的后台管理应用，限定 MFA 数据归属。
     *
     * @return 后台管理应用记录
     * @throws ServiceException 后台应用未配置时抛出
     */
    private SysAppDO getAdminApp() {
        SysAppDO app = sysAppMapper.selectOne(
                Wrappers.<SysAppDO>lambdaQuery()
                        .eq(SysAppDO::getAppCode, AuthConstants.APP_ADMIN)
                        .eq(SysAppDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (app == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "ADMIN app not found");
        }
        return app;
    }

    private SysAccountDO getAccount(Long appId, Long accountId) {
        SysAccountDO account = sysAccountMapper.selectOne(
                Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getId, accountId)
                        .eq(SysAccountDO::getAppId, appId)
                        .eq(SysAccountDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (account == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "account not found");
        }
        return account;
    }

    private SysAccountMfaDO ensureMfa(SysAppDO app, SysAccountDO account, LocalDateTime now) {
        SysAccountMfaDO mfa = sysAccountMfaMapper.selectOne(
                Wrappers.<SysAccountMfaDO>lambdaQuery()
                        .eq(SysAccountMfaDO::getAppId, app.getId())
                        .eq(SysAccountMfaDO::getAccountId, account.getId())
                        .eq(SysAccountMfaDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (mfa != null) {
            return mfa;
        }
        SysAccountMfaDO created = new SysAccountMfaDO();
        created.setAppId(app.getId());
        created.setAccountId(account.getId());
        created.setUserId(account.getUserId());
        created.setMerchantId(account.getMerchantId());
        created.setMfaPolicy(AuthConstants.MFA_POLICY_OPTIONAL);
        created.setMfaStatus(AuthConstants.MFA_STATUS_NOT_ENABLED);
        created.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        created.setIssuer("Acquiring Admin");
        created.setAccountLabel(account.getLoginAccount());
        created.setFailedVerifyCount(0);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        created.setCreatedBy(currentOperatorId());
        created.setUpdatedBy(currentOperatorId());
        created.setDeleted(NOT_DELETED);
        sysAccountMfaMapper.insert(created);
        return created;
    }

    private void expireOpenMfaTokens(Long appId, Long accountId, LocalDateTime now) {
        sysAccountMfaTokenMapper.update(
                Wrappers.<SysAccountMfaTokenDO>lambdaUpdate()
                        .set(SysAccountMfaTokenDO::getUsed, AuthConstants.ENABLED)
                        .set(SysAccountMfaTokenDO::getUsedAt, now)
                        .set(SysAccountMfaTokenDO::getUpdatedAt, now)
                        .eq(SysAccountMfaTokenDO::getAppId, appId)
                        .eq(SysAccountMfaTokenDO::getAccountId, accountId)
                        .eq(SysAccountMfaTokenDO::getTokenType, MFA_TOKEN_TYPE_LOGIN)
                        .eq(SysAccountMfaTokenDO::getUsed, AuthConstants.DISABLED)
                        .eq(SysAccountMfaTokenDO::getDeleted, NOT_DELETED)
        );
    }

    private void logoutSessions(Long appId, Long accountId, LocalDateTime now) {
        sysLoginSessionMapper.update(
                Wrappers.<SysLoginSessionDO>lambdaUpdate()
                        .set(SysLoginSessionDO::getLogout, LOGOUT)
                        .set(SysLoginSessionDO::getLogoutAt, now)
                        .set(SysLoginSessionDO::getUpdatedAt, now)
                        .eq(SysLoginSessionDO::getAppId, appId)
                        .eq(SysLoginSessionDO::getAccountId, accountId)
                        .eq(SysLoginSessionDO::getLogout, NOT_LOGOUT)
        );
    }

    private void sendNotice(SysAccountDO account, String templateCode, String reason, LocalDateTime exemptUntil) {
        if (!StringUtils.hasText(account.getEmail())) {
            return;
        }
        try {
            EmailSendRequest request = new EmailSendRequest();
            request.setAppCode(AuthConstants.APP_ADMIN);
            request.setTemplateCode(templateCode);
            request.setSceneCode("ADMIN_MFA");
            request.setLocale("zh-CN");
            request.getToEmails().add(account.getEmail());
            request.setBizType("ADMIN_MFA");
            request.setBizNo(String.valueOf(account.getId()));
            request.setVariables(emailVariables(account, reason, exemptUntil));
            adminEmailService.sendByTemplate(request);
        } catch (RuntimeException exception) {
            log.warn("admin mfa notice send failed, accountId: {}, templateCode: {}, exceptionType: {}",
                    account.getId(), templateCode, exception.getClass().getSimpleName());
            SysAppDO app = getAdminApp();
            SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
            recordLog(app, account, mfa, "SEND_NOTICE", RESULT_FAILED,
                    exception.getClass().getSimpleName(), mfa.getMfaPolicy(), mfa.getMfaStatus(),
                    currentOperator(), clientIpFallback(), null);
        }
    }

    private Map<String, Object> emailVariables(SysAccountDO account, String reason, LocalDateTime exemptUntil) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("loginAccount", account.getLoginAccount());
        variables.put("email", account.getEmail());
        variables.put("reason", StringUtils.hasText(reason) ? reason : "-");
        variables.put("exemptUntil", exemptUntil == null ? "长期有效" : exemptUntil.toString().replace("T", " "));
        variables.put("bindUrl", adminLoginUrl());
        return variables;
    }

    private String adminLoginUrl() {
        Map<String, String> configValues = adminConfigService.enabledConfigValues(Set.of(SystemConfigKeys.ADMIN_FRONTEND_BASE_URL));
        String baseUrl = configValues.get(SystemConfigKeys.ADMIN_FRONTEND_BASE_URL);
        if (!StringUtils.hasText(baseUrl)) {
            return "http://127.0.0.1:5173/login";
        }
        return baseUrl.replaceAll("/+$", "") + "/login";
    }

    private void recordLog(SysAppDO app,
                           SysAccountDO account,
                           SysAccountMfaDO mfa,
                           String actionType,
                           String result,
                           String reason,
                           String beforePolicy,
                           String beforeStatus,
                           InternalAuthAccount operator,
                           String clientIp,
                           String userAgent) {
        SysAccountMfaLogDO logRow = new SysAccountMfaLogDO();
        logRow.setAppId(app.getId());
        logRow.setAccountId(account.getId());
        logRow.setUserId(account.getUserId());
        logRow.setMerchantId(account.getMerchantId());
        logRow.setActionType(actionType);
        logRow.setResult(result);
        logRow.setReason(normalize(reason));
        logRow.setBeforePolicy(beforePolicy);
        logRow.setBeforeStatus(beforeStatus);
        logRow.setAfterPolicy(mfa.getMfaPolicy());
        logRow.setAfterStatus(mfa.getMfaStatus());
        logRow.setOperatorAccountId(operator == null ? null : operator.getAccountId());
        logRow.setOperatorLoginAccount(operator == null ? null : operator.getLoginAccount());
        logRow.setClientIp(clientIp);
        logRow.setUserAgent(userAgent);
        logRow.setEventTime(LocalDateTime.now());
        logRow.setCreatedAt(LocalDateTime.now());
        sysAccountMfaLogMapper.insert(logRow);
    }

    private UserMfaStatusResponse toStatusResponse(SysAccountDO account, SysAccountMfaDO mfa) {
        UserMfaStatusResponse response = new UserMfaStatusResponse();
        response.setAccountId(account.getId());
        response.setLoginAccount(account.getLoginAccount());
        response.setMfaPolicy(mfa.getMfaPolicy());
        response.setMfaStatus(mfa.getMfaStatus());
        response.setBindTime(mfa.getBindTime());
        response.setLastVerifyTime(mfa.getLastVerifyTime());
        response.setLockedUntil(mfa.getLockedUntil());
        response.setExemptUntil(mfa.getExemptUntil());
        return response;
    }

    private UserMfaLogResponse toLogResponse(SysAccountMfaLogDO row) {
        UserMfaLogResponse response = new UserMfaLogResponse();
        response.setId(row.getId());
        response.setAccountId(row.getAccountId());
        response.setLoginAccount(accountLoginAccount(row.getAccountId()));
        response.setActionType(row.getActionType());
        response.setResult(row.getResult());
        response.setReason(row.getReason());
        response.setBeforePolicy(row.getBeforePolicy());
        response.setBeforeStatus(row.getBeforeStatus());
        response.setAfterPolicy(row.getAfterPolicy());
        response.setAfterStatus(row.getAfterStatus());
        response.setOperatorLoginAccount(resolveOperatorLoginAccount(row, response.getLoginAccount()));
        response.setClientIp(row.getClientIp());
        response.setEventTime(row.getEventTime());
        return response;
    }

    private String accountLoginAccount(Long accountId) {
        if (accountId == null) {
            return "-";
        }
        SysAccountDO account = sysAccountMapper.selectById(accountId);
        return account == null ? "-" : account.getLoginAccount();
    }

    private String resolveOperatorLoginAccount(SysAccountMfaLogDO row, String targetLoginAccount) {
        if (StringUtils.hasText(row.getOperatorLoginAccount())) {
            return row.getOperatorLoginAccount();
        }
        if (row.getOperatorAccountId() != null) {
            String operatorLoginAccount = accountLoginAccount(row.getOperatorAccountId());
            if (StringUtils.hasText(operatorLoginAccount) && !"-".equals(operatorLoginAccount)) {
                return operatorLoginAccount;
            }
        }
        return targetLoginAccount;
    }

    private void assertNotSelf(Long targetAccountId, String message) {
        InternalAuthAccount operator = currentOperator();
        if (operator != null && Objects.equals(operator.getAccountId(), targetAccountId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
        }
    }

    private Long currentOperatorId() {
        InternalAuthAccount operator = currentOperator();
        return operator == null ? null : operator.getAccountId();
    }

    private InternalAuthAccount currentOperator() {
        return InternalAuthContextHolder.get();
    }

    private String clientIpFallback() {
        return "-";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
