package com.scott.payment.settlement.application;

import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementPrincipalCorrectionCommandResult;
import com.scott.payment.settlement.dto.SettlementPrincipalCorrectionDecisionCommand;
import com.scott.payment.settlement.dto.SettlementPrincipalCorrectionSubmitCommand;
import com.scott.payment.settlement.entity.MerchantFundAccountDO;
import com.scott.payment.settlement.entity.MerchantFundLedgerDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementBatchRateDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementOperationIdentityDO;
import com.scott.payment.settlement.entity.SettlementPrincipalCorrectionItemDO;
import com.scott.payment.settlement.entity.SettlementPrincipalCorrectionOrderDO;
import com.scott.payment.settlement.entity.SettlementProjectionTaskDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.entity.SettlementTransactionClearingDetailDO;
import com.scott.payment.settlement.exception.SettlementPrincipalCorrectionProjectionException;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementBatchRateMapper;
import com.scott.payment.settlement.mapper.SettlementClearingFactMapper;
import com.scott.payment.settlement.mapper.SettlementFundMapper;
import com.scott.payment.settlement.mapper.SettlementPrincipalCorrectionMapper;
import com.scott.payment.settlement.mapper.SettlementPrincipalCorrectionProjectionMapper;
import com.scott.payment.settlement.mapper.SettlementProjectionMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证已入账标签本金差额按原批次标签汇率重算，并通过异人复核、唯一流水和可靠投影闭环。
 */
class SettlementPrincipalCorrectionApplicationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 10, 0);

    @Test
    void shouldFreezeDebitDifferenceAndExecuteWithDifferentChecker() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));

        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);

        assertThat(submitted.correctionStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(submitted.correctionDirection()).isEqualTo("DEBIT");
        assertThat(submitted.correctionAmount()).isEqualByComparingTo("1.00");
        assertThat(fixture.insertedItems).singleElement().satisfies(item -> {
            assertThat(item.getLabelAmount()).isEqualByComparingTo("100.00");
            assertThat(item.getLabelCurrency()).isEqualTo("AUD");
            assertThat(item.getOriginalLockedRateId()).isEqualTo(300L);
            assertThat(item.getOriginalLockedDirectRate()).isEqualByComparingTo("1.000000000000");
            assertThat(item.getLabelLockedRateId()).isEqualTo(301L);
            assertThat(item.getLabelLockedDirectRate()).isEqualByComparingTo("0.650000000000");
            assertThat(item.getExpectedTargetAmount()).isEqualByComparingTo("65.00");
            assertThat(item.getSignedDeltaAmount()).isEqualByComparingTo("-1.00");
            assertThat(item.getCorrectedProjectionAmount()).isEqualByComparingTo("59.00");
        });

        fixture.prepareApproval();
        SettlementPrincipalCorrectionCommandResult executed = fixture.service.decide(
                submitted.correctionNo(), decision(99L));

        assertThat(executed.correctionStatus()).isEqualTo("EXECUTED");
        assertThat(fixture.insertedLedger.getBusinessType())
                .isEqualTo("SETTLEMENT_PRINCIPAL_CORRECTION");
        assertThat(fixture.insertedLedger.getDirection()).isEqualTo("DEBIT");
        assertThat(fixture.insertedLedger.getAmount()).isEqualByComparingTo("1.00");
        assertThat(fixture.insertedLedger.getBalanceBefore()).isEqualByComparingTo("500.00");
        assertThat(fixture.insertedLedger.getBalanceAfter()).isEqualByComparingTo("499.00");
        assertThat(fixture.insertedLedger.getOperatorId()).isEqualTo(88L);
        assertThat(fixture.insertedLedger.getReviewerId()).isEqualTo(99L);
        verify(fixture.correctionMapper).activateItems(eq(submitted.correctionNo()), any());
        verify(fixture.correctionMapper).execute(any(), eq(0L));
    }

    @Test
    void shouldBoundLedgerRequestIdWithoutLosingDecisionIdempotencyKey() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);
        fixture.prepareApproval();
        String decisionRequestKey = "principal-correction-approve-20261114-"
                + "SPCbdbd8c57988072fa791336c96c13cbc7";

        fixture.service.decide(submitted.correctionNo(), decision(99L, decisionRequestKey));

        assertThat(fixture.order.getDecisionRequestKey()).isEqualTo(decisionRequestKey);
        assertThat(fixture.insertedLedger.getRequestId())
                .startsWith("SPCR")
                .hasSizeLessThanOrEqualTo(64)
                .isNotEqualTo(decisionRequestKey);
        assertThat(fixture.insertedLedger.getIdempotencyKey())
                .isEqualTo("SETTLEMENT_PRINCIPAL_CORRECTION:" + submitted.correctionNo());
    }

    @Test
    void shouldAcceptLedgerReloadWithDatabaseMillisecondPrecision() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);
        fixture.prepareApproval();
        LocalDateTime decisionTime = NOW.plusMinutes(5).plusNanos(123_456_789);
        when(fixture.fundMapper.selectLedgerByIdempotencyForUpdate(
                "SETTLEMENT_PRINCIPAL_CORRECTION:" + submitted.correctionNo()))
                .thenAnswer(invocation -> {
                    if (fixture.insertedLedger == null) {
                        return null;
                    }
                    fixture.insertedLedger.setReviewTime(
                            fixture.insertedLedger.getReviewTime().truncatedTo(ChronoUnit.MILLIS));
                    return fixture.insertedLedger;
                });

        SettlementPrincipalCorrectionCommandResult executed = fixture.service.decide(
                submitted.correctionNo(),
                new SettlementPrincipalCorrectionDecisionCommand(
                        "DECIDE-PC-MILLIS", 0L, "APPROVE", "frozen facts checked",
                        operator(99L, decisionTime)));

        assertThat(executed.correctionStatus()).isEqualTo("EXECUTED");
        assertThat(fixture.order.getDecisionTime())
                .isEqualTo(decisionTime.truncatedTo(ChronoUnit.MILLIS));
        assertThat(fixture.insertedLedger.getReviewTime())
                .isEqualTo(decisionTime.truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void zeroDifferenceShouldKeepAuditAndSkipZeroLedger() {
        Fixture fixture = new Fixture(new BigDecimal("65.00"));
        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);
        fixture.prepareApproval();

        SettlementPrincipalCorrectionCommandResult executed = fixture.service.decide(
                submitted.correctionNo(), decision(99L));

        assertThat(executed.correctionDirection()).isEqualTo("CREDIT");
        assertThat(executed.correctionAmount()).isEqualByComparingTo("0.00");
        assertThat(fixture.order.getCorrectionLedgerId()).isNull();
        verify(fixture.fundMapper, never()).insertLedger(any());
        verify(fixture.correctionMapper).activateItems(eq(submitted.correctionNo()), any());
    }

    @Test
    void shouldRejectMakerAsCheckerBeforeAnyMoneyMovement() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);
        when(fixture.correctionMapper.selectByCorrectionNoForUpdate(submitted.correctionNo()))
                .thenReturn(fixture.order);

        assertThatThrownBy(() -> fixture.service.decide(submitted.correctionNo(), decision(88L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maker and Checker");
        verify(fixture.fundMapper, never()).insertLedger(any());
    }

    @Test
    void shouldCorrectThreeTransactionProjectionsWithFrozenValues() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);
        fixture.prepareApproval();
        fixture.service.decide(submitted.correctionNo(), decision(99L));
        SettlementPrincipalCorrectionItemDO item = fixture.insertedItems.get(0);
        item.setProjectionStatus("INIT");
        item.setVersion(1L);
        fixture.order.setCorrectionStatus("EXECUTED");
        when(fixture.correctionMapper.selectNextProjectionForUpdate(NOW.plusHours(1))).thenReturn(item);
        when(fixture.correctionMapper.markProjectionProcessing(
                item.getCorrectionItemNo(), 1L, NOW.plusHours(1))).thenReturn(1);
        when(fixture.correctionMapper.selectByCorrectionNoForUpdate(item.getCorrectionNo()))
                .thenReturn(fixture.order);
        SettlementOperationIdentityDO operation = new SettlementOperationIdentityDO();
        operation.setTransactionId(item.getTransactionId());
        operation.setOperationId(item.getOperationId());
        operation.setMerchantId(item.getMerchantId());
        operation.setTransactionCurrency("USD");
        operation.setLabelCurrency("AUD");
        operation.setSettlementStatus("SETTLED");
        operation.setRootTransactionDateTime(NOW.minusDays(1));
        when(fixture.projectionMapper.selectOperationIdentity(
                item.getTransactionId(), item.getTransactionDateTime())).thenReturn(operation);
        when(fixture.correctionProjectionMapper.correctFinanceState(
                any(), any(), anyInt(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(fixture.correctionProjectionMapper.correctOperation(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(fixture.correctionProjectionMapper.correctOrder(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(fixture.correctionMapper.markProjectionCompleted(
                item.getCorrectionItemNo(), 2L, NOW.plusHours(1))).thenReturn(1);
        when(fixture.correctionMapper.incrementProjectionCompleted(
                item.getCorrectionNo(), NOW.plusHours(1))).thenReturn(1);

        assertThat(fixture.service.processNextProjection(NOW.plusHours(1))).isTrue();

        verify(fixture.correctionProjectionMapper).correctFinanceState(
                eq(item.getTransactionId()), eq(item.getTransactionDateTime()),
                eq(1), eq("USD"), eq(new BigDecimal("60.00")),
                eq(new BigDecimal("59.00")), eq(new BigDecimal("1.000000000000")),
                eq(new BigDecimal("0.650000000000")),
                eq(LocalDate.of(2026, 10, 31)), eq("SB20261031-00000001"), any());
        verify(fixture.correctionProjectionMapper).correctOperation(
                eq(item.getTransactionId()), eq(item.getTransactionDateTime()), eq("USD"),
                eq(new BigDecimal("60.00")), eq(new BigDecimal("59.00")),
                eq(new BigDecimal("1.000000000000")), eq(new BigDecimal("0.650000000000")),
                eq(LocalDate.of(2026, 10, 31)), eq("SB20261031-00000001"), any());
        verify(fixture.correctionProjectionMapper).correctOrder(
                eq(item.getOperationId()), eq(NOW.minusDays(1)),
                eq(item.getTransactionId()), eq(item.getTransactionDateTime()), eq("USD"),
                eq(new BigDecimal("60.00")), eq(new BigDecimal("59.00")),
                eq(new BigDecimal("1.000000000000")), eq(new BigDecimal("0.650000000000")),
                eq(LocalDate.of(2026, 10, 31)), eq("SB20261031-00000001"), any());
    }

    @Test
    void shouldRejectProjectionWhenOperationCurrenciesDoNotMatchFrozenCurrencies() {
        assertProjectionCurrencyIdentityRejected("AUD", "AUD");
        assertProjectionCurrencyIdentityRejected("USD", "CAD");
    }

    @Test
    void shouldRejectApprovalWhenSourceFactsChangedAfterSubmission() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);
        fixture.prepareApproval();
        fixture.detail.setLabelAmount(new BigDecimal("101.00"));

        assertThatThrownBy(() -> fixture.service.decide(submitted.correctionNo(), decision(99L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source changed");
        verify(fixture.fundMapper, never()).insertLedger(any());
    }

    @Test
    void shouldRejectApprovalWhenEitherFrozenRateChangedAfterSubmission() {
        Fixture changedOriginalRate = new Fixture(new BigDecimal("66.00"));
        SettlementPrincipalCorrectionCommandResult originalSubmitted = changedOriginalRate.submit(88L);
        changedOriginalRate.prepareApproval();
        changedOriginalRate.originalRate.setDirectRate(new BigDecimal("0.990000000000"));

        assertThatThrownBy(() -> changedOriginalRate.service.decide(
                originalSubmitted.correctionNo(), decision(99L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source changed");
        verify(changedOriginalRate.fundMapper, never()).insertLedger(any());

        Fixture changedLabelRate = new Fixture(new BigDecimal("66.00"));
        SettlementPrincipalCorrectionCommandResult labelSubmitted = changedLabelRate.submit(88L);
        changedLabelRate.prepareApproval();
        changedLabelRate.labelRate.setDirectRate(new BigDecimal("0.660000000000"));

        assertThatThrownBy(() -> changedLabelRate.service.decide(
                labelSubmitted.correctionNo(), decision(99L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source changed");
        verify(changedLabelRate.fundMapper, never()).insertLedger(any());
    }

    @Test
    void shouldRejectApprovalWhenAnyStoredProjectionFieldChanged() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);
        fixture.prepareApproval();
        fixture.insertedItems.get(0).setOperationId("OP-TAMPERED");

        assertThatThrownBy(() -> fixture.service.decide(submitted.correctionNo(), decision(99L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stored item changed");
        verify(fixture.fundMapper, never()).insertLedger(any());
    }

    @Test
    void shouldRejectSubmitIdempotencyKeyWithDifferentPayload() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        fixture.submit(88L);
        when(fixture.correctionMapper.selectBySubmitRequestKeyForUpdate("CREATE-PC-1"))
                .thenReturn(fixture.order);

        assertThatThrownBy(() -> fixture.service.submit(new SettlementPrincipalCorrectionSubmitCommand(
                "CREATE-PC-1", fixture.batch.getSettlementBatchNo(), fixture.batch.getVersion(),
                "different reason", operator(88L, NOW))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("submit idempotency conflict");
    }

    @Test
    void shouldRejectDecisionIdempotencyKeyWithDifferentPayload() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);
        fixture.prepareApproval();
        fixture.service.decide(submitted.correctionNo(), decision(99L));
        when(fixture.correctionMapper.selectByDecisionRequestKeyForUpdate("DECIDE-PC-1"))
                .thenReturn(fixture.order);

        assertThatThrownBy(() -> fixture.service.decide(submitted.correctionNo(),
                new SettlementPrincipalCorrectionDecisionCommand(
                        "DECIDE-PC-1", 0L, "APPROVE", "different comment",
                        operator(99L, NOW.plusMinutes(5)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decision idempotency conflict");
    }

    @Test
    void shouldRejectWithoutPostingMoneyAndCancelFrozenItems() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);
        when(fixture.correctionMapper.selectByCorrectionNoForUpdate(submitted.correctionNo()))
                .thenReturn(fixture.order);
        when(fixture.correctionMapper.cancelItems(eq(submitted.correctionNo()), any())).thenReturn(1);
        when(fixture.correctionMapper.reject(any(), eq(0L))).thenReturn(1);

        SettlementPrincipalCorrectionCommandResult rejected = fixture.service.decide(
                submitted.correctionNo(), new SettlementPrincipalCorrectionDecisionCommand(
                        "REJECT-PC-1", 0L, "REJECT", "source evidence rejected",
                        operator(99L, NOW.plusMinutes(5))));

        assertThat(rejected.correctionStatus()).isEqualTo("REJECTED");
        verify(fixture.correctionMapper).cancelItems(eq(submitted.correctionNo()), any());
        verify(fixture.fundMapper, never()).insertLedger(any());
    }

    @Test
    void shouldRejectStaleBatchAndOrderVersions() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));

        assertThatThrownBy(() -> fixture.service.submit(new SettlementPrincipalCorrectionSubmitCommand(
                "CREATE-STALE-PC", fixture.batch.getSettlementBatchNo(),
                fixture.batch.getVersion() - 1, "stale batch", operator(88L, NOW))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stale batch version");

        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);
        when(fixture.correctionMapper.selectByCorrectionNoForUpdate(submitted.correctionNo()))
                .thenReturn(fixture.order);
        assertThatThrownBy(() -> fixture.service.decide(submitted.correctionNo(),
                new SettlementPrincipalCorrectionDecisionCommand(
                        "DECIDE-STALE-PC", 1L, "APPROVE", "stale order",
                        operator(99L, NOW.plusMinutes(5)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending at the expected version");
        verify(fixture.fundMapper, never()).insertLedger(any());
    }

    @Test
    void shouldRejectCorrectionLedgerIdempotencyIdentityConflict() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        SettlementPrincipalCorrectionCommandResult submitted = fixture.submit(88L);
        fixture.prepareApproval();
        MerchantFundLedgerDO conflict = new MerchantFundLedgerDO();
        conflict.setId(999L);
        conflict.setAccountId(21L);
        conflict.setMerchantId("OTHER-MERCHANT");
        when(fixture.fundMapper.selectLedgerByIdempotencyForUpdate(
                "SETTLEMENT_PRINCIPAL_CORRECTION:" + submitted.correctionNo()))
                .thenReturn(conflict);

        assertThatThrownBy(() -> fixture.service.decide(submitted.correctionNo(), decision(99L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ledger identity");
        verify(fixture.fundMapper, never()).insertLedger(any());
    }

    @Test
    void projectionFailureShouldExposeRollbackIdentityAndRecordBoundedRetry() {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        fixture.submit(88L);
        SettlementPrincipalCorrectionItemDO item = fixture.insertedItems.get(0);
        item.setProjectionStatus("INIT");
        item.setRetryCount(0);
        item.setVersion(1L);
        LocalDateTime processingTime = NOW.plusHours(1);
        when(fixture.correctionMapper.selectNextProjectionForUpdate(processingTime)).thenReturn(item);
        when(fixture.correctionMapper.markProjectionProcessing(
                item.getCorrectionItemNo(), 1L, processingTime)).thenReturn(1);

        SettlementPrincipalCorrectionProjectionException failure = assertThrows(
                SettlementPrincipalCorrectionProjectionException.class,
                () -> fixture.service.processNextProjection(processingTime));

        assertThat(failure.getCorrectionItemNo()).isEqualTo(item.getCorrectionItemNo());
        assertThat(failure.getExpectedVersion()).isEqualTo(1L);
        assertThat(failure.getExpectedRetryCount()).isZero();
        LocalDateTime failureTime = NOW.plusHours(2);
        when(fixture.correctionMapper.recordProjectionFailure(
                item.getCorrectionItemNo(), 0, 1L,
                "PRINCIPAL_CORRECTION_PROJECTION_FAILED", failureTime.plusMinutes(1), failureTime))
                .thenReturn(1);

        assertThat(fixture.service.recordProjectionFailure(failure, failureTime)).isTrue();
        verify(fixture.correctionMapper).recordProjectionFailure(
                item.getCorrectionItemNo(), 0, 1L,
                "PRINCIPAL_CORRECTION_PROJECTION_FAILED", failureTime.plusMinutes(1), failureTime);
    }

    @Test
    void shouldRejectNonIsoCurrencyAndOverPrecisionRate() {
        Fixture invalidCurrency = new Fixture(new BigDecimal("66.00"));
        invalidCurrency.batch.setTargetCurrency("usd");
        assertThatThrownBy(() -> invalidCurrency.submit(88L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("target currency is invalid");

        Fixture invalidRate = new Fixture(new BigDecimal("66.00"));
        invalidRate.labelRate.setDirectRate(new BigDecimal("0.1234567890123"));
        assertThatThrownBy(() -> invalidRate.submit(88L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked direct rate is invalid");
    }

    @Test
    void shouldRejectMissingOrMisdirectedOriginalAndLabelRates() {
        Fixture missingOriginal = new Fixture(new BigDecimal("66.00"));
        missingOriginal.principal.setSettlementBatchRateId(999L);
        assertThatThrownBy(() -> missingOriginal.submit(88L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen source identity");

        Fixture missingLabel = new Fixture(new BigDecimal("66.00"));
        missingLabel.detail.setLabelCurrency("CAD");
        assertThatThrownBy(() -> missingLabel.submit(88L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen source identity");

        Fixture wrongOriginalSource = new Fixture(new BigDecimal("66.00"));
        wrongOriginalSource.originalRate.setSourceCurrency("EUR");
        assertThatThrownBy(() -> wrongOriginalSource.submit(88L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen source identity");

        Fixture wrongLabelTarget = new Fixture(new BigDecimal("66.00"));
        wrongLabelTarget.labelRate.setTargetCurrency("CAD");
        assertThatThrownBy(() -> wrongLabelTarget.submit(88L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rate target is inconsistent");
    }

    private SettlementPrincipalCorrectionDecisionCommand decision(long accountId) {
        return decision(accountId, "DECIDE-PC-1");
    }

    private SettlementPrincipalCorrectionDecisionCommand decision(long accountId,
                                                                    String requestKey) {
        return new SettlementPrincipalCorrectionDecisionCommand(
                requestKey, 0L, "APPROVE", "frozen facts checked",
                operator(accountId, NOW.plusMinutes(5)));
    }

    private void assertProjectionCurrencyIdentityRejected(String transactionCurrency,
                                                           String labelCurrency) {
        Fixture fixture = new Fixture(new BigDecimal("66.00"));
        fixture.submit(88L);
        SettlementPrincipalCorrectionItemDO item = fixture.insertedItems.get(0);
        item.setProjectionStatus("INIT");
        item.setRetryCount(0);
        item.setVersion(1L);
        LocalDateTime processingTime = NOW.plusHours(1);
        when(fixture.correctionMapper.selectNextProjectionForUpdate(processingTime)).thenReturn(item);
        when(fixture.correctionMapper.markProjectionProcessing(
                item.getCorrectionItemNo(), 1L, processingTime)).thenReturn(1);
        SettlementOperationIdentityDO operation = new SettlementOperationIdentityDO();
        operation.setTransactionId(item.getTransactionId());
        operation.setOperationId(item.getOperationId());
        operation.setMerchantId(item.getMerchantId());
        operation.setTransactionCurrency(transactionCurrency);
        operation.setLabelCurrency(labelCurrency);
        operation.setSettlementStatus("SETTLED");
        operation.setRootTransactionDateTime(NOW.minusDays(1));
        when(fixture.projectionMapper.selectOperationIdentity(
                item.getTransactionId(), item.getTransactionDateTime())).thenReturn(operation);

        SettlementPrincipalCorrectionProjectionException failure = assertThrows(
                SettlementPrincipalCorrectionProjectionException.class,
                () -> fixture.service.processNextProjection(processingTime));

        assertThat(failure.getCause())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("projection identity is inconsistent");
    }

    private SettlementOperatorSnapshot operator(long accountId, LocalDateTime time) {
        return new SettlementOperatorSnapshot(accountId, "Admin " + accountId,
                "SUPER_ADMIN", "127.0.0.1", "JUnit", time);
    }

    private final class Fixture {
        private final SettlementPrincipalCorrectionMapper correctionMapper =
                mock(SettlementPrincipalCorrectionMapper.class);
        private final SettlementPrincipalCorrectionProjectionMapper correctionProjectionMapper =
                mock(SettlementPrincipalCorrectionProjectionMapper.class);
        private final SettlementBatchMapper batchMapper = mock(SettlementBatchMapper.class);
        private final SettlementBatchRateMapper batchRateMapper = mock(SettlementBatchRateMapper.class);
        private final SettlementClearingFactMapper clearingFactMapper = mock(SettlementClearingFactMapper.class);
        private final SettlementResultMapper resultMapper = mock(SettlementResultMapper.class);
        private final SettlementFundMapper fundMapper = mock(SettlementFundMapper.class);
        private final SettlementProjectionMapper projectionMapper = mock(SettlementProjectionMapper.class);
        private final SettlementPrincipalCorrectionApplicationService service =
                new SettlementPrincipalCorrectionApplicationService(
                        correctionMapper, correctionProjectionMapper, batchMapper, batchRateMapper,
                        clearingFactMapper, resultMapper, fundMapper, projectionMapper,
                        Clock.fixed(Instant.parse("2026-11-06T02:00:00Z"), ZoneOffset.UTC));
        private final SettlementBatchDO batch = batch();
        private final MerchantFundLedgerDO originalLedger = originalLedger();
        private final SettlementTransactionClearingDetailDO detail;
        private final SettlementResultItemDO principal;
        private final SettlementBatchRateDO originalRate;
        private final SettlementBatchRateDO labelRate;
        private final SettlementProjectionTaskDO task;
        private SettlementPrincipalCorrectionOrderDO order;
        private List<SettlementPrincipalCorrectionItemDO> insertedItems;
        private MerchantFundLedgerDO insertedLedger;

        private Fixture(BigDecimal originalTargetAmount) {
            when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
            SettlementResultItemDO net = new SettlementResultItemDO();
            net.setSettlementBatchNo(batch.getSettlementBatchNo());
            net.setMerchantId(batch.getMerchantId());
            net.setSettlementAccountId(batch.getSettlementAccountId());
            net.setResultItemType("NET_SETTLEMENT");
            net.setResultRole("LEDGER_POSTING");
            net.setDirection("CREDIT");
            net.setTargetAmount(new BigDecimal("60.00"));
            net.setTargetCurrency(batch.getTargetCurrency());
            net.setTargetCurrencyExponent(batch.getTargetCurrencyExponent());
            net.setLedgerIdempotencyKey("SETTLEMENT:" + batch.getSettlementBatchNo());
            when(resultMapper.selectNetPostingForUpdate(batch.getSettlementBatchNo())).thenReturn(net);
            when(fundMapper.selectLedgerByIdempotencyForUpdate(net.getLedgerIdempotencyKey()))
                    .thenReturn(originalLedger);

            SettlementCandidateDO candidate = new SettlementCandidateDO();
            candidate.setId(101L);
            candidate.setSourceTransactionId("TX-1001");
            candidate.setSourceTransactionDateTime(NOW.minusHours(1));
            candidate.setSourceRevision(1);
            candidate.setMerchantId("M1001");
            candidate.setSettlementBatchNo(batch.getSettlementBatchNo());
            candidate.setTargetCurrency(batch.getTargetCurrency());
            candidate.setTargetCurrencyExponent(batch.getTargetCurrencyExponent());
            when(clearingFactMapper.selectPostedTransactionCandidatesForUpdate(
                    batch.getSettlementBatchNo())).thenReturn(List.of(candidate));

            detail = new SettlementTransactionClearingDetailDO();
            detail.setClearingDetailNo("CD-1001");
            detail.setTransactionId(candidate.getSourceTransactionId());
            detail.setTransactionDateTime(candidate.getSourceTransactionDateTime());
            detail.setClearingRevision(1);
            detail.setOperationId("OP-1001");
            detail.setMerchantId("M1001");
            detail.setItemType("PRINCIPAL");
            detail.setRecordStatus("ACTIVE");
            detail.setAmount(new BigDecimal("66.00"));
            detail.setCurrency("USD");
            detail.setCurrencyExponent(2);
            detail.setLabelAmount(new BigDecimal("100.00"));
            detail.setLabelCurrency("AUD");
            detail.setLabelCurrencyExponent(2);
            when(clearingFactMapper.selectTransactionDetails(any())).thenReturn(List.of(detail));

            principal = new SettlementResultItemDO();
            principal.setId(201L);
            principal.setSettlementBatchNo(batch.getSettlementBatchNo());
            principal.setCandidateId(candidate.getId());
            principal.setMerchantId(batch.getMerchantId());
            principal.setSettlementAccountId(batch.getSettlementAccountId());
            principal.setSourceDetailNo(detail.getClearingDetailNo());
            principal.setResultItemType("PRINCIPAL");
            principal.setResultRole("FINANCIAL_COMPONENT");
            principal.setDirection("CREDIT");
            principal.setSourceAmount(detail.getAmount());
            principal.setSourceCurrency(detail.getCurrency());
            principal.setSourceCurrencyExponent(detail.getCurrencyExponent());
            principal.setSettlementBatchRateId(300L);
            principal.setTargetAmount(originalTargetAmount);
            principal.setTargetCurrency("USD");
            principal.setTargetCurrencyExponent(2);
            principal.setRoundingMode("HALF_UP");
            when(resultMapper.selectFinancialItemsByBatch(batch.getSettlementBatchNo()))
                    .thenReturn(List.of(principal));

            originalRate = rate(300L, "USD", new BigDecimal("1.000000000000"));
            labelRate = rate(301L, "AUD", new BigDecimal("0.650000000000"));
            when(batchRateMapper.selectByBatchNo(batch.getSettlementBatchNo()))
                    .thenReturn(List.of(originalRate, labelRate));

            task = new SettlementProjectionTaskDO();
            task.setId(401L);
            task.setTaskNo("SP-1001");
            task.setSettlementBatchNo(batch.getSettlementBatchNo());
            task.setProjectionAction("SETTLE");
            task.setCandidateId(candidate.getId());
            task.setTransactionId(detail.getTransactionId());
            task.setTransactionDateTime(detail.getTransactionDateTime());
            task.setClearingRevision(1);
            task.setOperationId(detail.getOperationId());
            task.setMerchantId(detail.getMerchantId());
            task.setSettlementCurrency("USD");
            task.setSettlementAmount(new BigDecimal("60.00"));
            task.setSettlementDate(LocalDate.of(2026, 10, 31));
            task.setTaskStatus("COMPLETED");
            when(projectionMapper.selectTasksByBatch(batch.getSettlementBatchNo()))
                    .thenReturn(List.of(task));

            when(correctionMapper.insertOrder(any())).thenAnswer(invocation -> {
                order = invocation.getArgument(0);
                return 1;
            });
            when(correctionMapper.insertItems(any())).thenAnswer(invocation -> {
                insertedItems = invocation.getArgument(0);
                for (int index = 0; index < insertedItems.size(); index++) {
                    insertedItems.get(index).setId(601L + index);
                }
                return insertedItems.size();
            });
        }

        private SettlementPrincipalCorrectionCommandResult submit(long accountId) {
            return service.submit(new SettlementPrincipalCorrectionSubmitCommand(
                    "CREATE-PC-1", batch.getSettlementBatchNo(), batch.getVersion(),
                    "repair historical label principal", operator(accountId, NOW)));
        }

        private void prepareApproval() {
            when(correctionMapper.selectByCorrectionNoForUpdate(order.getCorrectionNo())).thenReturn(order);
            when(correctionMapper.selectItemsForUpdate(order.getCorrectionNo())).thenReturn(insertedItems);
            when(correctionMapper.activateItems(eq(order.getCorrectionNo()), any())).thenReturn(1);
            when(correctionMapper.execute(any(), eq(0L))).thenReturn(1);
            MerchantFundAccountDO account = new MerchantFundAccountDO();
            account.setId(21L);
            account.setMerchantId("M1001");
            account.setSettlementCurrency("USD");
            account.setAvailableBalance(new BigDecimal("500.00"));
            account.setAccountStatus("NORMAL");
            account.setAccountVersion(9L);
            when(fundMapper.selectAccountForUpdate(21L)).thenReturn(account);
            when(fundMapper.selectMaxAccountSequence(21L)).thenReturn(20L);
            when(fundMapper.insertLedger(any())).thenAnswer(invocation -> {
                insertedLedger = invocation.getArgument(0);
                insertedLedger.setId(901L);
                return 1;
            });
            when(fundMapper.updateAccountBalance(eq(21L), any(), any(), eq(9L), any())).thenReturn(1);
            when(fundMapper.selectLedgerByIdempotencyForUpdate(
                    "SETTLEMENT_PRINCIPAL_CORRECTION:" + order.getCorrectionNo()))
                    .thenAnswer(invocation -> insertedLedger);
        }

        private SettlementBatchDO batch() {
            SettlementBatchDO value = new SettlementBatchDO();
            value.setSettlementBatchNo("SB20261031-00000001");
            value.setBusinessDate(LocalDate.of(2026, 10, 31));
            value.setMerchantId("M1001");
            value.setSettlementAccountId(21L);
            value.setTargetCurrency("USD");
            value.setTargetCurrencyExponent(2);
            value.setBatchType("REGULAR");
            value.setBatchStatus("POSTED");
            value.setVersion(7L);
            return value;
        }

        private MerchantFundLedgerDO originalLedger() {
            MerchantFundLedgerDO value = new MerchantFundLedgerDO();
            value.setId(501L);
            value.setAccountId(21L);
            value.setMerchantId("M1001");
            value.setBusinessType("TRANSACTION_SETTLEMENT");
            value.setBusinessNo("SB20261031-00000001");
            value.setSettlementBatchNo("SB20261031-00000001");
            value.setCurrency("USD");
            value.setDirection("CREDIT");
            value.setAmount(new BigDecimal("60.00"));
            value.setIdempotencyKey("SETTLEMENT:SB20261031-00000001");
            return value;
        }

        private SettlementBatchRateDO rate(long id, String sourceCurrency, BigDecimal directRate) {
            SettlementBatchRateDO value = new SettlementBatchRateDO();
            value.setId(id);
            value.setSettlementBatchNo(batch.getSettlementBatchNo());
            value.setSourceCurrency(sourceCurrency);
            value.setTargetCurrency("USD");
            value.setSourceCurrencyExponent(2);
            value.setTargetCurrencyExponent(2);
            value.setDirectRate(directRate);
            value.setRateType("SETTLEMENT");
            boolean identity = sourceCurrency.equals(batch.getTargetCurrency());
            value.setRateSource(identity ? "SYSTEM_IDENTITY" : "SYSTEM_TEST");
            value.setQuoteId(identity ? null : "QUOTE-" + id);
            value.setSourceQuoteDirection("DIRECT");
            value.setEffectiveTime(NOW.minusDays(1));
            value.setLockedTime(NOW.minusHours(2));
            value.setLockedBy("service-settlement-test");
            value.setRateStatus("LOCKED");
            return value;
        }
    }
}
