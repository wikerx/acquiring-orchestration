package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionResult;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.FinanceSummary;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.LocatorFacts;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.SourceContext;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.dto.ClearingFeeTierAccumulatorDelta;
import com.scott.payment.clearing.entity.ClearingReserveDetailDO;
import com.scott.payment.clearing.entity.ClearingReserveStateDO;
import com.scott.payment.clearing.entity.ClearingFeeTierAccumulatorDO;
import com.scott.payment.clearing.entity.ClearingTransactionDetailDO;
import com.scott.payment.clearing.entity.ClearingTransactionEventOutboxDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionLocatorDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.mapper.ClearingFeeTierAccumulatorMapper;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionContextMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionDetailMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionEventOutboxMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionIdempotencyMapper;
import com.scott.payment.clearing.service.ClearingProjectionService;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeTierSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierMetric;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeCurrencyPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.PercentageBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.RefundFeeReturnPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReservePolicySnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveRefundPolicy;
import com.scott.payment.finance.money.model.Money;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingCompletionServiceTest
 * @date : 2026-08-26 11:45
 * @email : scott_x@163.com
 * @description : 验证 Stage B 在同一事务语义下分开持久化交易费用和标签币种保证金，并以 CAS、数据库幂等和 Outbox 完成动作清分。
 * @status : create
 */
@SuppressWarnings("unchecked")
class DefaultClearingCompletionServiceTest {

    @Test
    void completeShouldPersistOriginalCurrencyDetailsAndIndependentReserveHold() {
        ClearingTransactionFinanceStateMapper financeMapper = mock(ClearingTransactionFinanceStateMapper.class);
        ClearingTransactionDetailMapper detailMapper = mock(ClearingTransactionDetailMapper.class);
        ClearingReserveMapper reserveMapper = mock(ClearingReserveMapper.class);
        ClearingFeeTierAccumulatorMapper tierMapper = mock(ClearingFeeTierAccumulatorMapper.class);
        ClearingTransactionContextMapper contextMapper = mock(ClearingTransactionContextMapper.class);
        ClearingProjectionService projectionService = mock(ClearingProjectionService.class);
        ClearingTransactionIdempotencyMapper idempotencyMapper = mock(ClearingTransactionIdempotencyMapper.class);
        ClearingTransactionEventOutboxMapper outboxMapper = mock(ClearingTransactionEventOutboxMapper.class);
        GlobalIdGenerator idGenerator = mock(GlobalIdGenerator.class);
        DefaultClearingCompletionService service = new DefaultClearingCompletionService(
                financeMapper, detailMapper, reserveMapper, tierMapper, contextMapper, projectionService,
                idempotencyMapper, outboxMapper, new DefaultClearingCalculationService(), idGenerator,
                mock(ClearingSettlementCandidateService.class),
                mock(com.scott.payment.clearing.service.ClearingAnomalyService.class),
                mock(ClearingOperationalMetrics.class),
                mock(com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper.class));
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 11, 45);
        CompletionCommand command = command("USD", "110.00");

        when(financeMapper.selectForUpdate("TX-1", command.claim().operation().transactionDateTime()))
                .thenReturn(processingState(now));
        when(detailMapper.insertBatch(any())).thenAnswer(invocation -> invocation.<List<?>>getArgument(0).size());
        when(reserveMapper.insertDetail(any())).thenReturn(1);
        when(reserveMapper.insertState(any())).thenReturn(1);
        when(financeMapper.completeProcessing(anyString(), any(), anyString(), anyInt(), any(), any()))
                .thenReturn(1);
        when(idempotencyMapper.insertSuccessfulConsumption(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(outboxMapper.insertLogical(any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(
                "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008");

        CompletionResult result = service.complete(command, now);

        ArgumentCaptor<List<ClearingTransactionDetailDO>> detailCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(detailMapper).insertBatch(detailCaptor.capture());
        assertThat(detailCaptor.getValue()).hasSize(3);
        assertThat(detailCaptor.getValue()).extracting(ClearingTransactionDetailDO::getItemType)
                .containsExactly("PRINCIPAL", "PLATFORM_FEE", "PLATFORM_FEE");
        assertThat(detailCaptor.getValue()).extracting(ClearingTransactionDetailDO::getCurrency)
                .containsExactly("USD", "EUR", "USD");

        ArgumentCaptor<FinanceSummary> summaryCaptor = ArgumentCaptor.forClass(FinanceSummary.class);
        verify(financeMapper).completeProcessing(
                org.mockito.ArgumentMatchers.eq("TX-1"),
                org.mockito.ArgumentMatchers.eq(command.claim().operation().transactionDateTime()),
                org.mockito.ArgumentMatchers.eq("worker-1"), org.mockito.ArgumentMatchers.eq(3),
                summaryCaptor.capture(), org.mockito.ArgumentMatchers.eq(now));
        assertThat(summaryCaptor.getValue().grossLabelAmount()).isEqualByComparingTo("100.00");
        assertThat(summaryCaptor.getValue().labelCurrency()).isEqualTo("EUR");
        assertThat(summaryCaptor.getValue().merchantReceivableAmount()).isNull();

        ArgumentCaptor<ClearingReserveDetailDO> reserveCaptor = ArgumentCaptor.forClass(ClearingReserveDetailDO.class);
        org.mockito.Mockito.verify(reserveMapper).insertDetail(reserveCaptor.capture());
        assertThat(reserveCaptor.getValue().getReserveActionType()).isEqualTo("HOLD");
        assertThat(reserveCaptor.getValue().getReserveCurrency()).isEqualTo("EUR");
        assertThat(reserveCaptor.getValue().getRetainedAmount()).isEqualByComparingTo("10.00");
        ArgumentCaptor<ClearingTransactionEventOutboxDO> outboxCaptor =
                ArgumentCaptor.forClass(ClearingTransactionEventOutboxDO.class);
        verify(outboxMapper).insertLogical(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getTopic()).isEqualTo(MqTopic.PAYMENT_TRANSACTION_FIFO);
        assertThat(outboxCaptor.getValue().getDeliveryMode()).isEqualTo("ORDERLY");
        assertThat(outboxCaptor.getValue().getMessageGroup()).isEqualTo(command.claim().operation().operationId());
        verify(projectionService).updateWithLocator(
                command.claim().operation(), command.currentLocator(), ClearingStateEnum.CLEARED, null, now);
        assertThat(result).isEqualTo(new CompletionResult("CLEARED", 1, 3, 1));
    }

    @Test
    void completeAuthorizationShouldKeepLabelAmountButPersistZeroGrossPrincipal() {
        ClearingTransactionFinanceStateMapper financeMapper = mock(ClearingTransactionFinanceStateMapper.class);
        ClearingTransactionDetailMapper detailMapper = mock(ClearingTransactionDetailMapper.class);
        ClearingReserveMapper reserveMapper = mock(ClearingReserveMapper.class);
        ClearingFeeTierAccumulatorMapper tierMapper = mock(ClearingFeeTierAccumulatorMapper.class);
        ClearingTransactionContextMapper contextMapper = mock(ClearingTransactionContextMapper.class);
        ClearingProjectionService projectionService = mock(ClearingProjectionService.class);
        ClearingTransactionIdempotencyMapper idempotencyMapper = mock(ClearingTransactionIdempotencyMapper.class);
        ClearingTransactionEventOutboxMapper outboxMapper = mock(ClearingTransactionEventOutboxMapper.class);
        GlobalIdGenerator idGenerator = mock(GlobalIdGenerator.class);
        DefaultClearingCompletionService service = new DefaultClearingCompletionService(
                financeMapper, detailMapper, reserveMapper, tierMapper, contextMapper, projectionService,
                idempotencyMapper, outboxMapper, new DefaultClearingCalculationService(), idGenerator,
                mock(ClearingSettlementCandidateService.class),
                mock(com.scott.payment.clearing.service.ClearingAnomalyService.class),
                mock(ClearingOperationalMetrics.class),
                mock(com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper.class));
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 0);
        CompletionCommand command = authorizationCommand();

        when(financeMapper.selectForUpdate("TX-1", command.claim().operation().transactionDateTime()))
                .thenReturn(processingState(now));
        when(detailMapper.insertBatch(any())).thenAnswer(invocation -> invocation.<List<?>>getArgument(0).size());
        when(financeMapper.completeProcessing(anyString(), any(), anyString(), anyInt(), any(), any()))
                .thenReturn(1);
        when(idempotencyMapper.insertSuccessfulConsumption(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(outboxMapper.insertLogical(any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn("1101", "1102", "1103", "1104", "1105", "1106");

        CompletionResult result = service.complete(command, now);

        ArgumentCaptor<List<ClearingTransactionDetailDO>> detailCaptor = ArgumentCaptor.forClass(List.class);
        verify(detailMapper).insertBatch(detailCaptor.capture());
        assertThat(detailCaptor.getValue()).isNotEmpty()
                .allSatisfy(detail -> assertThat(detail.getItemType()).isEqualTo("PLATFORM_FEE"));
        ArgumentCaptor<FinanceSummary> summaryCaptor = ArgumentCaptor.forClass(FinanceSummary.class);
        verify(financeMapper).completeProcessing(
                org.mockito.ArgumentMatchers.eq("TX-1"),
                org.mockito.ArgumentMatchers.eq(command.claim().operation().transactionDateTime()),
                org.mockito.ArgumentMatchers.eq("worker-1"), org.mockito.ArgumentMatchers.eq(3),
                summaryCaptor.capture(), org.mockito.ArgumentMatchers.eq(now));
        assertThat(summaryCaptor.getValue().grossLabelAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summaryCaptor.getValue().labelCurrency()).isEqualTo("EUR");
        verify(reserveMapper, never()).insertDetail(any());
        verify(reserveMapper, never()).insertState(any());
        assertThat(result.clearingStatus()).isEqualTo("CLEARED");
        assertThat(result.reserveDetailCount()).isZero();
    }

    @Test
    void completeRefundShouldReverseActualSourceFeeAndReturnReserveWithCas() {
        ClearingTransactionFinanceStateMapper financeMapper = mock(ClearingTransactionFinanceStateMapper.class);
        ClearingTransactionDetailMapper detailMapper = mock(ClearingTransactionDetailMapper.class);
        ClearingReserveMapper reserveMapper = mock(ClearingReserveMapper.class);
        ClearingFeeTierAccumulatorMapper tierMapper = mock(ClearingFeeTierAccumulatorMapper.class);
        ClearingTransactionContextMapper contextMapper = mock(ClearingTransactionContextMapper.class);
        ClearingProjectionService projectionService = mock(ClearingProjectionService.class);
        ClearingTransactionIdempotencyMapper idempotencyMapper = mock(ClearingTransactionIdempotencyMapper.class);
        ClearingTransactionEventOutboxMapper outboxMapper = mock(ClearingTransactionEventOutboxMapper.class);
        GlobalIdGenerator idGenerator = mock(GlobalIdGenerator.class);
        DefaultClearingCompletionService service = new DefaultClearingCompletionService(
                financeMapper, detailMapper, reserveMapper, tierMapper, contextMapper, projectionService,
                idempotencyMapper, outboxMapper, new DefaultClearingCalculationService(), idGenerator,
                mock(ClearingSettlementCandidateService.class),
                mock(com.scott.payment.clearing.service.ClearingAnomalyService.class),
                mock(ClearingOperationalMetrics.class),
                mock(com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper.class));
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 30);
        CompletionCommand command = refundCommand();
        LocalDateTime refundTime = command.currentLocator().transactionDateTime();
        LocalDateTime sourceTime = command.source().locator().transactionDateTime();
        List<LocatorFacts> refundLocators = List.of(
                new LocatorFacts("RF-1", "OP-1", "PAY-1", "M-1", "ORDER-1",
                        "REFUND", refundTime.minusDays(1), sourceTime),
                command.currentLocator());

        when(financeMapper.selectForUpdate("RF-2", refundTime)).thenReturn(processingRefundState(now, refundTime));
        when(financeMapper.selectForUpdate("PAY-1", sourceTime)).thenReturn(clearedSourceState(sourceTime));
        when(detailMapper.selectActiveRevision("PAY-1", sourceTime, 1)).thenReturn(sourceClearingDetails(sourceTime));
        when(contextMapper.selectRefundLocators("M-1", "OP-1"))
                .thenReturn(refundLocators.stream().map(this::locatorRow).toList());
        when(detailMapper.selectRefundFacts("PAY-1", refundLocators)).thenReturn(refundFacts());
        when(reserveMapper.selectStateForUpdate("PAY-1", sourceTime)).thenReturn(reserveState(sourceTime));
        when(detailMapper.insertBatch(any())).thenAnswer(invocation -> invocation.<List<?>>getArgument(0).size());
        when(reserveMapper.insertDetail(any())).thenReturn(1);
        when(reserveMapper.applyReturn(anyString(), any(), any(Long.class), any(), any(), anyString(),
                anyString(), any(), any())).thenReturn(1);
        when(financeMapper.completeProcessing(anyString(), any(), anyString(), anyInt(), any(), any()))
                .thenReturn(1);
        when(idempotencyMapper.insertSuccessfulConsumption(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(outboxMapper.insertLogical(any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(
                "2001", "2002", "2003", "2004", "2005", "2006", "2007", "2008");

        CompletionResult result = service.complete(command, now);

        ArgumentCaptor<List<ClearingTransactionDetailDO>> detailCaptor = ArgumentCaptor.forClass(List.class);
        verify(detailMapper).insertBatch(detailCaptor.capture());
        assertThat(detailCaptor.getValue()).extracting(ClearingTransactionDetailDO::getItemType)
                .containsExactly("PRINCIPAL", "PLATFORM_FEE", "FEE_REVERSAL");
        ClearingTransactionDetailDO reversal = detailCaptor.getValue().get(2);
        assertThat(reversal.getSourceClearingDetailNo()).isEqualTo("SRC-FEE-1");
        assertThat(reversal.getDirection()).isEqualTo("CREDIT");
        assertThat(reversal.getCurrency()).isEqualTo("USD");
        assertThat(reversal.getAmount()).isEqualByComparingTo("1.00");

        ArgumentCaptor<ClearingReserveDetailDO> reserveCaptor = ArgumentCaptor.forClass(ClearingReserveDetailDO.class);
        verify(reserveMapper).insertDetail(reserveCaptor.capture());
        assertThat(reserveCaptor.getValue().getReserveActionType()).isEqualTo("RETURN");
        assertThat(reserveCaptor.getValue().getSourceReserveDetailNo()).isEqualTo("SRC-HOLD-1");
        assertThat(reserveCaptor.getValue().getReserveCurrency()).isEqualTo("USD");
        assertThat(reserveCaptor.getValue().getReturnedAmount()).isEqualByComparingTo("2.00");
        assertThat(reserveCaptor.getValue().getRemainingAmount()).isEqualByComparingTo("7.00");
        verify(reserveMapper).applyReturn("PAY-1", sourceTime, 4L, new BigDecimal("2.00"),
                new BigDecimal("7.00"), "OPEN", "RF-2", refundTime, now);
        verify(projectionService).updateWithLocator(
                command.claim().operation(), command.currentLocator(), ClearingStateEnum.CLEARED, null, now);
        assertThat(result).isEqualTo(new CompletionResult("CLEARED", 1, 3, 1));
    }

    @Test
    void completeRefundShouldWaitForSourceSettlementWhenSourceFeeGroupUsesMultipleCurrencies() {
        ClearingTransactionFinanceStateMapper financeMapper = mock(ClearingTransactionFinanceStateMapper.class);
        ClearingTransactionDetailMapper detailMapper = mock(ClearingTransactionDetailMapper.class);
        ClearingReserveMapper reserveMapper = mock(ClearingReserveMapper.class);
        ClearingFeeTierAccumulatorMapper tierMapper = mock(ClearingFeeTierAccumulatorMapper.class);
        ClearingTransactionContextMapper contextMapper = mock(ClearingTransactionContextMapper.class);
        ClearingProjectionService projectionService = mock(ClearingProjectionService.class);
        ClearingTransactionIdempotencyMapper idempotencyMapper = mock(ClearingTransactionIdempotencyMapper.class);
        ClearingTransactionEventOutboxMapper outboxMapper = mock(ClearingTransactionEventOutboxMapper.class);
        DefaultClearingCompletionService service = new DefaultClearingCompletionService(
                financeMapper, detailMapper, reserveMapper, tierMapper, contextMapper, projectionService,
                idempotencyMapper, outboxMapper, new DefaultClearingCalculationService(),
                mock(GlobalIdGenerator.class), mock(ClearingSettlementCandidateService.class),
                mock(com.scott.payment.clearing.service.ClearingAnomalyService.class),
                mock(ClearingOperationalMetrics.class),
                mock(com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper.class));
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 45);
        CompletionCommand command = refundCommand("EUR");
        LocalDateTime refundTime = command.currentLocator().transactionDateTime();
        LocalDateTime sourceTime = command.source().locator().transactionDateTime();
        List<LocatorFacts> refundLocators = List.of(command.currentLocator());
        List<ClearingTransactionDetailDO> sourceDetails = sourceClearingDetails(sourceTime);
        sourceDetails.get(0).setCurrency("EUR");

        when(financeMapper.selectForUpdate("RF-2", refundTime)).thenReturn(processingRefundState(now, refundTime));
        when(financeMapper.selectForUpdate("PAY-1", sourceTime)).thenReturn(clearedSourceState(sourceTime));
        when(contextMapper.selectRefundLocators("M-1", "OP-1"))
                .thenReturn(List.of(locatorRow(command.currentLocator())));
        when(detailMapper.selectRefundFacts("PAY-1", refundLocators)).thenReturn(List.of());
        when(detailMapper.selectActiveRevision("PAY-1", sourceTime, 1)).thenReturn(sourceDetails);

        assertThatExceptionOfType(ClearingProcessingException.class)
                .isThrownBy(() -> service.complete(command, now))
                .satisfies(exception -> assertThat(exception.getFailureCode())
                        .isEqualTo(ClearingFailureCodeEnum.SOURCE_SETTLEMENT_PENDING));
        verifyNoInteractions(reserveMapper, idempotencyMapper, outboxMapper);
    }

    @Test
    void recalculateShouldSupersedeOldRevisionAndAtomicallyReplaceSettlementCandidate() {
        ClearingTransactionFinanceStateMapper financeMapper = mock(ClearingTransactionFinanceStateMapper.class);
        ClearingTransactionDetailMapper detailMapper = mock(ClearingTransactionDetailMapper.class);
        ClearingReserveMapper reserveMapper = mock(ClearingReserveMapper.class);
        ClearingFeeTierAccumulatorMapper tierMapper = mock(ClearingFeeTierAccumulatorMapper.class);
        ClearingTransactionContextMapper contextMapper = mock(ClearingTransactionContextMapper.class);
        ClearingProjectionService projectionService = mock(ClearingProjectionService.class);
        ClearingTransactionIdempotencyMapper idempotencyMapper = mock(ClearingTransactionIdempotencyMapper.class);
        ClearingTransactionEventOutboxMapper outboxMapper = mock(ClearingTransactionEventOutboxMapper.class);
        GlobalIdGenerator idGenerator = mock(GlobalIdGenerator.class);
        ClearingSettlementCandidateService candidateService = mock(ClearingSettlementCandidateService.class);
        com.scott.payment.clearing.service.ClearingAnomalyService anomalyService =
                mock(com.scott.payment.clearing.service.ClearingAnomalyService.class);
        DefaultClearingCompletionService service = new DefaultClearingCompletionService(
                financeMapper, detailMapper, reserveMapper, tierMapper, contextMapper, projectionService,
                idempotencyMapper, outboxMapper, new DefaultClearingCalculationService(), idGenerator,
                candidateService, anomalyService, mock(ClearingOperationalMetrics.class),
                mock(com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper.class));
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 13, 30);
        CompletionCommand command = recalculationCommand();
        LocalDateTime transactionTime = command.claim().operation().transactionDateTime();
        ClearingTransactionFinanceStateDO state = recalculationState(transactionTime);
        ClearingTransactionDetailDO existing = new ClearingTransactionDetailDO();
        existing.setClearingDetailNo("OLD-1");
        existing.setTransactionId("TX-1");
        existing.setClearingRevision(1);
        existing.setRecordStatus("ACTIVE");
        existing.setTransactionDateTime(transactionTime);

        when(financeMapper.selectForUpdate("TX-1", transactionTime)).thenReturn(state);
        when(reserveMapper.selectActiveRevision("TX-1", transactionTime, 1)).thenReturn(List.of());
        when(detailMapper.selectActiveRevision("TX-1", transactionTime, 1)).thenReturn(List.of(existing));
        when(detailMapper.supersedeActiveRevision("TX-1", transactionTime, 1, now)).thenReturn(1);
        when(detailMapper.insertBatch(any())).thenAnswer(invocation -> invocation.<List<?>>getArgument(0).size());
        when(financeMapper.completeRecalculation(anyString(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(1);
        when(outboxMapper.insertLogical(any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn("3001", "3002", "3003", "3004", "3005", "3006");

        CompletionResult result = service.recalculate(command, 3, 1, now);

        verify(detailMapper).supersedeActiveRevision("TX-1", transactionTime, 1, now);
        ArgumentCaptor<List<ClearingTransactionDetailDO>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(detailMapper).insertBatch(detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).allSatisfy(row -> {
            assertThat(row.getClearingRevision()).isEqualTo(2);
            assertThat(row.getRecordStatus()).isEqualTo("ACTIVE");
            assertThat(row.getTransactionDateTime()).isEqualTo(transactionTime);
        });
        verify(financeMapper).completeRecalculation(
                org.mockito.ArgumentMatchers.eq("TX-1"), org.mockito.ArgumentMatchers.eq(transactionTime),
                org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq(1), any(),
                org.mockito.ArgumentMatchers.eq(now));
        verify(candidateService).replace("FS-1", 1, 2, command.claim().operation(),
                "USD", command.settlementEligibleDate(), now);
        verify(anomalyService).resolve("TX-1", transactionTime, "FS-1:2", now);
        verifyNoInteractions(idempotencyMapper, projectionService);
        assertThat(result.clearingRevision()).isEqualTo(2);
        assertThat(result.clearingStatus()).isEqualTo("CLEARED");
    }

    @Test
    void completeShouldAdvanceMatchedZeroFeeTierWithoutWritingFeeDetail() {
        ClearingTransactionFinanceStateMapper financeMapper = mock(ClearingTransactionFinanceStateMapper.class);
        ClearingTransactionDetailMapper detailMapper = mock(ClearingTransactionDetailMapper.class);
        ClearingReserveMapper reserveMapper = mock(ClearingReserveMapper.class);
        ClearingFeeTierAccumulatorMapper tierMapper = mock(ClearingFeeTierAccumulatorMapper.class);
        ClearingTransactionContextMapper contextMapper = mock(ClearingTransactionContextMapper.class);
        ClearingProjectionService projectionService = mock(ClearingProjectionService.class);
        ClearingTransactionIdempotencyMapper idempotencyMapper = mock(ClearingTransactionIdempotencyMapper.class);
        ClearingTransactionEventOutboxMapper outboxMapper = mock(ClearingTransactionEventOutboxMapper.class);
        GlobalIdGenerator idGenerator = mock(GlobalIdGenerator.class);
        DefaultClearingCompletionService service = new DefaultClearingCompletionService(
                financeMapper, detailMapper, reserveMapper, tierMapper, contextMapper, projectionService,
                idempotencyMapper, outboxMapper, new DefaultClearingCalculationService(), idGenerator,
                mock(ClearingSettlementCandidateService.class),
                mock(com.scott.payment.clearing.service.ClearingAnomalyService.class),
                mock(ClearingOperationalMetrics.class),
                mock(com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper.class));
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 14, 0);
        CompletionCommand command = zeroFeeTierCommand();
        LocalDateTime transactionTime = command.claim().operation().transactionDateTime();
        ClearingFeeTierAccumulatorDO accumulator = new ClearingFeeTierAccumulatorDO();
        accumulator.setFeeRuleId(301L);
        accumulator.setAccumulatedCount(4L);
        accumulator.setAccumulatedAmountUsd(BigDecimal.ZERO);
        accumulator.setVersion(7L);

        when(financeMapper.selectForUpdate("TX-1", transactionTime)).thenReturn(processingState(now));
        when(tierMapper.selectForUpdateBatch("M-1", 11L, List.of(301L), "202608"))
                .thenReturn(List.of(accumulator));
        List<ClearingFeeTierAccumulatorDelta> deltas = List.of(
                new ClearingFeeTierAccumulatorDelta(301L, 7L, BigDecimal.ZERO));
        when(tierMapper.applyDeltas("M-1", 11L, "202608", deltas,
                "TX-1", 1, transactionTime, now)).thenReturn(1);
        when(detailMapper.insertBatch(any())).thenAnswer(invocation -> invocation.<List<?>>getArgument(0).size());
        when(financeMapper.completeProcessing(anyString(), any(), anyString(), anyInt(), any(), any()))
                .thenReturn(1);
        when(idempotencyMapper.insertSuccessfulConsumption(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), any(), anyString(), anyString(), any(), any())).thenReturn(1);
        when(outboxMapper.insertLogical(any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn("4001");

        service.complete(command, now);

        ArgumentCaptor<List<ClearingTransactionDetailDO>> detailCaptor = ArgumentCaptor.forClass(List.class);
        verify(detailMapper).insertBatch(detailCaptor.capture());
        assertThat(detailCaptor.getValue()).extracting(ClearingTransactionDetailDO::getItemType)
                .containsExactly("PRINCIPAL");
        verify(tierMapper).insertIfAbsentBatch("M-1", 11L, List.of(301L), "202608", now);
        verify(tierMapper).selectForUpdateBatch("M-1", 11L, List.of(301L), "202608");
        verify(tierMapper).applyDeltas("M-1", 11L, "202608", deltas,
                "TX-1", 1, transactionTime, now);
        verifyNoInteractions(reserveMapper);
    }

    @Test
    void completeShouldStopBeforeTierFactsWhenPeriodReplayIsActive() {
        ClearingTransactionFinanceStateMapper financeMapper = mock(ClearingTransactionFinanceStateMapper.class);
        ClearingTransactionDetailMapper detailMapper = mock(ClearingTransactionDetailMapper.class);
        ClearingReserveMapper reserveMapper = mock(ClearingReserveMapper.class);
        ClearingFeeTierAccumulatorMapper tierMapper = mock(ClearingFeeTierAccumulatorMapper.class);
        ClearingTransactionContextMapper contextMapper = mock(ClearingTransactionContextMapper.class);
        ClearingProjectionService projectionService = mock(ClearingProjectionService.class);
        ClearingTransactionIdempotencyMapper idempotencyMapper = mock(ClearingTransactionIdempotencyMapper.class);
        ClearingTransactionEventOutboxMapper outboxMapper = mock(ClearingTransactionEventOutboxMapper.class);
        GlobalIdGenerator idGenerator = mock(GlobalIdGenerator.class);
        ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
        com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper replayMapper =
                mock(com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper.class);
        DefaultClearingCompletionService service = new DefaultClearingCompletionService(
                financeMapper, detailMapper, reserveMapper, tierMapper, contextMapper, projectionService,
                idempotencyMapper, outboxMapper, new DefaultClearingCalculationService(), idGenerator,
                mock(ClearingSettlementCandidateService.class),
                mock(com.scott.payment.clearing.service.ClearingAnomalyService.class), metrics, replayMapper);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 14, 0);
        CompletionCommand command = zeroFeeTierCommand();
        LocalDateTime transactionTime = command.claim().operation().transactionDateTime();
        when(financeMapper.selectForUpdate("TX-1", transactionTime)).thenReturn(processingState(now));
        when(replayMapper.countBlocking("M-1", 11L, "202608")).thenReturn(1);

        assertThatExceptionOfType(ClearingProcessingException.class)
                .isThrownBy(() -> service.complete(command, now))
                .satisfies(exception -> assertThat(exception.getFailureCode())
                        .isEqualTo(ClearingFailureCodeEnum.TIER_ACCUMULATOR_CONFLICT));

        verify(metrics).recordTierReplay("BLOCKED_CLEARING");
        verifyNoInteractions(tierMapper, detailMapper, reserveMapper, idempotencyMapper, outboxMapper);
    }

    @Test
    void tierPeriodReplayShouldAtomicallyReplaceRevisionAccumulatorCandidateAndProgress() {
        ClearingTransactionFinanceStateMapper financeMapper = mock(ClearingTransactionFinanceStateMapper.class);
        ClearingTransactionDetailMapper detailMapper = mock(ClearingTransactionDetailMapper.class);
        ClearingReserveMapper reserveMapper = mock(ClearingReserveMapper.class);
        ClearingFeeTierAccumulatorMapper tierMapper = mock(ClearingFeeTierAccumulatorMapper.class);
        ClearingTransactionContextMapper contextMapper = mock(ClearingTransactionContextMapper.class);
        ClearingProjectionService projectionService = mock(ClearingProjectionService.class);
        ClearingTransactionIdempotencyMapper idempotencyMapper = mock(ClearingTransactionIdempotencyMapper.class);
        ClearingTransactionEventOutboxMapper outboxMapper = mock(ClearingTransactionEventOutboxMapper.class);
        GlobalIdGenerator idGenerator = mock(GlobalIdGenerator.class);
        ClearingSettlementCandidateService candidateService = mock(ClearingSettlementCandidateService.class);
        com.scott.payment.clearing.service.ClearingAnomalyService anomalyService =
                mock(com.scott.payment.clearing.service.ClearingAnomalyService.class);
        com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper replayMapper =
                mock(com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper.class);
        DefaultClearingCompletionService service = new DefaultClearingCompletionService(
                financeMapper, detailMapper, reserveMapper, tierMapper, contextMapper, projectionService,
                idempotencyMapper, outboxMapper, new DefaultClearingCalculationService(), idGenerator,
                candidateService, anomalyService, mock(ClearingOperationalMetrics.class), replayMapper);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 14, 30);
        CompletionCommand source = zeroFeeTierCommand();
        ClearingClaimResult replayClaim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.ACQUIRED, "FS-1", 1, 3, source.claim().operation());
        CompletionCommand command = new CompletionCommand(
                source.message(), replayClaim, "tier-replay:TR-1", source.feeSnapshot(),
                source.currentLocator(), source.paymentType(), source.paymentMethod(),
                source.occurredRiskServices(), source.source(), source.settlementEligibleDate(), null);
        LocalDateTime transactionTime = command.claim().operation().transactionDateTime();
        com.scott.payment.clearing.entity.ClearingTierPeriodReplayDO replay =
                new com.scott.payment.clearing.entity.ClearingTierPeriodReplayDO();
        replay.setReplayNo("TR-1");
        replay.setMerchantId("M-1");
        replay.setFeePlanId(10L);
        replay.setFeePlanVersionId(11L);
        replay.setPeriodKey("202608");
        replay.setReplayStatus("RUNNING");
        replay.setVersion(5L);
        com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemDO item =
                new com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemDO();
        item.setReplayNo("TR-1");
        item.setSequenceNo(1);
        item.setFinanceStateId("FS-1");
        item.setTransactionId("TX-1");
        item.setTransactionDateTime(transactionTime);
        item.setExpectedClearingRevision(1);
        item.setExpectedFinanceStateVersion(3);
        item.setClearingCompleteTime(now.minusHours(1));
        item.setVersion(2L);
        ClearingFeeTierAccumulatorDO accumulator = new ClearingFeeTierAccumulatorDO();
        accumulator.setFeeRuleId(301L);
        accumulator.setAccumulatedCount(0L);
        accumulator.setAccumulatedAmountUsd(BigDecimal.ZERO);
        accumulator.setVersion(7L);
        ClearingTransactionDetailDO existing = new ClearingTransactionDetailDO();
        existing.setClearingDetailNo("OLD-1");
        existing.setTransactionId("TX-1");
        existing.setClearingRevision(1);
        existing.setRecordStatus("ACTIVE");
        existing.setTransactionDateTime(transactionTime);
        List<ClearingFeeTierAccumulatorDelta> deltas = List.of(
                new ClearingFeeTierAccumulatorDelta(301L, 7L, BigDecimal.ZERO));

        when(replayMapper.selectForUpdate("TR-1")).thenReturn(replay);
        when(replayMapper.selectNextItemForUpdate("TR-1", now)).thenReturn(item);
        when(financeMapper.selectForUpdate("TX-1", transactionTime))
                .thenReturn(recalculationState(transactionTime));
        when(tierMapper.selectForUpdateBatch("M-1", 11L, List.of(301L), "202608"))
                .thenReturn(List.of(accumulator));
        when(reserveMapper.selectActiveRevision("TX-1", transactionTime, 1)).thenReturn(List.of());
        when(detailMapper.selectActiveRevision("TX-1", transactionTime, 1)).thenReturn(List.of(existing));
        when(detailMapper.supersedeActiveRevision("TX-1", transactionTime, 1, now)).thenReturn(1);
        when(detailMapper.insertBatch(any())).thenAnswer(invocation -> invocation.<List<?>>getArgument(0).size());
        when(tierMapper.applyDeltas("M-1", 11L, "202608", deltas,
                "TX-1", 2, transactionTime, now)).thenReturn(1);
        when(financeMapper.completeRecalculation(anyString(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(1);
        when(outboxMapper.insertLogical(any())).thenReturn(1);
        when(replayMapper.markItemCompleted("TR-1", 1, 2L, 2, now)).thenReturn(1);
        when(replayMapper.advanceAfterItem("TR-1", 5L, 1,
                item.getClearingCompleteTime(), "TX-1", now)).thenReturn(1);
        when(idGenerator.nextId()).thenReturn("5001", "5002", "5003", "5004");

        CompletionResult result = service.recalculateTierPeriod(command, "TR-1", 1, 3, 1, now);

        assertThat(result.clearingRevision()).isEqualTo(2);
        verify(detailMapper).supersedeActiveRevision("TX-1", transactionTime, 1, now);
        verify(tierMapper).applyDeltas("M-1", 11L, "202608", deltas,
                "TX-1", 2, transactionTime, now);
        verify(candidateService).replaceReplayHeld(
                "FS-1", 1, 2, command.claim().operation(), "USD", command.settlementEligibleDate(), now);
        verify(replayMapper).markItemCompleted("TR-1", 1, 2L, 2, now);
        verify(replayMapper).advanceAfterItem(
                "TR-1", 5L, 1, item.getClearingCompleteTime(), "TX-1", now);
        verifyNoInteractions(idempotencyMapper, projectionService);
    }

    private CompletionCommand command() {
        return command("EUR", "100.00");
    }

    private CompletionCommand command(String approvedCurrency, String approvedAmount) {
        LocalDateTime time = LocalDateTime.of(2026, 8, 26, 9, 0);
        ClearingOperationFacts operation = new ClearingOperationFacts(
                "TX-1", "OP-1", null, "M-1", "ORDER-1", "PAYMENT", "SUCCESS",
                "EUR", new BigDecimal("100.00"), approvedCurrency, new BigDecimal(approvedAmount),
                "EUR", new BigDecimal("100.00"), 2, time, time.minusHours(8),
                "Asia/Shanghai", 5);
        ClearingClaimResult claim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.ACQUIRED, "FS-1", 0, 3, operation);
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("MSG-1");
        message.setTraceId("TRACE-1");
        message.setTransactionId("TX-1");
        message.setOperationId("OP-1");
        message.setMerchantId("M-1");
        message.setMerchantOrderNo("ORDER-1");
        message.setTransactionType("PAYMENT");
        message.setTransactionDateTime(time);
        LocatorFacts locator = new LocatorFacts(
                "TX-1", "OP-1", "TX-1", "M-1", "ORDER-1", "PAYMENT", time, time);
        return new CompletionCommand(message, claim, "worker-1", snapshot(), locator,
                "BANK_CARD", "VISA", Set.of(), null,
                LocalDate.of(2026, 8, 26), LocalDate.of(2027, 2, 22));
    }

    private CompletionCommand authorizationCommand() {
        CompletionCommand source = command();
        ClearingOperationFacts operation = source.claim().operation();
        ClearingOperationFacts authorization = new ClearingOperationFacts(
                operation.transactionId(), operation.operationId(), operation.sourceTransactionId(),
                operation.merchantId(), operation.merchantOrderNo(), "AUTHORIZATION",
                operation.transactionStatus(), operation.labelCurrency(), operation.labelAmount(),
                operation.approvedCurrency(), operation.approvedAmount(), operation.transactionCurrency(),
                operation.transactionAmount(), operation.currencyExponent(), operation.transactionDateTime(),
                operation.transactionUtcTime(), operation.transactionTimeZone(), operation.operationVersion());
        ClearingClaimResult claim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.ACQUIRED, "FS-1", 0, 3, authorization);
        source.message().setTransactionType("AUTHORIZATION");
        LocatorFacts locator = new LocatorFacts(
                "TX-1", "OP-1", "TX-1", "M-1", "ORDER-1", "AUTHORIZATION",
                operation.transactionDateTime(), operation.transactionDateTime());
        FeeVersionSnapshot snapshot = source.feeSnapshot();
        FeeRuleConfigurationSnapshot rule = snapshot.rules().get(0);
        FeeRuleConfigurationSnapshot authorizationRule = new FeeRuleConfigurationSnapshot(
                rule.ruleId(), rule.feeCategory(), "AUTHORIZATION", rule.paymentType(), rule.paymentMethod(),
                rule.riskServiceType(), rule.chargeTrigger(), rule.calculationRule(), rule.tiers());
        FeeVersionSnapshot authorizationSnapshot = new FeeVersionSnapshot(
                snapshot.schemaVersion(), snapshot.merchantId(), snapshot.feePlanId(),
                snapshot.feePlanVersionId(), snapshot.feePlanVersionNo(), snapshot.pricingLockTime(),
                snapshot.settlementCurrency(), snapshot.percentageBasis(), snapshot.feeCurrencyPolicy(),
                snapshot.roundingMode(), snapshot.reserve(), snapshot.refundFeeReturnPolicy(),
                List.of(authorizationRule), snapshot.snapshotHash());
        return new CompletionCommand(
                source.message(), claim, source.processingOwner(), authorizationSnapshot, locator,
                source.paymentType(), source.paymentMethod(), source.occurredRiskServices(), null,
                source.settlementEligibleDate(), null);
    }

    private CompletionCommand recalculationCommand() {
        CompletionCommand source = command();
        FeeVersionSnapshot original = source.feeSnapshot();
        FeeVersionSnapshot target = new FeeVersionSnapshot(
                original.schemaVersion(), original.merchantId(), 20L, 21L, 3,
                original.pricingLockTime(), original.settlementCurrency(), original.percentageBasis(),
                original.feeCurrencyPolicy(), original.roundingMode(),
                new ReservePolicySnapshot(BigDecimal.ZERO, ReserveBasis.LABEL_AMOUNT,
                        "D", 180, ReserveRefundPolicy.PROPORTIONAL_RETURN),
                original.refundFeeReturnPolicy(), original.rules(), "e".repeat(64));
        ClearingClaimResult claim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.ACQUIRED, "FS-1", 1, 3, source.claim().operation());
        return new CompletionCommand(
                source.message(), claim, "manual-recalc:admin-1", target, source.currentLocator(),
                source.paymentType(), source.paymentMethod(), source.occurredRiskServices(), null,
                source.settlementEligibleDate(), null);
    }

    private CompletionCommand zeroFeeTierCommand() {
        CompletionCommand source = command();
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                301L, FeeMode.TIER, BigDecimal.ZERO, null, null, null, TierMetric.COUNT);
        FeeRuleConfigurationSnapshot configuredRule = new FeeRuleConfigurationSnapshot(
                301L, "TRANSACTION_FEE", "PAYMENT", "BANK_CARD", "VISA", "NONE", "SUCCESS",
                rule, List.of(new FeeTierSnapshot(
                        302L, BigDecimal.ZERO, null, BigDecimal.ZERO, null, null, null)));
        FeeVersionSnapshot original = source.feeSnapshot();
        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                original.schemaVersion(), original.merchantId(), original.feePlanId(),
                original.feePlanVersionId(), original.feePlanVersionNo(), original.pricingLockTime(),
                original.settlementCurrency(), original.percentageBasis(), original.feeCurrencyPolicy(),
                original.roundingMode(), new ReservePolicySnapshot(
                        BigDecimal.ZERO, ReserveBasis.LABEL_AMOUNT, "D", 180,
                        ReserveRefundPolicy.PROPORTIONAL_RETURN), original.refundFeeReturnPolicy(),
                List.of(configuredRule), original.snapshotHash());
        return new CompletionCommand(
                source.message(), source.claim(), source.processingOwner(), snapshot, source.currentLocator(),
                source.paymentType(), source.paymentMethod(), source.occurredRiskServices(), source.source(),
                source.settlementEligibleDate(), null);
    }

    private FeeVersionSnapshot snapshot() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                101L, FeeMode.STANDARD, new BigDecimal("2.3"),
                new Money(new BigDecimal("0.30"), "USD", 2),
                new Money(new BigDecimal("0.50"), "USD", 2),
                new Money(new BigDecimal("5.00"), "USD", 2), null);
        FeeRuleConfigurationSnapshot configuredRule = new FeeRuleConfigurationSnapshot(
                101L, "TRANSACTION_FEE", "PAYMENT", "BANK_CARD", "VISA", "NONE", "SUCCESS",
                rule, List.of());
        return new FeeVersionSnapshot(
                3, "M-1", 10L, 11L, 2, LocalDateTime.of(2026, 8, 26, 8, 59), "USD",
                PercentageBasis.LABEL_AMOUNT, FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP,
                new ReservePolicySnapshot(new BigDecimal("10"), ReserveBasis.LABEL_AMOUNT,
                        "D", 180, ReserveRefundPolicy.PROPORTIONAL_RETURN),
                RefundFeeReturnPolicy.NONE, List.of(configuredRule), "a".repeat(64));
    }

    private CompletionCommand refundCommand() {
        return refundCommand("USD");
    }

    private CompletionCommand refundCommand(String labelCurrency) {
        LocalDateTime sourceTime = LocalDateTime.of(2026, 8, 20, 9, 0);
        LocalDateTime refundTime = LocalDateTime.of(2026, 8, 26, 10, 0);
        ClearingOperationFacts refund = new ClearingOperationFacts(
                "RF-2", "OP-1", "PAY-1", "M-1", "ORDER-1", "REFUND", "SUCCESS",
                labelCurrency, new BigDecimal("20.00"), labelCurrency, new BigDecimal("20.00"),
                labelCurrency, new BigDecimal("20.00"), 2, refundTime, refundTime.minusHours(8),
                "Asia/Shanghai", 8);
        ClearingOperationFacts source = new ClearingOperationFacts(
                "PAY-1", "OP-1", null, "M-1", "ORDER-1", "PAYMENT", "SUCCESS",
                labelCurrency, new BigDecimal("100.00"), labelCurrency, new BigDecimal("100.00"),
                labelCurrency, new BigDecimal("100.00"), 2, sourceTime, sourceTime.minusHours(8),
                "Asia/Shanghai", 6);
        ClearingClaimResult claim = new ClearingClaimResult(
                ClearingClaimResult.Outcome.ACQUIRED, "FS-RF-2", 0, 3, refund);
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("MSG-RF-2");
        message.setTraceId("TRACE-RF-2");
        message.setTransactionId("RF-2");
        message.setOperationId("OP-1");
        message.setMerchantId("M-1");
        message.setMerchantOrderNo("ORDER-1");
        message.setTransactionType("REFUND");
        message.setTransactionDateTime(refundTime);
        LocatorFacts currentLocator = new LocatorFacts(
                "RF-2", "OP-1", "PAY-1", "M-1", "ORDER-1", "REFUND", refundTime, sourceTime);
        LocatorFacts sourceLocator = new LocatorFacts(
                "PAY-1", "OP-1", "PAY-1", "M-1", "ORDER-1", "PAYMENT", sourceTime, sourceTime);
        return new CompletionCommand(message, claim, "worker-2", refundSnapshot(), currentLocator,
                "BANK_CARD", "VISA", Set.of(), new SourceContext(source, sourceLocator, sourceSnapshot()),
                refundTime.toLocalDate(), null);
    }

    private FeeVersionSnapshot refundSnapshot() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                201L, FeeMode.STANDARD, BigDecimal.ONE, null, null, null, null);
        FeeRuleConfigurationSnapshot configuredRule = new FeeRuleConfigurationSnapshot(
                201L, "REFUND_FEE", "REFUND", "BANK_CARD", "VISA", "NONE", "SUCCESS",
                rule, List.of());
        return new FeeVersionSnapshot(
                3, "M-1", 20L, 21L, 1, LocalDateTime.of(2026, 8, 26, 9, 59), "USD",
                PercentageBasis.LABEL_AMOUNT, FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP,
                new ReservePolicySnapshot(BigDecimal.ZERO, ReserveBasis.LABEL_AMOUNT,
                        "D", 180, ReserveRefundPolicy.PROPORTIONAL_RETURN),
                RefundFeeReturnPolicy.NONE, List.of(configuredRule), "b".repeat(64));
    }

    private FeeVersionSnapshot sourceSnapshot() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                101L, FeeMode.STANDARD, new BigDecimal("10"),
                new Money(new BigDecimal("1.00"), "USD", 2), null,
                new Money(new BigDecimal("5.00"), "USD", 2), null);
        FeeRuleConfigurationSnapshot configuredRule = new FeeRuleConfigurationSnapshot(
                101L, "TRANSACTION_FEE", "PAYMENT", "BANK_CARD", "VISA", "NONE", "SUCCESS",
                rule, List.of());
        return new FeeVersionSnapshot(
                3, "M-1", 10L, 11L, 2, LocalDateTime.of(2026, 8, 20, 8, 59), "USD",
                PercentageBasis.LABEL_AMOUNT, FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP,
                new ReservePolicySnapshot(new BigDecimal("10"), ReserveBasis.LABEL_AMOUNT,
                        "D", 180, ReserveRefundPolicy.PROPORTIONAL_RETURN),
                RefundFeeReturnPolicy.PROPORTIONAL, List.of(configuredRule), "c".repeat(64));
    }

    private List<ClearingTransactionDetailDO> sourceClearingDetails(LocalDateTime sourceTime) {
        return List.of(
                sourceFeeDetail("SRC-FEE-1", "PERCENTAGE", "DEBIT", "10.00", 1, sourceTime),
                sourceFeeDetail("SRC-FEE-2", "FIXED", "DEBIT", "1.00", 2, sourceTime),
                sourceFeeDetail("SRC-FEE-3", "LIMIT_ADJUSTMENT", "CREDIT", "6.00", 3, sourceTime));
    }

    private ClearingTransactionDetailDO sourceFeeDetail(String detailNo,
                                                         String componentType,
                                                         String direction,
                                                         String amount,
                                                         int lineNo,
                                                         LocalDateTime sourceTime) {
        ClearingTransactionDetailDO row = new ClearingTransactionDetailDO();
        row.setClearingDetailNo(detailNo);
        row.setFinanceStateId("FS-PAY-1");
        row.setTransactionId("PAY-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setTransactionType("PAYMENT");
        row.setClearingRevision(1);
        row.setLineNo(lineNo);
        row.setItemType("PLATFORM_FEE");
        row.setFeeCategory("TRANSACTION_FEE");
        row.setRiskServiceType("NONE");
        row.setDirection(direction);
        row.setFeeGroupNo("SRC-GROUP-1");
        row.setComponentType(componentType);
        row.setAmount(new BigDecimal(amount));
        row.setCurrency("USD");
        row.setCurrencyExponent(2);
        row.setLimitEvaluationStatus("FINAL_AT_CLEARING");
        row.setRecordStatus("ACTIVE");
        row.setTransactionDateTime(sourceTime);
        return row;
    }

    private List<ClearingTransactionDetailDO> refundFacts() {
        LocalDateTime firstRefundTime = LocalDateTime.of(2026, 8, 25, 10, 0);
        ClearingTransactionDetailDO principal = new ClearingTransactionDetailDO();
        principal.setClearingDetailNo("RF-1-PRINCIPAL");
        principal.setTransactionId("RF-1");
        principal.setOperationId("OP-1");
        principal.setSourceTransactionId("PAY-1");
        principal.setMerchantId("M-1");
        principal.setTransactionType("REFUND");
        principal.setItemType("PRINCIPAL");
        principal.setDirection("DEBIT");
        principal.setLabelCurrency("USD");
        principal.setLabelAmount(new BigDecimal("10.00"));
        principal.setLabelCurrencyExponent(2);
        principal.setRecordStatus("ACTIVE");
        principal.setTransactionDateTime(firstRefundTime);

        ClearingTransactionDetailDO reversal = new ClearingTransactionDetailDO();
        reversal.setClearingDetailNo("RF-1-REVERSAL");
        reversal.setTransactionId("RF-1");
        reversal.setOperationId("OP-1");
        reversal.setSourceTransactionId("PAY-1");
        reversal.setSourceClearingDetailNo("SRC-FEE-1");
        reversal.setMerchantId("M-1");
        reversal.setTransactionType("REFUND");
        reversal.setItemType("FEE_REVERSAL");
        reversal.setDirection("CREDIT");
        reversal.setAmount(new BigDecimal("0.50"));
        reversal.setCurrency("USD");
        reversal.setCurrencyExponent(2);
        reversal.setRecordStatus("ACTIVE");
        reversal.setTransactionDateTime(firstRefundTime);
        return List.of(principal, reversal);
    }

    private ClearingTransactionFinanceStateDO processingRefundState(LocalDateTime now,
                                                                    LocalDateTime refundTime) {
        ClearingTransactionFinanceStateDO row = processingState(now);
        row.setFinanceStateId("FS-RF-2");
        row.setTransactionId("RF-2");
        row.setMerchantId("M-1");
        row.setProcessingOwner("worker-2");
        row.setTransactionDateTime(refundTime);
        return row;
    }

    private ClearingTransactionFinanceStateDO clearedSourceState(LocalDateTime sourceTime) {
        ClearingTransactionFinanceStateDO row = new ClearingTransactionFinanceStateDO();
        row.setFinanceStateId("FS-PAY-1");
        row.setTransactionId("PAY-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setClearingStatus("CLEARED");
        row.setClearingRevision(1);
        row.setVersion(7);
        row.setTransactionDateTime(sourceTime);
        return row;
    }

    private ClearingReserveStateDO reserveState(LocalDateTime sourceTime) {
        ClearingReserveStateDO row = new ClearingReserveStateDO();
        row.setReserveStateId("RS-PAY-1");
        row.setOriginalTransactionId("PAY-1");
        row.setOperationId("OP-1");
        row.setOriginalFinanceStateId("FS-PAY-1");
        row.setOriginalHoldDetailNo("SRC-HOLD-1");
        row.setOriginalFeePlanVersionId(11L);
        row.setOriginalReserveSnapshotHash("d".repeat(64));
        row.setMerchantId("M-1");
        row.setReserveCurrency("USD");
        row.setReserveCurrencyExponent(2);
        row.setOriginalBasisAmount(new BigDecimal("100.00"));
        row.setOriginalReserveRate(new BigDecimal("10"));
        row.setOriginalRoundingMode("HALF_UP");
        row.setRetainedAmount(new BigDecimal("10.00"));
        row.setReturnedAmount(new BigDecimal("1.00"));
        row.setReleasedAmount(BigDecimal.ZERO);
        row.setRemainingAmount(new BigDecimal("9.00"));
        row.setExpectedReserveReleaseDate(LocalDate.of(2027, 2, 16));
        row.setReserveStatus("OPEN");
        row.setTransactionDateTime(sourceTime);
        row.setVersion(4L);
        return row;
    }

    private ClearingTransactionFinanceStateDO processingState(LocalDateTime now) {
        ClearingTransactionFinanceStateDO row = new ClearingTransactionFinanceStateDO();
        row.setFinanceStateId("FS-1");
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setClearingStatus("PROCESSING");
        row.setClearingRevision(0);
        row.setProcessingOwner("worker-1");
        row.setProcessingDeadline(now.plusMinutes(1));
        row.setVersion(3);
        row.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 9, 0));
        return row;
    }

    private ClearingTransactionFinanceStateDO recalculationState(LocalDateTime transactionTime) {
        ClearingTransactionFinanceStateDO row = new ClearingTransactionFinanceStateDO();
        row.setFinanceStateId("FS-1");
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setClearingStatus("CLEARED");
        row.setClearingRevision(1);
        row.setSettlementStatus("NOT_SETTLED");
        row.setVersion(3);
        row.setTransactionDateTime(transactionTime);
        return row;
    }

    private ClearingTransactionLocatorDO locatorRow(LocatorFacts facts) {
        ClearingTransactionLocatorDO row = new ClearingTransactionLocatorDO();
        row.setTransactionId(facts.transactionId());
        row.setOperationId(facts.operationId());
        row.setRootTransactionId(facts.rootTransactionId());
        row.setMerchantId(facts.merchantId());
        row.setMerchantOrderNo(facts.merchantOrderNo());
        row.setTransactionType(facts.transactionType());
        row.setTransactionDateTime(facts.transactionDateTime());
        row.setRootTransactionDateTime(facts.rootTransactionDateTime());
        return row;
    }

}
