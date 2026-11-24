package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.db.outbox.model.ReliableMqOutboxMetricsSnapshot;
import com.scott.payment.component.db.outbox.service.ReliableMqOutboxStore;
import com.scott.payment.component.mq.observability.MqOutboxOperationalMetrics;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.component.mq.properties.ReliableMqOutboxProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Outbox Relay CAS、原始快照投递和失败重试状态测试
 */
@Slf4j
class ReliableMqOutboxRelayServiceTests {

    @Test
    void paymentCapabilityRecoveryUsesOnlyTheDedicatedOwnerScope() {
        ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
        MqProducer producer = mock(MqProducer.class);
        ReliableMqOutboxRelayService unauthorized = new ReliableMqOutboxRelayService(
                store, producer, new ReliableMqOutboxProperties(), "service-payment");
        assertThatThrownBy(() -> unauthorized.recoverPaymentCapabilityDelivery(100, Long.MAX_VALUE))
                .hasMessageContaining("owned by service-admin");
        verifyNoInteractions(store);

        ReliableMqOutboxRelayService admin = new ReliableMqOutboxRelayService(
                store, producer, new ReliableMqOutboxProperties(), "service-admin");
        when(store.findPaymentCapabilityDue(any(), eq(100))).thenReturn(List.of());
        assertThat(admin.recoverPaymentCapabilityDelivery(500, Long.MAX_VALUE))
                .isEqualTo(new ReliableMqOutboxRelayService.CapabilityRecovery(0, 0, 0, 0, false));
        verify(store).recoverPaymentCapabilityStale(any(), any(), eq(100));
        verify(store, never()).findDue(any(), any(), any(Integer.class));
        verifyNoInteractions(producer);
    }

    /**
     * CAS 抢占成功后应发送冻结 JSON，并以抢占后的版本标记 SENT。
     */
    @Test
    void shouldClaimSendAndMarkSent() {
        log.info("测试Outbox正常投递，关键输入: INIT、version=0");
        ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
        MqProducer producer = mock(MqProducer.class);
        ReliableMqOutboxDO event = event();
        when(store.findByEventId("MSG-OUTBOX-001")).thenReturn(event);
        when(store.claim(eq(1L), eq(0), any(LocalDateTime.class))).thenReturn(1);
        when(store.markSent(eq(1L), eq(1), any(LocalDateTime.class))).thenReturn(1);
        ReliableMqOutboxRelayService service = new ReliableMqOutboxRelayService(
                store, producer, new ReliableMqOutboxProperties(), "service-admin");

        assertThat(service.relayEvent("MSG-OUTBOX-001")).isTrue();

        verify(producer).sendSerialized(
                "audit-topic", "audit-tag", "MSG-OUTBOX-001", "TRACE-001", 0, "{\"messageId\":\"MSG-OUTBOX-001\"}");
        verify(store).markSent(eq(1L), eq(1), any(LocalDateTime.class));
        log.info("Outbox正常投递测试完成，结果: SENT CAS成功");
    }

    /**
     * MQ 异常不得标记成功，应进入 RETRY_WAIT 且只保存异常类型。
     */
    @Test
    void shouldScheduleRetryWhenMqDeliveryFails() {
        log.info("测试Outbox失败重试，关键输入: MQ发送异常、未耗尽重试");
        ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
        MqProducer producer = mock(MqProducer.class);
        ReliableMqOutboxDO event = event();
        when(store.findByEventId("MSG-OUTBOX-001")).thenReturn(event);
        when(store.claim(eq(1L), eq(0), any(LocalDateTime.class))).thenReturn(1);
        when(store.markFailed(
                eq(1L), eq(1), eq("RETRY_WAIT"), any(LocalDateTime.class),
                eq("IllegalStateException"), any(LocalDateTime.class))).thenReturn(1);
        doThrow(new IllegalStateException("internal endpoint detail"))
                .when(producer).sendSerialized(any(), any(), any(), any(), eq(0), any());
        ReliableMqOutboxRelayService service = new ReliableMqOutboxRelayService(
                store, producer, new ReliableMqOutboxProperties(), "service-admin");

        assertThat(service.relayEvent("MSG-OUTBOX-001")).isFalse();

        verify(store).markFailed(
                eq(1L), eq(1), eq("RETRY_WAIT"), any(LocalDateTime.class),
                eq("IllegalStateException"), any(LocalDateTime.class));
        log.info("Outbox失败重试测试完成，结果: RETRY_WAIT且未保存异常详情");
    }

    @Test
    void keepsAnAcceptedBrokerSendPendingWhenItsDatabaseAcknowledgementIsUnknown() {
        ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
        MqProducer producer = mock(MqProducer.class);
        ReliableMqOutboxDO event = event();
        when(store.findPaymentCapabilityDue(any(), eq(100))).thenReturn(List.of(event));
        when(store.claim(eq(1L), eq(0), any())).thenReturn(1);
        when(store.markSent(eq(1L), eq(1), any())).thenThrow(new IllegalStateException("database unavailable"));
        ReliableMqOutboxRelayService service = new ReliableMqOutboxRelayService(
                store, producer, new ReliableMqOutboxProperties(), "service-admin");

        assertThat(service.recoverPaymentCapabilityDelivery(100, Long.MAX_VALUE))
                .isEqualTo(new ReliableMqOutboxRelayService.CapabilityRecovery(1, 0, 0, 1, false));
        verify(store, never()).markFailed(any(), any(), any(), any(), any(), any());
    }

    /**
     * 事件号即使可查到，也不能由其他生产服务的 Relay 投递。
     */
    @Test
    void shouldRejectEventOwnedByAnotherProducerService() {
        ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
        MqProducer producer = mock(MqProducer.class);
        ReliableMqOutboxDO event = event();
        event.setProducerService("service-payment");
        when(store.findByEventId("MSG-OUTBOX-001")).thenReturn(event);
        ReliableMqOutboxRelayService service = new ReliableMqOutboxRelayService(
                store, producer, new ReliableMqOutboxProperties(), "service-admin");

        assertThat(service.relayEvent("MSG-OUTBOX-001")).isFalse();

        verify(store, never()).claim(any(), any(), any());
        verifyNoInteractions(producer);
    }

    @Test
    void shouldRecordReliableSnapshotRefreshSuccessAndFailure() {
        ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
        MqProducer producer = mock(MqProducer.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MqOutboxOperationalMetrics metrics =
                new MqOutboxOperationalMetrics(Optional.of(registry));
        ReliableMqOutboxMetricsSnapshot snapshot = new ReliableMqOutboxMetricsSnapshot();
        snapshot.setInitCount(1L);
        when(store.metricsSnapshot("service-admin"))
                .thenReturn(snapshot)
                .thenThrow(new IllegalStateException("database unavailable"));
        ReliableMqOutboxRelayService service = new ReliableMqOutboxRelayService(
                store, producer, new ReliableMqOutboxProperties(), metrics, "service-admin");

        service.refreshMetrics();

        assertThat(registry.find(MqOutboxOperationalMetrics.SNAPSHOT_REFRESH)
                .tags("outbox_type", "reliable", "outcome", "success")
                .counter().count()).isEqualTo(1D);
        assertThatThrownBy(service::refreshMetrics)
                .isInstanceOf(IllegalStateException.class);
        assertThat(registry.find(MqOutboxOperationalMetrics.SNAPSHOT_REFRESH)
                .tags("outbox_type", "reliable", "outcome", "failed")
                .counter().count()).isEqualTo(1D);
    }

    /**
     * 创建待投递测试消息。
     */
    private ReliableMqOutboxDO event() {
        ReliableMqOutboxDO event = new ReliableMqOutboxDO();
        event.setId(1L);
        event.setEventId("MSG-OUTBOX-001");
        event.setTopic("audit-topic");
        event.setTag("audit-tag");
        event.setProducerService("service-admin");
        event.setTraceId("TRACE-001");
        event.setPayloadJson("{\"messageId\":\"MSG-OUTBOX-001\"}");
        event.setEventStatus("INIT");
        event.setRetryCount(0);
        event.setMaxRetryCount(3);
        event.setVersion(0);
        return event;
    }
}
