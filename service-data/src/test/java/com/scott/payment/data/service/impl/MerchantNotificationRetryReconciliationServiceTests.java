package com.scott.payment.data.service.impl;

import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.mapper.DataMerchantNotificationMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证低频 Job 对账会覆盖全部已发布季度，并且只补发 MQ 事件。 */
class MerchantNotificationRetryReconciliationServiceTests {

    @Test
    void shouldRequeueDueTasksAcrossPublishedQuartersWithoutCallingMerchant() {
        DataMerchantNotificationMapper mapper = mock(DataMerchantNotificationMapper.class);
        ReliableMqPublisher publisher = mock(ReliableMqPublisher.class);
        TransactionShardingProperties sharding = new TransactionShardingProperties();
        sharding.setPhysicalNodes(List.of("202601", "202602", "202603", "202604"));
        when(mapper.selectStaleProcessing(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(mapper.selectDueForNotify(
                eq(LocalDateTime.of(2026, 4, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 7, 1, 0, 0)), any(), eq(5)))
                .thenReturn(List.of(task("NOTIFY-Q2", "TX-Q2", LocalDateTime.of(2026, 5, 1, 10, 0))));
        when(mapper.selectDueForNotify(
                eq(LocalDateTime.of(2026, 7, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)), any(), eq(5)))
                .thenReturn(List.of(task("NOTIFY-Q3", "TX-Q3", LocalDateTime.of(2026, 8, 1, 10, 0))));
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-06T04:45:00Z"), ZoneId.of("Asia/Shanghai"));
        MerchantNotificationRetryReconciliationService service =
                new MerchantNotificationRetryReconciliationService(mapper, publisher, sharding, clock, 120, 100);

        int queued = service.reconcile(5, List.of());

        assertThat(queued).isEqualTo(2);
        ArgumentCaptor<MerchantNotificationRetryDueMessage> messageCaptor =
                ArgumentCaptor.forClass(MerchantNotificationRetryDueMessage.class);
        verify(publisher, times(2)).publish(
                any(), eq(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE), messageCaptor.capture());
        assertThat(messageCaptor.getAllValues()).extracting(MerchantNotificationRetryDueMessage::getNotifyId)
                .containsExactly("NOTIFY-Q3", "NOTIFY-Q2");
        assertThat(messageCaptor.getAllValues()).allSatisfy(message -> {
            assertThat(message.getExpectedVersion()).isEqualTo(4);
            assertThat(message.getAttemptNo()).isEqualTo(2);
        });
        verify(mapper, never()).selectDueForNotify(
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)), any(), any(), anyInt());
    }

    private DataMerchantNotificationTaskDO task(String notifyId,
                                                String transactionId,
                                                LocalDateTime transactionDateTime) {
        DataMerchantNotificationTaskDO task = new DataMerchantNotificationTaskDO();
        task.setNotifyId(notifyId);
        task.setTransactionId(transactionId);
        task.setTransactionDateTime(transactionDateTime);
        task.setVersion(4);
        task.setLastAttemptNo(1);
        return task;
    }
}
