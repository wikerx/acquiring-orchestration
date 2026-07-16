package com.scott.payment.payment.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.TransactionMerchantNotificationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationConsumerTests
 * @date : 2026-07-14 23:30
 * @email : scott_x@163.com
 * @description : 商户通知 MQ 消费者单元测试，验证交易事件优先按 transaction_id 精准触发通知，并在无单笔任务时降级扫表补偿。
 * @status : create
 */
class TransactionMerchantNotificationConsumerTests {

    /**
     * 消费交易事件时应优先按平台交易 ID 精准处理商户通知任务。
     */
    @Test
    void shouldNotifyTransactionFirstWhenEventConsumed() {
        InMemoryNotificationService notificationService = new InMemoryNotificationService(true);
        TransactionMerchantNotificationConsumer consumer = new TransactionMerchantNotificationConsumer(notificationService);

        consumer.onMessage(JsonUtils.toJsonString(message()));

        assertThat(notificationService.notifyTransactionCalled).isTrue();
        assertThat(notificationService.notifyDueCalled).isFalse();
        assertThat(notificationService.transactionId).isEqualTo("TX202607121000000010001");
    }

    /**
     * 单笔通知未命中时，应保留按分表扫描到期任务的补偿能力。
     */
    @Test
    void shouldFallbackToDueScanWhenTransactionNotificationNotReady() {
        InMemoryNotificationService notificationService = new InMemoryNotificationService(false);
        TransactionMerchantNotificationConsumer consumer = new TransactionMerchantNotificationConsumer(notificationService);

        consumer.onMessage(JsonUtils.toJsonString(message()));

        assertThat(notificationService.notifyTransactionCalled).isTrue();
        assertThat(notificationService.notifyDueCalled).isTrue();
        assertThat(notificationService.limit).isEqualTo(20);
    }

    private TransactionEventMessage message() {
        TransactionEventMessage message = new TransactionEventMessage();
        message.setMessageId("TX202607121000000010001");
        message.setTransactionId("TX202607121000000010001");
        message.setEventType(TransactionMqConstants.TRANSACTION_CREATED_TAG);
        message.setTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 0));
        return message;
    }

    private static class InMemoryNotificationService implements TransactionMerchantNotificationService {

        private final boolean transactionNotifyResult;

        private boolean notifyTransactionCalled;

        private boolean notifyDueCalled;

        private String transactionId;

        private int limit;

        private InMemoryNotificationService(boolean transactionNotifyResult) {
            this.transactionNotifyResult = transactionNotifyResult;
        }

        @Override
        public int notifyDue(LocalDateTime transactionDateTime, int limit) {
            this.notifyDueCalled = true;
            this.limit = limit;
            return 1;
        }

        @Override
        public boolean notifyTransaction(LocalDateTime transactionDateTime, String transactionId) {
            this.notifyTransactionCalled = true;
            this.transactionId = transactionId;
            return transactionNotifyResult;
        }
    }
}
