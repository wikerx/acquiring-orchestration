package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.message.MerchantNotificationRetryDueMessage;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
        TransactionEventOutboxDO event = event();
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(event);
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService = new DefaultTransactionEventOutboxRelayService(
                eventOutboxService,
                mqProducer);

        int successCount = relayService.publishDueEvents(LocalDateTime.of(2026, 7, 12, 10, 0), 10);

        assertThat(successCount).isEqualTo(1);
        assertThat(mqProducer.sent).isTrue();
        assertThat(mqProducer.payloadJson).isEqualTo(event.getPayloadJson());
        assertThat(JsonUtils.parseObject(mqProducer.payloadJson, TransactionEventMessage.class)
                .getTransactionDateTime())
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
        assertThat(mqProducer.payloadJson).isEqualTo(event.getPayloadJson());
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
        orderedEvent.setDeliveryMode("ORDERLY");
        orderedEvent.setMessageGroup("TX202607121000000010001");
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(orderedEvent);
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService =
                new DefaultTransactionEventOutboxRelayService(eventOutboxService, mqProducer);

        int successCount = relayService.publishDueEvents(orderedEvent.getTransactionDateTime(), 10);

        assertThat(successCount).isEqualTo(1);
        assertThat(mqProducer.deliveryMethod).isEqualTo("ORDERLY");
        assertThat(mqProducer.messageGroup).isEqualTo("TX202607121000000010001");
        assertThat(mqProducer.payloadJson).isEqualTo(orderedEvent.getPayloadJson());
    }

    /** NORMAL 模式必须忽略已有分组键并原样发送冻结 JSON。 */
    @Test
    void shouldUseNormalDeliveryModeWithoutRewritingFrozenJson() {
        TransactionEventOutboxDO normalEvent = event();
        normalEvent.setDeliveryMode("NORMAL");
        normalEvent.setMessageGroup("legacy-group-must-not-force-orderly");
        normalEvent.setPayloadJson("{ \"eventType\": \"TRANSACTION_CREATED\", \"messageId\": \"M-1\" }");
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(normalEvent);
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService =
                new DefaultTransactionEventOutboxRelayService(eventOutboxService, mqProducer);

        int successCount = relayService.publishDueEvents(normalEvent.getTransactionDateTime(), 10);

        assertThat(successCount).isEqualTo(1);
        assertThat(mqProducer.deliveryMethod).isEqualTo("NORMAL");
        assertThat(mqProducer.messageGroup).isNull();
        assertThat(mqProducer.payloadJson).isEqualTo(normalEvent.getPayloadJson());
    }

    /** SCHEDULED 模式按数据库 UTC 时间生成绝对 Instant，不读取或改写消息体时间。 */
    @Test
    void shouldUseScheduledDeliveryModeWithUtcDeliverAt() {
        TransactionEventOutboxDO scheduledEvent = event();
        LocalDateTime deliverAt = LocalDateTime.of(2026, 8, 26, 2, 30, 0, 123_000_000);
        scheduledEvent.setDeliveryMode("SCHEDULED");
        scheduledEvent.setDeliverAt(deliverAt);
        scheduledEvent.setMessageGroup(null);
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(scheduledEvent);
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService =
                new DefaultTransactionEventOutboxRelayService(eventOutboxService, mqProducer);

        int successCount = relayService.publishDueEvents(scheduledEvent.getTransactionDateTime(), 10);

        assertThat(successCount).isEqualTo(1);
        assertThat(mqProducer.deliveryMethod).isEqualTo("SCHEDULED");
        assertThat(mqProducer.deliverAt).isEqualTo(deliverAt.toInstant(ZoneOffset.UTC));
        assertThat(mqProducer.payloadJson).isEqualTo(scheduledEvent.getPayloadJson());
    }

    @Test
    void shouldPublishClearingRetryOnlyWhenPayloadAndOutboxDeliverAtMatch() {
        LocalDateTime deliverAt = LocalDateTime.of(2026, 8, 26, 2, 30, 0, 123_000_000);
        TransactionEventOutboxDO scheduledEvent = clearingRetryEvent(deliverAt);
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(scheduledEvent);
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService =
                new DefaultTransactionEventOutboxRelayService(eventOutboxService, mqProducer);

        int successCount = relayService.publishDueEvents(scheduledEvent.getTransactionDateTime(), 10);

        assertThat(successCount).isEqualTo(1);
        assertThat(mqProducer.deliveryMethod).isEqualTo("SCHEDULED");
        assertThat(mqProducer.deliverAt).isEqualTo(deliverAt.toInstant(ZoneOffset.UTC));
    }

    @Test
    void shouldAcceptLegacyClearingRetryPayloadWithinSameDatabaseMillisecond() {
        LocalDateTime deliverAt = LocalDateTime.of(2026, 8, 26, 2, 30, 0, 123_000_000);
        TransactionEventOutboxDO scheduledEvent = clearingRetryEvent(deliverAt);
        ClearingRetryDueMessage retryMessage = JsonUtils.parseObject(
                scheduledEvent.getPayloadJson(), ClearingRetryDueMessage.class);
        retryMessage.setDeliverAt(deliverAt.toInstant(ZoneOffset.UTC).plusNanos(456_000));
        scheduledEvent.setPayloadJson(JsonUtils.toJsonString(retryMessage));
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(scheduledEvent);
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService =
                new DefaultTransactionEventOutboxRelayService(eventOutboxService, mqProducer);

        int successCount = relayService.publishDueEvents(scheduledEvent.getTransactionDateTime(), 10);

        assertThat(successCount).isEqualTo(1);
        assertThat(mqProducer.deliveryMethod).isEqualTo("SCHEDULED");
        assertThat(mqProducer.deliverAt).isEqualTo(deliverAt.toInstant(ZoneOffset.UTC));
    }

    @Test
    void shouldRejectClearingRetryWhenPayloadAndOutboxDeliverAtDiffer() {
        LocalDateTime deliverAt = LocalDateTime.of(2026, 8, 26, 2, 30, 0, 123_000_000);
        TransactionEventOutboxDO scheduledEvent = clearingRetryEvent(deliverAt);
        scheduledEvent.setDeliverAt(deliverAt.plusSeconds(1));
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(scheduledEvent);
        CapturingMqProducer mqProducer = new CapturingMqProducer(null);
        DefaultTransactionEventOutboxRelayService relayService =
                new DefaultTransactionEventOutboxRelayService(eventOutboxService, mqProducer);

        int successCount = relayService.publishDueEvents(scheduledEvent.getTransactionDateTime(), 10);

        assertThat(successCount).isZero();
        assertThat(mqProducer.sendCount).isZero();
        assertThat(scheduledEvent.getEventStatus()).isEqualTo("FAILED");
    }

    private TransactionEventOutboxDO clearingRetryEvent(LocalDateTime deliverAt) {
        ClearingRetryDueMessage message = new ClearingRetryDueMessage();
        message.setMessageId("CR-1");
        message.setTraceId("TRACE-1");
        message.setTransactionId("TX-1");
        message.setOperationId("OP-1");
        message.setMerchantId("M-1");
        message.setMerchantOrderNo("ORDER-1");
        message.setTransactionType("REFUND");
        message.setTransactionStatus("SUCCESS");
        message.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        message.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 1, 0));
        message.setSourceEventNo("MSG-1");
        message.setExpectedClearingRevision(0);
        message.setClearingRetryCount(1);
        message.setRetryReasonCode("SOURCE_CLEARING_PENDING");
        message.setDeliverAt(deliverAt.toInstant(ZoneOffset.UTC));
        TransactionEventOutboxDO event = event();
        event.setTopic(MqTopic.PAYMENT_CLEARING_DELAY);
        event.setTag(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        event.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        event.setDeliveryMode("SCHEDULED");
        event.setDeliverAt(deliverAt);
        event.setMessageGroup(null);
        event.setPayloadJson(JsonUtils.toJsonString(message));
        return event;
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

        private final RuntimeException failure;

        private boolean sent;

        private int sendCount;

        private BaseMqMessage message;

        /** 是否使用定时消息接口。 */
        private boolean scheduled;

        /** 捕获的绝对投递时间。 */
        private Instant deliverAt;

        /** 捕获的顺序消息分组键。 */
        private String messageGroup;

        /** 捕获冻结 JSON，断言 Relay 不执行二次序列化。 */
        private String payloadJson;

        /** 捕获消息 Header 使用的平台消息号。 */
        private String messageId;

        /** 捕获消息 Header 使用的 traceId。 */
        private String traceId;

        /** 捕获消息 Header 使用的 Outbox 重试次数。 */
        private int retryCount;

        /** NORMAL、ORDERLY 或 SCHEDULED。 */
        private String deliveryMethod;

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

        /** 捕获普通冻结 JSON 及 Header 元数据。 */
        @Override
        public void sendSerialized(String topic,
                                   String tag,
                                   String messageId,
                                   String traceId,
                                   int retryCount,
                                   String payloadJson) {
            captureSerialized(messageId, traceId, retryCount, payloadJson, "NORMAL");
        }

        /** 捕获顺序冻结 JSON 及分组键。 */
        @Override
        public void sendSerializedOrderly(String topic,
                                          String tag,
                                          String messageId,
                                          String traceId,
                                          int retryCount,
                                          String payloadJson,
                                          String messageGroup) {
            captureSerialized(messageId, traceId, retryCount, payloadJson, "ORDERLY");
            this.messageGroup = messageGroup;
        }

        /** 捕获取绝对时间投递的冻结 JSON。 */
        @Override
        public void sendSerializedAt(String topic,
                                     String tag,
                                     String messageId,
                                     String traceId,
                                     int retryCount,
                                     String payloadJson,
                                     Instant deliverAt) {
            captureSerialized(messageId, traceId, retryCount, payloadJson, "SCHEDULED");
            this.deliverAt = deliverAt;
            this.scheduled = true;
        }

        private void captureSerialized(String messageId,
                                       String traceId,
                                       int retryCount,
                                       String payloadJson,
                                       String deliveryMethod) {
            if (failure != null) {
                throw failure;
            }
            this.messageId = messageId;
            this.traceId = traceId;
            this.retryCount = retryCount;
            this.payloadJson = payloadJson;
            this.deliveryMethod = deliveryMethod;
            this.sent = true;
            this.sendCount++;
        }
    }

    private static class InMemoryEventOutboxService implements TransactionEventOutboxService {

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
