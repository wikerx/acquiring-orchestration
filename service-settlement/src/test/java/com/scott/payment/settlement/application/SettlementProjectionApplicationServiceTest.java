package com.scott.payment.settlement.application;

import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import com.scott.payment.settlement.entity.SettlementOperationIdentityDO;
import com.scott.payment.settlement.entity.SettlementProjectionTaskDO;
import com.scott.payment.settlement.exception.SettlementProjectionProcessingException;
import com.scott.payment.settlement.mapper.SettlementProjectionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementProjectionApplicationServiceTest
 * @date : 2026-09-01 23:20
 * @email : scott_x@163.com
 * @description : 验证真实交易结算投影、FIFO Outbox 身份及失败后的独立退避记录
 * @status : create
 */
class SettlementProjectionApplicationServiceTest {

    private SettlementProjectionMapper mapper;
    private SettlementProjectionApplicationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SettlementProjectionMapper.class);
        service = new SettlementProjectionApplicationService(mapper);
    }

    /** 正常结算必须同步更新财务和动作投影，并按 operationId 生成 FIFO 事件。 */
    @Test
    void shouldProjectSettlementAndCreateFifoOutbox() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 0);
        SettlementProjectionTaskDO task = task("SETTLE");
        SettlementOperationIdentityDO operation = operation("NOT_SETTLED");
        when(mapper.selectNextDueForUpdate(now)).thenReturn(task);
        when(mapper.markProcessing(task.getTaskNo(), 0L, now)).thenReturn(1);
        when(mapper.selectOperationIdentity(task.getTransactionId(), task.getTransactionDateTime()))
                .thenReturn(operation);
        BigDecimal rate = new BigDecimal("0.800000000000");
        when(mapper.selectDirectSettlementRate(task.getSettlementBatchNo(),
                operation.getTransactionCurrency(), task.getSettlementCurrency())).thenReturn(rate);
        when(mapper.markFinanceStateSettled(task.getTransactionId(), task.getTransactionDateTime(),
                task.getClearingRevision(), task.getSettlementCurrency(), rate, task.getSettlementAmount(),
                task.getSettlementDate(), task.getSettlementBatchNo(), now)).thenReturn(1);
        when(mapper.markOperationSettled(task.getTransactionId(), task.getTransactionDateTime(),
                task.getSettlementCurrency(), task.getSettlementAmount(), rate, task.getSettlementDate(),
                task.getSettlementBatchNo(), now)).thenReturn(1);
        when(mapper.markOrderSettled(task.getOperationId(), operation.getRootTransactionDateTime(),
                task.getTransactionId(), task.getTransactionDateTime(), task.getSettlementCurrency(),
                task.getSettlementAmount(), rate, task.getSettlementDate(), task.getSettlementBatchNo(), now))
                .thenReturn(1);
        when(mapper.insertOutboxIdempotent(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.markCompleted(task.getTaskNo(), 1L, now)).thenReturn(1);

        assertThat(service.processNext(now)).isTrue();

        ArgumentCaptor<SettlementEventOutboxDO> captor = ArgumentCaptor.forClass(SettlementEventOutboxDO.class);
        verify(mapper).insertOutboxIdempotent(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo(MqTopic.PAYMENT_TRANSACTION_FIFO);
        assertThat(captor.getValue().getTag()).isEqualTo(MqTag.TRANSACTION_SETTLEMENT_COMPLETED);
        assertThat(captor.getValue().getMessageGroup()).isEqualTo(task.getOperationId());
    }

    /** 失败动作产生的合法调用费仍需完成三层结算投影，交易终态本身保持 FAILED。 */
    @Test
    void shouldProjectFeeBearingFailedOperationSettlement() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 3);
        SettlementProjectionTaskDO task = task("SETTLE");
        task.setSettlementAmount(new BigDecimal("-0.72"));
        SettlementOperationIdentityDO operation = operation("NOT_SETTLED");
        operation.setTransactionType("AUTHORIZATION");
        operation.setTransactionStatus("FAILED");
        BigDecimal rate = new BigDecimal("0.800000000000");
        when(mapper.selectNextDueForUpdate(now)).thenReturn(task);
        when(mapper.markProcessing(task.getTaskNo(), 0L, now)).thenReturn(1);
        when(mapper.selectOperationIdentity(task.getTransactionId(), task.getTransactionDateTime()))
                .thenReturn(operation);
        when(mapper.selectDirectSettlementRate(task.getSettlementBatchNo(),
                operation.getTransactionCurrency(), task.getSettlementCurrency())).thenReturn(rate);
        when(mapper.markFinanceStateSettled(task.getTransactionId(), task.getTransactionDateTime(),
                task.getClearingRevision(), task.getSettlementCurrency(), rate, task.getSettlementAmount(),
                task.getSettlementDate(), task.getSettlementBatchNo(), now)).thenReturn(1);
        when(mapper.markOperationSettled(task.getTransactionId(), task.getTransactionDateTime(),
                task.getSettlementCurrency(), task.getSettlementAmount(), rate, task.getSettlementDate(),
                task.getSettlementBatchNo(), now)).thenReturn(1);
        when(mapper.markOrderSettled(task.getOperationId(), operation.getRootTransactionDateTime(),
                task.getTransactionId(), task.getTransactionDateTime(), task.getSettlementCurrency(),
                task.getSettlementAmount(), rate, task.getSettlementDate(), task.getSettlementBatchNo(), now))
                .thenReturn(1);
        when(mapper.insertOutboxIdempotent(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.markCompleted(task.getTaskNo(), 1L, now)).thenReturn(1);

        assertThat(service.processNext(now)).isTrue();
    }

    /** 冲正只能把原批次已结算状态推进到 REVERSED，并发布对应顺序事件。 */
    @Test
    void shouldProjectPostedBatchReversal() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 5);
        SettlementProjectionTaskDO task = task("REVERSE");
        task.setOriginalBatchNo("SB20260825-00000001");
        SettlementOperationIdentityDO operation = operation("SETTLED");
        when(mapper.selectNextDueForUpdate(now)).thenReturn(task);
        when(mapper.markProcessing(task.getTaskNo(), 0L, now)).thenReturn(1);
        when(mapper.selectOperationIdentity(task.getTransactionId(), task.getTransactionDateTime()))
                .thenReturn(operation);
        BigDecimal rate = new BigDecimal("0.800000000000");
        when(mapper.selectDirectSettlementRate(task.getOriginalBatchNo(),
                operation.getTransactionCurrency(), task.getSettlementCurrency())).thenReturn(rate);
        when(mapper.markFinanceStateReversed(task.getTransactionId(), task.getTransactionDateTime(),
                task.getClearingRevision(), task.getSettlementCurrency(), task.getSettlementAmount(), rate,
                task.getSettlementDate(), task.getOriginalBatchNo(), task.getSettlementBatchNo(), now))
                .thenReturn(1);
        when(mapper.markOperationReversed(task.getTransactionId(), task.getTransactionDateTime(),
                task.getSettlementCurrency(), task.getSettlementAmount(), rate, task.getSettlementDate(),
                task.getOriginalBatchNo(), task.getSettlementBatchNo(), now)).thenReturn(1);
        when(mapper.markOrderReversed(task.getOperationId(), operation.getRootTransactionDateTime(),
                task.getTransactionId(), task.getTransactionDateTime(), task.getSettlementCurrency(),
                task.getSettlementAmount(), rate, task.getSettlementDate(), task.getOriginalBatchNo(),
                task.getSettlementBatchNo(), now)).thenReturn(1);
        when(mapper.insertOutboxIdempotent(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.markCompleted(task.getTaskNo(), 1L, now)).thenReturn(1);

        assertThat(service.processNext(now)).isTrue();

        ArgumentCaptor<SettlementEventOutboxDO> captor = ArgumentCaptor.forClass(SettlementEventOutboxDO.class);
        verify(mapper).insertOutboxIdempotent(captor.capture());
        assertThat(captor.getValue().getTag()).isEqualTo(MqTag.TRANSACTION_SETTLEMENT_REVERSED);
    }

    /** 冲正任务重放只接受三表金额、币种、汇率、日期和冲正批次完全一致。 */
    @Test
    void shouldAcceptExactReversalReplayWithoutOverwritingSettlementFacts() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 6);
        SettlementProjectionTaskDO task = task("REVERSE");
        task.setOriginalBatchNo("SB20260825-00000001");
        SettlementOperationIdentityDO operation = operation("REVERSED");
        BigDecimal rate = new BigDecimal("0.800000000000");
        when(mapper.selectNextDueForUpdate(now)).thenReturn(task);
        when(mapper.markProcessing(task.getTaskNo(), 0L, now)).thenReturn(1);
        when(mapper.selectOperationIdentity(task.getTransactionId(), task.getTransactionDateTime()))
                .thenReturn(operation);
        when(mapper.selectDirectSettlementRate(task.getOriginalBatchNo(),
                operation.getTransactionCurrency(), task.getSettlementCurrency())).thenReturn(rate);
        when(mapper.countMatchingReversedFinanceState(task.getTransactionId(), task.getTransactionDateTime(),
                task.getClearingRevision(), task.getSettlementCurrency(), task.getSettlementAmount(), rate,
                task.getSettlementDate(), task.getSettlementBatchNo())).thenReturn(1);
        when(mapper.countMatchingReversedOperation(task.getTransactionId(), task.getTransactionDateTime(),
                task.getSettlementCurrency(), task.getSettlementAmount(), rate, task.getSettlementDate(),
                task.getSettlementBatchNo())).thenReturn(1);
        when(mapper.countMatchingReversedOrder(task.getOperationId(), operation.getRootTransactionDateTime(),
                task.getTransactionId(), task.getTransactionDateTime(), task.getSettlementCurrency(),
                task.getSettlementAmount(), rate, task.getSettlementDate(), task.getSettlementBatchNo()))
                .thenReturn(1);
        when(mapper.insertOutboxIdempotent(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.markCompleted(task.getTaskNo(), 1L, now)).thenReturn(1);

        assertThat(service.processNext(now)).isTrue();
    }

    /** 较旧动作完成结算时不得覆盖生命周期主单中已经存在的较新真实动作快照。 */
    @Test
    void shouldKeepNewerLifecycleOrderSettlementSnapshot() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 7);
        SettlementProjectionTaskDO task = task("SETTLE");
        SettlementOperationIdentityDO operation = operation("NOT_SETTLED");
        BigDecimal rate = new BigDecimal("0.800000000000");
        when(mapper.selectNextDueForUpdate(now)).thenReturn(task);
        when(mapper.markProcessing(task.getTaskNo(), 0L, now)).thenReturn(1);
        when(mapper.selectOperationIdentity(task.getTransactionId(), task.getTransactionDateTime()))
                .thenReturn(operation);
        when(mapper.selectDirectSettlementRate(task.getSettlementBatchNo(),
                operation.getTransactionCurrency(), task.getSettlementCurrency())).thenReturn(rate);
        when(mapper.markFinanceStateSettled(task.getTransactionId(), task.getTransactionDateTime(),
                task.getClearingRevision(), task.getSettlementCurrency(), rate, task.getSettlementAmount(),
                task.getSettlementDate(), task.getSettlementBatchNo(), now)).thenReturn(1);
        when(mapper.markOperationSettled(task.getTransactionId(), task.getTransactionDateTime(),
                task.getSettlementCurrency(), task.getSettlementAmount(), rate, task.getSettlementDate(),
                task.getSettlementBatchNo(), now)).thenReturn(1);
        when(mapper.countNewerOrderSettlement(task.getOperationId(), operation.getRootTransactionDateTime(),
                task.getTransactionId(), task.getTransactionDateTime())).thenReturn(1);
        when(mapper.insertOutboxIdempotent(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.markCompleted(task.getTaskNo(), 1L, now)).thenReturn(1);

        assertThat(service.processNext(now)).isTrue();
    }

    /** 同一投影身份但任一冻结结算字段不一致时必须拒绝重放。 */
    @Test
    void shouldRejectConflictingSettlementReplay() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 8);
        SettlementProjectionTaskDO task = task("SETTLE");
        SettlementOperationIdentityDO operation = operation("SETTLED");
        when(mapper.selectNextDueForUpdate(now)).thenReturn(task);
        when(mapper.markProcessing(task.getTaskNo(), 0L, now)).thenReturn(1);
        when(mapper.selectOperationIdentity(task.getTransactionId(), task.getTransactionDateTime()))
                .thenReturn(operation);
        when(mapper.selectDirectSettlementRate(task.getSettlementBatchNo(),
                operation.getTransactionCurrency(), task.getSettlementCurrency()))
                .thenReturn(new BigDecimal("0.800000000000"));

        assertThatThrownBy(() -> service.processNext(now))
                .isInstanceOf(SettlementProjectionProcessingException.class)
                .hasRootCauseMessage("settlement finance projection identity is inconsistent");
    }

    /** 事务内投影失败必须携带任务 CAS 身份，回滚后由独立事务记录失败和有界退避。 */
    @Test
    void shouldRecordProjectionFailureAfterProcessingTransactionRollsBack() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 10);
        SettlementProjectionTaskDO task = task("SETTLE");
        when(mapper.selectNextDueForUpdate(now)).thenReturn(task);
        when(mapper.markProcessing(task.getTaskNo(), 0L, now)).thenReturn(1);
        when(mapper.selectOperationIdentity(task.getTransactionId(), task.getTransactionDateTime()))
                .thenReturn(null);

        SettlementProjectionProcessingException failure = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service.processNext(now), SettlementProjectionProcessingException.class);

        assertThat(failure).isNotNull();
        assertThat(failure.getTaskNo()).isEqualTo(task.getTaskNo());
        when(mapper.recordFailure(task.getTaskNo(), 0, 0L,
                failure.getFailureCode(), now.plusMinutes(1), now)).thenReturn(1);
        assertThat(service.recordFailure(failure, now)).isTrue();
    }

    private SettlementProjectionTaskDO task(String action) {
        SettlementProjectionTaskDO row = new SettlementProjectionTaskDO();
        row.setTaskNo("SP0123456789abcdef0123456789abcdef");
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setProjectionAction(action);
        row.setCandidateId(101L);
        row.setTransactionId("TXN-1001");
        row.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 8, 30));
        row.setClearingRevision(1);
        row.setOperationId("OP-1001");
        row.setMerchantId("M1001");
        row.setSettlementCurrency("USD");
        row.setSettlementAmount(new BigDecimal("80.00"));
        row.setSettlementDate(LocalDate.of(2026, 8, 26));
        row.setTaskStatus("INIT");
        row.setRetryCount(0);
        row.setVersion(0L);
        return row;
    }

    private SettlementOperationIdentityDO operation(String settlementStatus) {
        SettlementOperationIdentityDO row = new SettlementOperationIdentityDO();
        row.setTransactionId("TXN-1001");
        row.setOperationId("OP-1001");
        row.setMerchantId("M1001");
        row.setMerchantOrderNo("ORDER-1001");
        row.setTransactionType("PAYMENT");
        row.setTransactionStatus("SUCCESS");
        row.setTransactionCurrency("EUR");
        row.setSettlementStatus(settlementStatus);
        row.setRootTransactionDateTime(LocalDateTime.of(2026, 8, 20, 9, 0));
        return row;
    }
}
