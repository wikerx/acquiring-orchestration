package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFinanceCacheAnnotationsTests
 * @date : 2026-08-18 20:25
 * @email : scott_x@163.com
 * @description : 商户费用缓存契约测试，确保生效费率按稳定的商户号位置参数缓存。
 * @status : create
 */
class MerchantFinanceCacheAnnotationsTests {

    /** 当前生效费率必须使用项目登记缓存、稳定位置键，并跳过空结果。 */
    @Test
    void shouldCacheCurrentFeeByMerchantIdPosition() throws Exception {
        Method method = MerchantFinanceServiceImpl.class.getMethod("getCurrentFee", String.class);
        Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).containsExactly(PaymentCacheNames.MERCHANT_ACTIVE_FEE);
        assertThat(cacheable.key()).isEqualTo("#p0");
        assertThat(cacheable.condition())
                .isEqualTo("@merchantActiveFeeCachePolicy.isCacheReadAllowed(#p0)");
        assertThat(cacheable.unless()).isEqualTo("#result == null");
        assertThat(cacheable.sync()).isFalse();
        DS dataSource = AnnotatedElementUtils.findMergedAnnotation(method, DS.class);
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
    }
}
