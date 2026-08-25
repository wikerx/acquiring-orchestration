package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionEventOutboxRelayServiceTests
 * @date : 2026-07-12 22:43
 * @email : scott_x@163.com
 * @description : Default Transaction Event Outbox Relay Service Tests 自动化测试类，位于 支付核心服务，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
class DefaultTransactionEventOutboxRelayServiceTests {

    @Test
    void shouldPublishDueEventAndMarkSent() {
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(event());
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService = new DefaultTransactionEventOutboxRelayService(
                eventOutboxService,
                mqProducer);

        int successCount = relayService.publishDueEvents(LocalDateTime.of(2026, 7, 12, 10, 0), 10);

        assertThat(successCount).isEqualTo(1);
        assertThat(mqProducer.sent).isTrue();
        assertThat(mqProducer.message).isInstanceOf(TransactionEventMessage.class);
        assertThat(((TransactionEventMessage) mqProducer.message).getTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 12, 10, 0));
        assertThat(eventOutboxService.eventDO.getEventStatus()).isEqualTo("SENT");
        assertThat(eventOutboxService.eventDO.getSentTime()).isNotNull();
    }

    @Test
    void shouldMarkFailedWhenMqSendThrowsException() {
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(event());
        CapturingMqProducer mqProducer = new CapturingMqProducer(
                new IllegalStateException("send failed, secretKey=must-not-be-persisted"));
        DefaultTransactionEventOutboxRelayService relayService = new DefaultTransactionEventOutboxRelayService(
                eventOutboxService,
                mqProducer);

        int successCount = relayService.publishDueEvents(LocalDateTime.of(2026, 7, 12, 10, 0), 10);

        assertThat(successCount).isZero();
        assertThat(eventOutboxService.eventDO.getEventStatus()).isEqualTo("FAILED");
        assertThat(eventOutboxService.eventDO.getFailReason()).isEqualTo("IllegalStateException");
        assertThat(eventOutboxService.eventDO.getFailReason()).doesNotContain("must-not-be-persisted");
        assertThat(eventOutboxService.eventDO.getNextRetryTime()).isNotNull();
    }

    @Test
    void shouldNotRepublishAlreadySentEventWhenOutboxIsScannedAgain() {
        TransactionEventOutboxDO eventDO = event();
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(eventDO);
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService = new DefaultTransactionEventOutboxRelayService(
                eventOutboxService,
                mqProducer);

        int firstSuccessCount = relayService.publishDueEvents(LocalDateTime.of(2026, 7, 12, 10, 0), 10);
        int secondSuccessCount = relayService.publishDueEvents(LocalDateTime.of(2026, 7, 12, 10, 0), 10);

        assertThat(firstSuccessCount).isEqualTo(1);
        assertThat(secondSuccessCount).isZero();
        assertThat(mqProducer.sendCount).isEqualTo(1);
        assertThat(eventDO.getEventStatus()).isEqualTo("SENT");
    }

    /** 自动商户通知事件必须按消息内 deliverAt 使用 RocketMQ 绝对定时投递。 */
    @Test
    void shouldPublishMerchantNotificationRetryAsScheduledMessage() {
        LocalDateTime deliverAt = LocalDateTime.now().plusMinutes(5);
        MerchantNotificationRetryDueMessage message = new MerchantNotificationRetryDueMessage();
        message.setMessageId("MNR-DUE-1");
        message.setEventType(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);
        message.setNotifyId("NOTIFY-1");
        message.setTransactionId("TX-1");
        message.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 16, 0));
        message.setExpectedVersion(2);
        message.setAttemptNo(2);
        message.setDeliverAt(deliverAt);
        TransactionEventOutboxDO event = event();
        event.setTag(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);
        event.setEventType(MqTag.MERCHANT_NOTIFICATION_RETRY_DUE);
        event.setPayloadJson(JsonUtils.toJsonString(message));
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(event);
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService =
                new DefaultTransactionEventOutboxRelayService(eventOutboxService, mqProducer);

        int successCount = relayService.publishDueEvents(event.getTransactionDateTime(), 10);

        assertThat(successCount).isEqualTo(1);
        assertThat(mqProducer.scheduled).isTrue();
        assertThat(mqProducer.message).isInstanceOf(MerchantNotificationRetryDueMessage.class);
        assertThat(mqProducer.deliverAt).isEqualTo(
                deliverAt.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
    }

    @Test
    void shouldNotSendWhenAnotherInstanceAlreadyClaimedEvent() {
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(event());
        eventOutboxService.claimAllowed = false;
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService =
                new DefaultTransactionEventOutboxRelayService(eventOutboxService, mqProducer);

        int successCount = relayService.publishDueEvents(LocalDateTime.of(2026, 7, 12, 10, 0), 10);

        assertThat(successCount).isZero();
        assertThat(mqProducer.sendCount).isZero();
    }

    @Test
    void shouldFailInvalidPayloadWithoutSendingEmptyTransactionEvent() {
        TransactionEventOutboxDO invalidEvent = event();
        invalidEvent.setPayloadJson("not-json");
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(invalidEvent);
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService =
                new DefaultTransactionEventOutboxRelayService(eventOutboxService, mqProducer);

        int successCount = relayService.publishDueEvents(invalidEvent.getTransactionDateTime(), 10);

        assertThat(successCount).isZero();
        assertThat(mqProducer.sendCount).isZero();
        assertThat(invalidEvent.getEventStatus()).isEqualTo("FAILED");
    }

    @Test
    void shouldUseMessageGroupForOrderedTransactionDelivery() {
        TransactionEventOutboxDO orderedEvent = event();
        orderedEvent.setMessageGroup("TX202607121000000010001");
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(orderedEvent);
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService =
                new DefaultTransactionEventOutboxRelayService(eventOutboxService, mqProducer);

        int successCount = relayService.publishDueEvents(orderedEvent.getTransactionDateTime(), 10);

        assertThat(successCount).isEqualTo(1);
        assertThat(mqProducer.messageGroup).isEqualTo("TX202607121000000010001");
    }

    private TransactionEventOutboxDO event() {
        TransactionEventMessage message = new TransactionEventMessage();
        message.setMessageId("TX202607121000000010001");
        message.setCreatedAt(LocalDateTime.of(2026, 7, 12, 10, 0));
        message.setTransactionId("TX202607121000000010001");
        message.setOperationId("OP202607121000000010001");
        message.setMerchantId("200001");
        message.setMerchantOrderNo("M202607120001");
        message.setTransactionType("AUTHORIZATION");
        message.setTransactionStatus("SUCCESS");
        message.setEventType("TRANSACTION_CREATED");
        message.setTransactionDateTime(LocalDateTime.of(2026, 7, 12, 10, 0));
        TransactionEventOutboxDO eventDO = new TransactionEventOutboxDO();
        eventDO.setId(1L);
        eventDO.setEventNo("TX202607121000000010001");
        eventDO.setMessageKey("TX202607121000000010001");
        eventDO.setTopic("PAYMENT_EVENT");
        eventDO.setTag("TRANSACTION_CREATED");
        eventDO.setMessageGroup("OP202607121000000010001");
        eventDO.setPayloadJson(JsonUtils.toJsonString(message));
        eventDO.setEventTime(message.getCreatedAt());
        eventDO.setTransactionDateTime(message.getTransactionDateTime());
        eventDO.setEventStatus("INIT");
        eventDO.setRetryCount(0);
        eventDO.setMaxRetryCount(10);
        eventDO.setVersion(0);
        return eventDO;
    }

    private static class CapturingMqProducer implements MqProducer {

        /**
         * fail，用于保存 Capturing MQ Producer 中与 fail 相关的业务属性。
         * <p>
         * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的启停取值；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final RuntimeException failure;

        /**
         * sent，用于保存 Capturing MQ Producer 中与 sent 相关的业务属性。
         * <p>
         * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的启停取值；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private boolean sent;

        /**
         * send Count，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private int sendCount;

        /**
         * message，用于保存 Capturing MQ Producer 中与 message 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private BaseMqMessage message;

        /** 是否使用定时消息接口。 */
        private boolean scheduled;

        /** 捕获的绝对投递时间。 */
        private Instant deliverAt;

        /** 捕获的顺序消息分组键。 */
        private String messageGroup;

        private CapturingMqProducer(RuntimeException failure) {
            this.failure = failure;
        }

        /**
         * 按用例配置模拟 MQ 发送成功或抛出异常；成功时捕获消息并累计发送次数。
         */
        @Override
        public void send(String topic, String tag, BaseMqMessage message) {
            if (failure != null) {
                throw failure;
            }
            this.message = message;
            this.sent = true;
            this.sendCount++;
        }

        /** 捕获顺序消息及业务分组键。 */
        @Override
        public void sendOrderly(String topic, String tag, BaseMqMessage message, String messageGroup) {
            send(topic, tag, message);
            this.messageGroup = messageGroup;
        }

        /** 捕获定时消息及其绝对投递时间。 */
        @Override
        public void sendAt(String topic, String tag, BaseMqMessage message, Instant deliverAt) {
            if (failure != null) {
                throw failure;
            }
            this.message = message;
            this.deliverAt = deliverAt;
            this.scheduled = true;
            this.sent = true;
            this.sendCount++;
        }
    }

    private static class InMemoryEventOutboxService implements TransactionEventOutboxService {

        /**
         * event DO，用于保存 In Memory Event Outbox Service 中与 eventdo 相关的业务属性。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final TransactionEventOutboxDO eventDO;

        /** 模拟多实例 CAS 抢占是否成功。 */
        private boolean claimAllowed = true;

        private InMemoryEventOutboxService(TransactionEventOutboxDO eventDO) {
            this.eventDO = eventDO;
        }

        /**
         * 不执行新增写入，因为该内存替身的待发送事件由构造器预置。
         */
        @Override
        public void save(TransactionEventOutboxDO eventDO) {
        }

        /**
         * 仅在预置事件尚未发送或关闭时返回该事件，模拟到期事件扫描。
         */
        @Override
        public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
            return "SENT".equals(eventDO.getEventStatus()) || "CLOSED".equals(eventDO.getEventStatus())
                    ? List.of()
                    : List.of(eventDO);
        }

        /** 不存在超时记录时返回零。 */
        @Override
        public int recoverStaleProcessing(LocalDateTime eventTime,
                                          LocalDateTime staleBefore,
                                          LocalDateTime now) {
            return 0;
        }

        /** 模拟版本 CAS 抢占并推进 PROCESSING。 */
        @Override
        public boolean claimForPublish(TransactionEventOutboxDO eventDO, LocalDateTime claimedTime) {
            if (!claimAllowed) {
                return false;
            }
            eventDO.setEventStatus("PROCESSING");
            eventDO.setVersion(eventDO.getVersion() + 1);
            return true;
        }

        /**
         * 在内存中推进为已发送状态并递增版本，模拟成功的乐观锁更新。
         */
        @Override
        public boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime) {
            eventDO.setEventStatus("SENT");
            eventDO.setSentTime(sentTime);
            eventDO.setVersion(eventDO.getVersion() + 1);
            return true;
        }

        /**
         * 在内存中记录失败原因和下次重试时间，并递增版本模拟 CAS 成功。
         */
        @Override
        public boolean markFailed(TransactionEventOutboxDO eventDO,
                                  LocalDateTime nextRetryTime,
                                  String failReason,
                                  LocalDateTime now) {
            eventDO.setEventStatus("FAILED");
            eventDO.setNextRetryTime(nextRetryTime);
            eventDO.setFailReason(failReason);
            eventDO.setVersion(eventDO.getVersion() + 1);
            return true;
        }
    }
}
