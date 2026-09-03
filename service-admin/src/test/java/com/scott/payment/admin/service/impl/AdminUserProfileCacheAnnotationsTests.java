package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.SysUserAccountStatusRequest;
import com.scott.payment.admin.dto.SysUserAccountUpdateRequest;
import com.scott.payment.admin.dto.SysUserRoleGrantRequest;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserProfileCacheAnnotationsTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证低频后台用户资料不引入跨模块永久缓存和失效链。
 * @status : create
 */
class AdminUserProfileCacheAnnotationsTests {

    @Test
    void shouldNotCacheProfileOrDeclareEvictionsAfterUserChanges() throws Exception {
        Method reader = AdminUserServiceImpl.class.getMethod("getUserProfile", Long.class);
        Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(reader, Cacheable.class);

        assertThat(cacheable).isNull();

        assertNoEviction("updateUser", SysUserAccountUpdateRequest.class);
        assertNoEviction("updateStatus", SysUserAccountStatusRequest.class);
        assertNoEviction("grantRoles", SysUserRoleGrantRequest.class);

        Method batchDelete = AdminUserServiceImpl.class.getMethod("removeUsers", List.class);
        CacheEvict batchEviction = AnnotatedElementUtils.findMergedAnnotation(batchDelete, CacheEvict.class);
        assertThat(batchEviction).isNull();
    }

    private void assertNoEviction(String methodName, Class<?> parameterType) throws Exception {
        Method method = AdminUserServiceImpl.class.getMethod(methodName, parameterType);
        CacheEvict eviction = AnnotatedElementUtils.findMergedAnnotation(method, CacheEvict.class);
        assertThat(eviction).isNull();
    }
}
