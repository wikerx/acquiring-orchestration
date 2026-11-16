package com.scott.payment.admin.application.monitor;

import com.scott.payment.admin.mapper.AuditOutboxCompensationMapper;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.outbox.entity.ReliableMqOutboxDO;
import com.scott.payment.component.db.outbox.service.ReliableMqOutboxStore;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.component.mq.publisher.ReliableMqOutboxRelayService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 非资金审计 Outbox 补偿安全门槛测试。
 */
class AuditOutboxCompensationServiceTest {

    private final AuditOutboxCompensationMapper mapper = mock(AuditOutboxCompensationMapper.class);
    private final ReliableMqOutboxStore store = mock(ReliableMqOutboxStore.class);
    private final ReliableMqOutboxRelayService relay = mock(ReliableMqOutboxRelayService.class);
    private final AuditOutboxCompensationService service =
            new AuditOutboxCompensationService(mapper, store, relay);

    /** 预演只返回候选，不允许修改状态或发送 MQ。 */
    @Test
    void shouldPreviewWithoutMutation() {
        ReliableMqOutboxDO candidate = candidate("event-1", 16);
        when(mapper.selectMissingAdminOperationLogs(5)).thenReturn(List.of(candidate));

        AuditOutboxCompensationService.AuditOutboxCompensationResult result = service.preview(5);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.eventIds()).containsExactly("event-1");
        verify(store, never()).recoverClosed(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(relay, never()).relayEvent(org.mockito.ArgumentMatchers.any());
    }

    /** 单批数量绝不能通过配置突破五条。 */
    @Test
    void shouldRejectBatchLargerThanHardLimit() {
        assertThatThrownBy(() -> service.preview(6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 5");
    }

    /** 最终事实已存在时必须停止且不恢复 Outbox。 */
    @Test
    void shouldStopWhenFinalFactAppearsAfterCandidateQuery() {
        ReliableMqOutboxDO candidate = candidate("event-2", 8);
        when(mapper.selectMissingAdminOperationLogs(1)).thenReturn(List.of(candidate));
        when(mapper.countOperationLogFact("ADMIN:event-2")).thenReturn(1);

        AuditOutboxCompensationService.AuditOutboxCompensationResult result =
                service.recoverAndRelay(1, "approved local audit drill");

        assertThat(result.stopReason()).isEqualTo("FINAL_FACT_ALREADY_EXISTS");
        assertThat(result.recoveredCount()).isZero();
        verify(store, never()).recoverClosed(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    /** 满足全部门槛时只恢复原事件并使用原载荷投递。 */
    @Test
    void shouldRecoverAndRelayOneQualifiedAuditEvent() {
        ReliableMqOutboxDO candidate = candidate("event-3", 16);
        when(mapper.selectMissingAdminOperationLogs(1)).thenReturn(List.of(candidate));
        when(mapper.countOperationLogFact("ADMIN:event-3")).thenReturn(0);
        when(store.recoverClosed(org.mockito.ArgumentMatchers.eq("event-3"),
                org.mockito.ArgumentMatchers.eq(16), org.mockito.ArgumentMatchers.eq("approved local audit drill"),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(relay.relayEvent("event-3")).thenReturn(true);

        AuditOutboxCompensationService.AuditOutboxCompensationResult result =
                service.recoverAndRelay(1, "approved local audit drill");

        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(result.relayedCount()).isEqualTo(1);
        assertThat(result.stopReason()).isNull();
        verify(relay).relayEvent("event-3");
    }

    /** Outbox 版本已变化时必须立即停止，不能继续投递或处理下一条。 */
    @Test
    void shouldStopBatchOnOutboxCasConflict() {
        ReliableMqOutboxDO first = candidate("event-4", 16);
        ReliableMqOutboxDO second = candidate("event-5", 16);
        when(mapper.selectMissingAdminOperationLogs(2)).thenReturn(List.of(first, second));
        when(mapper.countOperationLogFact("ADMIN:event-4")).thenReturn(0);
        when(store.recoverClosed(org.mockito.ArgumentMatchers.eq("event-4"),
                org.mockito.ArgumentMatchers.eq(16), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(false);

        AuditOutboxCompensationService.AuditOutboxCompensationResult result =
                service.recoverAndRelay(2, "approved local audit drill");

        assertThat(result.stopReason()).isEqualTo("OUTBOX_CAS_CONFLICT");
        assertThat(result.recoveredCount()).isZero();
        assertThat(result.relayedCount()).isZero();
        verify(relay, never()).relayEvent(org.mockito.ArgumentMatchers.any());
        verify(mapper, never()).countOperationLogFact("ADMIN:event-5");
    }

    /** Broker 未确认原事件投递时必须停止，不能推进同批后续候选。 */
    @Test
    void shouldStopBatchWhenRelayIsNotConfirmed() {
        ReliableMqOutboxDO first = candidate("event-6", 16);
        ReliableMqOutboxDO second = candidate("event-7", 16);
        when(mapper.selectMissingAdminOperationLogs(2)).thenReturn(List.of(first, second));
        when(mapper.countOperationLogFact("ADMIN:event-6")).thenReturn(0);
        when(store.recoverClosed(org.mockito.ArgumentMatchers.eq("event-6"),
                org.mockito.ArgumentMatchers.eq(16), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(relay.relayEvent("event-6")).thenReturn(false);

        AuditOutboxCompensationService.AuditOutboxCompensationResult result =
                service.recoverAndRelay(2, "approved local audit drill");

        assertThat(result.stopReason()).isEqualTo("RELAY_NOT_CONFIRMED");
        assertThat(result.recoveredCount()).isEqualTo(1);
        assertThat(result.relayedCount()).isZero();
        verify(mapper, never()).countOperationLogFact("ADMIN:event-7");
        verify(relay, never()).relayEvent("event-7");
    }

    /** 候选路由或消息类型漂移时必须在任何写入前拒绝。 */
    @Test
    void shouldRejectCandidateWhoseRouteNoLongerMatches() {
        ReliableMqOutboxDO candidate = candidate("event-8", 16);
        candidate.setTopic("acquiring_payment_result_topic");
        when(mapper.selectMissingAdminOperationLogs(1)).thenReturn(List.of(candidate));

        AuditOutboxCompensationService.AuditOutboxCompensationResult result =
                service.recoverAndRelay(1, "approved local audit drill");

        assertThat(result.stopReason()).isEqualTo("VALIDATION_OR_RELAY_EXCEPTION:IllegalStateException");
        assertThat(result.recoveredCount()).isZero();
        assertThat(result.relayedCount()).isZero();
        verify(store, never()).recoverClosed(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(relay, never()).relayEvent(org.mockito.ArgumentMatchers.any());
    }

    /** 构造不含敏感业务内容的测试候选。 */
    private ReliableMqOutboxDO candidate(String eventId, int version) {
        OperationLogMessage message = new OperationLogMessage();
        message.setMessageId(eventId);
        message.setSystemCode("ADMIN");
        ReliableMqOutboxDO candidate = new ReliableMqOutboxDO();
        candidate.setEventId(eventId);
        candidate.setProducerService("service-admin");
        candidate.setTopic("acquiring_admin_operation_log_topic");
        candidate.setEventStatus("CLOSED");
        candidate.setRetryCount(8);
        candidate.setMaxRetryCount(8);
        candidate.setVersion(version);
        candidate.setPayloadJson(JsonUtils.toJsonString(message));
        return candidate;
    }
}
