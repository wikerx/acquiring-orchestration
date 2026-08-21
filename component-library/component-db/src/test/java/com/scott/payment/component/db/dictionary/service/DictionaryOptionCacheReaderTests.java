package com.scott.payment.component.db.dictionary.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.dictionary.model.DictionaryOptionSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DictionaryOptionCacheReaderTests
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 共享启用数据字典下拉缓存的固定键和主库重建契约测试
 * @status : create
 */
class DictionaryOptionCacheReaderTests {

    /** 缓存未命中时必须从主库读取，并使用字典类型与语言组成稳定业务键。 */
    @Test
    void shouldRebuildDictionaryOptionsFromMasterWithNormalizedKey() throws Exception {
        Method method = DictionaryOptionCacheReader.class.getMethod(
                "findEnabled", String.class, String.class);
        DS dataSource = AnnotatedElementUtils.findMergedAnnotation(method, DS.class);
        Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(method, Cacheable.class);

        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).containsExactly(PaymentCacheNames.SYSTEM_DICT_OPTIONS);
        assertThat(cacheable.key()).contains("DictionaryOptionCacheKey");
        assertThat(cacheable.condition()).contains("dictionaryOptionCacheCondition");
        assertThat(method.getReturnType()).isEqualTo(List.class);
        assertThat(method.getGenericReturnType().getTypeName())
                .contains(DictionaryOptionSnapshot.class.getName());
    }
}
