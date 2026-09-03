package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantRouteProfileCacheServiceTests
 * @date : 2026-09-01 23:20
 * @email : scott_x@163.com
 * @description : 验证商户路由永久缓存的失效门禁、generation 联动和结构版本升级
 * @status : create
 */
class DefaultMerchantRouteProfileCacheServiceTests {

    /** 旧 Redis 快照缺少新增能力字段时必须从主库重建并覆盖原 Key。 */
    @Test
    void shouldRefreshLegacyRouteProfileSchema() {
        MerchantRouteProfileCacheReader cacheReader = mock(MerchantRouteProfileCacheReader.class);
        CacheInvalidationGuard invalidationGuard = mock(CacheInvalidationGuard.class);
        MerchantRouteProfile legacy = new MerchantRouteProfile();
        legacy.setMerchantId("200045");
        legacy.setSchemaVersion(2);
        MerchantRouteProfile current = new MerchantRouteProfile();
        current.setMerchantId("200045");
        current.setSchemaVersion(MerchantRouteProfile.CURRENT_SCHEMA_VERSION);
        when(cacheReader.findCached("200045")).thenReturn(legacy);
        when(cacheReader.refreshCached("200045")).thenReturn(current);
        DefaultMerchantRouteProfileCacheService service =
                new DefaultMerchantRouteProfileCacheService(cacheReader, invalidationGuard);

        MerchantRouteProfile result = service.findRouteProfile("200045");

        assertThat(result).isSameAs(current);
        verify(cacheReader).refreshCached("200045");
    }
}
