package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.enums.PaymentTransactionEventStatus;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
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
 * @description : service-data 商户通知事件消费测试，覆盖终态精确触发、创建事件隔离和畸形消息跳过
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
        assertThat(deliveryService.transactionDateTime)
                .isEqualTo(LocalDateTime.of(2026, 8, 1, 16, 0, 0, 255_000_000));
        log.info("商户通知事件精确触发测试完成，结果: 未执行补偿扫描");
    }

    /** 终态事件精确任务未命中时不得顺带投递其他交易，补偿由独立 Job 负责。 */
    @Test
    void shouldNotFallbackToOtherTransactionsWhenExactTaskIsNotReady() {
        log.info("测试商户通知事件精确边界，关键输入: 终态事件单笔未命中");
        InMemoryDeliveryService deliveryService = new InMemoryDeliveryService(false);
        TransactionMerchantNotificationConsumer consumer = consumer(deliveryService);

        consumer.onMessage(JsonUtils.toJsonString(message()));

        assertThat(deliveryService.notifyTransactionCalled).isTrue();
        assertThat(deliveryService.notifyDueCalled).isFalse();
        log.info("商户通知事件精确边界测试完成，结果: 未投递其他交易");
    }

    /** 创建事件早于渠道终态，不得触发尚未激活的商户通知。 */
    @Test
    void shouldIgnoreTransactionCreatedEvent() {
        log.info("测试创建事件通知边界，关键输入: TRANSACTION_CREATED");
        InMemoryDeliveryService deliveryService = new InMemoryDeliveryService(false);
        TransactionMerchantNotificationConsumer consumer = consumer(deliveryService);
        PaymentTransactionEventMessage message = message();
        message.setEventType(MqTag.TRANSACTION_CREATED);

        consumer.onMessage(JsonUtils.toJsonString(message));

        assertThat(deliveryService.notifyTransactionCalled).isFalse();
        assertThat(deliveryService.notifyDueCalled).isFalse();
        log.info("创建事件通知边界测试完成，结果: 未访问通知任务");
    }

    /** 状态变更 Tag 只有携带成功或失败终态时才能触发商户通知。 */
    @Test
    void shouldIgnoreNonTerminalStatusChangedEvent() {
        log.info("测试非终态通知边界，关键输入: PROCESSING 状态变更事件");
        InMemoryDeliveryService deliveryService = new InMemoryDeliveryService(false);
        TransactionMerchantNotificationConsumer consumer = consumer(deliveryService);
        PaymentTransactionEventMessage message = message();
        message.setTransactionStatus(PaymentTransactionEventStatus.PROCESSING.getCode());

        consumer.onMessage(JsonUtils.toJsonString(message));

        assertThat(deliveryService.notifyTransactionCalled).isFalse();
        assertThat(deliveryService.notifyDueCalled).isFalse();
        log.info("非终态通知边界测试完成，结果: 未访问通知任务");
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

    /** 第一版事件缺少真实分片时间时必须拒绝消费，不允许解析交易号或全分片检索。 */
    @Test
    void shouldSkipMessageWithoutTransactionDateTime() {
        InMemoryDeliveryService deliveryService = new InMemoryDeliveryService(false);
        TransactionMerchantNotificationConsumer consumer = consumer(deliveryService);
        PaymentTransactionEventMessage message = message();
        message.setTransactionDateTime(null);

        consumer.onMessage(JsonUtils.toJsonString(message));

        assertThat(deliveryService.notifyTransactionCalled).isFalse();
        assertThat(deliveryService.notifyDueCalled).isFalse();
    }

    /** 创建仅处理消息对应交易的消费者。 */
    private TransactionMerchantNotificationConsumer consumer(InMemoryDeliveryService deliveryService) {
        return new TransactionMerchantNotificationConsumer(deliveryService);
    }

    /** 构造共享支付事件契约。 */
    private PaymentTransactionEventMessage message() {
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("MSG202608011600000000001");
        message.setTransactionId("TX202608011600000000001");
        message.setEventType(MqTag.TRANSACTION_STATUS_CHANGED);
        message.setTransactionStatus(PaymentTransactionEventStatus.SUCCESS.getCode());
        message.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 16, 0, 0, 255_000_000));
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

        /** 消费者传入的真实交易分片时间。 */
        private LocalDateTime transactionDateTime;

        /** 消费者传入的补偿批量。 */
        private int limit;

        private InMemoryDeliveryService(boolean transactionResult) {
            this.transactionResult = transactionResult;
        }

        /** 记录到期扫描参数并返回一条成功记录。 */
        @Override
        public int notifyDue(LocalDateTime transactionDateTime, int limit) {
            this.notifyDueCalled = true;
            this.transactionDateTime = transactionDateTime;
            this.limit = limit;
            return 1;
        }

        /** 记录单笔通知参数并返回预设结果。 */
        @Override
        public boolean notifyTransaction(LocalDateTime transactionDateTime, String transactionId) {
            this.notifyTransactionCalled = true;
            this.transactionDateTime = transactionDateTime;
            this.transactionId = transactionId;
            return transactionResult;
        }
    }
}
