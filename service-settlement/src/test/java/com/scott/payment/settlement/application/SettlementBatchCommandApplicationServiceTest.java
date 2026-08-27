package com.scott.payment.settlement.application;

import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.entity.MerchantFundAccountDO;
import com.scott.payment.settlement.entity.MerchantFundLedgerDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementProjectionTaskDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementFundMapper;
import com.scott.payment.settlement.mapper.SettlementProjectionMapper;
import com.scott.payment.settlement.mapper.SettlementReserveMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import com.scott.payment.settlement.service.SettlementBatchCreationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证结算批次入账前取消和入账后独立冲正的资金幂等边界。 */
class SettlementBatchCommandApplicationServiceTest {

    private SettlementBatchMapper batchMapper;
    private SettlementCandidateMapper candidateMapper;
    private SettlementBatchCandidateMapper relationMapper;
    private SettlementBatchCreationService creationService;
    private SettlementResultMapper resultMapper;
    private SettlementFundMapper fundMapper;
    private SettlementReserveMapper reserveMapper;
    private SettlementProjectionMapper projectionMapper;
    private SettlementBatchCommandApplicationService service;

    @BeforeEach
    void setUp() {
        batchMapper = mock(SettlementBatchMapper.class);
        candidateMapper = mock(SettlementCandidateMapper.class);
        relationMapper = mock(SettlementBatchCandidateMapper.class);
        creationService = mock(SettlementBatchCreationService.class);
        resultMapper = mock(SettlementResultMapper.class);
        fundMapper = mock(SettlementFundMapper.class);
        reserveMapper = mock(SettlementReserveMapper.class);
        projectionMapper = mock(SettlementProjectionMapper.class);
        service = new SettlementBatchCommandApplicationService(
                batchMapper, candidateMapper, relationMapper, creationService,
                resultMapper, fundMapper, reserveMapper, projectionMapper);
    }

    /** 取消尚未入账批次时，候选恢复 READY，认领关系只迁移为 RELEASED。 */
    @Test
    void shouldCancelBeforePostingAndReleaseExactlyAllCandidates() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 15, 0);
        SettlementBatchDO batch = originalBatch("CLAIMED");
        batch.setCandidateCount(2);
        batch.setVersion(4L);
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(batchMapper.cancelBeforePosting(batch.getSettlementBatchNo(), 4L, now)).thenReturn(1);
        when(candidateMapper.releaseCancelledBatch(batch.getSettlementBatchNo(), now)).thenReturn(2);
        when(relationMapper.releaseCancelledBatch(batch.getSettlementBatchNo(), now)).thenReturn(2);

        assertThat(service.cancelBeforePosting(batch.getSettlementBatchNo(), 4L, now)).isEqualTo(2);

        verify(candidateMapper).releaseCancelledBatch(batch.getSettlementBatchNo(), now);
        verify(relationMapper).releaseCancelledBatch(batch.getSettlementBatchNo(), now);
    }

    /** 已取消批次重复提交命令必须直接返回，不得再次改变候选。 */
    @Test
    void shouldTreatRepeatedCancellationAsIdempotent() {
        SettlementBatchDO batch = originalBatch("CANCELLED");
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);

        assertThat(service.cancelBeforePosting(
                batch.getSettlementBatchNo(), 0L, LocalDateTime.of(2026, 8, 26, 15, 5))).isZero();

        verify(candidateMapper, never()).releaseCancelledBatch(any(), any());
        verify(relationMapper, never()).releaseCancelledBatch(any(), any());
    }

    /** 已入账批次必须通过独立 REVERSAL 批次写相反净额和引用原流水的资金冲正。 */
    @Test
    void shouldReversePostedBatchWithOppositeLedgerAndProjection() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 16, 0);
        SettlementBatchDO original = originalBatch("POSTED");
        SettlementBatchDO reversal = reversalBatch();
        SettlementProjectionTaskDO originalTask = originalProjectionTask(original);
        SettlementResultItemDO originalNet = originalNet(original);
        MerchantFundAccountDO account = account(original);
        MerchantFundLedgerDO originalLedger = originalLedger(original);

        when(batchMapper.selectByBatchNoForUpdate(original.getSettlementBatchNo())).thenReturn(original);
        when(batchMapper.selectReversalByOriginalForUpdate(original.getSettlementBatchNo())).thenReturn(null);
        when(projectionMapper.selectTasksByBatch(original.getSettlementBatchNo()))
                .thenReturn(List.of(originalTask));
        when(creationService.create(any())).thenReturn(new SettlementBatchCreateResult(
                reversal.getId(), reversal.getSettlementBatchNo(), "2026-08-26 00000002", false));
        when(batchMapper.selectByBatchNoForUpdate(reversal.getSettlementBatchNo())).thenReturn(reversal);
        when(batchMapper.markReversing(original.getSettlementBatchNo(), 0L, now)).thenReturn(1);
        when(batchMapper.prepareReversalPosting(reversal.getSettlementBatchNo(), 1, 0L, now)).thenReturn(1);
        when(resultMapper.selectNetPostingForUpdate(original.getSettlementBatchNo())).thenReturn(originalNet);
        when(resultMapper.insertItemsIdempotent(anyList())).thenReturn(1);
        when(resultMapper.countLedgerPostingByBatch(reversal.getSettlementBatchNo())).thenReturn(1);
        when(fundMapper.selectAccountForUpdate(original.getSettlementAccountId())).thenReturn(account);
        when(fundMapper.selectLedgerByIdempotencyForUpdate("SETTLEMENT:" + original.getSettlementBatchNo()))
                .thenReturn(originalLedger);
        when(fundMapper.selectLedgerByIdempotencyForUpdate("SETTLEMENT:" + reversal.getSettlementBatchNo()))
                .thenReturn(null);
        when(fundMapper.selectMaxAccountSequence(account.getId())).thenReturn(5L);
        when(fundMapper.insertLedger(any())).thenReturn(1);
        when(fundMapper.updateAccountBalance(account.getId(), new BigDecimal("20.00"),
                new BigDecimal("100.00"), 3L, now)).thenReturn(1);
        when(reserveMapper.selectActionsByBatchForUpdate(original.getSettlementBatchNo()))
                .thenReturn(List.of());
        when(projectionMapper.insertTasksIdempotent(anyList())).thenReturn(1);
        when(batchMapper.markReversalPosted(reversal.getSettlementBatchNo(), 1L, now)).thenReturn(1);
        when(batchMapper.markReversed(original.getSettlementBatchNo(), 1L, now)).thenReturn(1);

        assertThat(service.reversePostedBatch(original.getSettlementBatchNo(), "OPS-REV-1", 0L, now))
                .isEqualTo(reversal.getSettlementBatchNo());

        ArgumentCaptor<MerchantFundLedgerDO> ledgerCaptor = ArgumentCaptor.forClass(MerchantFundLedgerDO.class);
        verify(fundMapper).insertLedger(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getDirection()).isEqualTo("DEBIT");
        assertThat(ledgerCaptor.getValue().getAmount()).isEqualByComparingTo("80.00");
        assertThat(ledgerCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("20.00");
        assertThat(ledgerCaptor.getValue().getReversalOfLedgerId()).isEqualTo(originalLedger.getId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SettlementProjectionTaskDO>> taskCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectionMapper).insertTasksIdempotent(taskCaptor.capture());
        SettlementProjectionTaskDO reversedTask = taskCaptor.getValue().get(0);
        assertThat(reversedTask.getProjectionAction()).isEqualTo("REVERSE");
        assertThat(reversedTask.getOriginalBatchNo()).isEqualTo(original.getSettlementBatchNo());
        assertThat(reversedTask.getSettlementAmount()).isEqualByComparingTo("-80.00");
    }

    /** 原批次已完成冲正时，同一命令重放只返回既有冲正批次号。 */
    @Test
    void shouldReturnExistingCompletedReversalWithoutPostingAgain() {
        SettlementBatchDO original = originalBatch("REVERSED");
        SettlementBatchDO reversal = reversalBatch();
        reversal.setBatchStatus("POSTED");
        when(batchMapper.selectByBatchNoForUpdate(original.getSettlementBatchNo())).thenReturn(original);
        when(batchMapper.selectReversalByOriginalForUpdate(original.getSettlementBatchNo()))
                .thenReturn(reversal);

        assertThat(service.reversePostedBatch(original.getSettlementBatchNo(), "OPS-REV-1", 0L,
                LocalDateTime.of(2026, 8, 26, 16, 5))).isEqualTo(reversal.getSettlementBatchNo());

        verify(fundMapper, never()).insertLedger(any());
        verify(projectionMapper, never()).insertTasksIdempotent(anyList());
    }

    private SettlementBatchDO originalBatch(String status) {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setId(1L);
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setBusinessDate(LocalDate.of(2026, 8, 26));
        row.setBusinessTimeZone("Asia/Shanghai");
        row.setMerchantId("M1001");
        row.setSettlementProfileId(11L);
        row.setSettlementAccountId(21L);
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setBatchType("REGULAR");
        row.setCutoffBeginTime(LocalDateTime.of(2026, 8, 25, 0, 0));
        row.setCutoffEndTime(LocalDateTime.of(2026, 8, 26, 0, 0));
        row.setBatchStatus(status);
        row.setCandidateCount(1);
        row.setVersion(0L);
        return row;
    }

    private SettlementBatchDO reversalBatch() {
        SettlementBatchDO row = originalBatch("CREATED");
        row.setId(2L);
        row.setSettlementBatchNo("SB20260826-00000002");
        row.setBatchType("REVERSAL");
        row.setOriginalBatchNo("SB20260826-00000001");
        row.setCandidateCount(0);
        return row;
    }

    private SettlementProjectionTaskDO originalProjectionTask(SettlementBatchDO original) {
        SettlementProjectionTaskDO row = new SettlementProjectionTaskDO();
        row.setTaskNo("SP01");
        row.setSettlementBatchNo(original.getSettlementBatchNo());
        row.setCandidateId(101L);
        row.setTransactionId("TXN-1001");
        row.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 8, 0));
        row.setClearingRevision(1);
        row.setOperationId("OP-1001");
        row.setMerchantId(original.getMerchantId());
        row.setSettlementCurrency("USD");
        row.setSettlementAmount(new BigDecimal("80.00"));
        row.setTaskStatus("COMPLETED");
        return row;
    }

    private SettlementResultItemDO originalNet(SettlementBatchDO original) {
        SettlementResultItemDO row = new SettlementResultItemDO();
        row.setId(501L);
        row.setSettlementBatchNo(original.getSettlementBatchNo());
        row.setDirection("CREDIT");
        row.setTargetAmount(new BigDecimal("80.00"));
        row.setTargetCurrency("USD");
        row.setTargetCurrencyExponent(2);
        row.setSettlementBatchRateId(601L);
        row.setLedgerIdempotencyKey("SETTLEMENT:" + original.getSettlementBatchNo());
        return row;
    }

    private MerchantFundAccountDO account(SettlementBatchDO original) {
        MerchantFundAccountDO row = new MerchantFundAccountDO();
        row.setId(original.getSettlementAccountId());
        row.setMerchantId(original.getMerchantId());
        row.setSettlementCurrency(original.getTargetCurrency());
        row.setAvailableBalance(new BigDecimal("100.00"));
        row.setAccountVersion(3L);
        return row;
    }

    private MerchantFundLedgerDO originalLedger(SettlementBatchDO original) {
        MerchantFundLedgerDO row = new MerchantFundLedgerDO();
        row.setId(701L);
        row.setBusinessType("TRANSACTION_SETTLEMENT");
        row.setSettlementBatchNo(original.getSettlementBatchNo());
        return row;
    }
}
