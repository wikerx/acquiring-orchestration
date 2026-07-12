package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.producer.MqProducer;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    private TransactionEventOutboxDO event() {
        BaseMqMessage message = new BaseMqMessage();
        message.setMessageId("TX202607121000000010001");
        message.setCreatedAt(LocalDateTime.of(2026, 7, 12, 10, 0));
        TransactionEventOutboxDO eventDO = new TransactionEventOutboxDO();
        eventDO.setId(1L);
        eventDO.setEventNo("TX202607121000000010001");
        eventDO.setMessageKey("TX202607121000000010001");
        eventDO.setTopic("PAYMENT_EVENT");
        eventDO.setTag("PAYMENT_CREATED");
        eventDO.setPayloadJson(JsonUtils.toJsonString(message));
        eventDO.setEventTime(message.getCreatedAt());
        eventDO.setEventStatus("INIT");
        eventDO.setVersion(0);
        return eventDO;
    }

    private static class CapturingMqProducer implements MqProducer {

        private final boolean fail;

        private boolean sent;

        private CapturingMqProducer(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void send(String topic, String tag, BaseMqMessage message) {
            if (fail) {
                throw new IllegalStateException("send failed");
            }
            this.sent = true;
        }
    }

    private static class InMemoryEventOutboxService implements TransactionEventOutboxService {

        private final TransactionEventOutboxDO eventDO;

        private InMemoryEventOutboxService(TransactionEventOutboxDO eventDO) {
            this.eventDO = eventDO;
        }

        @Override
        public void save(TransactionEventOutboxDO eventDO) {
        }

        @Override
        public List<TransactionEventOutboxDO> listDueEvents(LocalDateTime eventTime, LocalDateTime now, int limit) {
            return List.of(eventDO);
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
