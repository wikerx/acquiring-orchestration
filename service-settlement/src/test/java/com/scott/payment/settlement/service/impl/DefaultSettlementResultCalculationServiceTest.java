package com.scott.payment.settlement.service.impl;

import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementCurrency;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReserveClearingDetailDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.entity.SettlementResultSummaryDO;
import com.scott.payment.settlement.entity.SettlementTransactionClearingDetailDO;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementResultCalculationServiceTest
 * @date : 2026-08-26 23:55
 * @email : scott_x@163.com
 * @description : 验证统一批次汇率下本金、跨币种费用限额和保证金结果分离，最终仅形成 CALCULATED。
 * @status : create
 */
class DefaultSettlementResultCalculationServiceTest {

    private SettlementBatchMapper batchMapper;
    private SettlementResultMapper resultMapper;
    private DefaultSettlementResultCalculationService service;

    @BeforeEach
    void setUp() {
        batchMapper = mock(SettlementBatchMapper.class);
        resultMapper = mock(SettlementResultMapper.class);
        service = new DefaultSettlementResultCalculationService(batchMapper, resultMapper);
    }

    /** EUR 百分比加 USD 固定费命中 USD 最低费，保证金独立换算且 TRACE 不进入汇总。 */
    @Test
    void shouldCalculateFeeGroupReserveAndStopAtCalculated() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 10, 0);
        SettlementBatchDO batch = batch(now);
        SettlementBatchFacts facts = facts();
        SettlementLockedRateMatrix rates = rates(now);
        AtomicReference<List<SettlementResultItemDO>> items = new AtomicReference<>();
        AtomicReference<List<SettlementResultSummaryDO>> summaries = new AtomicReference<>();
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(batchMapper.beginCalculating(batch.getSettlementBatchNo(), "worker-1", 5L, now))
                .thenReturn(1);
        when(resultMapper.insertItemsIdempotent(anyList())).thenAnswer(invocation -> {
            List<SettlementResultItemDO> current = invocation.getArgument(0);
            items.set(List.copyOf(current));
            return current.size();
        });
        when(resultMapper.selectItemsByBatch(batch.getSettlementBatchNo()))
                .thenAnswer(invocation -> items.get());
        when(resultMapper.insertSummariesIdempotent(anyList())).thenAnswer(invocation -> {
            List<SettlementResultSummaryDO> current = invocation.getArgument(0);
            summaries.set(List.copyOf(current));
            return current.size();
        });
        when(resultMapper.selectSummariesByBatch(batch.getSettlementBatchNo()))
                .thenAnswer(invocation -> summaries.get());
        when(batchMapper.markCalculated(batch.getSettlementBatchNo(), "worker-1", 6L, now))
                .thenReturn(1);

        int count = service.calculateAndPersist(batch, facts, rates, "worker-1", now);

        assertThat(count).isEqualTo(5);
        assertThat(items.get()).extracting(SettlementResultItemDO::getResultRole)
                .containsExactly("FINANCIAL_COMPONENT", "TRACE", "TRACE",
                        "FINANCIAL_COMPONENT", "FINANCIAL_COMPONENT");
        SettlementResultItemDO feeFinal = items.get().stream()
                .filter(row -> "FEE_GROUP_FINAL".equals(row.getResultItemType())).findFirst().orElseThrow();
        assertThat(feeFinal.getAppliedLimit()).isEqualTo("MINIMUM");
        assertThat(feeFinal.getUnroundedTargetAmount()).isEqualByComparingTo("3.00000000000000");
        assertThat(feeFinal.getTargetAmount()).isEqualByComparingTo("3.00");
        assertThat(feeFinal.getSourceCurrency()).isEqualTo("GBP");
        assertThat(items.get()).noneMatch(row -> "LEDGER_POSTING".equals(row.getResultRole()));
        assertThat(summaries.get()).hasSize(3)
                .allMatch(row -> row.getTransactionCount() == 1L);
        assertThat(batch.getBatchStatus()).isEqualTo("CALCULATED");
        assertThat(batch.getProcessingOwner()).isNull();
        verify(batchMapper).markCalculated(batch.getSettlementBatchNo(), "worker-1", 6L, now);
    }

    private SettlementBatchDO batch(LocalDateTime now) {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setMerchantId("M-1");
        row.setSettlementAccountId(21L);
        row.setTargetCurrency("GBP");
        row.setTargetCurrencyExponent(2);
        row.setBatchStatus("RATE_LOCKED");
        row.setProcessingOwner("worker-1");
        row.setProcessingDeadline(now.plusMinutes(5));
        row.setVersion(5L);
        return row;
    }

    private SettlementBatchFacts facts() {
        SettlementCandidateDO candidate = new SettlementCandidateDO();
        candidate.setId(101L);
        candidate.setSourceRevision(1);
        candidate.setSourceTransactionId("TX-1");
        candidate.setSourceTransactionDateTime(LocalDateTime.of(2026, 8, 26, 9, 0));
        List<SettlementTransactionClearingDetailDO> transactionDetails = List.of(
                principal(), fee("CD-FEE-P", 2, "PERCENTAGE", "2.00", "EUR", 2),
                fee("CD-FEE-F", 3, "FIXED", "1.00", "USD", 2));
        return new SettlementBatchFacts(List.of(candidate), transactionDetails, List.of(reserve()), Set.of(
                new SettlementCurrency("EUR", 2), new SettlementCurrency("USD", 2),
                new SettlementCurrency("GBP", 2)));
    }

    private SettlementTransactionClearingDetailDO principal() {
        SettlementTransactionClearingDetailDO row = baseTransaction("CD-PRINCIPAL", 1);
        row.setItemType("PRINCIPAL");
        row.setDirection("CREDIT");
        row.setComponentType("PRINCIPAL");
        row.setAmount(new BigDecimal("100.00"));
        row.setCurrency("EUR");
        row.setCurrencyExponent(2);
        row.setAppliedLimit("NONE");
        row.setLimitEvaluationStatus("NOT_REQUIRED");
        return row;
    }

    private SettlementTransactionClearingDetailDO fee(String detailNo,
                                                       int lineNo,
                                                       String componentType,
                                                       String amount,
                                                       String currency,
                                                       int exponent) {
        SettlementTransactionClearingDetailDO row = baseTransaction(detailNo, lineNo);
        row.setItemType("PLATFORM_FEE");
        row.setFeeCategory("TRANSACTION_FEE");
        row.setDirection("DEBIT");
        row.setFeeGroupNo("FG-1");
        row.setComponentType(componentType);
        row.setAmount(new BigDecimal(amount));
        row.setCurrency(currency);
        row.setCurrencyExponent(exponent);
        row.setMinimumAmountUsd(new BigDecimal("4.00"));
        row.setMaximumAmountUsd(new BigDecimal("10.00"));
        row.setAppliedLimit("NONE");
        row.setLimitEvaluationStatus("PENDING_SETTLEMENT_RATE");
        return row;
    }

    private SettlementTransactionClearingDetailDO baseTransaction(String detailNo, int lineNo) {
        SettlementTransactionClearingDetailDO row = new SettlementTransactionClearingDetailDO();
        row.setId((long) lineNo);
        row.setClearingDetailNo(detailNo);
        row.setTransactionId("TX-1");
        row.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 9, 0));
        row.setClearingRevision(1);
        row.setLineNo(lineNo);
        row.setPaymentType("BANK_CARD");
        row.setPaymentMethod("VISA");
        row.setTransactionType("PAYMENT");
        row.setRoundingMode("HALF_UP");
        return row;
    }

    private SettlementReserveClearingDetailDO reserve() {
        SettlementReserveClearingDetailDO row = new SettlementReserveClearingDetailDO();
        row.setId(1L);
        row.setReserveClearingDetailNo("RD-HOLD");
        row.setTransactionId("TX-1");
        row.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 9, 0));
        row.setClearingRevision(1);
        row.setLineNo(1);
        row.setPaymentType("BANK_CARD");
        row.setPaymentMethod("VISA");
        row.setTransactionType("PAYMENT");
        row.setReserveActionType("HOLD");
        row.setDirection("DEBIT");
        row.setReserveCurrency("EUR");
        row.setReserveCurrencyExponent(2);
        row.setRetainedAmount(new BigDecimal("10.00"));
        row.setRoundingMode("HALF_UP");
        return row;
    }

    private SettlementLockedRateMatrix rates(LocalDateTime now) {
        RateMatrix matrix = RateMatrix.of(List.of(
                rate("EUR", "0.800000000000", "ECB", "1", now),
                rate("USD", "0.750000000000", "ECB", "2", now),
                rate("GBP", "1.000000000000", "SYSTEM_IDENTITY", null, now)));
        return new SettlementLockedRateMatrix(matrix, Map.of("EUR", 11L, "USD", 12L, "GBP", 13L));
    }

    private LockedRate rate(String source,
                            String value,
                            String provider,
                            String quoteId,
                            LocalDateTime now) {
        return new LockedRate(new CurrencyPair(source, "GBP"), new BigDecimal(value), 2, 2,
                provider, quoteId, QuoteDirection.DIRECT, now.minusMinutes(1));
    }
}
