package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.entity.MerchantFundAccountDO;
import com.scott.payment.settlement.entity.MerchantFundLedgerDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReserveClearingDetailDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementFundMapper;
import com.scott.payment.settlement.mapper.SettlementProjectionMapper;
import com.scott.payment.settlement.mapper.SettlementReserveMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementLedgerPostingServiceTest
 * @date : 2026-09-01 23:20
 * @email : scott_x@163.com
 * @description : 验证结算净额、资金流水、保证金资金化和候选状态在同一幂等入账边界完成
 * @status : create
 */
class DefaultSettlementLedgerPostingServiceTest {

    /** 100本金减10手续费减10保证金后，只允许形成一条80 USD净入账流水。 */
    @Test
    void shouldPostOneNetLedgerAndMaterializeReserve() {
        SettlementBatchMapper batchMapper = mock(SettlementBatchMapper.class);
        SettlementResultMapper resultMapper = mock(SettlementResultMapper.class);
        SettlementFundMapper fundMapper = mock(SettlementFundMapper.class);
        SettlementReserveMapper reserveMapper = mock(SettlementReserveMapper.class);
        SettlementCandidateMapper candidateMapper = mock(SettlementCandidateMapper.class);
        SettlementBatchCandidateMapper relationMapper = mock(SettlementBatchCandidateMapper.class);
        SettlementProjectionMapper projectionMapper = mock(SettlementProjectionMapper.class);
        DefaultSettlementLedgerPostingService service = new DefaultSettlementLedgerPostingService(
                batchMapper, resultMapper, fundMapper, reserveMapper,
                candidateMapper, relationMapper, projectionMapper);

        SettlementBatchDO batch = batch();
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(batchMapper.beginPosting(anyString(), anyString(), anyLong(), any())).thenReturn(1);
        when(resultMapper.selectFinancialItemsByBatch(batch.getSettlementBatchNo()))
                .thenReturn(List.of(item(1L, "PRINCIPAL", "CREDIT", "100.00"),
                        item(1L, "FEE_GROUP_FINAL", "DEBIT", "10.00"),
                        item(1L, "RESERVE_HOLD", "DEBIT", "10.00")));
        when(resultMapper.selectIdentityRateId(batch.getSettlementBatchNo(), "USD")).thenReturn(9L);
        when(resultMapper.insertItemsIdempotent(anyList())).thenReturn(1);
        when(resultMapper.countLedgerPostingByBatch(batch.getSettlementBatchNo())).thenReturn(1);

        MerchantFundAccountDO account = new MerchantFundAccountDO();
        account.setId(21L);
        account.setMerchantId("240001");
        account.setSettlementCurrency("USD");
        account.setAvailableBalance(new BigDecimal("20.00"));
        account.setAccountStatus("NORMAL");
        account.setAccountVersion(3L);
        when(fundMapper.selectAccountForUpdate(21L)).thenReturn(account);
        when(fundMapper.selectMaxAccountSequence(21L)).thenReturn(7L);
        when(fundMapper.insertLedger(any())).thenReturn(1);
        when(fundMapper.updateAccountBalance(anyLong(), any(), any(), anyLong(), any())).thenReturn(1);
        AtomicReference<com.scott.payment.settlement.entity.MerchantReserveItemDO> reserveStored =
                new AtomicReference<>();
        when(reserveMapper.insertItemIdempotent(any())).thenAnswer(invocation -> {
            com.scott.payment.settlement.entity.MerchantReserveItemDO row = invocation.getArgument(0);
            row.setId(31L);
            reserveStored.set(row);
            return 1;
        });
        when(reserveMapper.selectBySourceForUpdate("240001", "RCD-1"))
                .thenAnswer(ignored -> reserveStored.get());
        when(reserveMapper.insertActionIdempotent(any())).thenReturn(1);
        when(candidateMapper.markBatchPosted(anyString(), any())).thenReturn(1);
        when(relationMapper.markBatchPosted(anyString(), any())).thenReturn(1);
        when(projectionMapper.insertTasksIdempotent(anyList())).thenReturn(1);
        when(batchMapper.markPosted(anyString(), anyString(), anyLong(), any())).thenReturn(1);

        int posted = service.post(batch, facts(), "worker-1", LocalDateTime.of(2026, 8, 26, 10, 0));

        assertThat(posted).isEqualTo(1);
        ArgumentCaptor<BigDecimal> balanceCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(fundMapper).updateAccountBalance(anyLong(), balanceCaptor.capture(),
                any(), anyLong(), any());
        assertThat(balanceCaptor.getValue()).isEqualByComparingTo("100.00");
        verify(resultMapper).insertItemsIdempotent(org.mockito.ArgumentMatchers.argThat(rows -> {
            if (rows.size() != 1) {
                return false;
            }
            SettlementResultItemDO net = rows.get(0);
            return "LEDGER_POSTING".equals(net.getResultRole())
                    && "NET_SETTLEMENT".equals(net.getResultItemType())
                    && "CREDIT".equals(net.getDirection())
                    && net.getTargetAmount().compareTo(new BigDecimal("80.00")) == 0;
        }));
        verify(fundMapper).insertLedger(org.mockito.ArgumentMatchers.argThat(ledger ->
                "CREDIT".equals(ledger.getDirection())
                        && ledger.getAmount().compareTo(new BigDecimal("80.00")) == 0
                        && ledger.getBalanceBefore().compareTo(new BigDecimal("20.00")) == 0
                        && ledger.getBalanceAfter().compareTo(new BigDecimal("100.00")) == 0
                        && ledger.getAccountSequence() == 8L));
        verify(reserveMapper).insertItemIdempotent(org.mockito.ArgumentMatchers.argThat(reserve ->
                "USD".equals(reserve.getCurrency())
                        && reserve.getRetainedAmount().compareTo(new BigDecimal("10.00")) == 0));
        verify(candidateMapper).markBatchPosted(batch.getSettlementBatchNo(),
                LocalDateTime.of(2026, 8, 26, 10, 0));
    }

    /** 保证金释放只改变资金和保证金责任，不得为不存在的交易修订生成伪投影任务。 */
    @Test
    void shouldPostReserveReleaseWithoutTransactionProjectionTask() {
        SettlementBatchMapper batchMapper = mock(SettlementBatchMapper.class);
        SettlementResultMapper resultMapper = mock(SettlementResultMapper.class);
        SettlementFundMapper fundMapper = mock(SettlementFundMapper.class);
        SettlementReserveMapper reserveMapper = mock(SettlementReserveMapper.class);
        SettlementCandidateMapper candidateMapper = mock(SettlementCandidateMapper.class);
        SettlementBatchCandidateMapper relationMapper = mock(SettlementBatchCandidateMapper.class);
        SettlementProjectionMapper projectionMapper = mock(SettlementProjectionMapper.class);
        DefaultSettlementLedgerPostingService service = new DefaultSettlementLedgerPostingService(
                batchMapper, resultMapper, fundMapper, reserveMapper,
                candidateMapper, relationMapper, projectionMapper);

        SettlementBatchDO batch = batch();
        batch.setBatchType("RESERVE_RELEASE");
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(batchMapper.beginPosting(anyString(), anyString(), anyLong(), any())).thenReturn(1);
        when(resultMapper.selectFinancialItemsByBatch(batch.getSettlementBatchNo()))
                .thenReturn(List.of(item(1L, "RESERVE_RELEASE", "CREDIT", "10.00")));
        when(resultMapper.selectIdentityRateId(batch.getSettlementBatchNo(), "USD")).thenReturn(9L);
        when(resultMapper.insertItemsIdempotent(anyList())).thenReturn(1);
        when(resultMapper.countLedgerPostingByBatch(batch.getSettlementBatchNo())).thenReturn(1);

        MerchantFundAccountDO account = new MerchantFundAccountDO();
        account.setId(21L);
        account.setMerchantId("240001");
        account.setSettlementCurrency("USD");
        account.setAvailableBalance(new BigDecimal("20.00"));
        account.setAccountStatus("NORMAL");
        account.setAccountVersion(3L);
        when(fundMapper.selectAccountForUpdate(21L)).thenReturn(account);
        when(fundMapper.selectMaxAccountSequence(21L)).thenReturn(7L);
        when(fundMapper.insertLedger(any())).thenReturn(1);
        when(fundMapper.updateAccountBalance(anyLong(), any(), any(), anyLong(), any())).thenReturn(1);

        com.scott.payment.settlement.entity.MerchantReserveItemDO reserveItem =
                new com.scott.payment.settlement.entity.MerchantReserveItemDO();
        reserveItem.setId(31L);
        reserveItem.setReserveNo("RS-1");
        reserveItem.setAccountId(21L);
        reserveItem.setMerchantId("240001");
        reserveItem.setCurrency("USD");
        reserveItem.setVersion(2L);
        when(reserveMapper.selectBySourceForUpdate("240001", "RCD-HOLD")).thenReturn(reserveItem);
        when(reserveMapper.insertActionIdempotent(any())).thenReturn(1);
        when(reserveMapper.applyRelease(31L, "USD", new BigDecimal("10.00"),
                batch.getSettlementBatchNo(), 2L, LocalDateTime.of(2026, 8, 26, 10, 0))).thenReturn(1);
        when(candidateMapper.markBatchPosted(anyString(), any())).thenReturn(1);
        when(relationMapper.markBatchPosted(anyString(), any())).thenReturn(1);
        when(batchMapper.markPosted(anyString(), anyString(), anyLong(), any())).thenReturn(1);

        int posted = service.post(batch, reserveReleaseFacts(), "worker-1",
                LocalDateTime.of(2026, 8, 26, 10, 0));

        assertThat(posted).isEqualTo(1);
        verify(projectionMapper, never()).insertTasksIdempotent(anyList());
        verify(reserveMapper).applyRelease(31L, "USD", new BigDecimal("10.00"),
                batch.getSettlementBatchNo(), 2L, LocalDateTime.of(2026, 8, 26, 10, 0));
    }

    /** 借方保证金调整增加责任、扣减可用余额，并保持原标签币种动作。 */
    @Test
    void shouldPostDebitReserveAdjustmentAndIncreaseLiability() {
        AdjustmentPostingFixture fixture = postAdjustment("DEBIT");

        verify(fixture.reserveMapper()).applyDebitAdjustment(31L, "USD", new BigDecimal("10.00"),
                2L, fixture.now());
        verify(fixture.reserveMapper(), never()).applyCreditAdjustment(anyLong(), anyString(), any(), anyLong(), any());
        verify(fixture.fundMapper()).insertLedger(org.mockito.ArgumentMatchers.argThat(ledger ->
                "RESERVE_SETTLEMENT".equals(ledger.getBusinessType())
                        && "DEBIT".equals(ledger.getDirection())
                        && ledger.getBalanceBefore().compareTo(new BigDecimal("100.00")) == 0
                        && ledger.getBalanceAfter().compareTo(new BigDecimal("90.00")) == 0));
        verify(fixture.reserveMapper()).insertActionIdempotent(org.mockito.ArgumentMatchers.argThat(action ->
                "ADJUSTMENT".equals(action.getActionType())
                        && "DEBIT".equals(action.getDirection())
                        && "USD".equals(action.getCurrency())));
        verify(fixture.projectionMapper(), never()).insertTasksIdempotent(anyList());
    }

    /** 贷方保证金调整减少责任、增加可用余额，并保持原标签币种动作。 */
    @Test
    void shouldPostCreditReserveAdjustmentAndDecreaseLiability() {
        AdjustmentPostingFixture fixture = postAdjustment("CREDIT");

        verify(fixture.reserveMapper()).applyCreditAdjustment(31L, "USD", new BigDecimal("10.00"),
                2L, fixture.now());
        verify(fixture.reserveMapper(), never()).applyDebitAdjustment(anyLong(), anyString(), any(), anyLong(), any());
        verify(fixture.fundMapper()).insertLedger(org.mockito.ArgumentMatchers.argThat(ledger ->
                "RESERVE_SETTLEMENT".equals(ledger.getBusinessType())
                        && "CREDIT".equals(ledger.getDirection())
                        && ledger.getBalanceBefore().compareTo(new BigDecimal("100.00")) == 0
                        && ledger.getBalanceAfter().compareTo(new BigDecimal("110.00")) == 0));
        verify(fixture.reserveMapper()).insertActionIdempotent(org.mockito.ArgumentMatchers.argThat(action ->
                "ADJUSTMENT".equals(action.getActionType())
                        && "CREDIT".equals(action.getDirection())
                        && "USD".equals(action.getCurrency())));
        verify(fixture.projectionMapper(), never()).insertTasksIdempotent(anyList());
    }

    /** 同一资金幂等键不得接受不同 Checker 审计身份的人工流水。 */
    @Test
    void shouldRejectManualLedgerReplayWhenCheckerAuditDiffers() {
        SettlementBatchMapper batchMapper = mock(SettlementBatchMapper.class);
        SettlementResultMapper resultMapper = mock(SettlementResultMapper.class);
        SettlementFundMapper fundMapper = mock(SettlementFundMapper.class);
        SettlementReserveMapper reserveMapper = mock(SettlementReserveMapper.class);
        SettlementCandidateMapper candidateMapper = mock(SettlementCandidateMapper.class);
        SettlementBatchCandidateMapper relationMapper = mock(SettlementBatchCandidateMapper.class);
        SettlementProjectionMapper projectionMapper = mock(SettlementProjectionMapper.class);
        DefaultSettlementLedgerPostingService service = new DefaultSettlementLedgerPostingService(
                batchMapper, resultMapper, fundMapper, reserveMapper,
                candidateMapper, relationMapper, projectionMapper);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 10, 0);
        SettlementBatchDO batch = batch();
        batch.setCreateMode("MANUAL_REVIEW");
        batch.setReviewOrderNo("SO20260826-00000001");
        batch.setMakerAccountId(101L);
        batch.setMakerAccountName("Settlement Maker");
        batch.setMakerReason("manual settlement requested");
        batch.setMakerTime(LocalDateTime.of(2026, 8, 26, 9, 0));
        batch.setCheckerAccountId(202L);
        batch.setCheckerAccountName("Settlement Checker");
        batch.setCheckerComment("approved against immutable snapshots");
        batch.setCheckerTime(LocalDateTime.of(2026, 8, 26, 9, 30));
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(batchMapper.beginPosting(anyString(), anyString(), anyLong(), any())).thenReturn(1);
        when(resultMapper.selectFinancialItemsByBatch(batch.getSettlementBatchNo()))
                .thenReturn(List.of(item(1L, "PRINCIPAL", "CREDIT", "10.00")));
        when(resultMapper.selectIdentityRateId(batch.getSettlementBatchNo(), "USD")).thenReturn(9L);
        when(resultMapper.insertItemsIdempotent(anyList())).thenReturn(1);
        when(resultMapper.countLedgerPostingByBatch(batch.getSettlementBatchNo())).thenReturn(1);
        MerchantFundAccountDO account = new MerchantFundAccountDO();
        account.setId(21L);
        account.setMerchantId("240001");
        account.setSettlementCurrency("USD");
        account.setAvailableBalance(new BigDecimal("100.00"));
        account.setAccountStatus("NORMAL");
        account.setAccountVersion(3L);
        when(fundMapper.selectAccountForUpdate(21L)).thenReturn(account);
        MerchantFundLedgerDO existing = new MerchantFundLedgerDO();
        existing.setAccountId(21L);
        existing.setMerchantId("240001");
        existing.setBusinessType("TRANSACTION_SETTLEMENT");
        existing.setBusinessNo(batch.getSettlementBatchNo());
        existing.setSettlementBatchNo(batch.getSettlementBatchNo());
        existing.setCurrency("USD");
        existing.setDirection("CREDIT");
        existing.setAmount(new BigDecimal("10.00"));
        existing.setOperationMode("MANUAL");
        existing.setOperatorId(101L);
        existing.setOperatorName("Settlement Maker");
        existing.setReviewerId(999L);
        existing.setReviewerName("Settlement Checker");
        existing.setOperationReason(batch.getMakerReason());
        existing.setReviewComment(batch.getCheckerComment());
        existing.setBusinessTime(batch.getMakerTime());
        existing.setSubmitTime(batch.getMakerTime());
        existing.setReviewTime(batch.getCheckerTime());
        existing.setRequestId(batch.getReviewOrderNo());
        existing.setIdempotencyKey("SETTLEMENT:" + batch.getSettlementBatchNo());
        when(fundMapper.selectLedgerByIdempotencyForUpdate(
                "SETTLEMENT:" + batch.getSettlementBatchNo())).thenReturn(existing);

        assertThatThrownBy(() -> service.post(batch, facts(), "worker-1", now))
                .isInstanceOf(SettlementProcessingException.class)
                .satisfies(failure -> assertThat(((SettlementProcessingException) failure).getFailureCode())
                        .isEqualTo("SETTLEMENT_LEDGER_IDEMPOTENCY_CONFLICT"));

        verify(candidateMapper, never()).markBatchPosted(anyString(), any());
        verify(batchMapper, never()).markPosted(anyString(), anyString(), anyLong(), any());
    }

    private AdjustmentPostingFixture postAdjustment(String direction) {
        SettlementBatchMapper batchMapper = mock(SettlementBatchMapper.class);
        SettlementResultMapper resultMapper = mock(SettlementResultMapper.class);
        SettlementFundMapper fundMapper = mock(SettlementFundMapper.class);
        SettlementReserveMapper reserveMapper = mock(SettlementReserveMapper.class);
        SettlementCandidateMapper candidateMapper = mock(SettlementCandidateMapper.class);
        SettlementBatchCandidateMapper relationMapper = mock(SettlementBatchCandidateMapper.class);
        SettlementProjectionMapper projectionMapper = mock(SettlementProjectionMapper.class);
        DefaultSettlementLedgerPostingService service = new DefaultSettlementLedgerPostingService(
                batchMapper, resultMapper, fundMapper, reserveMapper,
                candidateMapper, relationMapper, projectionMapper);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 10, 0);
        SettlementBatchDO batch = batch();
        batch.setBatchType("ADJUSTMENT");
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(batchMapper.beginPosting(anyString(), anyString(), anyLong(), any())).thenReturn(1);
        when(resultMapper.selectFinancialItemsByBatch(batch.getSettlementBatchNo()))
                .thenReturn(List.of(item(1L, "ADJUSTMENT", direction, "10.00")));
        when(resultMapper.selectIdentityRateId(batch.getSettlementBatchNo(), "USD")).thenReturn(9L);
        when(resultMapper.insertItemsIdempotent(anyList())).thenReturn(1);
        when(resultMapper.countLedgerPostingByBatch(batch.getSettlementBatchNo())).thenReturn(1);
        MerchantFundAccountDO account = new MerchantFundAccountDO();
        account.setId(21L);
        account.setMerchantId("240001");
        account.setSettlementCurrency("USD");
        account.setAvailableBalance(new BigDecimal("100.00"));
        account.setAccountStatus("NORMAL");
        account.setAccountVersion(3L);
        when(fundMapper.selectAccountForUpdate(21L)).thenReturn(account);
        when(fundMapper.selectMaxAccountSequence(21L)).thenReturn(7L);
        when(fundMapper.insertLedger(any())).thenReturn(1);
        when(fundMapper.updateAccountBalance(anyLong(), any(), any(), anyLong(), any())).thenReturn(1);
        com.scott.payment.settlement.entity.MerchantReserveItemDO reserveItem =
                new com.scott.payment.settlement.entity.MerchantReserveItemDO();
        reserveItem.setId(31L);
        reserveItem.setReserveNo("RS-1");
        reserveItem.setAccountId(21L);
        reserveItem.setMerchantId("240001");
        reserveItem.setCurrency("USD");
        reserveItem.setVersion(2L);
        when(reserveMapper.selectBySourceForUpdate("240001", "RCD-HOLD")).thenReturn(reserveItem);
        when(reserveMapper.insertActionIdempotent(any())).thenReturn(1);
        when(reserveMapper.applyDebitAdjustment(31L, "USD", new BigDecimal("10.00"), 2L, now))
                .thenReturn(1);
        when(reserveMapper.applyCreditAdjustment(31L, "USD", new BigDecimal("10.00"), 2L, now))
                .thenReturn(1);
        when(candidateMapper.markBatchPosted(anyString(), any())).thenReturn(1);
        when(relationMapper.markBatchPosted(anyString(), any())).thenReturn(1);
        when(batchMapper.markPosted(anyString(), anyString(), anyLong(), any())).thenReturn(1);

        assertThat(service.post(batch, adjustmentFacts(direction), "worker-1", now)).isEqualTo(1);
        return new AdjustmentPostingFixture(reserveMapper, fundMapper, projectionMapper, now);
    }

    private SettlementBatchDO batch() {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setBusinessDate(LocalDate.of(2026, 8, 26));
        row.setMerchantId("240001");
        row.setSettlementAccountId(21L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setBatchType("REGULAR");
        row.setBatchStatus("CALCULATED");
        row.setCandidateCount(1);
        row.setProcessingOwner("worker-1");
        row.setProcessingDeadline(LocalDateTime.of(2026, 8, 26, 10, 5));
        row.setVersion(5L);
        return row;
    }

    private SettlementBatchFacts facts() {
        SettlementCandidateDO candidate = new SettlementCandidateDO();
        candidate.setId(1L);
        candidate.setSourceType("CLEARING_REVISION");
        candidate.setSourceRevision(1);
        candidate.setSourceTransactionId("TX-1");
        candidate.setSourceTransactionDateTime(LocalDateTime.of(2026, 8, 25, 9, 0));
        candidate.setMerchantId("240001");
        SettlementReserveClearingDetailDO reserve = new SettlementReserveClearingDetailDO();
        reserve.setReserveClearingDetailNo("RCD-1");
        reserve.setTransactionId("TX-1");
        reserve.setOperationId("OP-1");
        reserve.setOriginalTransactionId("TX-1");
        reserve.setMerchantId("240001");
        reserve.setClearingRevision(1);
        reserve.setLineNo(1);
        reserve.setReserveActionType("HOLD");
        reserve.setDirection("DEBIT");
        reserve.setReserveCurrency("USD");
        reserve.setReserveCurrencyExponent(2);
        reserve.setRetainedAmount(new BigDecimal("10.00"));
        reserve.setExpectedReserveReleaseDate(LocalDate.of(2027, 2, 25));
        reserve.setTransactionDateTime(LocalDateTime.of(2026, 8, 25, 9, 0));
        return new SettlementBatchFacts(List.of(candidate), List.of(), List.of(reserve), Set.of());
    }

    private SettlementBatchFacts reserveReleaseFacts() {
        SettlementCandidateDO candidate = new SettlementCandidateDO();
        candidate.setId(1L);
        candidate.setSourceType("RESERVE_RELEASE");
        candidate.setSourceRevision(1);
        candidate.setSourceTransactionId("RESERVE-RELEASE-1");
        candidate.setSourceTransactionDateTime(LocalDateTime.of(2026, 8, 25, 9, 0));
        candidate.setMerchantId("240001");
        SettlementReserveClearingDetailDO reserve = new SettlementReserveClearingDetailDO();
        reserve.setReserveClearingDetailNo("RCD-RELEASE");
        reserve.setSourceReserveDetailNo("RCD-HOLD");
        reserve.setTransactionId("RESERVE-RELEASE-1");
        reserve.setOperationId("OP-RELEASE-1");
        reserve.setOriginalTransactionId("TX-1");
        reserve.setMerchantId("240001");
        reserve.setClearingRevision(1);
        reserve.setLineNo(1);
        reserve.setReserveActionType("RELEASE");
        reserve.setDirection("CREDIT");
        reserve.setReserveCurrency("USD");
        reserve.setReserveCurrencyExponent(2);
        reserve.setReleasedAmount(new BigDecimal("10.00"));
        reserve.setTransactionDateTime(LocalDateTime.of(2026, 8, 25, 9, 0));
        return new SettlementBatchFacts(List.of(candidate), List.of(), List.of(reserve), Set.of());
    }

    private SettlementBatchFacts adjustmentFacts(String direction) {
        SettlementCandidateDO candidate = new SettlementCandidateDO();
        candidate.setId(1L);
        candidate.setSourceType("ADJUSTMENT");
        candidate.setSourceRevision(1);
        candidate.setSourceTransactionId("RESERVE-ADJUSTMENT-1");
        candidate.setSourceTransactionDateTime(LocalDateTime.of(2026, 8, 25, 9, 0));
        candidate.setMerchantId("240001");
        SettlementReserveClearingDetailDO reserve = new SettlementReserveClearingDetailDO();
        reserve.setReserveClearingDetailNo("RCD-ADJUSTMENT");
        reserve.setSourceReserveDetailNo("RCD-HOLD");
        reserve.setTransactionId("RESERVE-ADJUSTMENT-1");
        reserve.setOperationId("OP-ADJUSTMENT-1");
        reserve.setOriginalTransactionId("TX-1");
        reserve.setMerchantId("240001");
        reserve.setClearingRevision(1);
        reserve.setLineNo(1);
        reserve.setReserveActionType("ADJUSTMENT");
        reserve.setDirection(direction);
        reserve.setReserveCurrency("USD");
        reserve.setReserveCurrencyExponent(2);
        reserve.setAdjustmentAmount(new BigDecimal("10.00"));
        reserve.setTransactionDateTime(LocalDateTime.of(2026, 8, 25, 9, 0));
        return new SettlementBatchFacts(List.of(candidate), List.of(), List.of(reserve), Set.of());
    }

    private SettlementResultItemDO item(long candidateId, String type, String direction, String amount) {
        SettlementResultItemDO row = new SettlementResultItemDO();
        row.setCandidateId(candidateId);
        row.setResultItemType(type);
        row.setResultRole("FINANCIAL_COMPONENT");
        row.setDirection(direction);
        row.setTargetAmount(new BigDecimal(amount));
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        return row;
    }

    private record AdjustmentPostingFixture(SettlementReserveMapper reserveMapper,
                                            SettlementFundMapper fundMapper,
                                            SettlementProjectionMapper projectionMapper,
                                            LocalDateTime now) {
    }
}
