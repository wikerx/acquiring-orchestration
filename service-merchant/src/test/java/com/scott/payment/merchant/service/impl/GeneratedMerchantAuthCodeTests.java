package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysMerchantPostDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.mapper.*;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleSaveRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeneratedMerchantAuthCodeTests {
    private final Map<Class<?>, Object> dependencies = new HashMap<>();

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void roleAndPostUseTrustedMerchantPrefixAndPreserveCreator() throws Exception {
        var service = service();
        when(dependency(SysRoleMapper.class).insert(any(SysRoleDO.class))).thenAnswer(call -> {
            SysRoleDO role = call.getArgument(0);
            assertThat(role.getMerchantId()).isEqualTo("260001");
            assertThat(role.getCreatedBy()).isEqualTo(42L);
            role.setId(51L);
            return 1;
        });
        when(dependency(SysMerchantPostMapper.class).insert(any(SysMerchantPostDO.class))).thenAnswer(call -> {
            SysMerchantPostDO post = call.getArgument(0);
            assertThat(post.getMerchantId()).isEqualTo("260001");
            assertThat(post.getCreatedBy()).isEqualTo(42L);
            post.setId(61L);
            return 1;
        });
        authenticate("260001");
        for (String clientCode : new String[]{null, "MERCHANT_ANOTHER_TENANT_FAKE"}) {
            RoleSaveRequest role = new RoleSaveRequest();
            role.setRoleName("Reviewer");
            role.setRoleCode(clientCode);
            var createdRole = service.createRole(role);
            assertThat(createdRole.getRoleCode()).matches("MERCHANT_260001_[0-9A-Z]{6}");
            assertThat(createdRole.getRoleType()).isEqualTo("CUSTOM");
            PostSaveRequest post = new PostSaveRequest();
            post.setPostName("Reviewer");
            post.setPostCode(clientCode);
            assertThat(service.createPost(post).getPostCode()).matches("MERCHANT_260001_[0-9A-Z]{6}");
        }
        // Creating an object without grants must not change another role's associations.
        verifyNoInteractions(dependency(SysRoleMenuMapper.class), dependency(SysRolePermissionMapper.class));
    }

    @Test
    void missingMerchantContextCannotCreateAnyObject() throws Exception {
        var service = service();
        RoleSaveRequest role = new RoleSaveRequest();
        role.setRoleName("Reviewer");
        PostSaveRequest post = new PostSaveRequest();
        post.setPostName("Reviewer");
        assertThatThrownBy(() -> service.createRole(role)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> service.createPost(post)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(dependency(SysRoleMapper.class), dependency(SysMerchantPostMapper.class));
    }

    private MerchantSystemServiceImpl service() throws Exception {
        var constructor = MerchantSystemServiceImpl.class.getConstructors()[0];
        Object[] arguments = Arrays.stream(constructor.getParameterTypes()).map(this::dependency).toArray();
        SysAppDO app = new SysAppDO();
        app.setId(2L);
        app.setAppCode("MERCHANT");
        when(dependency(SysAppMapper.class).selectOne(any())).thenReturn(app);
        return (MerchantSystemServiceImpl) constructor.newInstance(arguments);
    }

    private <T> T dependency(Class<T> type) {
        return type.cast(dependencies.computeIfAbsent(type, key -> mock(type)));
    }

    private void authenticate(String merchantId) {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setMerchantId(merchantId);
        account.setAccountId(42L);
        InternalAuthContextHolder.set(account);
    }
}
