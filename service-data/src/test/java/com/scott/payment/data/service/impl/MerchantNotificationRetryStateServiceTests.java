package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.data.entity.DataMerchantNotificationRetryOutboxDO;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.mapper.DataMerchantNotificationMapper;
import com.scott.payment.data.mapper.DataMerchantNotificationRetryOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证通知失败状态和自动重试 Outbox 在同一业务入口中形成一致事件。 */
class MerchantNotificationRetryStateServiceTests {

    @Test
    void shouldPersistRetryEventWithoutMerchantHttpProtocolData() {
        DataMerchantNotificationMapper notificationMapper = mock(DataMerchantNotificationMapper.class);
        DataMerchantNotificationRetryOutboxMapper outboxMapper = mock(DataMerchantNotificationRetryOutboxMapper.class);
        when(notificationMapper.markFailed(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(outboxMapper.insert(any())).thenReturn(1);
        MerchantNotificationRetryStateService service =
                new MerchantNotificationRetryStateService(notificationMapper, outboxMapper);
        DataMerchantNotificationTaskDO task = task();
        LocalDateTime finishedTime = LocalDateTime.of(2026, 8, 6, 12, 40);
        LocalDateTime nextRetryTime = finishedTime.plusMinutes(1);

        service.recordFailure(task, 1, "FAILED", nextRetryTime, "timeout", finishedTime, 1);

        ArgumentCaptor<DataMerchantNotificationRetryOutboxDO> captor =
                ArgumentCaptor.forClass(DataMerchantNotificationRetryOutboxDO.class);
        verify(outboxMapper).insert(captor.capture());
        DataMerchantNotificationRetryOutboxDO event = captor.getValue();
        MerchantNotificationRetryDueMessage message = JsonUtils.parseObject(
                event.getPayloadJson(), MerchantNotificationRetryDueMessage.class);
        assertThat(event.getEventNo()).isEqualTo(message.getMessageId());
        assertThat(event.getTag()).isEqualTo(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);
        assertThat(message.getNotifyId()).isEqualTo(task.getNotifyId());
        assertThat(message.getExpectedVersion()).isEqualTo(2);
        assertThat(message.getAttemptNo()).isEqualTo(2);
        assertThat(message.getDeliverAt()).isEqualTo(nextRetryTime);
        assertThat(event.getPayloadJson())
                .doesNotContain("Authorization")
                .doesNotContain("callbackUrl")
                .doesNotContain("payloadJson")
                .doesNotContain("jwt");
    }

    private DataMerchantNotificationTaskDO task() {
        DataMerchantNotificationTaskDO task = new DataMerchantNotificationTaskDO();
        task.setId(1L);
        task.setNotifyId("NOTIFY-1");
        task.setTransactionId("TX-1");
        task.setOperationId("OP-1");
        task.setMerchantId("M-1");
        task.setMerchantOrderNo("MO-1");
        task.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 16, 0));
        return task;
    }
}
