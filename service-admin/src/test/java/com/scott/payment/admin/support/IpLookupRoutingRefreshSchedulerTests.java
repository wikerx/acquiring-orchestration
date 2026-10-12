package com.scott.payment.admin.support;

import com.scott.payment.component.db.reference.cache.IpShardRoutingSnapshot;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证管理端与 OpenAPI 一样定期刷新公共 IP 路由版本。 */
class IpLookupRoutingRefreshSchedulerTests {

    @Test
    void shouldRefreshSharedRoutingSnapshot() {
        IpShardRoutingSnapshot routingSnapshot = mock(IpShardRoutingSnapshot.class);
        when(routingSnapshot.refresh()).thenReturn(true, false);
        IpLookupRoutingRefreshScheduler scheduler = new IpLookupRoutingRefreshScheduler(routingSnapshot);

        scheduler.refresh();
        scheduler.refresh();

        verify(routingSnapshot, times(2)).refresh();
    }
}
