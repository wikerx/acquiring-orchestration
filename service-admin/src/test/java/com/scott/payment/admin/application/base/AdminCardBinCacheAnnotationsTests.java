package com.scott.payment.admin.application.base;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.dto.base.CardBinDTOs;
import com.scott.payment.component.db.constant.DataSourceName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Caching;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminCardBinCacheAnnotationsTests
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 管理端 Card BIN 写入口事务契约测试，确保数据变更通过 generation 协调器而非全量缓存清理注解处理。
 * @status : create
 */
class AdminCardBinCacheAnnotationsTests {

    /** 所有 BIN 写入口必须使用主库事务且不再执行 allEntries 全量清理。 */
    @Test
    void shouldUseMasterTransactionWithoutAllEntriesEvictionForEveryMutation() throws Exception {
        assertMutation("create", CardBinDTOs.CardBinSaveRequest.class);
        assertMutation("update", Long.class, CardBinDTOs.CardBinSaveRequest.class);
        assertMutation("remove", Long.class);
        assertMutation("updateStatus", Long.class, CardBinDTOs.CardBinStatusRequest.class);
        assertMutation("initFromLegacyDb");
    }

    /** 校验单个 Card BIN 写入口的数据源、事务和 generation 失效配置。 */
    private void assertMutation(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminBaseCardBinApplicationService.class.getMethod(methodName, parameterTypes);
        DS dataSource = AnnotatedElementUtils.findMergedAnnotation(method, DS.class);
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
        Caching caching = AnnotatedElementUtils.findMergedAnnotation(method, Caching.class);

        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
        assertThat(transactional).isNotNull();
        assertThat(caching).isNull();
    }
}
