package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.db.outbox.service.ReliableMqOutboxStore;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.properties.ReliableMqOutboxProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReliableMqPublisherTests
 * @date : 2026-08-02 22:25
 * @email : scott_x@163.com
 * @description : 可靠 MQ 发布测试，验证冻结快照、即时投递触发和独立审计事务传播级别
 * @status : create
 */
@Slf4j
class ReliableMqPublisherTests {

    /** 提交回调只调度即时 Relay，不能在仍绑定原事务连接的请求线程内执行。 */
    @Test
    void shouldPersistFrozenMessageAndTriggerImmediateRelay() {
        log.info("测试可靠MQ消息入队，关键输入: 固定messageId和脱敏载荷");
        ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
        ReliableMqOutboxRelayService relay = mock(ReliableMqOutboxRelayService.class);
        TaskExecutor relayExecutor = mock(TaskExecutor.class);
        ReliableMqPublisher publisher = new ReliableMqPublisher(
                store, relay, relayExecutor, new ReliableMqOutboxProperties(), "service-admin");
        BaseMqMessage message = new BaseMqMessage();
        message.setMessageId("MSG-OUTBOX-001");

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(publisher.publish("audit-topic", "audit-tag", message))
                    .isEqualTo("MSG-OUTBOX-001");

            verify(relayExecutor, never()).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
            TransactionSynchronization synchronization = TransactionSynchronizationManager
                    .getSynchronizations()
                    .get(0);
            synchronization.afterCommit();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        ArgumentCaptor<ReliableMqOutboxDO> captor = ArgumentCaptor.forClass(ReliableMqOutboxDO.class);
        verify(store).insert(captor.capture());
        assertThat(captor.getValue().getEventStatus()).isEqualTo("INIT");
        assertThat(captor.getValue().getPayloadJson()).contains("MSG-OUTBOX-001");
        assertThat(captor.getValue().getProducerService()).isEqualTo("service-admin");
        ArgumentCaptor<Runnable> relayTask = ArgumentCaptor.forClass(Runnable.class);
        verify(relayExecutor).execute(relayTask.capture());
        verify(relay, never()).relayEvent("MSG-OUTBOX-001");
        relayTask.getValue().run();
        verify(relay).relayEvent("MSG-OUTBOX-001");
        log.info("可靠MQ消息入队测试完成，结果: INIT快照已保存且提交后异步触发Relay");
    }

    /** 冻结消息写入入口必须使用 REQUIRES_NEW，不能加入已经结束或随后会回滚的认证事务。 */
    @Test
    void preparedEventPublisherShouldRequireNewTransaction() throws NoSuchMethodException {
        log.info("测试独立审计事务传播，关键输入: ReliableMqPublisher.publishPreparedEvent");
        Transactional transactional = ReliableMqPublisher.class
                .getMethod("publishPreparedEvent", ReliableMqOutboxDO.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        log.info("独立审计事务传播测试完成，结果: 外层事务结束后REQUIRES_NEW");
    }

    /** 外层事务结束前不得嵌套占用连接，回滚后仍应异步持久化冻结的审计消息。 */
    @Test
    void shouldPersistIndependentAuditOnlyAfterOuterTransactionCompletes() {
        log.info("测试独立审计延迟入队，关键输入: 外层事务回滚、连接受限");
        ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
        ReliableMqOutboxRelayService relay = mock(ReliableMqOutboxRelayService.class);
        TaskExecutor relayExecutor = mock(TaskExecutor.class);
        ReliableMqPublisher delegate = new ReliableMqPublisher(
                store, relay, relayExecutor, new ReliableMqOutboxProperties(), "service-admin");
        AtomicReference<Runnable> deferredTask = new AtomicReference<>();
        IndependentReliableMqPublisher publisher = new IndependentReliableMqPublisher(
                delegate, deferredTask::set);
        BaseMqMessage message = new BaseMqMessage();
        message.setMessageId("MSG-AUDIT-DEFERRED-001");

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThat(publisher.publish("audit-topic", "audit-tag", message))
                    .isEqualTo("MSG-AUDIT-DEFERRED-001");
            verifyNoInteractions(store);

            TransactionSynchronizationManager.getSynchronizations().get(0)
                    .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(deferredTask.get()).isNotNull();
        deferredTask.get().run();
        verify(store).insert(org.mockito.ArgumentMatchers.any(ReliableMqOutboxDO.class));
        log.info("独立审计延迟入队测试完成，结果: 回滚后冻结消息已持久化");
    }

    /** 相同事件号的业务载荷一致时允许幂等重放，不受新 traceId 和创建时间影响。 */
    @Test
    void shouldAcceptDuplicateEventWithSameBusinessPayload() {
        ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
        ReliableMqOutboxRelayService relay = mock(ReliableMqOutboxRelayService.class);
        TaskExecutor relayExecutor = Runnable::run;
        TestMqMessage message = testMessage("merchant-100");
        doThrow(new DuplicateKeyException("duplicate event"))
                .when(store).insert(org.mockito.ArgumentMatchers.any(ReliableMqOutboxDO.class));
        when(store.findByEventId("MSG-OUTBOX-001"))
                .thenReturn(existingEvent(testMessage("merchant-100")));
        ReliableMqPublisher publisher = new ReliableMqPublisher(
                store, relay, relayExecutor, new ReliableMqOutboxProperties(), "service-admin");

        assertThat(publisher.publish("audit-topic", "audit-tag", message))
                .isEqualTo("MSG-OUTBOX-001");

        verify(relay).relayEvent("MSG-OUTBOX-001");
    }

    /** 相同事件号若指向不同业务载荷必须失败，禁止静默丢弃新消息。 */
    @Test
    void shouldRejectDuplicateEventWithDifferentBusinessPayload() {
        ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
        ReliableMqOutboxRelayService relay = mock(ReliableMqOutboxRelayService.class);
        TaskExecutor relayExecutor = Runnable::run;
        doThrow(new DuplicateKeyException("duplicate event"))
                .when(store).insert(org.mockito.ArgumentMatchers.any(ReliableMqOutboxDO.class));
        when(store.findByEventId("MSG-OUTBOX-001"))
                .thenReturn(existingEvent(testMessage("merchant-previous")));
        ReliableMqPublisher publisher = new ReliableMqPublisher(
                store, relay, relayExecutor, new ReliableMqOutboxProperties(), "service-admin");

        assertThatThrownBy(() -> publisher.publish(
                "audit-topic", "audit-tag", testMessage("merchant-current")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mq outbox event id conflicts with a different message");
    }

    /** 构造带稳定业务字段和不同发布元数据的测试消息。 */
    private TestMqMessage testMessage(String businessKey) {
        TestMqMessage message = new TestMqMessage();
        message.setMessageId("MSG-OUTBOX-001");
        message.setBusinessKey(businessKey);
        message.setCreatedAt(LocalDateTime.of(2026, 8, 2, 23, 0));
        message.setTraceId("TRACE-CURRENT");
        message.setRetryCount(0);
        return message;
    }

    /** 构造数据库中已冻结的 Outbox 快照。 */
    private ReliableMqOutboxDO existingEvent(TestMqMessage message) {
        message.setCreatedAt(LocalDateTime.of(2026, 8, 1, 23, 0));
        message.setTraceId("TRACE-EXISTING");
        message.setRetryCount(3);
        ReliableMqOutboxDO event = new ReliableMqOutboxDO();
        event.setEventId(message.getMessageId());
        event.setTopic("audit-topic");
        event.setTag("audit-tag");
        event.setProducerService("service-admin");
        event.setPayloadJson(JsonUtils.toJsonString(message));
        return event;
    }

    /** 测试专用消息，businessKey 代表必须参与冲突判断的业务载荷。 */
    @lombok.Getter
    @lombok.Setter
    private static class TestMqMessage extends BaseMqMessage {
        /** 必须参与重复事件冲突判断的稳定业务键。 */
        private String businessKey;
    }
}
