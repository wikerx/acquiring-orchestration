package com.scott.payment.admin.application.base;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.dto.base.CardBinDTOs;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminCardBinCacheAnnotationsTests
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 管理端 Card BIN 写事务和有限期正负缓存失效契约测试
 * @status : create
 */
class AdminCardBinCacheAnnotationsTests {

    /** 所有 BIN 写入口必须在主库事务提交后清理命中与未命中缓存。 */
    @Test
    void shouldEvictPositiveAndMissCachesForEveryMutation() throws Exception {
        assertMutation("create", CardBinDTOs.CardBinSaveRequest.class);
        assertMutation("update", Long.class, CardBinDTOs.CardBinSaveRequest.class);
        assertMutation("remove", Long.class);
        assertMutation("updateStatus", Long.class, CardBinDTOs.CardBinStatusRequest.class);
        assertMutation("initFromLegacyDb");
    }

    /** 校验单个 Card BIN 写入口的数据源、事务和双缓存失效配置。 */
    private void assertMutation(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminBaseCardBinApplicationService.class.getMethod(methodName, parameterTypes);
        DS dataSource = AnnotatedElementUtils.findMergedAnnotation(method, DS.class);
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        Caching caching = AnnotatedElementUtils.findMergedAnnotation(method, Caching.class);

        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
        assertThat(transactional).isNotNull();
        assertThat(caching).isNotNull();
        Set<String> cacheNames = Arrays.stream(caching.evict())
                .map(CacheEvict::cacheNames)
                .flatMap(Arrays::stream)
                .collect(Collectors.toSet());
        assertThat(cacheNames).containsExactlyInAnyOrder(
                PaymentCacheNames.CARD_BIN,
                PaymentCacheNames.CARD_BIN_MISS
        );
        assertThat(Arrays.stream(caching.evict()).allMatch(CacheEvict::allEntries)).isTrue();
    }
}
