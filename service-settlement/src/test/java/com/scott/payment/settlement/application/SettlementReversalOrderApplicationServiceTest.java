package com.scott.payment.settlement.application;

import com.scott.payment.settlement.domain.model.SettlementReversalStatus;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementReversalCommandResult;
import com.scott.payment.settlement.dto.SettlementReversalCreateCommand;
import com.scott.payment.settlement.dto.SettlementReversalDecisionCommand;
import com.scott.payment.settlement.entity.MerchantFundLedgerDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementFundMapper;
import com.scott.payment.settlement.mapper.SettlementProjectionMapper;
import com.scott.payment.settlement.mapper.SettlementReserveMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import com.scott.payment.settlement.mapper.SettlementReversalDailySequenceMapper;
import com.scott.payment.settlement.mapper.SettlementReversalOrderMapper;
import com.scott.payment.settlement.support.SettlementReversalNumberFormatter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalOrderApplicationServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 独立冲正单必须先申请，再由不同账号复核后才能调用资金冲正。
 * @status : create
 */
class SettlementReversalOrderApplicationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 31, 10, 0);

    @Test
    void shouldSubmitAndApproveWithDifferentChecker() {
        Fixture fixture = new Fixture();
        fixture.stubOriginalBatch();
        when(fixture.orderMapper.approve(any(), any(Long.class))).thenReturn(1);
        when(fixture.commandService.reversePostedBatch(any(), any(), any(Long.class), any(), any()))
                .thenReturn("SB20260831-00000002");

        SettlementReversalCommandResult submitted = fixture.service.submit(new SettlementReversalCreateCommand(
                "REV-CREATE-1", "SB20260830-00000001", 7L,
                "confirmed duplicate posting", operator(88L, NOW)));

        assertThat(submitted.reversalStatus()).isEqualTo(SettlementReversalStatus.PENDING_APPROVAL.name());
        fixture.capturePendingOrderForDecision();

        SettlementReversalCommandResult approved = fixture.service.decide(submitted.reversalOrderNo(),
                new SettlementReversalDecisionCommand("REV-APPROVE-1", 0L, "APPROVE",
                        "evidence checked", operator(99L, NOW.plusMinutes(5))));

        assertThat(approved.reversalStatus()).isEqualTo(SettlementReversalStatus.APPROVED.name());
        assertThat(approved.reversalBatchNo()).isEqualTo("SB20260831-00000002");
        verify(fixture.commandService).reversePostedBatch(any(), any(), any(Long.class), any(), any());
    }

    @Test
    void shouldRejectSameAccountCheckerBeforeReversal() {
        Fixture fixture = new Fixture();
        fixture.stubOriginalBatch();
        SettlementReversalCommandResult submitted = fixture.service.submit(new SettlementReversalCreateCommand(
                "REV-CREATE-2", "SB20260830-00000001", 7L,
                "confirmed duplicate posting", operator(88L, NOW)));
        fixture.capturePendingOrderForDecision();

        assertThatThrownBy(() -> fixture.service.decide(submitted.reversalOrderNo(),
                new SettlementReversalDecisionCommand("REV-APPROVE-2", 0L, "APPROVE",
                        "self approval", operator(88L, NOW.plusMinutes(5)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maker and Checker");
    }

    @Test
    void shouldRejectProjectionTaskCountDifferentFromProjectableCandidates() {
        Fixture fixture = new Fixture();
        fixture.stubOriginalBatch();
        when(fixture.batchCandidateMapper.countProjectableCandidates("SB20260830-00000001"))
                .thenReturn(1);

        assertThatThrownBy(() -> fixture.service.submit(new SettlementReversalCreateCommand(
                "REV-CREATE-3", "SB20260830-00000001", 7L,
                "confirmed duplicate posting", operator(88L, NOW))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("projectable candidate identity");
    }

    private static SettlementOperatorSnapshot operator(long accountId, LocalDateTime time) {
        return new SettlementOperatorSnapshot(accountId, "Admin " + accountId,
                "SUPER_ADMIN", "127.0.0.1", "JUnit", time);
    }

    private static final class Fixture {
        private final SettlementReversalDailySequenceMapper sequenceMapper =
                mock(SettlementReversalDailySequenceMapper.class);
        private final SettlementReversalOrderMapper orderMapper = mock(SettlementReversalOrderMapper.class);
        private final SettlementBatchMapper batchMapper = mock(SettlementBatchMapper.class);
        private final SettlementBatchCandidateMapper batchCandidateMapper =
                mock(SettlementBatchCandidateMapper.class);
        private final SettlementResultMapper resultMapper = mock(SettlementResultMapper.class);
        private final SettlementFundMapper fundMapper = mock(SettlementFundMapper.class);
        private final SettlementProjectionMapper projectionMapper = mock(SettlementProjectionMapper.class);
        private final SettlementReserveMapper reserveMapper = mock(SettlementReserveMapper.class);
        private final SettlementBatchCommandApplicationService commandService =
                mock(SettlementBatchCommandApplicationService.class);
        private final SettlementReversalOrderApplicationService service =
                new SettlementReversalOrderApplicationService(sequenceMapper, orderMapper, batchMapper,
                        batchCandidateMapper, resultMapper, fundMapper, projectionMapper, reserveMapper, commandService,
                        new SettlementReversalNumberFormatter(),
                        Clock.fixed(Instant.parse("2026-08-31T02:00:00Z"), ZoneOffset.UTC));
        private com.scott.payment.settlement.entity.SettlementReversalOrderDO pending;

        void stubOriginalBatch() {
            var sequence = new com.scott.payment.settlement.entity.SettlementReversalDailySequenceDO();
            sequence.setBusinessDate(LocalDate.of(2026, 8, 31));
            sequence.setCurrentSequence(0);
            sequence.setVersion(0L);
            when(sequenceMapper.selectForUpdate(LocalDate.of(2026, 8, 31))).thenReturn(sequence);
            when(sequenceMapper.increment(LocalDate.of(2026, 8, 31), 0, 0L)).thenReturn(1);

            SettlementBatchDO batch = new SettlementBatchDO();
            batch.setSettlementBatchNo("SB20260830-00000001");
            batch.setMerchantId("M1001");
            batch.setSettlementAccountId(21L);
            batch.setTargetCurrency("USD");
            batch.setTargetCurrencyExponent(2);
            batch.setBatchStatus("POSTED");
            batch.setCandidateCount(0);
            batch.setProjectableCandidateCount(0);
            batch.setVersion(7L);
            when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);

            SettlementResultItemDO net = new SettlementResultItemDO();
            net.setId(501L);
            net.setDirection("CREDIT");
            net.setTargetCurrency("USD");
            net.setTargetAmount(new BigDecimal("80.00"));
            net.setLedgerIdempotencyKey("SETTLEMENT:" + batch.getSettlementBatchNo());
            when(resultMapper.selectNetPostingForUpdate(batch.getSettlementBatchNo())).thenReturn(net);
            MerchantFundLedgerDO ledger = new MerchantFundLedgerDO();
            ledger.setId(601L);
            ledger.setIdempotencyKey(net.getLedgerIdempotencyKey());
            when(fundMapper.selectLedgerByIdempotencyForUpdate(net.getLedgerIdempotencyKey()))
                    .thenReturn(ledger);
            when(projectionMapper.selectTasksByBatch(batch.getSettlementBatchNo())).thenReturn(List.of());
            when(batchCandidateMapper.countProjectableCandidates(batch.getSettlementBatchNo())).thenReturn(0);
            when(reserveMapper.selectActionsByBatchForUpdate(batch.getSettlementBatchNo())).thenReturn(List.of());
            when(orderMapper.selectByCreateRequestKeyForUpdate(any())).thenReturn(null);
            when(orderMapper.selectActiveByOriginalBatchForUpdate(batch.getSettlementBatchNo())).thenReturn(null);
            when(orderMapper.insertIdempotent(any())).thenAnswer(invocation -> {
                pending = invocation.getArgument(0);
                return 1;
            });
        }

        void capturePendingOrderForDecision() {
            when(orderMapper.selectByDecisionRequestKeyForUpdate(any())).thenReturn(null);
            when(orderMapper.selectByReversalOrderNoForUpdate(pending.getReversalOrderNo())).thenReturn(pending);
        }
    }
}
