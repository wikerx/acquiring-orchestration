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

        /**
         * transaction Notify Result，用于保存 In Memory Notification Service 中与 交易通知result 相关的业务属性。
         * <p>
         * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的启停取值；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final boolean transactionNotifyResult;

        /**
         * notify Transaction Called，用于保存 In Memory Notification Service 中与 通知交易called 相关的业务属性。
         * <p>
         * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的启停取值；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private boolean notifyTransactionCalled;

        /**
         * notify Due Called，用于保存 In Memory Notification Service 中与 通知duecalled 相关的业务属性。
         * <p>
         * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的启停取值；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private boolean notifyDueCalled;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与 operationId、merchantOrderNo 共同定位一笔平台交易。
         * </p>
         */
        private String transactionId;

        /**
         * limit，用于控制分页查询、批量扫描或任务单次处理规模。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
         * </p>
         */
        private int limit;

        private InMemoryNotificationService(boolean transactionNotifyResult) {
            this.transactionNotifyResult = transactionNotifyResult;
        }

        /**
         * 记录到期通知扫描是否被消费端触发及其批量上限，并固定返回一条已处理记录。
         */
        @Override
        public int notifyDue(LocalDateTime transactionDateTime, int limit) {
            this.notifyDueCalled = true;
            this.limit = limit;
            return 1;
        }

        /**
         * 捕获消费消息携带的平台交易号，并按用例预设值模拟单笔通知结果。
         */
        @Override
        public boolean notifyTransaction(LocalDateTime transactionDateTime, String transactionId) {
            this.notifyTransactionCalled = true;
            this.transactionId = transactionId;
            return transactionNotifyResult;
        }
    }
}
