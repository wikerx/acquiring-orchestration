package com.scott.payment.admin.application.risk.cache;

import com.scott.payment.admin.entity.RiskCacheInvalidationOutboxDO;
import com.scott.payment.admin.mapper.RiskCacheInvalidationOutboxMapper;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.generation.RedisCachePublication;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 风控规则缓存失效事件发布与重试测试。
 */
class RiskCacheInvalidationRelayServiceTests {

    @Test
    void shouldCommitGenerationAndMarkEventSent() {
        RiskCacheInvalidationOutboxMapper mapper = mock(RiskCacheInvalidationOutboxMapper.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        RiskCacheInvalidationOutboxDO event = event("INIT", "t-owner", "g-next");
        when(mapper.selectByEventId("event-1")).thenReturn(event);
        when(generationStore.commit(publication("t-owner", "g-next"))).thenReturn(true);
        when(mapper.markSent(eq(1L), eq(0), any())).thenReturn(1);
        RiskCacheInvalidationRelayService relay =
                new RiskCacheInvalidationRelayService(mapper, generationStore);

        assertThat(relay.publish("event-1")).isTrue();

        verify(mapper).markSent(eq(1L), eq(0), any());
        verify(mapper, never()).markFailed(
                eq(1L), eq(0), any(), anyString(), any()
        );
    }

    @Test
    void shouldReplaceExpiredPublicationCredentialBeforeRetrying() {
        RiskCacheInvalidationOutboxMapper mapper = mock(RiskCacheInvalidationOutboxMapper.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        RiskCacheInvalidationOutboxDO event = event("FAILED", "t-expired", "g-expired");
        RedisCachePublication original = publication("t-expired", "g-expired");
        RedisCachePublication replacement = publication("t-retry", "g-retry");
        when(mapper.selectByEventId("event-1")).thenReturn(event);
        when(generationStore.commit(original)).thenReturn(false);
        when(generationStore.begin("risk-runtime-rule", Duration.ofSeconds(30)))
                .thenReturn(replacement);
        when(mapper.replacePublication(
                eq(1L),
                eq(0),
                eq("t-retry"),
                eq("g-retry"),
                any()
        )).thenReturn(1);
        when(generationStore.commit(replacement)).thenReturn(true);
        when(mapper.markSent(eq(1L), eq(1), any())).thenReturn(1);
        RiskCacheInvalidationRelayService relay =
                new RiskCacheInvalidationRelayService(mapper, generationStore);

        assertThat(relay.publish("event-1")).isTrue();

        verify(mapper).replacePublication(
                eq(1L),
                eq(0),
                eq("t-retry"),
                eq("g-retry"),
                any()
        );
        verify(mapper).markSent(eq(1L), eq(1), any());
    }

    @Test
    void shouldReleaseReplacementGateWhenCredentialPersistenceFails() {
        RiskCacheInvalidationOutboxMapper mapper = mock(RiskCacheInvalidationOutboxMapper.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        RiskCacheInvalidationOutboxDO event = event("FAILED", "t-expired", "g-expired");
        RedisCachePublication original = publication("t-expired", "g-expired");
        RedisCachePublication replacement = publication("t-retry", "g-retry");
        when(mapper.selectByEventId("event-1")).thenReturn(event);
        when(generationStore.commit(original)).thenReturn(false);
        when(generationStore.begin("risk-runtime-rule", Duration.ofSeconds(30)))
                .thenReturn(replacement);
        when(mapper.replacePublication(
                eq(1L),
                eq(0),
                eq("t-retry"),
                eq("g-retry"),
                any()
        )).thenThrow(new IllegalStateException("database unavailable"));
        when(mapper.markFailed(eq(1L), eq(0), any(), anyString(), any())).thenReturn(1);
        RiskCacheInvalidationRelayService relay =
                new RiskCacheInvalidationRelayService(mapper, generationStore);

        assertThat(relay.publish("event-1")).isFalse();

        verify(generationStore).abort(replacement);
        verify(mapper).markFailed(eq(1L), eq(0), any(), anyString(), any());
    }

    @Test
    void shouldRecordRetryWhenRedisPublicationFails() {
        RiskCacheInvalidationOutboxMapper mapper = mock(RiskCacheInvalidationOutboxMapper.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        RiskCacheInvalidationOutboxDO event = event("INIT", "t-owner", "g-next");
        when(mapper.selectByEventId("event-1")).thenReturn(event);
        when(generationStore.commit(publication("t-owner", "g-next")))
                .thenThrow(new IllegalStateException("redis unavailable"));
        when(mapper.markFailed(eq(1L), eq(0), any(), anyString(), any())).thenReturn(1);
        RiskCacheInvalidationRelayService relay =
                new RiskCacheInvalidationRelayService(mapper, generationStore);

        assertThat(relay.publish("event-1")).isFalse();

        ArgumentCaptor<LocalDateTime> retryTimeCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).markFailed(
                eq(1L),
                eq(0),
                retryTimeCaptor.capture(),
                reasonCaptor.capture(),
                any()
        );
        assertThat(retryTimeCaptor.getValue()).isAfter(LocalDateTime.now());
        assertThat(reasonCaptor.getValue()).isEqualTo("IllegalStateException");
    }

    @Test
    void shouldTreatAlreadySentEventAsIdempotentSuccess() {
        RiskCacheInvalidationOutboxMapper mapper = mock(RiskCacheInvalidationOutboxMapper.class);
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        when(mapper.selectByEventId("event-1"))
                .thenReturn(event("SENT", "t-owner", "g-next"));
        RiskCacheInvalidationRelayService relay =
                new RiskCacheInvalidationRelayService(mapper, generationStore);

        assertThat(relay.publish("event-1")).isTrue();

        verify(generationStore, never()).commit(any());
        verify(mapper, never()).markSent(any(), any(), any());
    }

    private RiskCacheInvalidationOutboxDO event(String status,
                                                String token,
                                                String generation) {
        RiskCacheInvalidationOutboxDO event = new RiskCacheInvalidationOutboxDO();
        event.setId(1L);
        event.setEventId("event-1");
        event.setNamespace("risk-runtime-rule");
        event.setPublicationToken(token);
        event.setGeneration(generation);
        event.setEventStatus(status);
        event.setRetryCount(0);
        event.setVersion(0);
        return event;
    }

    private RedisCachePublication publication(String token, String generation) {
        return new RedisCachePublication("risk-runtime-rule", token, generation);
    }
}
