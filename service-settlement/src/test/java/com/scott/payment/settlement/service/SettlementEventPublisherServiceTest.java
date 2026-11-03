package com.scott.payment.settlement.service;

import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证结算 Outbox 使用冻结的 operationId 分组发布，MQ 失败时只进入持久化退避。
 */
class SettlementEventPublisherServiceTest {

    @Test
    void shouldPublishFrozenPayloadOrderlyByOperationId() {
        SettlementEventOutboxPersistenceService persistence = mock(SettlementEventOutboxPersistenceService.class);
        MqProducer producer = mock(MqProducer.class);
        SettlementEventPublisherService service = new SettlementEventPublisherService(persistence, producer);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 13, 0);
        SettlementEventOutboxDO row = row();
        when(persistence.claimNext(now)).thenReturn(Optional.of(row));
        when(persistence.markSent(org.mockito.ArgumentMatchers.eq(row),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);

        assertThat(service.publishNext(now)).isTrue();

        verify(producer).sendSerializedOrderly(row.getTopic(), row.getTag(), row.getMessageKey(),
                row.getSettlementBatchNo(), row.getRetryCount(), row.getPayloadJson(), row.getMessageGroup());
    }

    @Test
    void shouldPersistFailureWhenRocketMqSendFails() {
        SettlementEventOutboxPersistenceService persistence = mock(SettlementEventOutboxPersistenceService.class);
        MqProducer producer = mock(MqProducer.class);
        SettlementEventPublisherService service = new SettlementEventPublisherService(persistence, producer);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 13, 5);
        SettlementEventOutboxDO row = row();
        when(persistence.claimNext(now)).thenReturn(Optional.of(row));
        doThrow(new IllegalStateException("broker unavailable")).when(producer)
                .sendSerializedOrderly(row.getTopic(), row.getTag(), row.getMessageKey(),
                        row.getSettlementBatchNo(), row.getRetryCount(), row.getPayloadJson(), row.getMessageGroup());
        when(persistence.markFailed(org.mockito.ArgumentMatchers.eq(row),
                org.mockito.ArgumentMatchers.eq("IllegalStateException"),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);

        assertThat(service.publishNext(now)).isTrue();

        verify(persistence).markFailed(org.mockito.ArgumentMatchers.eq(row),
                org.mockito.ArgumentMatchers.eq("IllegalStateException"),
                org.mockito.ArgumentMatchers.any());
    }

    /**
     * 汇率使用 Topic 是普通消息，不得复用交易投影的 FIFO 发送入口。
     */
    @Test
    void shouldPublishExchangeRateUsageAsNormalMessage() {
        SettlementEventOutboxPersistenceService persistence = mock(SettlementEventOutboxPersistenceService.class);
        MqProducer producer = mock(MqProducer.class);
        SettlementEventPublisherService service = new SettlementEventPublisherService(persistence, producer);
        LocalDateTime now = LocalDateTime.of(2026, 11, 1, 13, 0);
        SettlementEventOutboxDO row = row();
        row.setTopic(MqTopic.EXCHANGE_RATE_USAGE);
        row.setTag("EXCHANGE_RATE_USAGE");
        when(persistence.claimNext(now)).thenReturn(Optional.of(row));
        when(persistence.markSent(org.mockito.ArgumentMatchers.eq(row),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);

        assertThat(service.publishNext(now)).isTrue();

        verify(producer).sendSerialized(row.getTopic(), row.getTag(), row.getMessageKey(),
                row.getSettlementBatchNo(), row.getRetryCount(), row.getPayloadJson());
    }

    private SettlementEventOutboxDO row() {
        SettlementEventOutboxDO row = new SettlementEventOutboxDO();
        row.setEventNo("SETTLEMENT:SP01");
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setTopic("payment-transaction-fifo");
        row.setTag("TRANSACTION_SETTLEMENT_COMPLETED");
        row.setMessageKey("SETTLEMENT:SP01");
        row.setMessageGroup("OP-1001");
        row.setPayloadJson("{\"messageId\":\"SETTLEMENT:SP01\"}");
        row.setRetryCount(0);
        return row;
    }
}
