package com.scott.payment.component.db.auth.service.impl;

import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
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
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private SysLoginSessionMapper sysLoginSessionMapper;
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
        SysRoleMapper sysRoleMapper = mock(SysRoleMapper.class);
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
        SysVerifyCodeMapper sysVerifyCodeMapper = mock(SysVerifyCodeMapper.class);
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
        when(sysAccountRoleMapper.selectList(any())).thenReturn(List.of());

        AuthLoginResponse response = systemAuthService.currentUser(AuthConstants.APP_ADMIN, "Bearer " + RAW_TOKEN);

        assertThat(response.getAccount().getLoginAccount()).isEqualTo("admin");
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
        account.setStatus(AuthConstants.ENABLED);
        account.setDeleted(AuthConstants.NOT_DELETED);
        return account;
    }

    private SysUserDO adminUser() {
        SysUserDO user = new SysUserDO();
        user.setId(20L);
        user.setRealName("Admin");
        user.setStatus(AuthConstants.ENABLED);
        user.setDeleted(AuthConstants.NOT_DELETED);
        return user;
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
}
