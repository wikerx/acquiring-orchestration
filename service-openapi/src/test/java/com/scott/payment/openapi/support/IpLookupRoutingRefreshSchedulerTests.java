package com.scott.payment.openapi.support;

import com.scott.payment.component.db.reference.cache.IpShardRoutingSnapshot;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 IP 路由按计划刷新；短 Key 中的旧版本值由查询入口拒绝并按需覆盖。 */
class IpLookupRoutingRefreshSchedulerTests {

    @Test
    void shouldRefreshRoutingSnapshotWithoutGlobalCacheDeletion() {
        IpShardRoutingSnapshot routingSnapshot = mock(IpShardRoutingSnapshot.class);
        when(routingSnapshot.refresh()).thenReturn(true, false);
        IpLookupRoutingRefreshScheduler scheduler = new IpLookupRoutingRefreshScheduler(routingSnapshot);

        scheduler.refresh();
        scheduler.refresh();

        verify(routingSnapshot, times(2)).refresh();
    }
}
