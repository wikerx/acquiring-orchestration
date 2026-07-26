package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
        CapturingMqProducer mqProducer = new CapturingMqProducer(false);
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
        CapturingMqProducer mqProducer = new CapturingMqProducer(true);
        DefaultTransactionEventOutboxRelayService relayService = new DefaultTransactionEventOutboxRelayService(
                eventOutboxService,
                mqProducer);

        int successCount = relayService.publishDueEvents(LocalDateTime.of(2026, 7, 12, 10, 0), 10);

        assertThat(successCount).isZero();
        assertThat(eventOutboxService.eventDO.getEventStatus()).isEqualTo("FAILED");
        assertThat(eventOutboxService.eventDO.getFailReason()).contains("send failed");
        assertThat(eventOutboxService.eventDO.getNextRetryTime()).isNotNull();
    }

    @Test
    void shouldNotRepublishAlreadySentEventWhenOutboxIsScannedAgain() {
        TransactionEventOutboxDO eventDO = event();
        InMemoryEventOutboxService eventOutboxService = new InMemoryEventOutboxService(eventDO);
        CapturingMqProducer mqProducer = new CapturingMqProducer(false);
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
        eventDO.setPayloadJson(JsonUtils.toJsonString(message));
        eventDO.setEventTime(message.getCreatedAt());
        eventDO.setEventStatus("INIT");
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
        private final boolean fail;

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

        private CapturingMqProducer(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void send(String topic, String tag, BaseMqMessage message) {
            if (fail) {
                throw new IllegalStateException("send failed");
            }
            this.message = message;
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

        private InMemoryEventOutboxService(TransactionEventOutboxDO eventDO) {
            this.eventDO = eventDO;
        }

        @Override
        public void save(TransactionEventOutboxDO eventDO) {
        }

        @Override
        public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
            return "SENT".equals(eventDO.getEventStatus()) || "CLOSED".equals(eventDO.getEventStatus())
                    ? List.of()
                    : List.of(eventDO);
        }

        @Override
        public boolean markSent(TransactionEventOutboxDO eventDO, LocalDateTime sentTime) {
            eventDO.setEventStatus("SENT");
            eventDO.setSentTime(sentTime);
            eventDO.setVersion(eventDO.getVersion() + 1);
            return true;
        }

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
