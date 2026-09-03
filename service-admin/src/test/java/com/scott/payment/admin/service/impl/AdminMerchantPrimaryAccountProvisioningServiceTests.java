package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.constant.SystemConfigKeys;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.component.core.auth.PasswordHashUtils;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantPrimaryAccountProvisioningServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证管理端新增商户时创建可登录的主账号并发送开户通知。
 * @status : create
 */
class AdminMerchantPrimaryAccountProvisioningServiceTests {

    @Test
    void shouldProvisionMerchantAdministratorAndSendAccountCreatedNotice() {
        SysAppMapper appMapper = mock(SysAppMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysAccountMapper accountMapper = mock(SysAccountMapper.class);
        SysAccountMfaMapper mfaMapper = mock(SysAccountMfaMapper.class);
        SysRoleMapper roleMapper = mock(SysRoleMapper.class);
        SysMenuMapper menuMapper = mock(SysMenuMapper.class);
        SysPermissionMapper permissionMapper = mock(SysPermissionMapper.class);
        SysRoleMenuMapper roleMenuMapper = mock(SysRoleMenuMapper.class);
        SysRolePermissionMapper rolePermissionMapper = mock(SysRolePermissionMapper.class);
        SysMerchantUserMapper merchantUserMapper = mock(SysMerchantUserMapper.class);
        SysMerchantUserRoleMapper merchantUserRoleMapper = mock(SysMerchantUserRoleMapper.class);
        SysMerchantMenuGrantMapper merchantMenuGrantMapper = mock(SysMerchantMenuGrantMapper.class);
        SysMerchantPermissionGrantMapper merchantPermissionGrantMapper = mock(SysMerchantPermissionGrantMapper.class);
        AdminEmailService emailService = mock(AdminEmailService.class);
        AdminConfigService configService = mock(AdminConfigService.class);

        SysAppDO merchantApp = new SysAppDO();
        merchantApp.setId(2L);
        merchantApp.setAppCode("MERCHANT");
        when(appMapper.selectOne(any())).thenReturn(merchantApp);
        when(accountMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(menuMapper.selectList(any())).thenReturn(List.of(menu(11L)));
        when(permissionMapper.selectList(any())).thenReturn(List.of(permission(21L)));
        when(configService.enabledConfigValues(any()))
                .thenReturn(Map.of(SystemConfigKeys.MERCHANT_FRONTEND_BASE_URL, "https://merchant.vexra.example"));
        assignId(userMapper, roleMapper, accountMapper, merchantUserMapper);

        AdminMerchantPrimaryAccountProvisioningService service =
                new AdminMerchantPrimaryAccountProvisioningService(
                        appMapper, userMapper, accountMapper, mfaMapper, roleMapper,
                        menuMapper, permissionMapper, roleMenuMapper, rolePermissionMapper,
                        merchantUserMapper, merchantUserRoleMapper,
                        merchantMenuGrantMapper, merchantPermissionGrantMapper,
                        emailService, configService
                );

        service.provision(merchant());

        ArgumentCaptor<SysRoleDO> roleCaptor = ArgumentCaptor.forClass(SysRoleDO.class);
        verify(roleMapper).insert(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRoleCode()).isEqualTo("MERCHANT_ADMIN_M10001");
        assertThat(roleCaptor.getValue().getMerchantId()).isEqualTo("M10001");
        verify(roleMenuMapper).insert(any(SysRoleMenuDO.class));
        verify(rolePermissionMapper).insert(any(SysRolePermissionDO.class));
        verify(merchantMenuGrantMapper).insert(any(SysMerchantMenuGrantDO.class));
        verify(merchantPermissionGrantMapper).insert(any(SysMerchantPermissionGrantDO.class));

        ArgumentCaptor<SysAccountDO> accountCaptor = ArgumentCaptor.forClass(SysAccountDO.class);
        verify(accountMapper).insert(accountCaptor.capture());
        SysAccountDO account = accountCaptor.getValue();
        assertThat(account.getLoginAccount()).isEqualTo("owner@example.com");
        assertThat(account.getPasswordHash()).isNotBlank();

        ArgumentCaptor<SysAccountMfaDO> mfaCaptor = ArgumentCaptor.forClass(SysAccountMfaDO.class);
        verify(mfaMapper).insert(mfaCaptor.capture());
        assertThat(mfaCaptor.getValue().getMfaPolicy()).isEqualTo("REQUIRED");
        assertThat(mfaCaptor.getValue().getMfaStatus()).isEqualTo("PENDING_BIND");

        ArgumentCaptor<SysMerchantUserRoleDO> relationCaptor = ArgumentCaptor.forClass(SysMerchantUserRoleDO.class);
        verify(merchantUserRoleMapper).insert(relationCaptor.capture());
        assertThat(relationCaptor.getValue().getRoleId()).isEqualTo(roleCaptor.getValue().getId());

        ArgumentCaptor<EmailSendRequest> emailCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailService).sendByTemplate(emailCaptor.capture());
        EmailSendRequest email = emailCaptor.getValue();
        assertThat(email.getTemplateCode()).isEqualTo("MERCHANT_ACCOUNT_CREATED");
        assertThat(email.getToEmails()).containsExactly("owner@example.com");
        assertThat(email.getVariables()).containsEntry("merchantId", "M10001");
        assertThat(email.getVariables()).containsEntry("loginUrl", "https://merchant.vexra.example/login");
        String initialPassword = String.valueOf(email.getVariables().get("initialPassword"));
        assertThat(initialPassword).hasSizeBetween(16, 64);
        assertThat(PasswordHashUtils.matches(initialPassword, account.getPasswordSalt(), account.getPasswordHash())).isTrue();
    }

    private void assignId(SysUserMapper userMapper,
                          SysRoleMapper roleMapper,
                          SysAccountMapper accountMapper,
                          SysMerchantUserMapper merchantUserMapper) {
        doAnswer(invocation -> setId(invocation.getArgument(0), 101L))
                .when(userMapper).insert(any(SysUserDO.class));
        doAnswer(invocation -> setId(invocation.getArgument(0), 102L))
                .when(roleMapper).insert(any(SysRoleDO.class));
        doAnswer(invocation -> setId(invocation.getArgument(0), 103L))
                .when(accountMapper).insert(any(SysAccountDO.class));
        doAnswer(invocation -> setId(invocation.getArgument(0), 104L))
                .when(merchantUserMapper).insert(any(SysMerchantUserDO.class));
    }

    private int setId(Object row, Long id) {
        if (row instanceof SysUserDO user) {
            user.setId(id);
        } else if (row instanceof SysRoleDO role) {
            role.setId(id);
        } else if (row instanceof SysAccountDO account) {
            account.setId(id);
        } else if (row instanceof SysMerchantUserDO merchantUser) {
            merchantUser.setId(id);
        }
        return 1;
    }

    private BaseMerchantInfoDO merchant() {
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setId(10L);
        merchant.setMerchantId("M10001");
        merchant.setMerchantName("Vexra Demo Merchant");
        merchant.setContactName("Owner");
        merchant.setContactEmail("owner@example.com");
        merchant.setContactPhone("13800000000");
        merchant.setCountryCode("CN");
        merchant.setTimezone("Asia/Shanghai");
        merchant.setMerchantStatus(1);
        return merchant;
    }

    private SysMenuDO menu(Long id) {
        SysMenuDO menu = new SysMenuDO();
        menu.setId(id);
        return menu;
    }

    private SysPermissionDO permission(Long id) {
        SysPermissionDO permission = new SysPermissionDO();
        permission.setId(id);
        return permission;
    }
}
