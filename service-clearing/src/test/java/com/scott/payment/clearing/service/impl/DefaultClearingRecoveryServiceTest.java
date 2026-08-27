package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.config.ClearingProperties;
import com.scott.payment.clearing.entity.ClearingCompensationCandidateDO;
import com.scott.payment.clearing.entity.ClearingTransactionEventOutboxDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.mapper.ClearingTransactionEventOutboxMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.service.ClearingProjectionService;
import com.scott.payment.clearing.service.ClearingAnomalyService;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultClearingRecoveryServiceTest {

    private static final LocalDateTime TX_TIME = LocalDateTime.of(2026, 8, 26, 10, 0);
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 26, 12, 0, 0, 123_456_789);
    private static final LocalDateTime DELIVER_AT =
            NOW.plusMinutes(1).truncatedTo(ChronoUnit.MILLIS);

    private ClearingTransactionFinanceStateMapper financeStateMapper;
    private ClearingTransactionEventOutboxMapper outboxMapper;
    private ClearingProjectionService projectionService;
    private GlobalIdGenerator idGenerator;
    private DefaultClearingRecoveryService service;

    @BeforeEach
    void setUp() {
        financeStateMapper = mock(ClearingTransactionFinanceStateMapper.class);
        outboxMapper = mock(ClearingTransactionEventOutboxMapper.class);
        projectionService = mock(ClearingProjectionService.class);
        idGenerator = mock(GlobalIdGenerator.class);
        ClearingProperties properties = new ClearingProperties();
        properties.setMaxRetryCount(8);
        service = new DefaultClearingRecoveryService(
                financeStateMapper, outboxMapper, projectionService, idGenerator, properties,
                mock(ClearingAnomalyService.class));
    }

    @Test
    void recoverMustUseAnIndependentRollbackCapableTransaction() throws NoSuchMethodException {
        Method recover = DefaultClearingRecoveryService.class.getMethod(
                "recover", ClearingCompensationCandidateDO.class, LocalDateTime.class);

        Transactional transactional = recover.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(Arrays.asList(transactional.rollbackFor())).contains(Exception.class);
    }

    @Test
    void failedDueShouldCasStateAndWriteDeterministicRetryOutbox() {
        ClearingCompensationCandidateDO candidate = candidate("FAILED_DUE", "FAILED", 2, 7);
        ClearingTransactionFinanceStateDO locked = state("FAILED", 2, 7);
        locked.setClearingRetryCount(2);
        locked.setLastFailureCode("FEE_VERSION_NOT_FOUND");
        locked.setNextRetryTime(NOW.minusMinutes(1));
        when(financeStateMapper.selectForUpdate("TX-1", TX_TIME)).thenReturn(locked);
        when(financeStateMapper.scheduleCompensationRetry(
                "TX-1", TX_TIME, "FAILED", 7, null, 3, DELIVER_AT,
                "CLEARING_COMPENSATION_DUE", "compensation retry for FEE_VERSION_NOT_FOUND", NOW))
                .thenReturn(1);
        when(outboxMapper.insertLogical(any())).thenReturn(1);

        assertThat(service.recover(candidate, NOW)).isEqualTo("RETRY_SCHEDULED");

        ArgumentCaptor<ClearingTransactionEventOutboxDO> captor =
                ArgumentCaptor.forClass(ClearingTransactionEventOutboxDO.class);
        verify(outboxMapper).insertLogical(captor.capture());
        ClearingTransactionEventOutboxDO outbox = captor.getValue();
        assertThat(outbox.getEventNo()).startsWith("CC").hasSize(34);
        assertThat(outbox.getDeliverAt()).isEqualTo(DELIVER_AT);
        ClearingRetryDueMessage message = JsonUtils.parseObject(
                outbox.getPayloadJson(), ClearingRetryDueMessage.class);
        assertThat(message.getExpectedClearingRevision()).isEqualTo(2);
        assertThat(message.getClearingRetryCount()).isEqualTo(3);
        assertThat(message.getRetryReasonCode()).isEqualTo("CLEARING_COMPENSATION_DUE");
        assertThat(message.getSourceEventNo()).isEqualTo("COMPENSATION:FS-1");
    }

    @Test
    void duplicateOutboxWithoutMatchingPersistedEventShouldFailTheTransaction() {
        ClearingCompensationCandidateDO candidate = candidate("PENDING_TIMEOUT", "PENDING", 0, 4);
        when(financeStateMapper.selectForUpdate("TX-1", TX_TIME))
                .thenReturn(state("PENDING", 0, 4));
        when(financeStateMapper.scheduleCompensationRetry(
                "TX-1", TX_TIME, "PENDING", 4, null, 1, DELIVER_AT,
                "CLEARING_COMPENSATION_DUE", "compensation retry for PENDING_TIMEOUT", NOW))
                .thenReturn(1);
        when(outboxMapper.insertLogical(any())).thenReturn(0);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.recover(candidate, NOW))
                .withMessageContaining("outbox");
    }

    @Test
    void exactDuplicateOutboxShouldBeAcceptedWithoutSecondInsert() {
        ClearingCompensationCandidateDO candidate = candidate("PENDING_TIMEOUT", "PENDING", 0, 4);
        AtomicReference<ClearingTransactionEventOutboxDO> persisted = new AtomicReference<>();
        when(financeStateMapper.selectForUpdate("TX-1", TX_TIME))
                .thenReturn(state("PENDING", 0, 4));
        when(financeStateMapper.scheduleCompensationRetry(
                "TX-1", TX_TIME, "PENDING", 4, null, 1, DELIVER_AT,
                "CLEARING_COMPENSATION_DUE", "compensation retry for PENDING_TIMEOUT", NOW))
                .thenReturn(1);
        when(outboxMapper.selectByEventNoForUpdate(any(), any()))
                .thenAnswer(invocation -> persisted.get());
        when(outboxMapper.insertLogical(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        });

        assertThat(service.recover(candidate, NOW)).isEqualTo("RETRY_SCHEDULED");
        assertThat(service.recover(candidate, NOW)).isEqualTo("ALREADY_SCHEDULED");

        verify(outboxMapper, times(1)).insertLogical(any());
    }

    @Test
    void duplicateOutboxWithMismatchedIdentityShouldFail() {
        ClearingCompensationCandidateDO candidate = candidate("PENDING_TIMEOUT", "PENDING", 0, 4);
        AtomicReference<ClearingTransactionEventOutboxDO> persisted = new AtomicReference<>();
        when(financeStateMapper.selectForUpdate("TX-1", TX_TIME))
                .thenReturn(state("PENDING", 0, 4));
        when(financeStateMapper.scheduleCompensationRetry(
                "TX-1", TX_TIME, "PENDING", 4, null, 1, DELIVER_AT,
                "CLEARING_COMPENSATION_DUE", "compensation retry for PENDING_TIMEOUT", NOW))
                .thenReturn(1);
        when(outboxMapper.selectByEventNoForUpdate(any(), any()))
                .thenAnswer(invocation -> persisted.get());
        when(outboxMapper.insertLogical(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        });

        assertThat(service.recover(candidate, NOW)).isEqualTo("RETRY_SCHEDULED");
        persisted.get().setMerchantId("M-OTHER");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.recover(candidate, NOW))
                .withMessageContaining("identity");
        verify(outboxMapper, times(1)).insertLogical(any());
    }

    @Test
    void expiredProcessingMustStillMatchDeadlineAndVersion() {
        ClearingCompensationCandidateDO candidate = candidate("PROCESSING_TIMEOUT", "PROCESSING", 1, 5);
        LocalDateTime deadline = NOW.minusSeconds(1);
        candidate.setProcessingDeadline(deadline);
        ClearingTransactionFinanceStateDO locked = state("PROCESSING", 1, 5);
        locked.setProcessingDeadline(deadline);
        when(financeStateMapper.selectForUpdate("TX-1", TX_TIME)).thenReturn(locked);
        when(financeStateMapper.scheduleCompensationRetry(
                "TX-1", TX_TIME, "PROCESSING", 5, deadline, 2, DELIVER_AT,
                "CLEARING_COMPENSATION_DUE", "compensation retry for PROCESSING_TIMEOUT", NOW))
                .thenReturn(0);

        assertThat(service.recover(candidate, NOW)).isEqualTo("SKIPPED_STALE");
        verify(outboxMapper, never()).insertLogical(any());
    }

    @Test
    void missingFinanceStateShouldInsertThenRecoverAuthoritativePendingState() {
        ClearingCompensationCandidateDO candidate = candidate("MISSING_FINANCE_STATE", null, 0, 0);
        candidate.setFinanceStateId(null);
        when(idGenerator.nextId()).thenReturn("7001");
        when(financeStateMapper.selectForUpdate("TX-1", TX_TIME))
                .thenReturn(null, state("PENDING", 0, 0));
        when(financeStateMapper.insertIfAbsent(any(), any(), any())).thenReturn(1);
        when(financeStateMapper.scheduleCompensationRetry(
                "TX-1", TX_TIME, "PENDING", 0, null, 1, DELIVER_AT,
                "CLEARING_COMPENSATION_DUE", "compensation retry for MISSING_FINANCE_STATE", NOW))
                .thenReturn(1);
        when(outboxMapper.insertLogical(any())).thenReturn(1);

        assertThat(service.recover(candidate, NOW)).isEqualTo("RETRY_SCHEDULED");
        verify(financeStateMapper).insertIfAbsent(
                org.mockito.ArgumentMatchers.eq("FS7001"), any(), org.mockito.ArgumentMatchers.eq(NOW));
    }

    @Test
    void completedProjectionMismatchShouldRepairProjectionWithoutRetry() {
        ClearingCompensationCandidateDO candidate = candidate("PROJECTION_MISMATCH", "CLEARED", 3, 9);
        when(financeStateMapper.selectForUpdate("TX-1", TX_TIME))
                .thenReturn(state("CLEARED", 3, 9));

        assertThat(service.recover(candidate, NOW)).isEqualTo("PROJECTION_REPAIRED");
        verify(projectionService).updateResolvingLocator(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(com.scott.payment.clearing.domain.state.ClearingStateEnum.CLEARED),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(NOW));
        verify(outboxMapper, never()).insertLogical(any());
    }

    @Test
    void maxRetryShouldEnterManualReviewWithoutPublishing() {
        ClearingCompensationCandidateDO candidate = candidate("FAILED_DUE", "FAILED", 2, 7);
        ClearingTransactionFinanceStateDO locked = state("FAILED", 8, 7);
        locked.setClearingRetryCount(8);
        locked.setNextRetryTime(NOW.minusMinutes(1));
        when(financeStateMapper.selectForUpdate("TX-1", TX_TIME)).thenReturn(locked);
        when(financeStateMapper.escalateCompensationReview(
                "TX-1", TX_TIME, "FAILED", 7, null,
                "clearing compensation exhausted after 8 retries", NOW)).thenReturn(1);

        assertThat(service.recover(candidate, NOW)).isEqualTo("MANUAL_REVIEW");
        verify(outboxMapper, never()).insertLogical(any());
    }

    private ClearingCompensationCandidateDO candidate(String reason, String status, int revision, int version) {
        ClearingCompensationCandidateDO row = new ClearingCompensationCandidateDO();
        row.setOperationRowId(1L);
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setMerchantOrderNo("ORDER-1");
        row.setTransactionType("PAYMENT");
        row.setTransactionStatus("SUCCESS");
        row.setLabelCurrency("EUR");
        row.setLabelAmount(new BigDecimal("100.00"));
        row.setApprovedCurrency("EUR");
        row.setApprovedAmount(new BigDecimal("100.00"));
        row.setTransactionCurrency("EUR");
        row.setTransactionAmount(new BigDecimal("100.00"));
        row.setCurrencyExponent(2);
        row.setTransactionDateTime(TX_TIME);
        row.setTransactionUtcTime(TX_TIME);
        row.setTransactionTimeZone("UTC");
        row.setOperationVersion(3);
        row.setFinanceStateId("FS-1");
        row.setClearingStatus(status);
        row.setClearingRevision(revision);
        row.setClearingRetryCount(0);
        row.setFinanceStateVersion(version);
        row.setReason(reason);
        return row;
    }

    private ClearingTransactionFinanceStateDO state(String status, int revision, int version) {
        ClearingTransactionFinanceStateDO row = new ClearingTransactionFinanceStateDO();
        row.setFinanceStateId("FS-1");
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setClearingStatus(status);
        row.setClearingRevision(revision);
        row.setClearingRetryCount(0);
        row.setTransactionDateTime(TX_TIME);
        row.setVersion(version);
        return row;
    }
}
