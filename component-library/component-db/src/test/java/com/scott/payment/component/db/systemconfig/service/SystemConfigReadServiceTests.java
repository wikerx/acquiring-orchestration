package com.scott.payment.component.db.systemconfig.service;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.systemconfig.model.SystemConfigSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 跨服务系统参数永久缓存门禁和降级测试。 */
class SystemConfigReadServiceTests {

    /** 前置 pending 门禁存在时必须完全绕过永久缓存。 */
    @Test
    void shouldReadMasterWhenInvalidationIsAlreadyPending() {
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        SystemConfigCacheReader cacheReader = mock(SystemConfigCacheReader.class);
        when(guard.isPending(PaymentCacheNames.SYSTEM_CONFIG, "risk.transaction.max-attempts"))
                .thenReturn(true);
        SystemConfigSnapshot fresh = snapshot("risk.transaction.max-attempts", "3", 1);
        when(cacheReader.findFresh("risk.transaction.max-attempts")).thenReturn(fresh);
        SystemConfigReadService service = new SystemConfigReadService(guard, cacheReader);

        Optional<SystemConfigSnapshot> result = service.findByKey(" risk.transaction.max-attempts ");

        assertThat(result).contains(fresh);
        verify(cacheReader, never()).findCached("risk.transaction.max-attempts");
        verify(cacheReader).findFresh("risk.transaction.max-attempts");
    }

    /** 缓存读取期间开始失效时必须丢弃旧快照并读取主库。 */
    @Test
    void shouldDiscardCachedSnapshotWhenInvalidationStartsDuringRead() {
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        SystemConfigCacheReader cacheReader = mock(SystemConfigCacheReader.class);
        when(guard.isPending(PaymentCacheNames.SYSTEM_CONFIG, "system.name"))
                .thenReturn(false, true);
        SystemConfigSnapshot cached = snapshot("system.name", "old", 1);
        SystemConfigSnapshot fresh = snapshot("system.name", "new", 1);
        when(cacheReader.findCached("system.name")).thenReturn(cached);
        when(cacheReader.findFresh("system.name")).thenReturn(fresh);
        SystemConfigReadService service = new SystemConfigReadService(guard, cacheReader);

        Optional<SystemConfigSnapshot> result = service.findByKey("system.name");

        assertThat(result).contains(fresh);
        var ordered = inOrder(guard, cacheReader);
        ordered.verify(guard).isPending(PaymentCacheNames.SYSTEM_CONFIG, "system.name");
        ordered.verify(cacheReader).findCached("system.name");
        ordered.verify(guard).isPending(PaymentCacheNames.SYSTEM_CONFIG, "system.name");
        ordered.verify(cacheReader).findFresh("system.name");
    }

    /** Redis 门禁状态不可判定时必须降级主库而不能使用永久缓存。 */
    @Test
    void shouldReadMasterWhenGateStateIsUnavailable() {
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        SystemConfigCacheReader cacheReader = mock(SystemConfigCacheReader.class);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(guard)
                .isPending(PaymentCacheNames.SYSTEM_CONFIG, "system.name");
        SystemConfigSnapshot fresh = snapshot("system.name", "Vexra", 1);
        when(cacheReader.findFresh("system.name")).thenReturn(fresh);
        SystemConfigReadService service = new SystemConfigReadService(guard, cacheReader);

        assertThat(service.findByKey("system.name")).contains(fresh);
        verify(cacheReader, never()).findCached("system.name");
    }

    /** 运行服务只能取得启用且非空的配置值。 */
    @Test
    void shouldExposeOnlyEnabledNonBlankValue() {
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        SystemConfigCacheReader cacheReader = mock(SystemConfigCacheReader.class);
        when(guard.isPending(PaymentCacheNames.SYSTEM_CONFIG, "enabled.key"))
                .thenReturn(false, false);
        when(guard.isPending(PaymentCacheNames.SYSTEM_CONFIG, "disabled.key"))
                .thenReturn(false, false);
        when(cacheReader.findCached("enabled.key"))
                .thenReturn(snapshot("enabled.key", " value ", 1));
        when(cacheReader.findCached("disabled.key"))
                .thenReturn(snapshot("disabled.key", "hidden", 0));
        SystemConfigReadService service = new SystemConfigReadService(guard, cacheReader);

        assertThat(service.findEnabledValue("enabled.key")).contains("value");
        assertThat(service.findEnabledValue("disabled.key")).isEmpty();
    }

    private SystemConfigSnapshot snapshot(String configKey, String value, int status) {
        LocalDateTime now = LocalDateTime.now();
        return new SystemConfigSnapshot(
                1L,
                "测试配置",
                configKey,
                value,
                1,
                "system",
                1,
                1,
                0,
                status,
                null,
                "system",
                "system",
                now,
                now
        );
    }
}
