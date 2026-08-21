package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.data.entity.DataMerchantNotificationLogDO;
import com.scott.payment.data.entity.DataMerchantNotificationRetryOutboxDO;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.mapper.DataMerchantNotificationLogMapper;
import com.scott.payment.data.mapper.DataMerchantNotificationMapper;
import com.scott.payment.data.mapper.DataMerchantNotificationRetryOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationAttemptCommitServiceTests
 * @date : 2026-08-20 23:35
 * @email : scott_x@163.com
 * @description : 验证商户回调结果提交服务生成安全重试事件并同时持久化尝试日志
 * @status : create
 */
@Slf4j
class MerchantNotificationAttemptCommitServiceTests {

    @Test
    void shouldPersistAttemptAndRetryEventWithoutMerchantHttpProtocolData() {
        log.info("测试回调失败结果原子提交，关键输入: 第一次自动回调超时");
        DataMerchantNotificationMapper notificationMapper = mock(DataMerchantNotificationMapper.class);
        DataMerchantNotificationLogMapper logMapper = mock(DataMerchantNotificationLogMapper.class);
        DataMerchantNotificationRetryOutboxMapper outboxMapper = mock(DataMerchantNotificationRetryOutboxMapper.class);
        when(logMapper.insert(any())).thenReturn(1);
        when(notificationMapper.markFailed(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(outboxMapper.insert(any())).thenReturn(1);
        MerchantNotificationAttemptCommitService service =
                new MerchantNotificationAttemptCommitService(notificationMapper, logMapper, outboxMapper);
        DataMerchantNotificationTaskDO task = task();
        DataMerchantNotificationLogDO attemptLog = attemptLog();
        LocalDateTime finishedTime = LocalDateTime.of(2026, 8, 6, 12, 40);
        LocalDateTime nextRetryTime = finishedTime.plusMinutes(1);

        service.recordFailure(
                task, 1, "FAILED", nextRetryTime, "timeout", finishedTime, 1, attemptLog);

        verify(logMapper).insert(attemptLog);
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
        log.info("回调失败结果原子提交测试完成，结果: 日志、状态和安全 Outbox 均进入同一入口");
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

    private DataMerchantNotificationLogDO attemptLog() {
        DataMerchantNotificationLogDO logDO = new DataMerchantNotificationLogDO();
        logDO.setNotifyLogId("LOG-1");
        logDO.setNotifyId("NOTIFY-1");
        logDO.setCallbackEventId("EVENT-1");
        logDO.setDeliveryMode("AUTO");
        logDO.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 16, 0));
        return logDO;
    }
}
