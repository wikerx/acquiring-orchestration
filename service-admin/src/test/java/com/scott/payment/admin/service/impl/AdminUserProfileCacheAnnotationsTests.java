package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.SysUserAccountStatusRequest;
import com.scott.payment.admin.dto.SysUserAccountUpdateRequest;
import com.scott.payment.admin.dto.SysUserRoleGrantRequest;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证后台用户资料缓存直接由领域服务的标准 Spring Cache 注解管理。 */
class AdminUserProfileCacheAnnotationsTests {

    @Test
    void shouldCacheProfileAndEvictItAfterUserChanges() throws Exception {
        Method reader = AdminUserServiceImpl.class.getMethod("getUserProfile", Long.class);
        Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(reader, Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).containsExactly(PaymentCacheNames.ADMIN_USER_PROFILE);
        assertThat(cacheable.key()).isEqualTo("#p0");

        assertPreciseEviction("updateUser", SysUserAccountUpdateRequest.class);
        assertPreciseEviction("updateStatus", SysUserAccountStatusRequest.class);
        assertPreciseEviction("grantRoles", SysUserRoleGrantRequest.class);

        Method batchDelete = AdminUserServiceImpl.class.getMethod("removeUsers", List.class);
        CacheEvict batchEviction = AnnotatedElementUtils.findMergedAnnotation(batchDelete, CacheEvict.class);
        assertThat(batchEviction).isNotNull();
        assertThat(batchEviction.cacheNames()).containsExactly(PaymentCacheNames.ADMIN_USER_PROFILE);
        assertThat(batchEviction.allEntries()).isTrue();
    }

    private void assertPreciseEviction(String methodName, Class<?> parameterType) throws Exception {
        Method method = AdminUserServiceImpl.class.getMethod(methodName, parameterType);
        CacheEvict eviction = AnnotatedElementUtils.findMergedAnnotation(method, CacheEvict.class);
        assertThat(eviction).isNotNull();
        assertThat(eviction.cacheNames()).containsExactly(PaymentCacheNames.ADMIN_USER_PROFILE);
        assertThat(eviction.key()).isEqualTo("#p0.accountId");
        assertThat(eviction.allEntries()).isFalse();
    }
}
