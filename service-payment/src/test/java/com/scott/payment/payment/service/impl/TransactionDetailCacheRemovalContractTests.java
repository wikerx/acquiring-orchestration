package com.scott.payment.payment.service.impl;

import com.scott.payment.component.redis.cache.PaymentCacheRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionDetailCacheRemovalContractTests
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : 交易详情实时查询契约测试，防止重新引入 Spring Cache 或 transaction:detail 注册项。
 * @status : create
 */
class TransactionDetailCacheRemovalContractTests {

    /**
     * 交易详情必须携带动作和根主单的真实分片时间直接查询数据库事实表，不能重新添加 Spring Cache。
     *
     * @throws NoSuchMethodException detail 方法不存在
     */
    @Test
    void shouldKeepTransactionDetailOutsideSpringCache() throws NoSuchMethodException {
        Method detailMethod = DefaultTransactionQueryService.class.getMethod(
                "detail", String.class, LocalDateTime.class, LocalDateTime.class);

        assertThat(detailMethod.getAnnotation(Cacheable.class)).isNull();
        assertThat(PaymentCacheRegistry.defaultTtls()).doesNotContainKey("transaction:detail");
    }
}
