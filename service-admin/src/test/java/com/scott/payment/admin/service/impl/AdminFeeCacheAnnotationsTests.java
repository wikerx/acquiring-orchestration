package com.scott.payment.admin.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理端费率审核缓存契约测试，确保新版本生效后商户只读缓存立即失效。
 */
class AdminFeeCacheAnnotationsTests {

    /**
     * 费率审核必须由事务 Outbox 精确失效，禁止注解清空全部商户缓存。
     */
    @Test
    void shouldNotClearAllMerchantFeeCachesAfterApproval() throws Exception {
        Method method = AdminFeeServiceImpl.class.getMethod(
                "approveVersion", Long.class, String.class, Long.class, String.class);
        CacheEvict eviction = AnnotatedElementUtils.findMergedAnnotation(method, CacheEvict.class);

        assertThat(eviction).isNull();
    }
}
