package com.scott.payment.component.db.reference.cache;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.reference.model.IpLookupCacheEntry;
import com.scott.payment.component.db.reference.model.IpLookupDetail;
import com.scott.payment.component.db.reference.model.IpLookupResult;
import com.scott.payment.component.db.reference.model.IpLookupSnapshot;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证共享 IP String 缓存的短 Key、物理 TTL、版本隔离和 SLAVE 回源行为。 */
class IpLookupCacheReaderTests {

    private static final Duration HIT_TTL = Duration.ofHours(6);
    private static final Duration MISS_TTL = Duration.ofMinutes(2);

    @Test
    void shouldReadIpv4StringKeyWithoutDatabaseLookup() {
        Fixture fixture = fixture();
        IpLookupCacheEntry cached = entry("route-v1", hitDetail("8.8.8.8", "IPV4"));
        when(fixture.routingSnapshot.currentVersion()).thenReturn("route-v1");
        when(fixture.values.get("acquiring:test:ip:v4:134744072"))
                .thenReturn(JsonUtils.toJsonString(cached));

        assertThat(fixture.reader.lookup("008.008.008.008")).isEqualTo(cached.result());

        verify(fixture.values).get("acquiring:test:ip:v4:134744072");
        verify(fixture.lookupService, never()).lookupIpSnapshot(anyString());
    }

    @Test
    void shouldSeparateIpv4AndIpv6WhenUnsignedNumbersAreEqual() {
        Fixture fixture = fixture();
        when(fixture.routingSnapshot.currentVersion()).thenReturn("route-v1");
        when(fixture.values.get("acquiring:test:ip:v4:1"))
                .thenReturn(JsonUtils.toJsonString(entry(
                        "route-v1", hitDetail("0.0.0.1", "IPV4"))));
        when(fixture.values.get("acquiring:test:ip:v6:1"))
                .thenReturn(JsonUtils.toJsonString(entry(
                        "route-v1", hitDetail("0:0:0:0:0:0:0:1", "IPV6"))));

        assertThat(fixture.reader.lookup("0.0.0.1").ipType()).isEqualTo("IPV4");
        assertThat(fixture.reader.lookup("::1").ipType()).isEqualTo("IPV6");

        verify(fixture.values).get("acquiring:test:ip:v4:1");
        verify(fixture.values).get("acquiring:test:ip:v6:1");
    }

    @Test
    void shouldQueryReplicaOnceAndWriteHitWithSixHourTtl() {
        Fixture fixture = fixture();
        IpLookupDetail detail = hitDetail("8.8.8.8", "IPV4");
        when(fixture.routingSnapshot.currentVersion()).thenReturn("route-v1");
        when(fixture.lookupService.lookupIpSnapshot("8.8.8.8"))
                .thenReturn(new IpLookupSnapshot("route-v1", detail));

        IpLookupCacheEntry result = fixture.reader.lookupEntry("8.8.8.8");

        assertThat(result.detail()).isEqualTo(detail);
        verify(fixture.lookupService).lookupIpSnapshot("8.8.8.8");
        verify(fixture.values).set(eq("acquiring:test:ip:v4:134744072"), anyString(), eq(HIT_TTL));
    }

    @Test
    void shouldWriteLegalMissWithTwoMinuteTtl() {
        Fixture fixture = fixture();
        IpLookupDetail detail = IpLookupDetail.miss(IpLookupResult.miss("8.8.4.4", "IPV4"));
        when(fixture.routingSnapshot.currentVersion()).thenReturn("route-v1");
        when(fixture.lookupService.lookupIpSnapshot("8.8.4.4"))
                .thenReturn(new IpLookupSnapshot("route-v1", detail));

        assertThat(fixture.reader.lookup("8.8.4.4").matched()).isFalse();

        verify(fixture.values).set(eq("acquiring:test:ip:v4:134743044"), anyString(), eq(MISS_TTL));
    }

    @Test
    void shouldRejectStaleVersionAndOverwriteSameShortKey() {
        Fixture fixture = fixture();
        when(fixture.routingSnapshot.currentVersion()).thenReturn("route-v2");
        when(fixture.values.get("acquiring:test:ip:v4:134744072"))
                .thenReturn(JsonUtils.toJsonString(entry(
                        "route-v1", hitDetail("8.8.8.8", "IPV4"))));
        IpLookupDetail refreshed = hitDetail("8.8.8.8", "IPV4");
        when(fixture.lookupService.lookupIpSnapshot("8.8.8.8"))
                .thenReturn(new IpLookupSnapshot("route-v2", refreshed));

        assertThat(fixture.reader.lookupEntry("8.8.8.8").routingVersion()).isEqualTo("route-v2");

        verify(fixture.lookupService).lookupIpSnapshot("8.8.8.8");
        verify(fixture.values).set(eq("acquiring:test:ip:v4:134744072"), anyString(), eq(HIT_TTL));
    }

    @Test
    void shouldRefreshIncompleteHitRequiredByAdminLookup() {
        Fixture fixture = fixture();
        IpLookupResult cachedResult = hitDetail("8.8.8.8", "IPV4").result();
        IpLookupDetail incomplete = new IpLookupDetail(
                cachedResult, null, null, null, null, null, null);
        IpLookupDetail refreshed = hitDetail("8.8.8.8", "IPV4");
        when(fixture.routingSnapshot.currentVersion()).thenReturn("route-v1");
        when(fixture.values.get("acquiring:test:ip:v4:134744072"))
                .thenReturn(JsonUtils.toJsonString(entry("route-v1", incomplete)));
        when(fixture.lookupService.lookupIpSnapshot("8.8.8.8"))
                .thenReturn(new IpLookupSnapshot("route-v1", refreshed));

        IpLookupCacheEntry result = fixture.reader.lookupEntry("8.8.8.8");

        assertThat(result.detail()).isEqualTo(refreshed);
        verify(fixture.lookupService).lookupIpSnapshot("8.8.8.8");
        verify(fixture.values).set(eq("acquiring:test:ip:v4:134744072"), anyString(), eq(HIT_TTL));
    }

    @Test
    void shouldReturnReplicaResultWhenRedisReadAndWriteFail() {
        Fixture fixture = fixture();
        IpLookupDetail detail = hitDetail("8.8.8.8", "IPV4");
        when(fixture.routingSnapshot.currentVersion()).thenReturn("route-v1");
        when(fixture.values.get("acquiring:test:ip:v4:134744072"))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(fixture.lookupService.lookupIpSnapshot("8.8.8.8"))
                .thenReturn(new IpLookupSnapshot("route-v1", detail));
        org.mockito.Mockito.doThrow(new IllegalStateException("redis unavailable"))
                .when(fixture.values)
                .set(eq("acquiring:test:ip:v4:134744072"), anyString(), eq(HIT_TTL));

        assertThat(fixture.reader.lookup("8.8.8.8")).isEqualTo(detail.result());
        verify(fixture.lookupService).lookupIpSnapshot("8.8.8.8");
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        ReferenceDataLookupService lookupService = mock(ReferenceDataLookupService.class);
        IpShardRoutingSnapshot routingSnapshot = mock(IpShardRoutingSnapshot.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        PaymentRedisProperties properties = new PaymentRedisProperties();
        properties.setKeyPrefix("acquiring:test");
        IpLookupCacheReader reader = new IpLookupCacheReader(
                lookupService, routingSnapshot, redis, properties, HIT_TTL, MISS_TTL);
        return new Fixture(lookupService, routingSnapshot, values, reader);
    }

    private IpLookupCacheEntry entry(String routingVersion, IpLookupDetail detail) {
        return new IpLookupCacheEntry(routingVersion, detail);
    }

    private IpLookupDetail hitDetail(String ip, String ipType) {
        IpLookupResult result = new IpLookupResult(true, ip, ipType, "US", "USA", "840",
                "United States", "California", "Mountain View");
        return new IpLookupDetail(result, 7L, "134744000", "134744255",
                "20261012", LocalDateTime.of(2026, 10, 12, 10, 30), "system");
    }

    private record Fixture(ReferenceDataLookupService lookupService,
                           IpShardRoutingSnapshot routingSnapshot,
                           ValueOperations<String, String> values,
                           IpLookupCacheReader reader) {
    }
}
