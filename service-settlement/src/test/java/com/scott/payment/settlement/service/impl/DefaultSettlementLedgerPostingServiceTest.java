package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.entity.MerchantFundAccountDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReserveClearingDetailDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证结算净额、资金流水、保证金和候选状态在同一入账边界内完成。 */
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
        reserve.setReserveCurrency("USD");
        reserve.setReserveCurrencyExponent(2);
        reserve.setRetainedAmount(new BigDecimal("10.00"));
        reserve.setExpectedReserveReleaseDate(LocalDate.of(2027, 2, 25));
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
}
