package com.scott.payment.settlement.application;

import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.dto.SettlementCommandAudit;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementReversalAudit;
import com.scott.payment.settlement.entity.MerchantFundAccountDO;
import com.scott.payment.settlement.entity.MerchantFundLedgerDO;
import com.scott.payment.settlement.entity.MerchantReserveActionDO;
import com.scott.payment.settlement.entity.MerchantReserveItemDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementBatchCancellationAuditDO;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchCommandApplicationServiceTest
 * @date : 2026-09-01 23:20
 * @email : scott_x@163.com
 * @description : 验证结算批次认领、审批、入账前取消和入账后独立冲正的状态与资金幂等边界
 * @status : create
 */
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
        when(batchMapper.insertCancellationAudit(any())).thenReturn(1);

        assertThat(service.cancelBeforePosting(
                batch.getSettlementBatchNo(), 4L, cancellationAudit(now), now)).isEqualTo(2);

        verify(candidateMapper).releaseCancelledBatch(batch.getSettlementBatchNo(), now);
        verify(relationMapper).releaseCancelledBatch(batch.getSettlementBatchNo(), now);
        ArgumentCaptor<SettlementBatchCancellationAuditDO> audit =
                ArgumentCaptor.forClass(SettlementBatchCancellationAuditDO.class);
        verify(batchMapper).insertCancellationAudit(audit.capture());
        assertThat(audit.getValue().getRequestKey()).isEqualTo("CANCEL-REQ-1");
        assertThat(audit.getValue().getExpectedVersion()).isEqualTo(4L);
        assertThat(audit.getValue().getBatchStatusBefore()).isEqualTo("CLAIMED");
        assertThat(audit.getValue().getReleasedCandidateCount()).isEqualTo(2);
        assertThat(audit.getValue().getOperatorAccountId()).isEqualTo(88L);
        assertThat(audit.getValue().getOperatorRoleSnapshot()).isEqualTo("SETTLEMENT_OPERATOR");
    }

    /** 同一请求键重复提交必须返回首次结果，不得再次锁定批次或改变候选。 */
    @Test
    void shouldTreatRepeatedCancellationAsIdempotent() {
        SettlementBatchCancellationAuditDO existing = cancellationAuditRow("CANCEL-REQ-1", 2);
        when(batchMapper.selectCancellationAuditByRequestKey("CANCEL-REQ-1")).thenReturn(existing);

        assertThat(service.cancelBeforePosting(
                existing.getSettlementBatchNo(), 4L,
                cancellationAudit(LocalDateTime.of(2026, 8, 26, 15, 5)),
                LocalDateTime.of(2026, 8, 26, 15, 5))).isEqualTo(2);

        verify(batchMapper, never()).selectByBatchNoForUpdate(any());
        verify(candidateMapper, never()).releaseCancelledBatch(any(), any());
        verify(relationMapper, never()).releaseCancelledBatch(any(), any());
    }

    /** 请求键不得跨批次复用，冲突时不能进入任何批次状态机。 */
    @Test
    void shouldRejectCancellationRequestKeyUsedByAnotherBatch() {
        SettlementBatchCancellationAuditDO existing = cancellationAuditRow("CANCEL-REQ-1", 2);
        existing.setSettlementBatchNo("SB20260826-00000002");
        when(batchMapper.selectCancellationAuditByRequestKey("CANCEL-REQ-1")).thenReturn(existing);

        assertThatThrownBy(() -> service.cancelBeforePosting(
                "SB20260826-00000001", 4L,
                cancellationAudit(LocalDateTime.of(2026, 8, 26, 15, 5)),
                LocalDateTime.of(2026, 8, 26, 15, 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("request key is already in use");

        verify(batchMapper, never()).selectByBatchNoForUpdate(any());
        verify(candidateMapper, never()).releaseCancelledBatch(any(), any());
    }

    /** 并发重放在等待批次行锁后必须回读首次审计结果。 */
    @Test
    void shouldReplayCancellationFoundAfterBatchLock() {
        SettlementBatchDO batch = originalBatch("CANCELLED");
        batch.setVersion(5L);
        SettlementBatchCancellationAuditDO existing = cancellationAuditRow("CANCEL-REQ-1", 2);
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(batchMapper.selectCancellationAuditByBatchNo(batch.getSettlementBatchNo()))
                .thenReturn(existing);

        assertThat(service.cancelBeforePosting(
                batch.getSettlementBatchNo(), 4L,
                cancellationAudit(LocalDateTime.of(2026, 8, 26, 15, 5)),
                LocalDateTime.of(2026, 8, 26, 15, 5))).isEqualTo(2);

        verify(candidateMapper, never()).releaseCancelledBatch(any(), any());
        verify(batchMapper, never()).insertCancellationAudit(any());
    }

    /** 过期页面版本必须在批次状态和候选更新前失败。 */
    @Test
    void shouldRejectStaleCancellationVersionBeforeMutation() {
        SettlementBatchDO batch = originalBatch("CLAIMED");
        batch.setVersion(5L);
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);

        assertThatThrownBy(() -> service.cancelBeforePosting(
                batch.getSettlementBatchNo(), 4L,
                cancellationAudit(LocalDateTime.of(2026, 8, 26, 15, 5)),
                LocalDateTime.of(2026, 8, 26, 15, 5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stale version");

        verify(batchMapper, never()).cancelBeforePosting(any(), anyLong(), any());
        verify(candidateMapper, never()).releaseCancelledBatch(any(), any());
        verify(batchMapper, never()).insertCancellationAudit(any());
    }

    /** 已入账批次通过独立 REVERSAL 批次冲正资金，但交易投影必须保留原结算事实。 */
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
        when(relationMapper.countProjectableCandidates(original.getSettlementBatchNo())).thenReturn(1);
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

        assertThat(service.reversePostedBatch(
                original.getSettlementBatchNo(), "SRO20260831-00000001", 0L, manualAudit(now), now))
                .isEqualTo(reversal.getSettlementBatchNo());

        ArgumentCaptor<MerchantFundLedgerDO> ledgerCaptor = ArgumentCaptor.forClass(MerchantFundLedgerDO.class);
        verify(fundMapper).insertLedger(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getDirection()).isEqualTo("DEBIT");
        assertThat(ledgerCaptor.getValue().getAmount()).isEqualByComparingTo("80.00");
        assertThat(ledgerCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("20.00");
        assertThat(ledgerCaptor.getValue().getReversalOfLedgerId()).isEqualTo(originalLedger.getId());
        assertThat(ledgerCaptor.getValue().getOperationMode()).isEqualTo("MANUAL");
        assertThat(ledgerCaptor.getValue().getOperatorId()).isEqualTo(88L);
        assertThat(ledgerCaptor.getValue().getOperatorName()).isEqualTo("Settlement Operator");
        assertThat(ledgerCaptor.getValue().getOperationReason()).isEqualTo("ledger mismatch confirmed");
        assertThat(ledgerCaptor.getValue().getReviewerId()).isEqualTo(99L);
        assertThat(ledgerCaptor.getValue().getReviewerName()).isEqualTo("Settlement Checker");
        assertThat(ledgerCaptor.getValue().getReviewComment()).isEqualTo("evidence checked");
        assertThat(ledgerCaptor.getValue().getRequestId()).isEqualTo("SRO20260831-00000001");
        assertThat(ledgerCaptor.getValue().getSubmitTime()).isEqualTo(now);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SettlementProjectionTaskDO>> taskCaptor = ArgumentCaptor.forClass(List.class);
        verify(projectionMapper).insertTasksIdempotent(taskCaptor.capture());
        SettlementProjectionTaskDO reversedTask = taskCaptor.getValue().get(0);
        assertThat(reversedTask.getProjectionAction()).isEqualTo("REVERSE");
        assertThat(reversedTask.getOriginalBatchNo()).isEqualTo(original.getSettlementBatchNo());
        assertThat(reversedTask.getSettlementAmount()).isEqualByComparingTo("80.00");
        assertThat(reversedTask.getSettlementDate()).isEqualTo(originalTask.getSettlementDate());
    }

    /** 保证金借方调整冲正追加反向动作并回减借方累计，不创建交易投影。 */
    @Test
    void shouldReverseDebitAdjustmentWithoutTransactionProjectionTask() {
        assertAdjustmentReversal("DEBIT", "CREDIT", "110.00");
    }

    /** 保证金贷方调整冲正追加反向动作并回减贷方累计，不创建交易投影。 */
    @Test
    void shouldReverseCreditAdjustmentWithoutTransactionProjectionTask() {
        assertAdjustmentReversal("CREDIT", "DEBIT", "90.00");
    }

    /** 冻结账户只允许被动入账，不得执行结算批次主动冲正。 */
    @Test
    void shouldRejectReversalForFrozenSettlementAccount() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 16, 0);
        SettlementBatchDO original = originalBatch("POSTED");
        SettlementBatchDO reversal = reversalBatch();
        MerchantFundAccountDO account = account(original);
        account.setAccountStatus("FROZEN");

        when(batchMapper.selectByBatchNoForUpdate(original.getSettlementBatchNo())).thenReturn(original);
        when(batchMapper.selectReversalByOriginalForUpdate(original.getSettlementBatchNo())).thenReturn(null);
        when(projectionMapper.selectTasksByBatch(original.getSettlementBatchNo()))
                .thenReturn(List.of(originalProjectionTask(original)));
        when(relationMapper.countProjectableCandidates(original.getSettlementBatchNo())).thenReturn(1);
        when(creationService.create(any())).thenReturn(new SettlementBatchCreateResult(
                reversal.getId(), reversal.getSettlementBatchNo(), "2026-08-26 00000002", false));
        when(batchMapper.selectByBatchNoForUpdate(reversal.getSettlementBatchNo())).thenReturn(reversal);
        when(batchMapper.markReversing(original.getSettlementBatchNo(), 0L, now)).thenReturn(1);
        when(batchMapper.prepareReversalPosting(reversal.getSettlementBatchNo(), 1, 0L, now)).thenReturn(1);
        when(resultMapper.selectNetPostingForUpdate(original.getSettlementBatchNo()))
                .thenReturn(originalNet(original));
        when(resultMapper.insertItemsIdempotent(anyList())).thenReturn(1);
        when(resultMapper.countLedgerPostingByBatch(reversal.getSettlementBatchNo())).thenReturn(1);
        when(fundMapper.selectAccountForUpdate(original.getSettlementAccountId())).thenReturn(account);

        assertThatThrownBy(() -> service.reversePostedBatch(
                original.getSettlementBatchNo(), "SRO20260831-00000001", 0L, manualAudit(now), now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fund account identity is invalid");

        verify(fundMapper, never()).insertLedger(any());
        verify(reserveMapper, never()).selectActionsByBatchForUpdate(any());
    }

    private void assertAdjustmentReversal(String originalDirection,
                                          String reversalDirection,
                                          String expectedBalanceAfter) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 16, 0);
        SettlementBatchDO original = originalBatch("POSTED");
        original.setBatchType("ADJUSTMENT");
        SettlementBatchDO reversal = reversalBatch();
        SettlementResultItemDO originalNet = originalNet(original);
        originalNet.setDirection(originalDirection);
        originalNet.setTargetAmount(new BigDecimal("10.00"));
        MerchantFundAccountDO account = account(original);
        MerchantFundLedgerDO originalLedger = originalLedger(original);
        originalLedger.setBusinessType("RESERVE_SETTLEMENT");
        MerchantReserveActionDO originalAction = new MerchantReserveActionDO();
        originalAction.setId(801L);
        originalAction.setReserveActionNo("RA-ADJUSTMENT-1");
        originalAction.setReserveItemId(31L);
        originalAction.setReserveNo("RS-1");
        originalAction.setCandidateId(101L);
        originalAction.setActionType("ADJUSTMENT");
        originalAction.setDirection(originalDirection);
        originalAction.setCurrency("USD");
        originalAction.setAmount(new BigDecimal("10.00"));
        MerchantReserveItemDO reserveItem = new MerchantReserveItemDO();
        reserveItem.setId(31L);
        reserveItem.setReserveNo("RS-1");
        reserveItem.setCurrency("USD");
        reserveItem.setVersion(2L);

        when(batchMapper.selectByBatchNoForUpdate(original.getSettlementBatchNo())).thenReturn(original);
        when(batchMapper.selectReversalByOriginalForUpdate(original.getSettlementBatchNo())).thenReturn(null);
        when(projectionMapper.selectTasksByBatch(original.getSettlementBatchNo())).thenReturn(List.of());
        when(relationMapper.countProjectableCandidates(original.getSettlementBatchNo())).thenReturn(0);
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
        when(fundMapper.updateAccountBalance(account.getId(), new BigDecimal(expectedBalanceAfter),
                new BigDecimal("100.00"), 3L, now)).thenReturn(1);
        when(reserveMapper.selectActionsByBatchForUpdate(original.getSettlementBatchNo()))
                .thenReturn(List.of(originalAction));
        when(reserveMapper.selectItemByIdForUpdate(31L)).thenReturn(reserveItem);
        when(reserveMapper.insertActionIdempotent(any())).thenReturn(1);
        if ("DEBIT".equals(originalDirection)) {
            when(reserveMapper.reverseDebitAdjustment(31L, new BigDecimal("10.00"), 2L, now))
                    .thenReturn(1);
        } else {
            when(reserveMapper.reverseCreditAdjustment(31L, new BigDecimal("10.00"), 2L, now))
                    .thenReturn(1);
        }
        when(batchMapper.markReversalPosted(reversal.getSettlementBatchNo(), 1L, now)).thenReturn(1);
        when(batchMapper.markReversed(original.getSettlementBatchNo(), 1L, now)).thenReturn(1);

        assertThat(service.reversePostedBatch(
                original.getSettlementBatchNo(), "SRO20260831-00000001", 0L, manualAudit(now), now))
                .isEqualTo(reversal.getSettlementBatchNo());

        verify(projectionMapper, never()).insertTasksIdempotent(anyList());
        if ("DEBIT".equals(originalDirection)) {
            verify(reserveMapper).reverseDebitAdjustment(31L, new BigDecimal("10.00"), 2L, now);
            verify(reserveMapper, never()).reverseCreditAdjustment(any(), any(), anyLong(), any());
        } else {
            verify(reserveMapper).reverseCreditAdjustment(31L, new BigDecimal("10.00"), 2L, now);
            verify(reserveMapper, never()).reverseDebitAdjustment(any(), any(), anyLong(), any());
        }
        verify(reserveMapper).insertActionIdempotent(org.mockito.ArgumentMatchers.argThat(action ->
                "REVERSAL_ADJUSTMENT".equals(action.getActionType())
                        && reversalDirection.equals(action.getDirection())
                        && action.getReversalOfActionId().equals(801L)
                        && "RA-ADJUSTMENT-1".equals(action.getSourceReserveDetailNo())));
        verify(batchMapper).markReversed(original.getSettlementBatchNo(), 1L, now);
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

        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 16, 5);
        assertThat(service.reversePostedBatch(
                original.getSettlementBatchNo(), "SRO20260831-00000001", 0L, manualAudit(now), now))
                .isEqualTo(reversal.getSettlementBatchNo());

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

    private SettlementReversalAudit manualAudit(LocalDateTime now) {
        return new SettlementReversalAudit("SRO20260831-00000001", "ledger mismatch confirmed",
                new SettlementOperatorSnapshot(88L, "Settlement Operator", "SETTLEMENT_MAKER",
                        "127.0.0.1", "JUnit", now),
                "evidence checked",
                new SettlementOperatorSnapshot(99L, "Settlement Checker", "SETTLEMENT_CHECKER",
                        "127.0.0.2", "JUnit", now.plusMinutes(1)));
    }

    private SettlementCommandAudit cancellationAudit(LocalDateTime now) {
        return new SettlementCommandAudit("CANCEL-REQ-1", "cancel before ledger posting",
                new SettlementOperatorSnapshot(88L, "Settlement Operator", "SETTLEMENT_OPERATOR",
                        "10.0.0.8", "JUnit Admin", now));
    }

    private SettlementBatchCancellationAuditDO cancellationAuditRow(String requestKey, int releasedCount) {
        SettlementBatchCancellationAuditDO row = new SettlementBatchCancellationAuditDO();
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setRequestKey(requestKey);
        row.setReleasedCandidateCount(releasedCount);
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
        row.setSettlementDate(LocalDate.of(2026, 8, 25));
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
        row.setAccountStatus("NORMAL");
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
