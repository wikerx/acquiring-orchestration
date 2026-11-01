package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.constant.SystemConfigKeys;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysEmailVerificationRequestDO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserRoleDO;
import com.scott.payment.component.db.auth.entity.SysMerchantMenuGrantDO;
import com.scott.payment.component.db.auth.entity.SysMerchantPermissionGrantDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.entity.SysRoleMenuDO;
import com.scott.payment.component.db.auth.entity.SysRolePermissionDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysEmailVerificationRequestMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantMenuGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantPermissionGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRolePermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.auth.support.MerchantLocaleSupport;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 管理端商户开户应用服务，负责在商户资料事务中创建独立管理员角色、主账号和强制 MFA，并在提交后发送开户通知
 */
@Service
public class AdminMerchantPrimaryAccountProvisioningService {

    private static final String ACCOUNT_ACTIVATION_TEMPLATE = "MERCHANT_ACCOUNT_ACTIVATION";
    private static final String ACCOUNT_ACTIVATION_SCENE = "ACCOUNT_ACTIVATION";
    private static final String ACCOUNT_ACTIVATION_PURPOSE = "ACCOUNT_ACTIVATION";
    private static final String VERIFICATION_STATE_PENDING = "PENDING";
    private static final int EMAIL_CODE_TTL_MINUTES = 30;
    private static final String ACCOUNT_LOGIN_SEPARATOR = "_";
    private static final String CODE_DIGITS = "23456789";
    private static final String CODE_LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    private static final String CODE_UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String CODE_CHARACTERS = CODE_DIGITS + CODE_LOWERCASE + CODE_UPPERCASE;
    private static final DateTimeFormatter EMAIL_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ROLE_TYPE_SYSTEM = "SYSTEM";
    private static final String DATA_SCOPE_SELF = "SELF";
    /**
     * 密码学安全随机数生成器，用于生成一次性 AES 密钥和 GCM IV。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SysAppMapper appMapper;
    private final SysUserMapper userMapper;
    private final SysAccountMapper accountMapper;
    private final SysAccountMfaMapper accountMfaMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysMerchantUserMapper merchantUserMapper;
    private final SysMerchantUserRoleMapper merchantUserRoleMapper;
    private final SysMerchantMenuGrantMapper merchantMenuGrantMapper;
    private final SysMerchantPermissionGrantMapper merchantPermissionGrantMapper;
    private final SysEmailVerificationRequestMapper emailVerificationRequestMapper;
    private final AdminEmailService emailService;
    private final AdminConfigService configService;

    /**
     * 创建管理端商户主账户开通服务。
     */
    public AdminMerchantPrimaryAccountProvisioningService(
            SysAppMapper appMapper,
            SysUserMapper userMapper,
            SysAccountMapper accountMapper,
            SysAccountMfaMapper accountMfaMapper,
            SysRoleMapper roleMapper,
            SysMenuMapper menuMapper,
            SysPermissionMapper permissionMapper,
            SysRoleMenuMapper roleMenuMapper,
            SysRolePermissionMapper rolePermissionMapper,
            SysMerchantUserMapper merchantUserMapper,
            SysMerchantUserRoleMapper merchantUserRoleMapper,
            SysMerchantMenuGrantMapper merchantMenuGrantMapper,
            SysMerchantPermissionGrantMapper merchantPermissionGrantMapper,
            SysEmailVerificationRequestMapper emailVerificationRequestMapper,
            AdminEmailService emailService,
            AdminConfigService configService) {
        this.appMapper = appMapper;
        this.userMapper = userMapper;
        this.accountMapper = accountMapper;
        this.accountMfaMapper = accountMfaMapper;
        this.roleMapper = roleMapper;
        this.menuMapper = menuMapper;
        this.permissionMapper = permissionMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.merchantUserMapper = merchantUserMapper;
        this.merchantUserRoleMapper = merchantUserRoleMapper;
        this.merchantMenuGrantMapper = merchantMenuGrantMapper;
        this.merchantPermissionGrantMapper = merchantPermissionGrantMapper;
        this.emailVerificationRequestMapper = emailVerificationRequestMapper;
        this.emailService = emailService;
        this.configService = configService;
    }

    /**
     * 创建商户主账号。账号、角色和商户资料共享事务，邮件仅在事务提交后进入可靠发送队列。
     *
     * @param merchant 已持久化且已生成主键的商户资料
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void provision(BaseMerchantInfoDO merchant) {
        requireProvisioningFields(merchant);
        if (hasProvisionedAccount(merchant.getMerchantId())) {
            return;
        }
        SysAppDO app = merchantApp();
        String displayLoginAccount = merchant.getContactEmail().trim().toLowerCase();
        String accountLoginName = toAccountLoginName(merchant.getMerchantId(), displayLoginAccount);
        assertAccountAvailable(app.getId(), accountLoginName);
        String roleCode = AuthConstants.DEFAULT_MERCHANT_ROLE + "_" + merchant.getMerchantId();
        assertRoleAvailable(app.getId(), roleCode);

        LocalDateTime now = LocalDateTime.now();
        SysRoleDO role = createAdministratorRole(app, merchant, roleCode, now);
        grantMerchantApplication(app.getId(), role.getId(), now);
        grantMerchantScope(app.getId(), merchant.getMerchantId(), now);

        SysUserDO user = createUser(merchant, now);
        SysAccountDO account = createAccount(app, merchant, user, accountLoginName, now);
        createRequiredMfa(app, merchant, account, now);
        SysMerchantUserDO merchantUser = createMerchantUser(merchant, user, account, displayLoginAccount, now);
        bindAdministratorRole(app, merchant, merchantUser, role, now);
        createActivationRequestAndScheduleEmail(merchant, account, displayLoginAccount, now);
    }

    /**
     * 判断商户主账号是否已创建，保证重复激活不会重复创建用户、角色或发送开户通知。
     *
     * @param merchantId 平台商户号
     * @return true 表示已存在未删除商户账号
     */
    private boolean hasProvisionedAccount(String merchantId) {
        Long count = accountMapper.selectCount(Wrappers.<SysAccountDO>lambdaQuery()
                .eq(SysAccountDO::getMerchantId, merchantId)
                .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED));
        return count != null && count > 0;
    }

    /**
     * 查询商户允许访问的后台应用。
     */
    private SysAppDO merchantApp() {
        SysAppDO app = appMapper.selectOne(Wrappers.<SysAppDO>lambdaQuery()
                .eq(SysAppDO::getAppCode, AuthConstants.APP_MERCHANT)
                .eq(SysAppDO::getStatus, AuthConstants.ENABLED)
                .eq(SysAppDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (app == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户系统应用不存在或未启用");
        }
        return app;
    }

    /**
     * 校验指定管理账户处于可操作状态。
     */
    private void assertAccountAvailable(Long appId, String loginAccount) {
        Long count = accountMapper.selectCount(Wrappers.<SysAccountDO>lambdaQuery()
                .eq(SysAccountDO::getAppId, appId)
                .eq(SysAccountDO::getLoginAccount, loginAccount)
                .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "联系人邮箱已绑定其他商户系统账号");
        }
    }

    /**
     * 校验指定角色处于可授权状态。
     */
    private void assertRoleAvailable(Long appId, String roleCode) {
        Long count = roleMapper.selectCount(Wrappers.<SysRoleDO>lambdaQuery()
                .eq(SysRoleDO::getAppId, appId)
                .eq(SysRoleDO::getRoleCode, roleCode)
                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户管理员角色已存在");
        }
    }

    /**
     * 创建商户管理员角色并授予默认权限。
     */
    private SysRoleDO createAdministratorRole(SysAppDO app,
                                               BaseMerchantInfoDO merchant,
                                               String roleCode,
                                               LocalDateTime now) {
        SysRoleDO role = new SysRoleDO();
        role.setAppId(app.getId());
        role.setRoleCode(roleCode);
        role.setRoleName("管理员");
        role.setMerchantId(merchant.getMerchantId());
        role.setRoleType(ROLE_TYPE_SYSTEM);
        role.setDataScope(DATA_SCOPE_SELF);
        role.setDescription("商户端默认管理员角色，权限上限为平台授权给当前商户的菜单和功能");
        role.setStatus(AuthConstants.ENABLED);
        role.setSortNo(1);
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setDeleted(AuthConstants.NOT_DELETED);
        roleMapper.insert(role);
        return role;
    }

    /**
     * 为商户授予允许访问的后台应用。
     */
    private void grantMerchantApplication(Long appId, Long roleId, LocalDateTime now) {
        List<SysMenuDO> menus = menuMapper.selectList(Wrappers.<SysMenuDO>lambdaQuery()
                .eq(SysMenuDO::getAppId, appId)
                .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                .eq(SysMenuDO::getDeleted, AuthConstants.NOT_DELETED));
        for (SysMenuDO menu : menus) {
            SysRoleMenuDO relation = new SysRoleMenuDO();
            relation.setAppId(appId);
            relation.setRoleId(roleId);
            relation.setMenuId(menu.getId());
            relation.setCreatedAt(now);
            relation.setDeleted(AuthConstants.NOT_DELETED);
            roleMenuMapper.insert(relation);
        }
        List<SysPermissionDO> permissions = permissionMapper.selectList(Wrappers.<SysPermissionDO>lambdaQuery()
                .eq(SysPermissionDO::getAppId, appId)
                .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED));
        for (SysPermissionDO permission : permissions) {
            SysRolePermissionDO relation = new SysRolePermissionDO();
            relation.setAppId(appId);
            relation.setRoleId(roleId);
            relation.setPermissionId(permission.getId());
            relation.setCreatedAt(now);
            relation.setDeleted(AuthConstants.NOT_DELETED);
            rolePermissionMapper.insert(relation);
        }
    }

    /**
     * 创建平台授予当前商户的菜单和权限上限。
     *
     * <p>商户端最终权限是商户授权与角色授权的交集，因此新开户必须同时保存两层关系。
     * 所有记录与商户资料、主账号和角色共享本地事务。</p>
     *
     * @param appId 商户系统应用 ID
     * @param merchantId 新开户商户号
     * @param now 统一开户时间
     */
    private void grantMerchantScope(Long appId, String merchantId, LocalDateTime now) {
        List<SysMenuDO> menus = menuMapper.selectList(Wrappers.<SysMenuDO>lambdaQuery()
                .eq(SysMenuDO::getAppId, appId)
                .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                .eq(SysMenuDO::getDeleted, AuthConstants.NOT_DELETED));
        for (SysMenuDO menu : menus) {
            SysMerchantMenuGrantDO grant = new SysMerchantMenuGrantDO();
            grant.setMerchantId(merchantId);
            grant.setAppId(appId);
            grant.setMenuId(menu.getId());
            grant.setGrantSource("SYSTEM");
            grant.setStatus(AuthConstants.ENABLED);
            grant.setCreatedAt(now);
            grant.setUpdatedAt(now);
            grant.setDeleted(AuthConstants.NOT_DELETED);
            merchantMenuGrantMapper.insert(grant);
        }
        List<SysPermissionDO> permissions = permissionMapper.selectList(Wrappers.<SysPermissionDO>lambdaQuery()
                .eq(SysPermissionDO::getAppId, appId)
                .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED));
        for (SysPermissionDO permission : permissions) {
            SysMerchantPermissionGrantDO grant = new SysMerchantPermissionGrantDO();
            grant.setMerchantId(merchantId);
            grant.setAppId(appId);
            grant.setPermissionId(permission.getId());
            grant.setGrantSource("SYSTEM");
            grant.setStatus(AuthConstants.ENABLED);
            grant.setCreatedAt(now);
            grant.setUpdatedAt(now);
            grant.setDeleted(AuthConstants.NOT_DELETED);
            merchantPermissionGrantMapper.insert(grant);
        }
    }

    /**
     * 根据商户联系人资料创建统一用户记录。
     */
    private SysUserDO createUser(BaseMerchantInfoDO merchant, LocalDateTime now) {
        SysUserDO user = new SysUserDO();
        user.setUserType(AuthConstants.USER_TYPE_MERCHANT);
        user.setRealName(StringUtils.hasText(merchant.getContactName())
                ? merchant.getContactName().trim()
                : merchant.getMerchantName());
        user.setMobile(trim(merchant.getContactPhone()));
        user.setEmail(merchant.getContactEmail().trim().toLowerCase());
        user.setCountryCode(trim(merchant.getCountryCode()));
        user.setLanguage(MerchantLocaleSupport.normalize(merchant.getDefaultLocale()));
        user.setTimezone(trim(merchant.getTimezone()));
        user.setStatus(AuthConstants.ENABLED);
        user.setRemark("商户主账号");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleted(AuthConstants.NOT_DELETED);
        userMapper.insert(user);
        return user;
    }

    /**
     * 创建账户。
     */
    private SysAccountDO createAccount(SysAppDO app,
                                       BaseMerchantInfoDO merchant,
                                       SysUserDO user,
                                       String loginAccount,
                                       LocalDateTime now) {
        String salt = PasswordHashUtils.generateSalt();
        String placeholderPassword = LoginTokenUtils.generateToken();
        SysAccountDO account = new SysAccountDO();
        account.setAppId(app.getId());
        account.setUserId(user.getId());
        account.setMerchantId(merchant.getMerchantId());
        account.setLoginAccount(loginAccount);
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(placeholderPassword, salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setMobile(trim(merchant.getContactPhone()));
        account.setEmail(merchant.getContactEmail().trim().toLowerCase());
        account.setMfaEnabled(AuthConstants.DISABLED);
        account.setPasswordExpired(AuthConstants.DISABLED);
        account.setPasswordUpdatedAt(now);
        account.setActivationStatus(AuthConstants.ACTIVATION_STATUS_PENDING);
        account.setActivatedAt(null);
        account.setEmailVerifiedAt(null);
        account.setFailedLoginCount(0);
        account.setLocked(AuthConstants.DISABLED);
        account.setStatus(AuthConstants.ENABLED);
        account.setRemark("商户主账号");
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setDeleted(AuthConstants.NOT_DELETED);
        accountMapper.insert(account);
        return account;
    }

    /**
     * 为商户主账户创建待绑定的强制 TOTP 配置。
     */
    private void createRequiredMfa(SysAppDO app,
                                   BaseMerchantInfoDO merchant,
                                   SysAccountDO account,
                                   LocalDateTime now) {
        SysAccountMfaDO mfa = new SysAccountMfaDO();
        mfa.setAppId(app.getId());
        mfa.setAccountId(account.getId());
        mfa.setUserId(account.getUserId());
        mfa.setMerchantId(merchant.getMerchantId());
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_PENDING_BIND);
        mfa.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        mfa.setIssuer("Vexra Merchant");
        mfa.setAccountLabel(merchant.getMerchantId() + ":" + account.getLoginAccount());
        mfa.setFailedVerifyCount(0);
        mfa.setCreatedAt(now);
        mfa.setUpdatedAt(now);
        mfa.setDeleted(AuthConstants.NOT_DELETED);
        accountMfaMapper.insert(mfa);
    }

    /**
     * 创建商户用户与统一账户的关联记录。
     */
    private SysMerchantUserDO createMerchantUser(BaseMerchantInfoDO merchant,
                                                  SysUserDO user,
                                                  SysAccountDO account,
                                                  String displayLoginAccount,
                                                  LocalDateTime now) {
        SysMerchantUserDO merchantUser = new SysMerchantUserDO();
        merchantUser.setMerchantInfoId(merchant.getId());
        merchantUser.setMerchantId(merchant.getMerchantId());
        merchantUser.setUserId(user.getId());
        merchantUser.setAccountId(account.getId());
        merchantUser.setLoginAccount(displayLoginAccount);
        merchantUser.setRealName(user.getRealName());
        merchantUser.setStatus(AuthConstants.ENABLED);
        merchantUser.setCreatedAt(now);
        merchantUser.setUpdatedAt(now);
        merchantUser.setDeleted(AuthConstants.NOT_DELETED);
        merchantUserMapper.insert(merchantUser);
        return merchantUser;
    }

    /**
     * 为商户管理员绑定默认角色。
     */
    private void bindAdministratorRole(SysAppDO app,
                                       BaseMerchantInfoDO merchant,
                                       SysMerchantUserDO merchantUser,
                                       SysRoleDO role,
                                       LocalDateTime now) {
        SysMerchantUserRoleDO relation = new SysMerchantUserRoleDO();
        relation.setAppId(app.getId());
        relation.setMerchantInfoId(merchant.getId());
        relation.setMerchantUserId(merchantUser.getId());
        relation.setRoleId(role.getId());
        relation.setCreatedAt(now);
        relation.setDeleted(AuthConstants.NOT_DELETED);
        merchantUserRoleMapper.insert(relation);
    }

    /**
     * 创建账号激活请求，并在事务提交后安排激活邮件。
     */
    private void createActivationRequestAndScheduleEmail(BaseMerchantInfoDO merchant,
                                                         SysAccountDO account,
                                                         String displayLoginAccount,
                                                         LocalDateTime now) {
        String verificationCode = generateEmailCode();
        String salt = PasswordHashUtils.generateSalt();
        SysEmailVerificationRequestDO request = new SysEmailVerificationRequestDO();
        request.setRequestNo(LoginTokenUtils.generateToken());
        request.setAppId(account.getAppId());
        request.setMerchantId(merchant.getMerchantId());
        request.setAccountId(account.getId());
        request.setPurpose(ACCOUNT_ACTIVATION_PURPOSE);
        request.setEmailFingerprint(emailFingerprint(account.getEmail()));
        request.setCodeSalt(salt);
        request.setCodeHash(PasswordHashUtils.hashPassword(verificationCode, salt));
        request.setExpireAt(now.plusMinutes(EMAIL_CODE_TTL_MINUTES));
        request.setVerifyCount(0);
        request.setState(VERIFICATION_STATE_PENDING);
        request.setRequestIp("internal");
        request.setLocale(MerchantLocaleSupport.normalize(merchant.getDefaultLocale()));
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        emailVerificationRequestMapper.insert(request);

        Runnable task = () -> sendAccountActivationNotice(
                merchant, account, displayLoginAccount, request, verificationCode);
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
     * 发送商户管理员账户激活通知。
     */
    private void sendAccountActivationNotice(BaseMerchantInfoDO merchant,
                                             SysAccountDO account,
                                             String displayLoginAccount,
                                             SysEmailVerificationRequestDO verificationRequest,
                                             String verificationCode) {
        String baseUrl = merchantSystemBaseUrl();
        EmailSendRequest request = new EmailSendRequest();
        request.setAppCode(AuthConstants.APP_MERCHANT);
        request.setMerchantId(merchant.getMerchantId());
        request.setMerchantNo(merchant.getMerchantId());
        request.setMerchantName(merchant.getMerchantName());
        request.setTemplateCode(ACCOUNT_ACTIVATION_TEMPLATE);
        request.setSceneCode(ACCOUNT_ACTIVATION_SCENE);
        request.setLocale(verificationRequest.getLocale());
        request.setToEmails(List.of(account.getEmail()));
        request.setBizType(ACCOUNT_ACTIVATION_PURPOSE);
        request.setBizNo(verificationRequest.getRequestNo());
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("systemName", "Vexra Merchant");
        variables.put("userName", StringUtils.hasText(merchant.getContactName())
                ? merchant.getContactName().trim()
                : displayLoginAccount);
        variables.put("merchantId", merchant.getMerchantId());
        variables.put("merchantName", merchant.getMerchantName());
        variables.put("loginAccount", displayLoginAccount);
        variables.put("verificationCode", verificationCode);
        variables.put("expireMinutes", EMAIL_CODE_TTL_MINUTES);
        variables.put("verificationUrl", activationUrl(baseUrl, verificationRequest.getRequestNo()));
        variables.put("requestTime", EMAIL_TIME_FORMATTER.format(verificationRequest.getCreatedAt()));
        variables.put("securityNotice", "en-US".equals(verificationRequest.getLocale())
                ? "If you did not request this account, ignore this email and contact Vexra support."
                : "如非本人申请此账号，请忽略本邮件并联系 Vexra 支持。");
        request.setVariables(variables);
        EmailSendResult result = emailService.sendByTemplate(request);
        if (result == null || Objects.equals(result.getSendStatus(), EmailDeliveryStatus.CLOSED.getCode())
                || Objects.equals(result.getSendStatus(), EmailDeliveryStatus.CANCELLED.getCode())) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "merchant account activation email could not be prepared");
        }
    }

    /**
     * 构建商户管理员账户激活地址。
     */
    private String activationUrl(String baseUrl, String requestNo) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/account-verification")
                .queryParam("requestId", requestNo)
                .build()
                .encode()
                .toUriString();
    }

    /**
     * 读取并校验商户后台基础地址。
     */
    private String merchantSystemBaseUrl() {
        Map<String, String> values = configService.enabledConfigValues(Set.of(SystemConfigKeys.MERCHANT_FRONTEND_BASE_URL));
        String configured = values.get(SystemConfigKeys.MERCHANT_FRONTEND_BASE_URL);
        String baseUrl = StringUtils.hasText(configured) ? configured.trim() : "http://127.0.0.1:5174";
        return baseUrl.replaceAll("/+$", "");
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
    private char randomCharacter(String characters) {
        return characters.charAt(SECURE_RANDOM.nextInt(characters.length()));
    }

    /**
     * 计算邮件发送内容指纹用于幂等控制。
     */
    private String emailFingerprint(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 生成商户主资金账户登录标识。
     */
    private String toAccountLoginName(String merchantId, String displayLoginAccount) {
        return displayLoginAccount + ACCOUNT_LOGIN_SEPARATOR + merchantId;
    }

    /**
     * 校验开通商户主账户所需的商户号和联系人邮箱。
     */
    private void requireProvisioningFields(BaseMerchantInfoDO merchant) {
        if (merchant == null || merchant.getId() == null || !StringUtils.hasText(merchant.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "商户主键和商户号不能为空");
        }
        if (!StringUtils.hasText(merchant.getContactEmail())) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "商户联系人邮箱不能为空");
        }
    }

    /**
     * 去除文本首尾空白并保留空值语义。
     */
    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
