package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.SysUserAccountCreateRequest;
import com.scott.payment.admin.service.AdminEmailVerificationService;
import com.scott.payment.admin.service.AdminPasswordResetService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysDeptMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysPostMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRolePermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.auth.mapper.SysUserPostMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 后台用户新增、编辑内嵌角色授权的服务端权限边界测试。 */
class AdminUserRoleAssignmentSecurityTests {

    @AfterEach
    void clearAuthContext() {
        InternalAuthContextHolder.clear();
    }

    /** 只有新增用户权限但没有分配角色权限时，构造 roleIds 也必须被服务层拒绝。 */
    @Test
    void shouldRejectEmbeddedRoleAssignmentWithoutAssignRolePermission() {
        SysAppMapper appMapper = mock(SysAppMapper.class);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysAppDO app = new SysAppDO();
        app.setId(1L);
        when(appMapper.selectOne(any())).thenReturn(app);

        AdminUserServiceImpl service = new AdminUserServiceImpl(
                appMapper,
                mock(SysAccountMapper.class),
                mock(SysAccountMfaMapper.class),
                userMapper,
                mock(SysDeptMapper.class),
                mock(SysPostMapper.class),
                mock(SysUserPostMapper.class),
                mock(SysRoleMapper.class),
                mock(SysMenuMapper.class),
                mock(SysAccountRoleMapper.class),
                mock(SysRoleMenuMapper.class),
                mock(SysRolePermissionMapper.class),
                mock(SysPermissionMapper.class),
                mock(SysLoginSessionMapper.class),
                mock(AdminEmailVerificationService.class),
                mock(AdminPasswordResetService.class)
        );
        InternalAuthAccount operator = new InternalAuthAccount();
        operator.setPermissions(List.of("system:user:add"));
        InternalAuthContextHolder.set(operator);

        SysUserAccountCreateRequest request = new SysUserAccountCreateRequest();
        request.setLoginAccount("new-user");
        request.setRealName("New User");
        request.setEmail("new@example.com");
        request.setRoleIds(List.of(11L));

        ServiceException exception = assertThrows(ServiceException.class, () -> service.createUser(request));

        assertEquals(ApiResultEnum.FORBIDDEN.getCode(), exception.getCode());
        verifyNoInteractions(userMapper);
    }
}
