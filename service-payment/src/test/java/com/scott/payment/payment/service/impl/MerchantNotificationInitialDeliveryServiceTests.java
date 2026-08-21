package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationInitialDeliveryServiceTests
 * @date : 2026-08-20 22:30
 * @email : scott_x@163.com
 * @description : 验证商户通知首次 HTTP 尝试只能由五秒延时的交易 Outbox 事件触发
 * @status : create
 */
@Slf4j
class MerchantNotificationInitialDeliveryServiceTests {

    /** 首次商户通知必须冻结为不含回调协议敏感数据的五秒延时消息。 */
    @Test
    void shouldScheduleFirstAttemptFiveSecondsAfterNotificationBecomesReady() {
        log.info("用例开始：校验首次商户通知通过五秒延时 Outbox 调度，且消息不携带回调地址和载荷");
        TransactionEventOutboxService outboxService = mock(TransactionEventOutboxService.class);
        MerchantNotificationInitialDeliveryService service =
                new MerchantNotificationInitialDeliveryService(outboxService);
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 22, 30, 0, 125_000_000);
        TransactionMerchantNotificationDO notification = notification(now);

        service.schedule(notification, 0, now);

        ArgumentCaptor<TransactionEventOutboxDO> captor =
                ArgumentCaptor.forClass(TransactionEventOutboxDO.class);
        verify(outboxService).save(captor.capture());
        TransactionEventOutboxDO event = captor.getValue();
        MerchantNotificationRetryDueMessage message = JsonUtils.parseObject(
                event.getPayloadJson(), MerchantNotificationRetryDueMessage.class);
        assertThat(event.getEventNo()).isEqualTo(message.getMessageId());
        assertThat(event.getTag()).isEqualTo(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);
        assertThat(event.getNextRetryTime()).isEqualTo(now);
        assertThat(message.getNotifyId()).isEqualTo(notification.getNotifyId());
        assertThat(message.getExpectedVersion()).isZero();
        assertThat(message.getAttemptNo()).isEqualTo(1);
        assertThat(message.getDeliverAt()).isEqualTo(now.plusSeconds(5));
        assertThat(event.getPayloadJson())
                .doesNotContain("callbackUrl")
                .doesNotContain("merchant.example")
                .doesNotContain("payloadJson")
                .doesNotContain("secret");
        log.info("用例结果：首次通知事件延时五秒，MQ 快照仅包含任务定位和 CAS 信息");
    }

    /** 构造包含敏感回调数据的通知任务，验证这些字段不会进入 MQ。 */
    private TransactionMerchantNotificationDO notification(LocalDateTime now) {
        TransactionMerchantNotificationDO notification = new TransactionMerchantNotificationDO();
        notification.setNotifyId("TMN-20260820-0001");
        notification.setTransactionId("TX-20260820-0001");
        notification.setOperationId("OP-20260820-0001");
        notification.setMerchantId("200046");
        notification.setMerchantOrderNo("ORDER-0001");
        notification.setCallbackUrl("https://merchant.example/callback?token=secret");
        notification.setPayloadJson("{\"secret\":\"sensitive\"}");
        notification.setTransactionDateTime(now.minusMinutes(1));
        return notification;
    }
}
