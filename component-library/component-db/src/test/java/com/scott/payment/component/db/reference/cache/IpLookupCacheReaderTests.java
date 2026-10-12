package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.reference.model.IpLookupCacheEntry;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupSnapshot;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证共享 IP 缓存的一次读取、版本隔离和单次从库回源行为。 */
class IpLookupCacheReaderTests {

    @Test
    void shouldReturnVersionMatchedCacheEntryWithoutDatabaseLookup() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        IpShardRoutingSnapshot routingSnapshot = mock(IpShardRoutingSnapshot.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        IpLookupResult cachedResult = hit("8.8.8.8");
        when(routingSnapshot.currentVersion()).thenReturn("route-v1");
        when(redis.execute(eq(PaymentRedisScripts.ipLookupReadV1()),
                anyList(), anyString(), anyString()))
                .thenReturn(JsonUtils.toJsonString(new IpLookupCacheEntry("route-v1", cachedResult)));
        IpLookupCacheReader reader = reader(lookupService, routingSnapshot, redis);

        assertThat(reader.lookup("008.008.008.008")).isEqualTo(cachedResult);
        verify(lookupService, never()).lookupIpSnapshot(anyString());
    }

    @Test
    void shouldQueryReplicaOnceAndWriteSharedCacheWhenEntryIsMissing() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        IpShardRoutingSnapshot routingSnapshot = mock(IpShardRoutingSnapshot.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        IpLookupResult databaseResult = hit("8.8.8.8");
        when(routingSnapshot.currentVersion()).thenReturn("route-v1");
        when(redis.execute(eq(PaymentRedisScripts.ipLookupReadV1()),
                anyList(), anyString(), anyString())).thenReturn(null);
        when(redis.execute(eq(PaymentRedisScripts.ipLookupWriteV1()), anyList(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(1L);
        when(lookupService.lookupIpSnapshot("8.8.8.8"))
                .thenReturn(new IpLookupSnapshot("route-v1", databaseResult));
        IpLookupCacheReader reader = reader(lookupService, routingSnapshot, redis);

        assertThat(reader.lookup("8.8.8.8")).isEqualTo(databaseResult);

        verify(lookupService).lookupIpSnapshot("8.8.8.8");
        verify(redis).execute(eq(PaymentRedisScripts.ipLookupReadV1()),
                anyList(), anyString(), anyString());
        verify(redis).execute(eq(PaymentRedisScripts.ipLookupWriteV1()), anyList(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectStaleRoutingVersionAndRefreshFromReplica() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        IpShardRoutingSnapshot routingSnapshot = mock(IpShardRoutingSnapshot.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(routingSnapshot.currentVersion()).thenReturn("route-v2");
        when(redis.execute(eq(PaymentRedisScripts.ipLookupReadV1()),
                anyList(), anyString(), anyString()))
                .thenReturn(JsonUtils.toJsonString(new IpLookupCacheEntry("route-v1", hit("8.8.8.8"))));
        when(redis.execute(eq(PaymentRedisScripts.ipLookupWriteV1()), anyList(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(1L);
        IpLookupResult refreshed = IpLookupResult.miss("8.8.8.8", "IPV4");
        when(lookupService.lookupIpSnapshot("8.8.8.8"))
                .thenReturn(new IpLookupSnapshot("route-v2", refreshed));
        IpLookupCacheReader reader = reader(lookupService, routingSnapshot, redis);

        assertThat(reader.lookup("8.8.8.8")).isEqualTo(refreshed);
        verify(lookupService).lookupIpSnapshot("8.8.8.8");
    }

    private IpLookupCacheReader reader(ReferenceDataLookupService lookupService,
                                       IpShardRoutingSnapshot routingSnapshot,
                                       StringRedisTemplate redis) {
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        return new IpLookupCacheReader(lookupService, routingSnapshot, redis, properties,
                Duration.ofHours(6), Duration.ofMinutes(2), Duration.ofHours(24), 1000);
    }

    private IpLookupResult hit(String ip) {
        return new IpLookupResult(true, ip, "IPV4", "US", "USA", "840",
                "United States", "California", "Mountain View");
    }
}
