package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.entity.system.AdminRoleMerchantScopeDO;
import com.scott.payment.admin.mapper.AdminRoleDataScopeMapper;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantDataScopeResolverImplTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Admin 多角色商户范围按 ALL 优先、CUSTOM 并集、SELF 空集解析。
 * @status : create
 */
class AdminMerchantDataScopeResolverImplTest {

    @Test
    void allRoleShouldOverrideCustomAndSelfRoles() {
        AdminRoleDataScopeMapper mapper = mock(AdminRoleDataScopeMapper.class);
        when(mapper.selectActiveMerchantScopes(1L, 88L)).thenReturn(List.of(
                scope("CUSTOM", "M1001"), scope("SELF", null), scope("ALL", null)));

        AdminMerchantDataScope result = new AdminMerchantDataScopeResolverImpl(mapper)
                .resolve(account());

        assertThat(result.allMerchants()).isTrue();
        assertThat(result.merchantIds()).isEmpty();
    }

    @Test
    void customRolesShouldUnionMerchantValuesAndIgnoreSelf() {
        AdminRoleDataScopeMapper mapper = mock(AdminRoleDataScopeMapper.class);
        when(mapper.selectActiveMerchantScopes(1L, 88L)).thenReturn(List.of(
                scope("CUSTOM", "M1002"), scope("SELF", null),
                scope("CUSTOM", "M1001"), scope("CUSTOM", "M1002")));

        AdminMerchantDataScope result = new AdminMerchantDataScopeResolverImpl(mapper)
                .resolve(account());

        assertThat(result.allMerchants()).isFalse();
        assertThat(result.merchantIds()).containsExactlyInAnyOrder("M1001", "M1002");
    }

    @Test
    void platformSelfOrMissingRolesShouldResolveToEmptyScope() {
        AdminRoleDataScopeMapper mapper = mock(AdminRoleDataScopeMapper.class);
        when(mapper.selectActiveMerchantScopes(1L, 88L)).thenReturn(List.of(scope("SELF", null)));

        AdminMerchantDataScope result = new AdminMerchantDataScopeResolverImpl(mapper)
                .resolve(account());

        assertThat(result.allMerchants()).isFalse();
        assertThat(result.merchantIds()).isEmpty();
    }

    private InternalAuthAccount account() {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setAppId(1L);
        account.setAccountId(88L);
        return account;
    }

    private AdminRoleMerchantScopeDO scope(String dataScope, String scopeValue) {
        AdminRoleMerchantScopeDO row = new AdminRoleMerchantScopeDO();
        row.setDataScope(dataScope);
        row.setScopeValue(scopeValue);
        return row;
    }
}
