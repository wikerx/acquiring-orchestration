package com.scott.payment.admin.service.impl;

import com.scott.payment.component.core.cache.PaymentCacheNames;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFeeCacheAnnotationsTests
 * @date : 2026-08-18 20:30
 * @email : scott_x@163.com
 * @description : 管理端费率审核缓存契约测试，确保新版本生效后商户只读缓存立即失效。
 * @status : create
 */
class AdminFeeCacheAnnotationsTests {

    /** 审核通过会改变生效版本，必须清空跨商户的当前费率缓存。 */
    @Test
    void shouldEvictActiveMerchantFeeCacheAfterApproval() throws Exception {
        Method method = AdminFeeServiceImpl.class.getMethod(
                "approveVersion", Long.class, String.class, Long.class, String.class);
        CacheEvict eviction = AnnotatedElementUtils.findMergedAnnotation(method, CacheEvict.class);

        assertThat(eviction).isNotNull();
        assertThat(eviction.cacheNames()).containsExactly(PaymentCacheNames.MERCHANT_ACTIVE_FEE);
        assertThat(eviction.allEntries()).isTrue();
        assertThat(eviction.beforeInvocation()).isFalse();
    }
}
