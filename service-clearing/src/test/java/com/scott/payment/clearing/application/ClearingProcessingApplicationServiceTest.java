package com.scott.payment.clearing.application;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionResult;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.service.ClearingEventValidator;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.dto.ClearingFailureResult;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.service.ClearingCompletionService;
import com.scott.payment.clearing.service.ClearingFailureService;
import com.scott.payment.clearing.service.ClearingPersistenceService;
import com.scott.payment.clearing.service.ClearingPreparationService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingProcessingApplicationServiceTest
 * @date : 2026-08-26 16:20
 * @email : scott_x@163.com
 * @description : 验证清分应用编排只ACK已完成和受控失败，未知技术异常与租约竞争继续交给RocketMQ原生重试。
 * @status : create
 */
class ClearingProcessingApplicationServiceTest {

    private static final LocalDateTime NOW_UTC = LocalDateTime.of(2026, 8, 26, 9, 0);

    private ClearingEventValidator validator;
    private ClearingPersistenceService persistenceService;
    private ClearingPreparationService preparationService;
    private ClearingCompletionService completionService;
    private ClearingFailureService failureService;
    private ClearingOperationalMetrics metrics;
    private ClearingProcessingApplicationService service;

    @BeforeEach
    void setUp() {
        validator = mock(ClearingEventValidator.class);
        persistenceService = mock(ClearingPersistenceService.class);
        preparationService = mock(ClearingPreparationService.class);
        completionService = mock(ClearingCompletionService.class);
        failureService = mock(ClearingFailureService.class);
        metrics = mock(ClearingOperationalMetrics.class);
        service = new ClearingProcessingApplicationService(
                validator, persistenceService, preparationService, completionService, failureService,
                metrics);
    }

    @Test
    void acquiredMessageShouldPrepareAndComplete() {
        PaymentTransactionEventMessage message = message();
        ClearingClaimResult claim = mock(ClearingClaimResult.class);
        CompletionCommand command = mock(CompletionCommand.class);
        ClearingOperationFacts operation = operation();
        when(claim.acquired()).thenReturn(true);
        when(claim.outcome()).thenReturn(ClearingClaimResult.Outcome.ACQUIRED);
        when(claim.operation()).thenReturn(operation);
        when(persistenceService.claim(message, "worker-1", NOW_UTC)).thenReturn(claim);
        when(preparationService.prepare(message, claim, "worker-1")).thenReturn(command);
        when(completionService.complete(command, NOW_UTC))
                .thenReturn(new CompletionResult("CLEARED", 1, 3, 1));

        ClearingProcessingResult result = service.process(message, "worker-1", NOW_UTC);

        assertThat(result).isEqualTo(ClearingProcessingResult.COMPLETED);
        verify(validator).validate(message);
        verify(completionService).complete(command, NOW_UTC);
        verifyNoInteractions(failureService);
        verify(metrics).recordCompleted("PAYMENT", "USD", "CLEARED");
        verify(metrics).recordProcessing(ClearingProcessingResult.COMPLETED);
        verify(metrics).recordEventConsumed(ClearingProcessingResult.COMPLETED, "PAYMENT");
    }

    @Test
    void controlledFailureShouldBePersistedAndAcknowledged() {
        PaymentTransactionEventMessage message = message();
        ClearingClaimResult claim = mock(ClearingClaimResult.class);
        ClearingProcessingException failure = new ClearingProcessingException(
                ClearingFailureCodeEnum.SOURCE_CLEARING_PENDING, "source clearing is pending");
        when(claim.acquired()).thenReturn(true);
        when(claim.outcome()).thenReturn(ClearingClaimResult.Outcome.ACQUIRED);
        when(persistenceService.claim(message, "worker-1", NOW_UTC)).thenReturn(claim);
        when(preparationService.prepare(message, claim, "worker-1")).thenThrow(failure);
        when(failureService.recordFailure(message, claim, "worker-1", failure, NOW_UTC))
                .thenReturn(new ClearingFailureResult(
                        "WAITING_SOURCE", "SOURCE_CLEARING_PENDING", 1, NOW_UTC.plusMinutes(1), true));

        ClearingProcessingResult result = service.process(message, "worker-1", NOW_UTC);

        assertThat(result).isEqualTo(ClearingProcessingResult.CONTROLLED_FAILURE_RECORDED);
        verify(failureService).recordFailure(message, claim, "worker-1", failure, NOW_UTC);
        verify(metrics).recordFailure(ClearingFailureCodeEnum.SOURCE_CLEARING_PENDING);
    }

    @Test
    void unknownFailureShouldPropagateForNativeRocketMqRetry() {
        PaymentTransactionEventMessage message = message();
        ClearingClaimResult claim = mock(ClearingClaimResult.class);
        IllegalStateException databaseFailure = new IllegalStateException("database unavailable");
        when(claim.acquired()).thenReturn(true);
        when(claim.outcome()).thenReturn(ClearingClaimResult.Outcome.ACQUIRED);
        when(persistenceService.claim(message, "worker-1", NOW_UTC)).thenReturn(claim);
        when(preparationService.prepare(message, claim, "worker-1")).thenThrow(databaseFailure);

        assertThatThrownBy(() -> service.process(message, "worker-1", NOW_UTC))
                .isSameAs(databaseFailure);
        verifyNoInteractions(failureService);
        verify(metrics).recordTechnicalFailure();
    }

    @Test
    void busyClaimShouldPropagateForNativeRocketMqRetry() {
        PaymentTransactionEventMessage message = message();
        ClearingClaimResult claim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.BUSY, "FS-1", 0, 5, null);
        when(persistenceService.claim(message, "worker-1", NOW_UTC)).thenReturn(claim);

        assertThatThrownBy(() -> service.process(message, "worker-1", NOW_UTC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease");
    }

    @Test
    void staleRetryShouldBeAcknowledgedWithoutPreparation() {
        PaymentTransactionEventMessage message = message();
        ClearingClaimResult claim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.STALE_RETRY, "FS-1", 0, 5, null);
        when(persistenceService.claim(message, "worker-1", NOW_UTC)).thenReturn(claim);

        ClearingProcessingResult result = service.process(message, "worker-1", NOW_UTC);

        assertThat(result).isEqualTo(ClearingProcessingResult.STALE_RETRY_ACKNOWLEDGED);
        verify(preparationService, never()).prepare(message, claim, "worker-1");
    }

    @Test
    void duplicateTerminalMessageCoveredByScheduledRetryShouldBeAcknowledged() {
        PaymentTransactionEventMessage message = message();
        ClearingClaimResult claim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.RETRY_ALREADY_SCHEDULED, "FS-1", 0, 5, null);
        when(persistenceService.claim(message, "worker-1", NOW_UTC)).thenReturn(claim);

        ClearingProcessingResult result = service.process(message, "worker-1", NOW_UTC);

        assertThat(result).isEqualTo(ClearingProcessingResult.RETRY_ALREADY_SCHEDULED);
        verify(preparationService, never()).prepare(message, claim, "worker-1");
    }

    @Test
    void automaticMessageForManualReviewShouldBeAcknowledged() {
        PaymentTransactionEventMessage message = message();
        ClearingClaimResult claim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.MANUAL_REVIEW_REQUIRED, "FS-1", 0, 5, null);
        when(persistenceService.claim(message, "worker-1", NOW_UTC)).thenReturn(claim);

        ClearingProcessingResult result = service.process(message, "worker-1", NOW_UTC);

        assertThat(result).isEqualTo(ClearingProcessingResult.MANUAL_REVIEW_ACKNOWLEDGED);
        verify(preparationService, never()).prepare(message, claim, "worker-1");
    }

    @Test
    void everyValidMerchantShouldReachDatabaseClaim() {
        PaymentTransactionEventMessage message = message();
        ClearingClaimResult claim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.ALREADY_CONSUMED, "FS-1", 1, 5, null);
        when(persistenceService.claim(message, "worker-1", NOW_UTC)).thenReturn(claim);

        ClearingProcessingResult result = service.process(message, "worker-1", NOW_UTC);

        assertThat(result).isEqualTo(ClearingProcessingResult.ALREADY_CONSUMED);
        verify(persistenceService).claim(message, "worker-1", NOW_UTC);
        verifyNoInteractions(preparationService, completionService, failureService);
    }

    private PaymentTransactionEventMessage message() {
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("MSG-1");
        message.setTransactionId("TX-1");
        message.setOperationId("OP-1");
        message.setMerchantId("M-1");
        message.setMerchantOrderNo("ORDER-1");
        message.setTransactionType("PAYMENT");
        message.setTransactionStatus("SUCCESS");
        message.setEventType(MqTag.TRANSACTION_STATUS_CHANGED);
        message.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 8, 30));
        return message;
    }

    private ClearingOperationFacts operation() {
        return new ClearingOperationFacts(
                "TX-1", "OP-1", null, "M-1", "ORDER-1", "PAYMENT", "SUCCESS",
                "USD", new BigDecimal("100.00"), "USD", new BigDecimal("100.00"),
                "USD", new BigDecimal("100.00"), 2,
                LocalDateTime.of(2026, 8, 26, 8, 30),
                LocalDateTime.of(2026, 8, 26, 0, 30), "Asia/Shanghai", 1);
    }
}
