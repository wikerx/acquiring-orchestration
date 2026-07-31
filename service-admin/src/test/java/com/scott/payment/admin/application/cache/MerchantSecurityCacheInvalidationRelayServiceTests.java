package com.scott.payment.admin.application.cache;

import com.scott.payment.admin.entity.MerchantSecurityCacheInvalidationOutboxDO;
import com.scott.payment.admin.mapper.MerchantSecurityCacheInvalidationOutboxMapper;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.CacheInvalidationLease;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.redis.cache.invalidation.ImmediateCacheEvictionService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSecurityCacheInvalidationRelayServiceTests
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : 永久缓存失效中继测试，验证精确删除、门禁释放、Outbox 终态和失败重试顺序
 * @status : create
 */
@Slf4j
class MerchantSecurityCacheInvalidationRelayServiceTests {

    @Test
    void shouldEvictBeforeReleasingGateAndMarkingSent() {
        log.info("测试永久缓存失效发布顺序，关键输入: INIT 事件 merchant:openapi/200045");
        MerchantSecurityCacheInvalidationOutboxMapper mapper =
                mock(MerchantSecurityCacheInvalidationOutboxMapper.class);
        ImmediateCacheEvictionService evictionService = mock(ImmediateCacheEvictionService.class);
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        MerchantSecurityCacheInvalidationOutboxDO event = event("INIT");
        when(mapper.selectByEventId("event-1")).thenReturn(event);
        when(mapper.markSent(eq(1L), eq(0), any())).thenReturn(1);
        MerchantSecurityCacheInvalidationRelayService relay =
                new MerchantSecurityCacheInvalidationRelayService(mapper, evictionService, guard);

        assertThat(relay.publish("event-1")).isTrue();

        InOrder publishOrder = inOrder(evictionService, guard, mapper);
        publishOrder.verify(evictionService).evict(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045"
        );
        publishOrder.verify(guard).release(lease());
        publishOrder.verify(mapper).markSent(eq(1L), eq(0), any());
        verify(mapper, never()).markFailed(any(), any(), any(), anyString(), any());
        log.info("永久缓存失效发布顺序测试完成，结果: 精确删除、释放门禁、标记 SENT 依次完成");
    }

    @Test
    void shouldPersistRetryWhenExactEvictionFails() {
        log.info("测试永久缓存精确删除失败重试，关键输入: Redis unavailable");
        MerchantSecurityCacheInvalidationOutboxMapper mapper =
                mock(MerchantSecurityCacheInvalidationOutboxMapper.class);
        ImmediateCacheEvictionService evictionService = mock(ImmediateCacheEvictionService.class);
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        MerchantSecurityCacheInvalidationOutboxDO event = event("INIT");
        when(mapper.selectByEventId("event-1")).thenReturn(event);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(evictionService)
                .evict(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS, "200045");
        when(mapper.markFailed(eq(1L), eq(0), any(), anyString(), any())).thenReturn(1);
        MerchantSecurityCacheInvalidationRelayService relay =
                new MerchantSecurityCacheInvalidationRelayService(mapper, evictionService, guard);

        assertThat(relay.publish("event-1")).isFalse();

        ArgumentCaptor<LocalDateTime> retryTime =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).markFailed(
                eq(1L),
                eq(0),
                retryTime.capture(),
                eq("redis unavailable"),
                any()
        );
        assertThat(retryTime.getValue()).isAfter(LocalDateTime.now());
        verify(guard, never()).release(any());
        verify(mapper, never()).markSent(any(), any(), any());
        log.info("永久缓存精确删除失败重试测试完成，结果: 保留门禁并持久化下次重试时间");
    }

    @Test
    void shouldRetryWhenGateReleaseCannotBeConfirmed() {
        log.info("测试永久缓存门禁释放失败重试，关键输入: Redis timeout");
        MerchantSecurityCacheInvalidationOutboxMapper mapper =
                mock(MerchantSecurityCacheInvalidationOutboxMapper.class);
        ImmediateCacheEvictionService evictionService = mock(ImmediateCacheEvictionService.class);
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        MerchantSecurityCacheInvalidationOutboxDO event = event("FAILED");
        when(mapper.selectByEventId("event-1")).thenReturn(event);
        when(guard.release(lease())).thenThrow(new IllegalStateException("redis timeout"));
        when(mapper.markFailed(eq(1L), eq(0), any(), anyString(), any())).thenReturn(1);
        MerchantSecurityCacheInvalidationRelayService relay =
                new MerchantSecurityCacheInvalidationRelayService(mapper, evictionService, guard);

        assertThat(relay.publish("event-1")).isFalse();

        verify(evictionService).evict(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045"
        );
        verify(mapper).markFailed(eq(1L), eq(0), any(), eq("redis timeout"), any());
        log.info("永久缓存门禁释放失败重试测试完成，结果: 缓存已删除且 Outbox 保持可重试");
    }

    @Test
    void shouldTreatAlreadySentEventAsIdempotentSuccess() {
        log.info("测试永久缓存已发布事件幂等，关键输入: SENT 事件 event-1");
        MerchantSecurityCacheInvalidationOutboxMapper mapper =
                mock(MerchantSecurityCacheInvalidationOutboxMapper.class);
        ImmediateCacheEvictionService evictionService = mock(ImmediateCacheEvictionService.class);
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        when(mapper.selectByEventId("event-1")).thenReturn(event("SENT"));
        MerchantSecurityCacheInvalidationRelayService relay =
                new MerchantSecurityCacheInvalidationRelayService(mapper, evictionService, guard);

        assertThat(relay.publish("event-1")).isTrue();

        verify(evictionService, never()).evict(anyString(), anyString());
        verify(guard, never()).release(any());
        log.info("永久缓存已发布事件幂等测试完成，结果: 未重复删除缓存或释放门禁");
    }

    private MerchantSecurityCacheInvalidationOutboxDO event(String status) {
        MerchantSecurityCacheInvalidationOutboxDO event =
                new MerchantSecurityCacheInvalidationOutboxDO();
        event.setId(1L);
        event.setEventId("event-1");
        event.setCacheName(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS);
        event.setBusinessKey("200045");
        event.setGateToken("t-owner");
        event.setEventStatus(status);
        event.setRetryCount(0);
        event.setVersion(0);
        return event;
    }

    private CacheInvalidationLease lease() {
        return new CacheInvalidationLease(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045",
                "t-owner"
        );
    }
}
