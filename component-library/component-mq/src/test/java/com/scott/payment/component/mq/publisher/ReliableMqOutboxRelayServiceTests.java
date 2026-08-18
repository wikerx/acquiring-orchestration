package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.db.outbox.service.ReliableMqOutboxStore;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.component.mq.properties.ReliableMqOutboxProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqOutboxRelayServiceTests
 * @date : 2026-08-02 22:25
 * @email : scott_x@163.com
 * @description : Outbox Relay CAS、原始快照投递和失败重试状态测试
 * @status : create
 */
@Slf4j
class ReliableMqOutboxRelayServiceTests {

    /** CAS 抢占成功后应发送冻结 JSON，并以抢占后的版本标记 SENT。 */
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
                store, producer, new ReliableMqOutboxProperties());

        assertThat(service.relayEvent("MSG-OUTBOX-001")).isTrue();

        verify(producer).sendSerialized(
                "audit-topic", "audit-tag", "MSG-OUTBOX-001", "TRACE-001", 0, "{\"messageId\":\"MSG-OUTBOX-001\"}");
        verify(store).markSent(eq(1L), eq(1), any(LocalDateTime.class));
        log.info("Outbox正常投递测试完成，结果: SENT CAS成功");
    }

    /** MQ 异常不得标记成功，应进入 RETRY_WAIT 且只保存异常类型。 */
    @Test
    void shouldScheduleRetryWhenMqDeliveryFails() {
        log.info("测试Outbox失败重试，关键输入: MQ发送异常、未耗尽重试");
        ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
        MqProducer producer = mock(MqProducer.class);
        ReliableMqOutboxDO event = event();
        when(store.findByEventId("MSG-OUTBOX-001")).thenReturn(event);
        when(store.claim(eq(1L), eq(0), any(LocalDateTime.class))).thenReturn(1);
        doThrow(new IllegalStateException("internal endpoint detail"))
                .when(producer).sendSerialized(any(), any(), any(), any(), eq(0), any());
        ReliableMqOutboxRelayService service = new ReliableMqOutboxRelayService(
                store, producer, new ReliableMqOutboxProperties());

        assertThat(service.relayEvent("MSG-OUTBOX-001")).isFalse();

        verify(store).markFailed(
                eq(1L), eq(1), eq("RETRY_WAIT"), any(LocalDateTime.class),
                eq("IllegalStateException"), any(LocalDateTime.class));
        log.info("Outbox失败重试测试完成，结果: RETRY_WAIT且未保存异常详情");
    }

    /** 创建待投递测试消息。 */
    private ReliableMqOutboxDO event() {
        ReliableMqOutboxDO event = new ReliableMqOutboxDO();
        event.setId(1L);
        event.setEventId("MSG-OUTBOX-001");
        event.setTopic("audit-topic");
        event.setTag("audit-tag");
        event.setTraceId("TRACE-001");
        event.setPayloadJson("{\"messageId\":\"MSG-OUTBOX-001\"}");
        event.setEventStatus("INIT");
        event.setRetryCount(0);
        event.setMaxRetryCount(3);
        event.setVersion(0);
        return event;
    }
}
