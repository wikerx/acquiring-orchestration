package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.constant.SystemConfigKeys;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantPrimaryAccountProvisioningService
 * @date : 2026-08-05 00:00
 * @email : scott_x@163.com
 * @description : 管理端商户开户应用服务，负责在商户资料事务中创建独立管理员角色、主账号和强制 MFA，并在提交后发送开户通知
 * @status : create
 */
@Slf4j
@Service
public class AdminMerchantPrimaryAccountProvisioningService {

    private static final String ACCOUNT_CREATED_TEMPLATE = "MERCHANT_ACCOUNT_CREATED";
    private static final String ACCOUNT_CREATED_SCENE = "ACCOUNT_CREATED";
    private static final String ROLE_TYPE_SYSTEM = "SYSTEM";
    private static final String DATA_SCOPE_SELF = "SELF";
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
    private final AdminEmailService emailService;
    private final AdminConfigService configService;

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
        SysAppDO app = merchantApp();
        String loginAccount = merchant.getContactEmail().trim().toLowerCase();
        assertAccountAvailable(app.getId(), loginAccount);
        String roleCode = AuthConstants.DEFAULT_MERCHANT_ROLE + "_" + merchant.getMerchantId();
        assertRoleAvailable(app.getId(), roleCode);

        LocalDateTime now = LocalDateTime.now();
        SysRoleDO role = createAdministratorRole(app, merchant, roleCode, now);
        grantMerchantApplication(app.getId(), role.getId(), now);
        grantMerchantScope(app.getId(), merchant.getMerchantId(), now);

        SysUserDO user = createUser(merchant, now);
        String initialPassword = generateInitialPassword();
        SysAccountDO account = createAccount(app, merchant, user, loginAccount, initialPassword, now);
        createRequiredMfa(app, merchant, account, now);
        SysMerchantUserDO merchantUser = createMerchantUser(merchant, user, account, now);
        bindAdministratorRole(app, merchant, merchantUser, role, now);
        sendAccountCreatedNoticeAfterCommit(merchant, account, initialPassword);
    }

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

    private void assertAccountAvailable(Long appId, String loginAccount) {
        Long count = accountMapper.selectCount(Wrappers.<SysAccountDO>lambdaQuery()
                .eq(SysAccountDO::getAppId, appId)
                .eq(SysAccountDO::getLoginAccount, loginAccount)
                .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "联系人邮箱已绑定其他商户系统账号");
        }
    }

    private void assertRoleAvailable(Long appId, String roleCode) {
        Long count = roleMapper.selectCount(Wrappers.<SysRoleDO>lambdaQuery()
                .eq(SysRoleDO::getAppId, appId)
                .eq(SysRoleDO::getRoleCode, roleCode)
                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户管理员角色已存在");
        }
    }

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

    private SysAccountDO createAccount(SysAppDO app,
                                       BaseMerchantInfoDO merchant,
                                       SysUserDO user,
                                       String loginAccount,
                                       String initialPassword,
                                       LocalDateTime now) {
        String salt = PasswordHashUtils.generateSalt();
        SysAccountDO account = new SysAccountDO();
        account.setAppId(app.getId());
        account.setUserId(user.getId());
        account.setMerchantId(merchant.getMerchantId());
        account.setLoginAccount(loginAccount);
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(initialPassword, salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setMobile(trim(merchant.getContactPhone()));
        account.setEmail(loginAccount);
        account.setMfaEnabled(AuthConstants.DISABLED);
        account.setPasswordExpired(AuthConstants.DISABLED);
        account.setPasswordUpdatedAt(now);
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

    private SysMerchantUserDO createMerchantUser(BaseMerchantInfoDO merchant,
                                                  SysUserDO user,
                                                  SysAccountDO account,
                                                  LocalDateTime now) {
        SysMerchantUserDO merchantUser = new SysMerchantUserDO();
        merchantUser.setMerchantInfoId(merchant.getId());
        merchantUser.setMerchantId(merchant.getMerchantId());
        merchantUser.setUserId(user.getId());
        merchantUser.setAccountId(account.getId());
        merchantUser.setLoginAccount(account.getLoginAccount());
        merchantUser.setRealName(user.getRealName());
        merchantUser.setStatus(AuthConstants.ENABLED);
        merchantUser.setCreatedAt(now);
        merchantUser.setUpdatedAt(now);
        merchantUser.setDeleted(AuthConstants.NOT_DELETED);
        merchantUserMapper.insert(merchantUser);
        return merchantUser;
    }

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

    private void sendAccountCreatedNoticeAfterCommit(BaseMerchantInfoDO merchant,
                                                     SysAccountDO account,
                                                     String initialPassword) {
        Runnable task = () -> sendAccountCreatedNotice(merchant, account, initialPassword);
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

    private void sendAccountCreatedNotice(BaseMerchantInfoDO merchant,
                                          SysAccountDO account,
                                          String initialPassword) {
        try {
            String baseUrl = merchantSystemBaseUrl();
            EmailSendRequest request = new EmailSendRequest();
            request.setAppCode(AuthConstants.APP_MERCHANT);
            request.setMerchantId(merchant.getMerchantId());
            request.setMerchantNo(merchant.getMerchantId());
            request.setMerchantName(merchant.getMerchantName());
            request.setTemplateCode(ACCOUNT_CREATED_TEMPLATE);
            request.setSceneCode(ACCOUNT_CREATED_SCENE);
            request.setLocale(MerchantLocaleSupport.normalize(merchant.getDefaultLocale()));
            request.setToEmails(List.of(account.getEmail()));
            request.setBizType(ACCOUNT_CREATED_SCENE);
            request.setBizNo(String.valueOf(account.getId()));
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("systemName", "Vexra Merchant");
            variables.put("userName", StringUtils.hasText(merchant.getContactName())
                    ? merchant.getContactName().trim()
                    : account.getLoginAccount());
            variables.put("merchantId", merchant.getMerchantId());
            variables.put("merchantName", merchant.getMerchantName());
            variables.put("loginAccount", account.getLoginAccount());
            variables.put("initialPassword", initialPassword);
            variables.put("loginUrl", baseUrl + "/login");
            variables.put("merchantSystemBaseUrl", baseUrl);
            variables.put("verifyCodeGuide", "登录页会自动加载图形验证码，请输入图片中的验证码后继续登录。");
            variables.put("mfaGuide", "首次登录时请按页面提示完成多因素认证（MFA）绑定。邮件不会包含 MFA 密钥、二维码或验证码。");
            request.setVariables(variables);
            emailService.sendByTemplate(request);
        } catch (RuntimeException exception) {
            log.warn("merchant primary account notice send failed, merchantId: {}, accountId: {}, exceptionType: {}",
                    merchant.getMerchantId(), account.getId(), exception.getClass().getSimpleName());
        }
    }

    private String merchantSystemBaseUrl() {
        Map<String, String> values = configService.enabledConfigValues(Set.of(SystemConfigKeys.MERCHANT_FRONTEND_BASE_URL));
        String configured = values.get(SystemConfigKeys.MERCHANT_FRONTEND_BASE_URL);
        String baseUrl = StringUtils.hasText(configured) ? configured.trim() : "http://127.0.0.1:5174";
        return baseUrl.replaceAll("/+$", "");
    }

    private String generateInitialPassword() {
        byte[] bytes = new byte[18];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void requireProvisioningFields(BaseMerchantInfoDO merchant) {
        if (merchant == null || merchant.getId() == null || !StringUtils.hasText(merchant.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "商户主键和商户号不能为空");
        }
        if (!StringUtils.hasText(merchant.getContactEmail())) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "商户联系人邮箱不能为空");
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
