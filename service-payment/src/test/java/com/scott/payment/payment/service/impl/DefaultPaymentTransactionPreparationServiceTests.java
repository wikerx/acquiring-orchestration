package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.service.capability.PaymentAdmissionExecutionGuard;
import com.scott.payment.payment.service.capability.PaymentAdmissionRejectedException;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionIdempotencyDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.PaymentRiskInvokeService;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionIdempotencyService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService.Acquisition;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService.CancellationDecision;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService.Lease;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import com.scott.payment.payment.service.dto.PaymentInitialPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentRiskDecisionDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证首次交易本地准备在风控和 MID 路由前补齐服务端卡品牌。
 */
class DefaultPaymentTransactionPreparationServiceTests {

    private static final String TRANSACTION_OPERATION_SCOPE = "TRANSACTION_OPERATION";
    private static final String IDEMPOTENCY_KEY = "payment-idempotency-key";

    @Test
    void shouldEnrichCardBrandBeforeRiskAndRouteProcessing() {
        PaymentCheckoutCardCapabilityService cardCapabilityService = mock(PaymentCheckoutCardCapabilityService.class);
        PaymentRiskInvokeService riskInvokeService = mock(PaymentRiskInvokeService.class);
        PaymentChannelRouteService routeService = mock(PaymentChannelRouteService.class);
        TransactionIdempotencyService idempotencyService = mock(TransactionIdempotencyService.class);
        when(idempotencyService.buildTransactionOperationKey("200045", "ORDER-ID-1", "PAYMENT"))
                .thenThrow(new IllegalStateException("stop after card brand enrichment"));
        DefaultPaymentTransactionPreparationService service = new DefaultPaymentTransactionPreparationService(
                mock(IsoDictionaryService.class),
                riskInvokeService,
                routeService,
                mock(PaymentExchangeRateService.class),
                idempotencyService,
                mock(TransactionEventOutboxService.class),
                mock(TransactionRecordService.class),
                cardCapabilityService);
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setMerchantId("200045");
        commandDTO.setMerchantOrderId("ORDER-ID-1");

        assertThatThrownBy(() -> service.prepareInitialTransaction(commandDTO, "PAYMENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stop after card brand enrichment");

        verify(cardCapabilityService).enrichCardBrand(commandDTO);
        verifyNoInteractions(riskInvokeService, routeService);
    }

    @Test
    void shouldKeepRemotePlanningOutsideAndPersistFactsInsideShortTransaction() {
        PreparationFixture fixture = new PreparationFixture();
        fixture.allowFreshRequest();

        PaymentInitialPreparationResultDTO resultDTO = fixture.service.prepareInitialTransaction(
                fixture.command(), "PAYMENT");

        assertThat(resultDTO.isDuplicate()).isFalse();
        assertThat(resultDTO.isCallChannel()).isTrue();
        assertThat(fixture.transactionManager.commitCount).isEqualTo(1);
        assertThat(fixture.transactionManager.rollbackCount).isZero();
        verify(fixture.riskInvokeService, never()).cancelMerchantLimitReservation(any(), any(), anyString());
    }

    @Test
    void shouldPersistHostedCardAmountRejectionBeforePreparingAnyChannelRequest() {
        PreparationFixture fixture = new PreparationFixture();
        fixture.allowFreshRequest();
        var limits = mock(com.scott.payment.link.service.ChannelSingleAmountLimits.class);
        var rules = mock(com.scott.payment.link.service.ChannelSingleAmountLimits.Rules.class);
        when(limits.load(java.util.List.of(101L), "USD")).thenReturn(rules);
        when(rules.rejects(eq(101L), eq("BANK_CARD"), eq("VISA"),
                org.mockito.ArgumentMatchers.<BigDecimal>argThat(amount -> amount != null
                        && amount.compareTo(new BigDecimal("11.00")) == 0))).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(fixture.service, "singleAmountLimits", limits);
        var command = fixture.command();
        command.setRequestSource("HOSTED_CHECKOUT");
        var card = new PaymentCreateCommandDTO.TransactionInfoDTO();
        card.setCardBrand("VISA");
        command.setTransactionInfo(card);

        var result = fixture.service.prepareInitialTransaction(command, "PAYMENT");

        assertThat(result.isCallChannel()).isFalse();
        var persisted = org.mockito.ArgumentCaptor.forClass(
                com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO.class);
        verify(fixture.recordService).recordInitialTransaction(eq(command), any(),
                org.mockito.ArgumentMatchers.isNull(), persisted.capture(), any(), eq(2));
        assertThat(persisted.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(persisted.getValue().getFailReasonCode()).isEqualTo("SINGLE_AMOUNT_LIMIT_EXCEEDED");
        assertThat(fixture.transactionManager.commitCount).isEqualTo(1);
    }

    @Test
    void shouldReturnExistingDuplicateBeforeRiskAndTransactionStart() {
        PreparationFixture fixture = new PreparationFixture();
        TransactionIdempotencyDO existing = existingResult();
        when(fixture.idempotencyService.find(TRANSACTION_OPERATION_SCOPE, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        PaymentInitialPreparationResultDTO resultDTO = fixture.service.prepareInitialTransaction(
                fixture.command(), "PAYMENT");

        assertThat(resultDTO.isDuplicate()).isTrue();
        assertThat(resultDTO.getResultDTO().getTransactionId()).isEqualTo("TX-EXISTING");
        assertThat(fixture.transactionManager.beginCount).isZero();
        verifyNoInteractions(fixture.riskInvokeService, fixture.routeService, fixture.exchangeRateService);
    }

    @Test
    void shouldCancelNewRiskReservationWhenDatabaseDetectsConcurrentDuplicate() {
        PreparationFixture fixture = new PreparationFixture();
        TransactionIdempotencyDO existing = existingResult();
        when(fixture.idempotencyService.find(TRANSACTION_OPERATION_SCOPE, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(fixture.idempotencyService.tryBegin(any(TransactionIdempotencyDO.class)))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                    return false;
                });
        PaymentCreateCommandDTO commandDTO = fixture.command();

        PaymentInitialPreparationResultDTO resultDTO = fixture.service.prepareInitialTransaction(commandDTO, "PAYMENT");

        assertThat(resultDTO.isDuplicate()).isTrue();
        assertThat(fixture.transactionManager.commitCount).isEqualTo(1);
        verify(fixture.riskInvokeService, times(1)).cancelMerchantLimitReservation(
                eq(commandDTO), eq(fixture.riskDecisionDTO), eq("duplicate payment preparation"));
        verifyNoInteractions(fixture.recordService);
    }

    @Test
    void shouldCancelRiskReservationOnceWhenPersistenceRollsBack() {
        PreparationFixture fixture = new PreparationFixture();
        fixture.allowFreshRequest();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
            throw new IllegalStateException("transaction facts can not be persisted");
        }).when(fixture.recordService).recordInitialTransaction(any(), any(), any(), any(), any(), anyInt());
        PaymentCreateCommandDTO commandDTO = fixture.command();

        assertThatThrownBy(() -> fixture.service.prepareInitialTransaction(commandDTO, "PAYMENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("transaction facts can not be persisted");

        assertThat(fixture.transactionManager.commitCount).isZero();
        assertThat(fixture.transactionManager.rollbackCount).isEqualTo(1);
        verify(fixture.riskInvokeService, times(1)).cancelMerchantLimitReservation(
                eq(commandDTO), eq(fixture.riskDecisionDTO), eq("payment local preparation rolled back"));
    }

    @Test
    void ownershipIsFrozenBeforeRiskAndAttachedWithPreparedFacts() {
        PreparationFixture fixture = new PreparationFixture();
        fixture.allowFreshRequest();
        PaymentPreparationOwnershipService ownership = mock(PaymentPreparationOwnershipService.class);
        var originalTime = LocalDateTime.of(2026, 9, 30, 23, 59);
        var lease = new Lease("preparation-key", IDEMPOTENCY_KEY, "200045", 2,
                "TX-CURRENT-CANDIDATE", originalTime, "normalized");
        when(ownership.acquire(any(), eq(IDEMPOTENCY_KEY), eq("PAYMENT"), eq("Asia/Shanghai"), eq("global-candidate")))
                .thenReturn(new Acquisition(lease, null));
        fixture.enableProtectedPreparation(ownership);
        doAnswer(invocation -> {
            PaymentCreateCommandDTO command = invocation.getArgument(0);
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
            assertThat(command.getRiskPreparationOwnershipKey()).isEqualTo(lease.key());
            assertThat(command.getRiskPreparationGeneration()).isEqualTo(2L);
            assertThat(command.getTransactionId()).isEqualTo(lease.candidateTransactionId());
            assertThat(command.getTransactionDateTime()).isEqualTo(originalTime);
            return fixture.riskDecisionDTO;
        }).when(fixture.riskInvokeService).checkPreRoute(any());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
            return null;
        }).when(ownership).attach(lease);

        var result = fixture.service.prepareInitialTransaction(fixture.command(), "PAYMENT");

        assertThat(result.getCommittedTransactionDateTime()).isEqualTo(originalTime);
        var calls = org.mockito.Mockito.inOrder(ownership, fixture.riskInvokeService, fixture.recordService);
        calls.verify(ownership).nextCandidate();
        calls.verify(ownership).acquire(any(), eq(IDEMPOTENCY_KEY), eq("PAYMENT"), eq("Asia/Shanghai"), eq("global-candidate"));
        calls.verify(fixture.riskInvokeService).checkPreRoute(any());
        calls.verify(ownership).requireCurrent(lease);
        calls.verify(fixture.recordService).recordInitialTransaction(any(), any(), any(), any(), any(), anyInt());
        calls.verify(ownership).attach(lease);
        verify(ownership, never()).terminateUncommitted(any());
    }

    @Test
    void knownRollbackCancelsOnlyAfterTheCandidateIsDurablyTerminated() {
        PreparationFixture fixture = new PreparationFixture();
        fixture.allowFreshRequest();
        PaymentPreparationOwnershipService ownership = mock(PaymentPreparationOwnershipService.class);
        PaymentCreateCommandDTO command = fixture.command();
        var lease = new Lease("preparation-key", IDEMPOTENCY_KEY, "200045", 1,
                "TX-EXCLUSIVE-CANDIDATE", command.getTransactionDateTime(), "normalized");
        when(ownership.acquire(any(), eq(IDEMPOTENCY_KEY), eq("PAYMENT"), eq("Asia/Shanghai"), eq("global-candidate")))
                .thenReturn(new Acquisition(lease, null));
        fixture.enableProtectedPreparation(ownership);
        doAnswer(invocation -> {
            throw new IllegalStateException("confirmed local rollback");
        }).when(fixture.recordService).recordInitialTransaction(any(), any(), any(), any(), any(), anyInt());
        when(ownership.terminateUncommitted(lease)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
            assertThat(fixture.transactionManager.rollbackCount).isEqualTo(1);
            return CancellationDecision.RELEASE_ALLOWED;
        });

        assertThatThrownBy(() -> fixture.service.prepareInitialTransaction(command, "PAYMENT"))
                .hasMessage("confirmed local rollback");

        var calls = org.mockito.Mockito.inOrder(ownership, fixture.riskInvokeService);
        calls.verify(ownership).terminateUncommitted(lease);
        calls.verify(fixture.riskInvokeService).cancelMerchantLimitReservation(
                eq(command), eq(fixture.riskDecisionDTO), eq("payment preparation not committed"));
        verify(ownership, never()).attach(any());
        verify(ownership, never()).readCommittedOperation(any());
    }

    @Test
    void recoversOriginalPreparedContextWhenSpringReportsRollbackAfterCommitFailure() {
        var scenario = new CommitFailureFixture();
        scenario.persistedFacts = true;

        var result = scenario.prepare();

        assertThat(scenario.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.isCallChannel()).isTrue();
        assertThat(result.getCommandDTO()).isSameAs(scenario.command);
        assertThat(result.getPreparedChannelRequestDTO().getRequestId()).isEqualTo(scenario.channel.getRequestId());
        assertThat(result.getResultDTO().getTransactionId()).isEqualTo(scenario.lease.candidateTransactionId());
        assertThat(result.getResultDTO().getOperationId()).isEqualTo(scenario.operation.getOperationId());
        assertThat(result.getCommittedTransactionDateTime()).isEqualTo(scenario.lease.transactionDateTime());
        scenario.verifyPreservation();
    }

    @Test
    void keepsCommitFailureWithoutAuthoritativeEvidenceInProgress() {
        var scenario = new CommitFailureFixture();

        assertThatThrownBy(scenario::prepare)
                .isInstanceOf(PreparationOutcomeUnknownException.class)
                .hasCause(scenario.fixture.transactionManager.commitFailure);

        assertThat(scenario.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        scenario.verifyPreservation();
    }

    @Test
    void keepsCommitFailureWithUnverifiedOriginalShardInProgress() {
        var scenario = new CommitFailureFixture();
        scenario.persistedFacts = true;
        when(scenario.fixture.recordService.findOrderPrimary(any(), any()))
                .thenThrow(new ServiceException(ApiResultEnum.ORDER_NOT_FOUND));

        assertThatThrownBy(scenario::prepare).isInstanceOf(PreparationOutcomeUnknownException.class);

        scenario.verifyPreservation();
    }

    @Test
    void observesCommittedOperationWithoutResendingWhenOriginalChannelWasSent() {
        var scenario = new CommitFailureFixture();
        scenario.persistedFacts = true;
        scenario.channelStatus = "SENT";

        var result = scenario.prepare();

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.isCallChannel()).isFalse();
        assertThat(result.getPreparedChannelRequestDTO()).isNull();
        assertThat(result.getResultDTO().getOperationId()).isEqualTo(scenario.operation.getOperationId());
        scenario.verifyPreservation();
    }

    @Test
    void observesCommittedOperationWithoutReusingMismatchedChannelContext() {
        var scenario = new CommitFailureFixture();
        scenario.persistedFacts = true;
        scenario.channelTransactionOverride = "DIFFERENT-CHANNEL-TRANSACTION";

        var result = scenario.prepare();

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.isCallChannel()).isFalse();
        assertThat(result.getPreparedChannelRequestDTO()).isNull();
        assertThat(result.getResultDTO().getOperationId()).isEqualTo(scenario.operation.getOperationId());
        scenario.verifyPreservation();
    }

    @Test
    void quickAdmissionRejectionDoesNotCreatePreparationOwnershipOrReserveRisk() {
        var fixture = new PreparationFixture();
        fixture.allowFreshRequest();
        var guard = mock(PaymentAdmissionExecutionGuard.class);
        var ownership = mock(PaymentPreparationOwnershipService.class);
        fixture.service.setAdmissionGuard(guard);
        fixture.service.setPreparationOwnership(ownership);
        when(guard.isEnabled()).thenReturn(true);
        var rejection = new PaymentAdmissionRejectedException("CARD_BRAND:VISA",
                PaymentAdmissionRejectedException.Reason.AUTHORIZATION_UNAVAILABLE, 4L);
        doAnswer(invocation -> { throw rejection; }).when(guard).precheck(any());

        assertThatThrownBy(() -> fixture.service.prepareInitialTransaction(fixture.command(), "PAYMENT"))
                .isSameAs(rejection);
        verify(ownership, never()).nextCandidate();
        verify(ownership, never()).acquire(any(), anyString(), anyString(), anyString(), anyString());
        verify(fixture.riskInvokeService, never()).checkPreRoute(any());
    }

    @Test
    void disabledGateUsesLegacyNumberAndDoesNotSendPreparationFieldsToRisk() {
        var fixture = new PreparationFixture();
        fixture.allowFreshRequest();
        var ownership = mock(PaymentPreparationOwnershipService.class);
        fixture.service.setPreparationOwnership(ownership);
        var command = fixture.command();
        command.setRiskPreparationOwnershipKey("stale-request-object-key");
        command.setRiskPreparationGeneration(4L);
        doAnswer(invocation -> {
            PaymentCreateCommandDTO sent = invocation.getArgument(0);
            assertThat(sent.getTransactionId()).matches("[0-9]{21}");
            assertThat(sent.getRiskPreparationOwnershipKey()).isNull();
            assertThat(sent.getRiskPreparationGeneration()).isNull();
            return fixture.riskDecisionDTO;
        }).when(fixture.riskInvokeService).checkPreRoute(any());

        var prepared = fixture.service.prepareInitialTransaction(command, "PAYMENT");

        assertThat(prepared.isCallChannel()).isTrue();
        verify(ownership).readBeforeLegacyPreparation(IDEMPOTENCY_KEY);
        verify(ownership).requireLegacyPreparationAllowed(IDEMPOTENCY_KEY);
        verify(ownership, never()).nextCandidate();
    }

    @Test
    void scottInitialPaymentUsesSystemOrderNumberAsMerchantReference() {
        PreparationFixture fixture = new PreparationFixture();
        fixture.allowFreshRequest();
        PaymentRouteResultDTO route = PaymentRouteResultDTO.routed("SCOTT_CHANNEL_A");
        route.setChannelId(101L);
        route.setMidConfigId(1001L);
        route.setMidNo("TEST-MID");
        route.setRequestedCurrency("EUR");
        route.setRoutedCurrency("USD");
        route.setEdcRequired(true);
        when(fixture.routeService.route(any(PaymentCreateCommandDTO.class))).thenReturn(route);

        var result = fixture.service.prepareInitialTransaction(fixture.command(), "PAYMENT");

        assertThat(result.isCallChannel()).isTrue();
        assertThat(result.getPreparedChannelRequestDTO().getChannelOrderNo())
                .isEqualTo(result.getResultDTO().getTransactionId())
                .isNotEqualTo(result.getPreparedChannelRequestDTO().getRequestId());
    }

    @Test
    void disabledGateWaitsForAnExistingProtectedPreparationBeforeRisk() {
        var fixture = new PreparationFixture();
        var ownership = mock(PaymentPreparationOwnershipService.class);
        fixture.service.setPreparationOwnership(ownership);
        var pending = new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS);
        when(ownership.readBeforeLegacyPreparation(IDEMPOTENCY_KEY)).thenThrow(pending);

        assertThatThrownBy(() -> fixture.service.prepareInitialTransaction(fixture.command(), "PAYMENT"))
                .isSameAs(pending);
        verifyNoInteractions(fixture.riskInvokeService, fixture.routeService);
        verify(ownership, never()).nextCandidate();
    }

    /** 构建已有首次交易结果，用于验证原始准备和数据库并发命中。 */
    private static TransactionIdempotencyDO existingResult() {
        TransactionIdempotencyDO existing = new TransactionIdempotencyDO();
        existing.setTransactionId("TX-EXISTING");
        existing.setOperationId("OP-EXISTING");
        existing.setMerchantOrderNo("ORDER-001");
        existing.setMerchantOrderId("ORDER-ID-001");
        existing.setTransactionType("PAYMENT");
        existing.setTransactionStatus("PROCESSING");
        return existing;
    }

    /**
     * 首次支付短事务测试夹具，所有外部规划调用和本地写入都会断言当前事务状态。
     */
    private static final class PreparationFixture {

        private final IsoDictionaryService isoDictionaryService = mock(IsoDictionaryService.class);
        private final PaymentRiskInvokeService riskInvokeService = mock(PaymentRiskInvokeService.class);
        private final PaymentChannelRouteService routeService = mock(PaymentChannelRouteService.class);
        private final PaymentExchangeRateService exchangeRateService = mock(PaymentExchangeRateService.class);
        private final TransactionIdempotencyService idempotencyService = mock(TransactionIdempotencyService.class);
        private final TransactionEventOutboxService outboxService = mock(TransactionEventOutboxService.class);
        private final TransactionRecordService recordService = mock(TransactionRecordService.class);
        private final PaymentCheckoutCardCapabilityService cardCapabilityService = mock(PaymentCheckoutCardCapabilityService.class);
        private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        private final PaymentRiskDecisionDTO riskDecisionDTO = reservedRiskDecision();
        private final DefaultPaymentTransactionPreparationService service;

        private PreparationFixture() {
            when(idempotencyService.buildTransactionOperationKey("200045", "ORDER-ID-001", "PAYMENT"))
                    .thenReturn(IDEMPOTENCY_KEY);
            when(idempotencyService.buildMerchantOrderFlowKey("200045", "ORDER-001"))
                    .thenReturn("merchant-order-flow-key");
            when(idempotencyService.newProcessingRecord(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                    any(LocalDateTime.class), anyString(), anyString(), any(LocalDateTime.class)))
                    .thenAnswer(invocation -> {
                        TransactionIdempotencyDO record = new TransactionIdempotencyDO();
                        record.setIdempotencyScope(invocation.getArgument(0));
                        record.setIdempotencyKey(invocation.getArgument(1));
                        return record;
                    });
            when(riskInvokeService.checkPreRoute(any(PaymentCreateCommandDTO.class)))
                    .thenAnswer(invocation -> {
                        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
                        return riskDecisionDTO;
                    });
            when(routeService.route(any(PaymentCreateCommandDTO.class)))
                    .thenAnswer(invocation -> {
                        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
                        PaymentRouteResultDTO routeResultDTO = PaymentRouteResultDTO.routed("MPGS");
                        routeResultDTO.setChannelId(101L);
                        routeResultDTO.setMidConfigId(1001L);
                        routeResultDTO.setMidNo("TEST-MID");
                        routeResultDTO.setRequestedCurrency("EUR");
                        routeResultDTO.setRoutedCurrency("USD");
                        routeResultDTO.setEdcRequired(true);
                        return routeResultDTO;
                    });
            when(exchangeRateService.findTransactionRate(eq("EUR"), eq("USD"), any(LocalDateTime.class)))
                    .thenAnswer(invocation -> {
                        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
                        PaymentExchangeRateDTO rateDTO = new PaymentExchangeRateDTO();
                        rateDTO.setRateId(1001L);
                        rateDTO.setRawRateId(901L);
                        rateDTO.setRuleId(801L);
                        rateDTO.setSourceCode("PLATFORM");
                        rateDTO.setBaseCurrency("EUR");
                        rateDTO.setQuoteCurrency("USD");
                        rateDTO.setFinalRate(new BigDecimal("1.10000000"));
                        rateDTO.setEffectiveTime(invocation.getArgument(2));
                        return Optional.of(rateDTO);
                    });
            when(isoDictionaryService.getCurrency("USD"))
                    .thenReturn(Optional.of(new IsoCurrencyInfo(
                            "USD", "840", "US Dollar", "美元", 2, 100L,
                            new BigDecimal("0.01"), "$")));
            when(isoDictionaryService.getCurrency("EUR"))
                    .thenReturn(Optional.of(new IsoCurrencyInfo(
                            "EUR", "978", "Euro", "欧元", 2, 100L,
                            new BigDecimal("0.01"), "€")));
            when(isoDictionaryService.toMinorUnit(any(BigDecimal.class), eq("USD")))
                    .thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(0))
                            .movePointRight(2).longValueExact());
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                return null;
            }).when(recordService).recordInitialTransaction(any(), any(), any(), any(), any(), anyInt());
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                return null;
            }).when(outboxService).save(any());
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                return null;
            }).when(idempotencyService).complete(
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    any(BigDecimal.class), anyString(), anyString());
            service = new DefaultPaymentTransactionPreparationService(
                    isoDictionaryService,
                    riskInvokeService,
                    routeService,
                    exchangeRateService,
                    idempotencyService,
                    outboxService,
                    recordService,
                    cardCapabilityService,
                    transactionManager);
        }

        private void enableProtectedPreparation(PaymentPreparationOwnershipService ownership) {
            var guard = mock(PaymentAdmissionExecutionGuard.class);
            when(guard.isEnabled()).thenReturn(true);
            when(guard.planRoute(any())).thenAnswer(invocation ->
                    routeService.route(invocation.getArgument(0)));
            when(guard.protect(any(), any(), eq("PAYMENT"), any())).thenAnswer(invocation ->
                    invocation.<java.util.function.Supplier<?>>getArgument(3).get());
            when(ownership.nextCandidate()).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
                return "global-candidate";
            });
            service.setAdmissionGuard(guard);
            service.setPreparationOwnership(ownership);
        }

        /**
         * 配置数据库幂等占位成功，并验证全部幂等写入都发生在短事务内。
         */
        private void allowFreshRequest() {
            when(idempotencyService.find(TRANSACTION_OPERATION_SCOPE, IDEMPOTENCY_KEY))
                    .thenReturn(Optional.empty());
            when(idempotencyService.tryBegin(any(TransactionIdempotencyDO.class)))
                    .thenAnswer(invocation -> {
                        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
                        return true;
                    });
        }

        /**
         * 创建包含跨币种换汇的首次支付命令。
         */
        private PaymentCreateCommandDTO command() {
            PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
            commandDTO.setMerchantId("200045");
            commandDTO.setMerchantOrderNo("ORDER-001");
            commandDTO.setMerchantOrderId("ORDER-ID-001");
            commandDTO.setTransactionType("PAYMENT");
            commandDTO.setPaymentMethod("BANK_CARD");
            commandDTO.setAmount(new BigDecimal("10.00"));
            commandDTO.setCurrency("EUR");
            commandDTO.setTransactionDateTime(LocalDateTime.of(2026, 11, 4, 16, 30));
            return commandDTO;
        }

        /**
         * 构建已预占商户累计限额的风控通过结果。
         */
        private static PaymentRiskDecisionDTO reservedRiskDecision() {
            PaymentRiskDecisionDTO decisionDTO = PaymentRiskDecisionDTO.pass();
            decisionDTO.setRiskRecordNo("RISK-001");
            decisionDTO.setMerchantLimitReserved(true);
            return decisionDTO;
        }
    }

    private static final class CommitFailureFixture {
        private final PreparationFixture fixture = new PreparationFixture();
        private final PaymentPreparationOwnershipService ownership = mock(PaymentPreparationOwnershipService.class);
        private final PaymentCreateCommandDTO command = fixture.command();
        private final Lease lease = new Lease("preparation-key", IDEMPOTENCY_KEY, "200045", 1,
                "TX-EXCLUSIVE-CANDIDATE", command.getTransactionDateTime(), null);
        private final TransactionIdempotencyDO idempotency = new TransactionIdempotencyDO();
        private final TransactionOperationDO operation = new TransactionOperationDO();
        private final TransactionOrderDO order = new TransactionOrderDO();
        private final TransactionChannelRequestDO channel = new TransactionChannelRequestDO();
        private boolean persistedFacts;
        private String channelStatus = "INIT";
        private String channelTransactionOverride;
        private int completion = -1;

        private CommitFailureFixture() {
            fixture.allowFreshRequest();
            fixture.enableProtectedPreparation(ownership);
            fixture.transactionManager.commitFailure =
                    new DataAccessResourceFailureException("simulated loss of commit acknowledgment");
            when(ownership.acquire(any(), eq(IDEMPOTENCY_KEY), eq("PAYMENT"),
                    eq("Asia/Shanghai"), eq("global-candidate"))).thenReturn(new Acquisition(lease, null));
            when(ownership.readCommittedOperation(lease)).thenAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
                return persistedFacts ? idempotency : null;
            });
            when(fixture.recordService.findSourceOperationByTransactionIdPrimary(
                    lease.candidateTransactionId(), lease.transactionDateTime())).thenReturn(operation);
            when(fixture.recordService.findOrderPrimary(eq(lease.transactionDateTime()), any())).thenAnswer(invocation ->
                    order.getOperationId().equals(invocation.getArgument(1)) ? order : null);
            when(fixture.recordService.findOriginalChannelRequestForQueryPrimary(operation)).thenReturn(channel);
            doAnswer(invocation -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int outcome) {
                        completion = outcome;
                    }
                });
                capturePreparedRows(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
                return null;
            }).when(fixture.recordService).recordInitialTransaction(any(), any(), any(), any(), any(), anyInt());
        }

        private void capturePreparedRows(PaymentRouteResultDTO route, PaymentChannelInvokeResultDTO channelResult,
                                         PaymentCreateResultDTO result) {
            idempotency.setRequestFingerprint(command.getRequestFingerprint());
            idempotency.setMerchantId(command.getMerchantId());
            idempotency.setMerchantOrderNo(command.getMerchantOrderNo());
            idempotency.setMerchantOrderId(command.getMerchantOrderId());
            idempotency.setIdempotencyKey(IDEMPOTENCY_KEY);
            idempotency.setTransactionType(result.getTransactionType());
            idempotency.setTransactionId(result.getTransactionId());
            idempotency.setOperationId(result.getOperationId());
            idempotency.setTransactionStatus(result.getStatus());
            idempotency.setTransactionDateTime(lease.transactionDateTime());
            idempotency.setResultSnapshot(JsonUtils.toJsonString(result));
            operation.setMerchantId(command.getMerchantId());
            operation.setMerchantOrderNo(command.getMerchantOrderNo());
            operation.setMerchantOrderId(command.getMerchantOrderId());
            operation.setTransactionType(result.getTransactionType());
            operation.setTransactionId(result.getTransactionId());
            operation.setOperationId(result.getOperationId());
            operation.setTransactionStatus(result.getStatus());
            operation.setProcessStage(result.getProcessStage());
            operation.setTransactionDateTime(lease.transactionDateTime());
            order.setMerchantId(command.getMerchantId());
            order.setMerchantOrderNo(command.getMerchantOrderNo());
            order.setMerchantOrderId(command.getMerchantOrderId());
            order.setRootTransactionId(result.getTransactionId());
            order.setOperationId(result.getOperationId());
            order.setTransactionDateTime(lease.transactionDateTime());
            channel.setTransactionId(result.getTransactionId());
            channel.setOperationId(result.getOperationId());
            channel.setTransactionDateTime(lease.transactionDateTime());
            channel.setRequestId(channelResult.getRequestId());
            channel.setRequestStatus(channelStatus);
            channel.setChannelId(route.getChannelId());
            channel.setChannelCode(route.getChannelCode());
            channel.setChannelMidConfigId(route.getMidConfigId());
            channel.setChannelOrderNo(channelResult.getChannelRequest().getChannelOrderNo());
            channel.setChannelTransactionId(channelTransactionOverride == null
                    ? channelResult.getChannelRequest().getChannelTransactionId() : channelTransactionOverride);
        }

        private PaymentInitialPreparationResultDTO prepare() {
            return fixture.service.prepareInitialTransaction(command, "PAYMENT");
        }

        private void verifyPreservation() {
            verify(ownership).readCommittedOperation(lease);
            verify(ownership, never()).terminateUncommitted(any());
            verify(fixture.riskInvokeService, never()).cancelMerchantLimitReservation(any(), any(), any());
            verify(fixture.recordService).recordInitialTransaction(any(), any(), any(), any(), any(), anyInt());
        }
    }

    /**
     * 最小本地事务管理器，用于验证 TransactionTemplate 的提交、回滚和同步回调行为。
     */
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private int beginCount;
        private int commitCount;
        private int rollbackCount;
        private DataAccessResourceFailureException commitFailure;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            beginCount++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
            if (commitFailure != null) {
                throw commitFailure;
            }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbackCount++;
        }
    }
}
