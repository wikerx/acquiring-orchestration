package com.scott.payment.component.db.auth.service.impl;

import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.dto.AuthPasswordChangeRequest;
import com.scott.payment.component.db.auth.dto.AuthProfileUpdateRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendResponse;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountRoleDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统认证服务测试，覆盖管理端和商户端共用的登录会话闲置超时规则。
 * @status : create
 */
class SystemAuthServiceImplTest {

    /**
     * 系统管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String RAW_TOKEN = "token-for-test";

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private SysAppMapper sysAppMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private SysUserMapper sysUserMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private SysAccountMapper sysAccountMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private SysAccountRoleMapper sysAccountRoleMapper;
    /**
     * 系统角色 Mapper，用于验证登录响应中的角色名称展示。
     */
    private SysRoleMapper sysRoleMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private SysLoginSessionMapper sysLoginSessionMapper;
    /**
     * 登录页图形验证码 Mapper，用于验证验证码生成与校验规则。
     */
    private SysVerifyCodeMapper sysVerifyCodeMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
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
        SysMerchantUserMapper sysMerchantUserMapper = mock(SysMerchantUserMapper.class);
        SysMerchantUserRoleMapper sysMerchantUserRoleMapper = mock(SysMerchantUserRoleMapper.class);
        SysLoginLogMapper sysLoginLogMapper = mock(SysLoginLogMapper.class);
        sysLoginSessionMapper = mock(SysLoginSessionMapper.class);
        sysVerifyCodeMapper = mock(SysVerifyCodeMapper.class);
        SysAccountMfaMapper sysAccountMfaMapper = mock(SysAccountMfaMapper.class);
        SysAccountMfaTokenMapper sysAccountMfaTokenMapper = mock(SysAccountMfaTokenMapper.class);
        SysAccountMfaLogMapper sysAccountMfaLogMapper = mock(SysAccountMfaLogMapper.class);
        BaseMerchantInfoMapper baseMerchantInfoMapper = mock(BaseMerchantInfoMapper.class);

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
                baseMerchantInfoMapper
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

    private SysAppDO adminApp() {
        SysAppDO app = new SysAppDO();
        app.setId(1L);
        app.setAppCode(AuthConstants.APP_ADMIN);
        app.setAppName("Admin");
        app.setStatus(AuthConstants.ENABLED);
        app.setDeleted(AuthConstants.NOT_DELETED);
        return app;
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
