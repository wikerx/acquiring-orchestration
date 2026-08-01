package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.service.MerchantNotificationDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationConsumerTests
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知事件消费测试，覆盖精确触发、有界补偿和畸形消息跳过
 * @status : create
 */
@Slf4j
class TransactionMerchantNotificationConsumerTests {

    /** 单笔任务成功时不应继续扫描同分表。 */
    @Test
    void shouldNotifyTransactionWithoutFallbackWhenTaskSucceeds() {
        log.info("测试商户通知事件精确触发，关键输入: 单笔通知成功");
        InMemoryDeliveryService deliveryService = new InMemoryDeliveryService(true);
        TransactionMerchantNotificationConsumer consumer = consumer(deliveryService);

        consumer.onMessage(JsonUtils.toJsonString(message()));

        assertThat(deliveryService.notifyTransactionCalled).isTrue();
        assertThat(deliveryService.notifyDueCalled).isFalse();
        assertThat(deliveryService.transactionId).isEqualTo("TX202608011600000000001");
        log.info("商户通知事件精确触发测试完成，结果: 未执行补偿扫描");
    }

    /** 单笔任务未命中时应按配置批量执行同分表补偿。 */
    @Test
    void shouldFallbackToBoundedDueScanWhenTaskIsNotReady() {
        log.info("测试商户通知事件补偿扫描，关键输入: 单笔未命中、批量上限 20");
        InMemoryDeliveryService deliveryService = new InMemoryDeliveryService(false);
        TransactionMerchantNotificationConsumer consumer = consumer(deliveryService);

        consumer.onMessage(JsonUtils.toJsonString(message()));

        assertThat(deliveryService.notifyDueCalled).isTrue();
        assertThat(deliveryService.limit).isEqualTo(20);
        log.info("商户通知事件补偿扫描测试完成，结果: 按配置执行有界扫描");
    }

    /** 畸形 JSON 不应访问数据库投递服务。 */
    @Test
    void shouldSkipMalformedPayloadWithoutDelivery() {
        log.info("测试商户通知畸形消息，关键输入: 非法 JSON");
        InMemoryDeliveryService deliveryService = new InMemoryDeliveryService(false);
        TransactionMerchantNotificationConsumer consumer = consumer(deliveryService);

        consumer.onMessage("{invalid-json");

        assertThat(deliveryService.notifyTransactionCalled).isFalse();
        assertThat(deliveryService.notifyDueCalled).isFalse();
        log.info("商户通知畸形消息测试完成，结果: 未访问通知数据库");
    }

    /** 创建使用默认补偿批量的消费者。 */
    private TransactionMerchantNotificationConsumer consumer(InMemoryDeliveryService deliveryService) {
        return new TransactionMerchantNotificationConsumer(
                deliveryService,
                new DataMerchantNotificationProperties());
    }

    /** 构造共享支付事件契约。 */
    private PaymentTransactionEventMessage message() {
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("MSG202608011600000000001");
        message.setTransactionId("TX202608011600000000001");
        message.setEventType(MqTag.TRANSACTION_STATUS_CHANGED);
        message.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 16, 0));
        return message;
    }

    /** 记录消费调用的内存通知服务。 */
    private static class InMemoryDeliveryService implements MerchantNotificationDeliveryService {

        /** 单笔通知预设结果。 */
        private final boolean transactionResult;

        /** 是否调用过单笔通知。 */
        private boolean notifyTransactionCalled;

        /** 是否调用过到期扫描。 */
        private boolean notifyDueCalled;

        /** 消费者传入的平台交易 ID。 */
        private String transactionId;

        /** 消费者传入的补偿批量。 */
        private int limit;

        private InMemoryDeliveryService(boolean transactionResult) {
            this.transactionResult = transactionResult;
        }

        /** 记录到期扫描参数并返回一条成功记录。 */
        @Override
        public int notifyDue(LocalDateTime transactionDateTime, int limit) {
            this.notifyDueCalled = true;
            this.limit = limit;
            return 1;
        }

        /** 记录单笔通知参数并返回预设结果。 */
        @Override
        public boolean notifyTransaction(LocalDateTime transactionDateTime, String transactionId) {
            this.notifyTransactionCalled = true;
            this.transactionId = transactionId;
            return transactionResult;
        }
    }
}
