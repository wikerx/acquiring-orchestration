package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.TransactionShardingMode;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.db.sharding.TransactionShardingRuntimeState;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.mapper.TransactionEventOutboxMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionEventOutboxServiceTests
 * @date : 2026-08-02 02:40
 * @email : scott_x@163.com
 * @description : 验证 Outbox 在 SHARDINGSPHERE 模式下只访问逻辑表，并按季度范围扫描及按分片时间执行 CAS。
 * @status : create
 */
class DefaultTransactionEventOutboxServiceTests {

    @Test
    void shardingModeShouldInsertOnlyThroughLogicalMapper() {
        TransactionEventOutboxMapper mapper = mock(TransactionEventOutboxMapper.class);
        ShardingDataTemplate legacyTemplate = mock(ShardingDataTemplate.class);
        DefaultTransactionEventOutboxService service = service(mapper, legacyTemplate);
        TransactionEventOutboxDO eventDO = event(LocalDateTime.of(2026, 8, 2, 2, 40));
        when(mapper.insertLogical(eventDO)).thenReturn(1);

        service.save(eventDO);

        verify(mapper).insertLogical(eventDO);
        verify(legacyTemplate, never()).insert(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shardingModeShouldScanOnlyTheEventQuarter() {
        TransactionEventOutboxMapper mapper = mock(TransactionEventOutboxMapper.class);
        ShardingDataTemplate legacyTemplate = mock(ShardingDataTemplate.class);
        DefaultTransactionEventOutboxService service = service(mapper, legacyTemplate);
        LocalDateTime eventTime = LocalDateTime.of(2026, 9, 30, 23, 59, 59);
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 10, 0);
        List<TransactionEventOutboxDO> expected = List.of(event(eventTime));
        when(mapper.selectDueForPublishLogical(
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 10, 1, 0, 0),
                now,
                20)).thenReturn(expected);

        assertThat(service.listDueEvents(eventTime, now, 20)).isSameAs(expected);
    }

    @Test
    void shardingModeShouldUseTransactionTimeAndVersionForStatusCas() {
        TransactionEventOutboxMapper mapper = mock(TransactionEventOutboxMapper.class);
        DefaultTransactionEventOutboxService service = service(mapper, mock(ShardingDataTemplate.class));
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 2, 2, 40);
        LocalDateTime sentTime = transactionTime.plusMinutes(1);
        LocalDateTime retryTime = transactionTime.plusMinutes(5);
        TransactionEventOutboxDO eventDO = event(transactionTime);
        eventDO.setId(101L);
        eventDO.setVersion(3);
        when(mapper.markSentLogical(101L, transactionTime, 3, sentTime)).thenReturn(1);
        when(mapper.markFailedLogical(101L, transactionTime, 3, retryTime, "timeout", sentTime)).thenReturn(1);

        assertThat(service.markSent(eventDO, sentTime)).isTrue();
        assertThat(service.markFailed(eventDO, retryTime, "timeout", sentTime)).isTrue();
    }

    private DefaultTransactionEventOutboxService service(TransactionEventOutboxMapper mapper,
                                                         ShardingDataTemplate legacyTemplate) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setMode(TransactionShardingMode.SHARDINGSPHERE);
        TransactionShardingRuntimeState runtimeState = new TransactionShardingRuntimeState();
        runtimeState.activate(properties);
        return new DefaultTransactionEventOutboxService(mapper, legacyTemplate, runtimeState);
    }

    private TransactionEventOutboxDO event(LocalDateTime transactionTime) {
        TransactionEventOutboxDO eventDO = new TransactionEventOutboxDO();
        eventDO.setEventNo("event-1");
        eventDO.setAggregateType("PAYMENT_TRANSACTION");
        eventDO.setAggregateNo("operation-1");
        eventDO.setEventType("PAYMENT_COMPLETED");
        eventDO.setEventStatus("INIT");
        eventDO.setTopic("payment-event");
        eventDO.setMessageKey("event-1");
        eventDO.setTransactionDateTime(transactionTime);
        eventDO.setEventTime(transactionTime);
        return eventDO;
    }
}
