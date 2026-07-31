package com.scott.payment.component.db.auth.service.impl;

import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthLoginRequest;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.dto.AuthPasswordChangeRequest;
import com.scott.payment.component.db.auth.dto.AuthProfileUpdateRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendResponse;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
import com.scott.payment.component.db.auth.entity.SysAccountRoleDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.entity.SysVerifyCodeDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaLogMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaTokenMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginLogMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantMenuGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantPermissionGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRolePermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.auth.mapper.SysVerifyCodeMapper;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SystemAuthServiceImplTest
 * @date : 2026-06-26 15:24
 * @email : scott_x@163.com
 * @description : System Auth Service Impl Test 服务实现，位于 公共组件库，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
class SystemAuthServiceImplTest {

    /**
     * RAW TOKEN，用于保存 System Auth Service Impl Test 中与 rawtoken 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String RAW_TOKEN = "token-for-test";

    /**
     * sys App Mapper 依赖，用于 System Auth Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private SysAppMapper sysAppMapper;
    /**
     * sys User Mapper 依赖，用于 System Auth Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private SysUserMapper sysUserMapper;
    /**
     * sys Account Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private SysAccountMapper sysAccountMapper;
    /**
     * sys Account Role Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private SysAccountRoleMapper sysAccountRoleMapper;
    /**
     * 系统角色 Mapper，用于验证登录响应中的角色名称展示。
     */
    private SysRoleMapper sysRoleMapper;
    /**
     * sys Login Session Mapper 依赖，用于 System Auth Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private SysLoginSessionMapper sysLoginSessionMapper;
    /**
     * 登录页图形验证码 Mapper，用于验证验证码生成与校验规则。
     */
    private SysVerifyCodeMapper sysVerifyCodeMapper;
    /**
     * 系统账号 MFA Mapper，用于验证停用后的商户账号登录不再触发 OTP。
     */
    private SysAccountMfaMapper sysAccountMfaMapper;
    /**
     * 商户用户 Mapper，用于商户端登录账号解析。
     */
    private SysMerchantUserMapper sysMerchantUserMapper;
    /**
     * 商户用户角色 Mapper，用于商户端登录响应组装。
     */
    private SysMerchantUserRoleMapper sysMerchantUserRoleMapper;
    /**
     * 商户基础信息 Mapper，用于商户端登录商户有效性校验。
     */
    private BaseMerchantInfoMapper baseMerchantInfoMapper;
    /**
     * 商户基础资料缓存服务，用于商户端登录商户有效性校验。
     */
    private MerchantRuntimeProfileCacheService merchantRuntimeProfileCacheService;
    /**
     * system Auth Service 依赖，用于 System Auth Service Impl Test 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private SystemAuthServiceImpl systemAuthService;

    /**
     * 构造认证服务的 mapper mock，测试只关注登录会话生命周期。
     */
    @BeforeEach
    void setUp() {
        sysAppMapper = mock(SysAppMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        sysAccountMapper = mock(SysAccountMapper.class);
        sysRoleMapper = mock(SysRoleMapper.class);
        sysAccountRoleMapper = mock(SysAccountRoleMapper.class);
        SysRoleMenuMapper sysRoleMenuMapper = mock(SysRoleMenuMapper.class);
        SysRolePermissionMapper sysRolePermissionMapper = mock(SysRolePermissionMapper.class);
        SysMenuMapper sysMenuMapper = mock(SysMenuMapper.class);
        SysPermissionMapper sysPermissionMapper = mock(SysPermissionMapper.class);
        SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper = mock(SysMerchantMenuGrantMapper.class);
        SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper = mock(SysMerchantPermissionGrantMapper.class);
        sysMerchantUserMapper = mock(SysMerchantUserMapper.class);
        sysMerchantUserRoleMapper = mock(SysMerchantUserRoleMapper.class);
        SysLoginLogMapper sysLoginLogMapper = mock(SysLoginLogMapper.class);
        sysLoginSessionMapper = mock(SysLoginSessionMapper.class);
        sysVerifyCodeMapper = mock(SysVerifyCodeMapper.class);
        sysAccountMfaMapper = mock(SysAccountMfaMapper.class);
        SysAccountMfaTokenMapper sysAccountMfaTokenMapper = mock(SysAccountMfaTokenMapper.class);
        SysAccountMfaLogMapper sysAccountMfaLogMapper = mock(SysAccountMfaLogMapper.class);
        baseMerchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        merchantRuntimeProfileCacheService = mock(MerchantRuntimeProfileCacheService.class);

        systemAuthService = new SystemAuthServiceImpl(
                sysAppMapper,
                sysUserMapper,
                sysAccountMapper,
                sysRoleMapper,
                sysAccountRoleMapper,
                sysRoleMenuMapper,
                sysRolePermissionMapper,
                sysMenuMapper,
                sysPermissionMapper,
                sysMerchantMenuGrantMapper,
                sysMerchantPermissionGrantMapper,
                sysMerchantUserMapper,
                sysMerchantUserRoleMapper,
                sysLoginLogMapper,
                sysLoginSessionMapper,
                sysVerifyCodeMapper,
                sysAccountMfaMapper,
                sysAccountMfaTokenMapper,
                sysAccountMfaLogMapper,
                baseMerchantInfoMapper,
                merchantRuntimeProfileCacheService
        );
    }

    /**
     * 有效会话被访问时应刷新最后活跃时间，避免用户持续操作时被误踢。
     */
    @Test
    void currentUserShouldTouchActiveSession() {
        SysLoginSessionDO session = activeSession(LocalDateTime.now().minusMinutes(5));
        when(sysAppMapper.selectOne(any())).thenReturn(adminApp());
        when(sysLoginSessionMapper.selectOne(any())).thenReturn(session);
        when(sysAccountMapper.selectById(eq(10L))).thenReturn(adminAccount());
        when(sysUserMapper.selectById(eq(20L))).thenReturn(adminUser());
        when(sysAccountRoleMapper.selectList(any())).thenReturn(List.of(adminAccountRole()));
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(adminRole()));

        AuthLoginResponse response = systemAuthService.currentUser(AuthConstants.APP_ADMIN, "Bearer " + RAW_TOKEN);

        assertThat(response.getAccount().getLoginAccount()).isEqualTo("admin");
        assertThat(response.getAccount().getMobile()).isEqualTo("13800138000");
        assertThat(response.getAccount().getEmail()).isEqualTo("admin@example.com");
        assertThat(response.getAccount().getRoleNames()).containsExactly("超级管理员");
        assertThat(response.getAccount().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 7, 11, 10, 20, 30));
        assertThat(response.getRoles()).containsExactly("SUPER_ADMIN");
        assertThat(session.getLogout()).isEqualTo(0);
        assertThat(session.getUpdatedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
        verify(sysLoginSessionMapper).updateById(session);
    }

    /**
     * 超过 30 分钟无操作的会话再次访问时应直接失效并返回 401 业务码。
     */
    @Test
    void currentUserShouldRejectIdleExpiredSession() {
        SysLoginSessionDO session = activeSession(LocalDateTime.now().minusMinutes(31));
        when(sysAppMapper.selectOne(any())).thenReturn(adminApp());
        when(sysLoginSessionMapper.selectOne(any())).thenReturn(session);

        assertThatThrownBy(() -> systemAuthService.currentUser(AuthConstants.APP_ADMIN, RAW_TOKEN))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.UNAUTHORIZED.getCode());
        assertThat(session.getLogout()).isEqualTo(1);
        assertThat(session.getLogoutAt()).isNotNull();
        verify(sysLoginSessionMapper).updateById(session);
    }

    /**
     * 当前账号修改个人资料时，应只更新本人用户主体和账号冗余联系方式。
     */
    @Test
    void updateCurrentProfileShouldPersistCurrentUserProfile() {
        SysLoginSessionDO session = activeSession(LocalDateTime.now().minusMinutes(5));
        SysUserDO user = adminUser();
        SysAccountDO account = adminAccount();
        AuthProfileUpdateRequest request = new AuthProfileUpdateRequest();
        request.setNickname("Scott Admin");
        request.setMobile("18777777777");
        request.setEmail("scott@vip.com");
        when(sysAppMapper.selectOne(any())).thenReturn(adminApp());
        when(sysLoginSessionMapper.selectOne(any())).thenReturn(session);
        when(sysAccountMapper.selectById(eq(10L))).thenReturn(account);
        when(sysUserMapper.selectById(eq(20L))).thenReturn(user);
        when(sysAccountRoleMapper.selectList(any())).thenReturn(List.of(adminAccountRole()));
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(adminRole()));

        AuthLoginResponse response = systemAuthService.updateCurrentProfile(AuthConstants.APP_ADMIN, "Bearer " + RAW_TOKEN, request);

        assertThat(response.getAccount().getRealName()).isEqualTo("Admin");
        assertThat(response.getAccount().getNickname()).isEqualTo("Scott Admin");
        assertThat(response.getAccount().getMobile()).isEqualTo("18777777777");
        assertThat(response.getAccount().getEmail()).isEqualTo("scott@vip.com");
        assertThat(user.getNickname()).isEqualTo("Scott Admin");
        assertThat(user.getUpdatedBy()).isEqualTo(10L);
        assertThat(account.getMobile()).isEqualTo("18777777777");
        assertThat(account.getEmail()).isEqualTo("scott@vip.com");
        verify(sysUserMapper).updateById(user);
        verify(sysAccountMapper).updateById(account);
    }

    /**
     * 修改密码必须先校验旧密码，旧密码错误时不得改写密码哈希。
     */
    @Test
    void changeCurrentPasswordShouldRejectWrongOldPassword() {
        SysLoginSessionDO session = activeSession(LocalDateTime.now().minusMinutes(5));
        SysAccountDO account = adminAccountWithPassword("Old@123456");
        AuthPasswordChangeRequest request = passwordChangeRequest("Wrong@123456", "New@123456");
        when(sysAppMapper.selectOne(any())).thenReturn(adminApp());
        when(sysLoginSessionMapper.selectOne(any())).thenReturn(session);
        when(sysAccountMapper.selectById(eq(10L))).thenReturn(account);

        assertThatThrownBy(() -> systemAuthService.changeCurrentPassword(AuthConstants.APP_ADMIN, "Bearer " + RAW_TOKEN, request))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.UNAUTHORIZED.getCode());
    }

    /**
     * 修改密码成功后应重新生成密码盐和哈希，并保留 PBKDF2 算法标识。
     */
    @Test
    void changeCurrentPasswordShouldRefreshSaltAndHash() {
        SysLoginSessionDO session = activeSession(LocalDateTime.now().minusMinutes(5));
        SysAccountDO account = adminAccountWithPassword("Old@123456");
        String oldSalt = account.getPasswordSalt();
        AuthPasswordChangeRequest request = passwordChangeRequest("Old@123456", "New@123456");
        when(sysAppMapper.selectOne(any())).thenReturn(adminApp());
        when(sysLoginSessionMapper.selectOne(any())).thenReturn(session);
        when(sysAccountMapper.selectById(eq(10L))).thenReturn(account);

        systemAuthService.changeCurrentPassword(AuthConstants.APP_ADMIN, "Bearer " + RAW_TOKEN, request);

        assertThat(account.getPasswordSalt()).isNotEqualTo(oldSalt);
        assertThat(PasswordHashUtils.matches("New@123456", account.getPasswordSalt(), account.getPasswordHash())).isTrue();
        assertThat(account.getPasswordAlgo()).isEqualTo(PasswordHashUtils.ALGORITHM);
        assertThat(account.getPasswordExpired()).isEqualTo(AuthConstants.DISABLED);
        assertThat(account.getPasswordUpdatedAt()).isNotNull();
        verify(sysAccountMapper).updateById(account);
    }

    /**
     * 登录前验证码应生成页面可见的图形验证码，不依赖账号、邮箱或短信通道。
     */
    @Test
    void sendLoginVerifyCodeShouldReturnCaptchaImageWithoutAccount() {
        when(sysAppMapper.selectOne(any())).thenReturn(adminApp());
        when(sysVerifyCodeMapper.selectCount(any())).thenReturn(0L);
        AuthVerifyCodeSendRequest request = new AuthVerifyCodeSendRequest();
        request.setScene("LOGIN");

        AuthVerifyCodeSendResponse response = systemAuthService.sendLoginVerifyCode(AuthConstants.APP_ADMIN, request, "127.0.0.1");

        assertThat(response.getReceiverType()).isEqualTo("CAPTCHA");
        assertThat(response.getMaskedReceiver()).isEqualTo("页面图形验证码");
        assertThat(response.getCaptchaImage()).startsWith("data:image/png;base64,");
        assertThat(response.getExpireSeconds()).isEqualTo(300);
        verify(sysVerifyCodeMapper).insert(argThat((SysVerifyCodeDO row) ->
                "LOGIN".equals(row.getScene())
                        && "CAPTCHA".equals(row.getReceiverType())
                        && "LOGIN_PAGE".equals(row.getReceiver())
                        && "PAGE_CAPTCHA".equals(row.getSendChannel())
                        && row.getCodeSalt() != null
                        && row.getCodeHash() != null
        ));
    }

    /**
     * 商户账号 OTP 被停用后，登录应直接签发会话而不是继续返回 MFA_REQUIRED。
     */
    @Test
    void merchantLoginShouldNotRequireMfaWhenAccountMfaDisabled() {
        String password = "Merchant@123456";
        SysAppDO app = merchantApp();
        SysAccountDO account = merchantAccountWithPassword(password);
        SysVerifyCodeDO verifyCode = validLoginCaptcha();
        when(sysAppMapper.selectOne(any())).thenReturn(app);
        when(merchantRuntimeProfileCacheService.findRuntimeProfile("200045")).thenReturn(activeMerchantInfo());
        when(sysMerchantUserMapper.selectOne(any())).thenReturn(merchantAccountUser());
        when(sysAccountMapper.selectById(eq(60L))).thenReturn(account);
        when(sysVerifyCodeMapper.selectById(eq(900L))).thenReturn(verifyCode);
        when(sysUserMapper.selectById(eq(70L))).thenReturn(merchantUser());
        when(sysAccountMfaMapper.selectOne(any())).thenReturn(disabledMerchantMfa());
        when(sysAccountRoleMapper.selectList(any())).thenReturn(List.of());
        when(sysMerchantUserRoleMapper.selectList(any())).thenReturn(List.of());

        AuthLoginResponse response = systemAuthService.login(AuthConstants.APP_MERCHANT,
                merchantLoginRequest(password), "127.0.0.1", "merchant-browser");

        assertThat(response.getLoginStatus()).isEqualTo(AuthConstants.LOGIN_STATUS_SUCCESS);
        assertThat(response.getMfaRequired()).isFalse();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getMfaPolicy()).isEqualTo(AuthConstants.MFA_POLICY_OPTIONAL);
        assertThat(response.getMfaStatus()).isEqualTo(AuthConstants.MFA_STATUS_NOT_ENABLED);
        verify(sysLoginSessionMapper).insert(any(SysLoginSessionDO.class));
    }

    private SysAppDO adminApp() {
        SysAppDO app = new SysAppDO();
        app.setId(1L);
        app.setAppCode(AuthConstants.APP_ADMIN);
        app.setAppName("Admin");
        app.setStatus(AuthConstants.ENABLED);
        app.setDeleted(AuthConstants.NOT_DELETED);
        return app;
    }

    private SysAppDO merchantApp() {
        SysAppDO app = new SysAppDO();
        app.setId(2L);
        app.setAppCode(AuthConstants.APP_MERCHANT);
        app.setAppName("Merchant");
        app.setStatus(AuthConstants.ENABLED);
        app.setDeleted(AuthConstants.NOT_DELETED);
        return app;
    }

    private MerchantRuntimeProfile activeMerchantInfo() {
        MerchantRuntimeProfile merchant = new MerchantRuntimeProfile();
        merchant.setId(50L);
        merchant.setMerchantId("200045");
        merchant.setMerchantStatus(AuthConstants.ENABLED);
        return merchant;
    }

    private SysAccountDO adminAccount() {
        SysAccountDO account = new SysAccountDO();
        account.setId(10L);
        account.setAppId(1L);
        account.setUserId(20L);
        account.setLoginAccount("admin");
        account.setMobile("13900139000");
        account.setEmail("account@example.com");
        account.setStatus(AuthConstants.ENABLED);
        account.setDeleted(AuthConstants.NOT_DELETED);
        return account;
    }

    private SysAccountDO adminAccountWithPassword(String password) {
        SysAccountDO account = adminAccount();
        String salt = PasswordHashUtils.generateSalt();
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(password, salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setPasswordExpired(AuthConstants.DISABLED);
        return account;
    }

    private SysUserDO adminUser() {
        SysUserDO user = new SysUserDO();
        user.setId(20L);
        user.setRealName("Admin");
        user.setMobile("13800138000");
        user.setEmail("admin@example.com");
        user.setCreatedAt(LocalDateTime.of(2026, 7, 11, 10, 20, 30));
        user.setStatus(AuthConstants.ENABLED);
        user.setDeleted(AuthConstants.NOT_DELETED);
        return user;
    }

    private SysAccountDO merchantAccountWithPassword(String password) {
        SysAccountDO account = new SysAccountDO();
        String salt = PasswordHashUtils.generateSalt();
        account.setId(60L);
        account.setAppId(2L);
        account.setUserId(70L);
        account.setMerchantId("200045");
        account.setLoginAccount("200045_operator");
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(password, salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setStatus(AuthConstants.ENABLED);
        account.setLocked(AuthConstants.DISABLED);
        account.setDeleted(AuthConstants.NOT_DELETED);
        return account;
    }

    private SysUserDO merchantUser() {
        SysUserDO user = new SysUserDO();
        user.setId(70L);
        user.setRealName("Merchant Operator");
        user.setStatus(AuthConstants.ENABLED);
        user.setDeleted(AuthConstants.NOT_DELETED);
        return user;
    }

    private SysMerchantUserDO merchantAccountUser() {
        SysMerchantUserDO merchantUser = new SysMerchantUserDO();
        merchantUser.setId(80L);
        merchantUser.setMerchantId("200045");
        merchantUser.setAccountId(60L);
        merchantUser.setLoginAccount("operator");
        merchantUser.setStatus(AuthConstants.ENABLED);
        merchantUser.setDeleted(AuthConstants.NOT_DELETED);
        return merchantUser;
    }

    private SysAccountMfaDO disabledMerchantMfa() {
        SysAccountMfaDO mfa = new SysAccountMfaDO();
        mfa.setAppId(2L);
        mfa.setAccountId(60L);
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_OPTIONAL);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_NOT_ENABLED);
        mfa.setDeleted(AuthConstants.NOT_DELETED);
        return mfa;
    }

    private SysVerifyCodeDO validLoginCaptcha() {
        SysVerifyCodeDO verifyCode = new SysVerifyCodeDO();
        String salt = PasswordHashUtils.generateSalt();
        verifyCode.setId(900L);
        verifyCode.setAppId(2L);
        verifyCode.setScene("LOGIN");
        verifyCode.setReceiverType("CAPTCHA");
        verifyCode.setReceiver("LOGIN_PAGE");
        verifyCode.setSendIp("127.0.0.1");
        verifyCode.setCodeSalt(salt);
        verifyCode.setCodeHash(PasswordHashUtils.hashPassword("abcde", salt));
        verifyCode.setVerifyCount(0);
        verifyCode.setUsed(AuthConstants.DISABLED);
        verifyCode.setExpireAt(LocalDateTime.now().plusMinutes(5));
        return verifyCode;
    }

    private AuthLoginRequest merchantLoginRequest(String password) {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setMerchantId("200045");
        request.setLoginAccount("operator");
        request.setPassword(password);
        request.setVerifyCodeId("900");
        request.setVerifyCode("ABCDE");
        return request;
    }

    private SysAccountRoleDO adminAccountRole() {
        SysAccountRoleDO accountRole = new SysAccountRoleDO();
        accountRole.setAppId(1L);
        accountRole.setAccountId(10L);
        accountRole.setRoleId(30L);
        accountRole.setDeleted(AuthConstants.NOT_DELETED);
        return accountRole;
    }

    private SysRoleDO adminRole() {
        SysRoleDO role = new SysRoleDO();
        role.setId(30L);
        role.setAppId(1L);
        role.setRoleCode("SUPER_ADMIN");
        role.setRoleName("超级管理员");
        role.setStatus(AuthConstants.ENABLED);
        role.setDeleted(AuthConstants.NOT_DELETED);
        return role;
    }

    private SysLoginSessionDO activeSession(LocalDateTime lastActiveAt) {
        SysLoginSessionDO session = new SysLoginSessionDO();
        session.setId(100L);
        session.setAppId(1L);
        session.setAccountId(10L);
        session.setUserId(20L);
        session.setTokenHash(LoginTokenUtils.hashToken(RAW_TOKEN));
        session.setExpireAt(LocalDateTime.now().plusHours(1));
        session.setLogout(0);
        session.setCreatedAt(lastActiveAt);
        session.setUpdatedAt(lastActiveAt);
        return session;
    }

    private AuthPasswordChangeRequest passwordChangeRequest(String oldPassword, String newPassword) {
        AuthPasswordChangeRequest request = new AuthPasswordChangeRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        return request;
    }
}
