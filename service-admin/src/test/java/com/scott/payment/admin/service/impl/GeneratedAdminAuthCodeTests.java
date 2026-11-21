package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.SysRoleCreateRequest;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysPostDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.mapper.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeneratedAdminAuthCodeTests {
    private final SysAppMapper apps = mock(SysAppMapper.class);
    private final SysRoleMapper roles = mock(SysRoleMapper.class);
    private final SysPostMapper posts = mock(SysPostMapper.class);

    @Test
    void createsRoleWithoutClientCodeAndIgnoresLegacySuppliedCode() {
        app();
        var service = new AdminRoleServiceImpl(apps, roles, mock(SysRoleMenuMapper.class), mock(SysRolePermissionMapper.class),
                mock(SysAccountRoleMapper.class), mock(SysMenuMapper.class), mock(SysPermissionMapper.class));
        when(roles.insert(any(SysRoleDO.class))).thenAnswer(call -> { call.<SysRoleDO>getArgument(0).setId(81L); return 1; });
        for (String clientCode : new String[]{null, "CLIENT_SELECTED_CODE"}) {
            SysRoleCreateRequest request = new SysRoleCreateRequest();
            request.setRoleName("Reviewer");
            request.setRoleCode(clientCode);
            var result = service.createRole(request);
            assertThat(result.getRoleCode()).matches("ADMIN_ROLE_[0-9A-Z]{6}");
            assertThat(result.getRoleId()).isEqualTo(81L);
            assertThat(result.getRoleType()).isEqualTo("CUSTOM");
        }
    }

    @Test
    void roleRequestNoLongerRequiresCodeButStillRequiresName() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            SysRoleCreateRequest request = new SysRoleCreateRequest();
            request.setRoleName("Reviewer");
            assertThat(factory.getValidator().validate(request)).isEmpty();
            request.setRoleName(" ");
            assertThat(factory.getValidator().validate(request)).anySatisfy(violation ->
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("roleName"));
        }
    }

    @Test
    void createsPostAndRetriesCollisionWithoutReusingGeneratedId() {
        app();
        var service = new AdminPostServiceImpl(posts, apps);
        AtomicInteger attempts = new AtomicInteger();
        when(posts.insert(any(SysPostDO.class))).thenAnswer(call -> {
            SysPostDO post = call.getArgument(0);
            assertThat(post.getId()).isNull();
            post.setId(90L);
            if (attempts.incrementAndGet() == 1) throw new DuplicateKeyException("collision");
            return 1;
        });
        SysPostDO post = new SysPostDO();
        post.setPostCode("CLIENT_POST");
        post.setPostName("Reviewer");
        var result = service.createPost(post);
        assertThat(result.getPostCode()).matches("ADMIN_POST_[0-9A-Z]{6}");
        assertThat(result.getAppId()).isEqualTo(1L);
        assertThat(result.getId()).isEqualTo(90L);
        assertThat(attempts.get()).isEqualTo(2);
    }

    private void app() {
        SysAppDO app = new SysAppDO();
        app.setId(1L);
        app.setAppCode("ADMIN");
        when(apps.selectOne(any())).thenReturn(app);
    }
}
