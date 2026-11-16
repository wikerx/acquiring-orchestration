package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.domain.model.SettlementFailureStage;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementClearingLocator;
import com.scott.payment.settlement.dto.SettlementCurrency;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReserveClearingDetailDO;
import com.scott.payment.settlement.entity.SettlementTransactionClearingDetailDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.SettlementClearingFactMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证结算以三字段 locator 批量加载清分事实、保留支付维度，并为 USD 限额补齐汇率币种。
 */
class DefaultSettlementClearingFactServiceTest {

    private SettlementClearingFactMapper mapper;
    private DefaultSettlementClearingFactService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SettlementClearingFactMapper.class);
        service = new DefaultSettlementClearingFactService(mapper);
    }

    /**
     * 单个候选只执行两次批量明细查询，USD 上限即使没有固定组件也必须进入矩阵。
     */
    @Test
    void shouldLoadExactRevisionFactsAndCollectEveryRateCurrency() {
        SettlementBatchDO batch = batch(1);
        SettlementCandidateDO candidate = candidate();
        SettlementTransactionClearingDetailDO fee = transactionDetail();
        SettlementReserveClearingDetailDO reserve = reserveDetail();
        when(mapper.selectClaimedCandidates(batch.getSettlementBatchNo())).thenReturn(List.of(candidate));
        when(mapper.selectTransactionDetails(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(fee));
        when(mapper.selectReserveDetails(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(reserve));

        SettlementBatchFacts result = service.load(batch);

        assertThat(result.currencies()).containsExactlyInAnyOrder(
                new SettlementCurrency("EUR", 2), new SettlementCurrency("USD", 2),
                new SettlementCurrency("GBP", 2));
        assertThat(result.transactionDetails()).singleElement()
                .extracting("paymentType", "paymentMethod")
                .containsExactly("BANK_CARD", "VISA");
        ArgumentCaptor<List<SettlementClearingLocator>> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper).selectTransactionDetails(captor.capture());
        assertThat(captor.getValue()).containsExactly(new SettlementClearingLocator(
                "TX-1", LocalDateTime.of(2026, 8, 26, 9, 0), 2));
    }

    /**
     * 批次候选数量与关系事实不一致时必须整批阻断。
     */
    @Test
    void shouldRejectMissingCandidateRelations() {
        SettlementBatchDO batch = batch(2);
        when(mapper.selectClaimedCandidates(batch.getSettlementBatchNo()))
                .thenReturn(List.of(candidate()));

        assertThatThrownBy(() -> service.load(batch))
                .isInstanceOf(SettlementProcessingException.class)
                .hasMessageContaining("candidate relation count");
    }

    /**
     * 独立保证金释放候选只能读取 RELEASE 明细，不能依赖原交易清分修订。
     */
    @Test
    void shouldLoadReserveReleaseCandidateAsIndependentReserveFact() {
        SettlementBatchDO batch = batch(1);
        batch.setBatchType("RESERVE_RELEASE");
        SettlementCandidateDO candidate = candidate();
        candidate.setSourceType("RESERVE_RELEASE");
        candidate.setSourceBusinessId("RS-1");
        candidate.setSourceTransactionId("RRL-1");
        SettlementReserveClearingDetailDO release = reserveDetail();
        release.setFinanceStateId("RS-1");
        release.setTransactionId("RRL-1");
        release.setReserveActionType("RELEASE");
        release.setRetainedAmount(BigDecimal.ZERO);
        release.setReleasedAmount(new BigDecimal("8.00"));
        when(mapper.selectClaimedCandidates(batch.getSettlementBatchNo())).thenReturn(List.of(candidate));
        when(mapper.selectTransactionDetails(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        when(mapper.selectReserveDetails(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(release));

        SettlementBatchFacts result = service.load(batch);

        assertThat(result.transactionDetails()).isEmpty();
        assertThat(result.reserveDetails()).singleElement()
                .extracting("reserveActionType", "releasedAmount")
                .containsExactly("RELEASE", new BigDecimal("8.00"));
    }

    /**
     * 调整来源如果伪装为交易费用事实必须整批拒绝。
     */
    @Test
    void shouldRejectTransactionDetailForAdjustmentSource() {
        SettlementBatchDO batch = batch(1);
        batch.setBatchType("ADJUSTMENT");
        SettlementCandidateDO candidate = candidate();
        candidate.setSourceType("ADJUSTMENT");
        candidate.setSourceBusinessId("RA-1");
        SettlementTransactionClearingDetailDO invalid = transactionDetail();
        invalid.setFinanceStateId("RA-1");
        when(mapper.selectClaimedCandidates(batch.getSettlementBatchNo())).thenReturn(List.of(candidate));
        when(mapper.selectTransactionDetails(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(invalid));
        when(mapper.selectReserveDetails(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> service.load(batch))
                .isInstanceOf(SettlementProcessingException.class)
                .hasMessageContaining("transaction clearing fact");
    }

    /**
     * 与标签口径一致的本金事实可以进入结算，并继续按标签币种收集结算汇率。
     */
    @Test
    void shouldAcceptPrincipalAlignedWithMerchantLabelFacts() {
        SettlementTransactionClearingDetailDO principal = principalDetail();

        SettlementBatchFacts result = loadTransactionDetail(principal);

        assertThat(result.transactionDetails()).singleElement()
                .extracting("amount", "currency", "currencyExponent")
                .containsExactly(new BigDecimal("100.00"), "EUR", 2);
        assertThat(result.currencies()).containsExactlyInAnyOrder(
                new SettlementCurrency("EUR", 2), new SettlementCurrency("GBP", 2));
    }

    /**
     * 历史本金金额与标签金额不一致时禁止结算侧静默替换。
     */
    @Test
    void shouldRejectPrincipalAmountDifferentFromLabelAmount() {
        SettlementTransactionClearingDetailDO principal = principalDetail();
        principal.setAmount(new BigDecimal("110.00"));

        assertPrincipalLabelMismatch(principal);
    }

    /**
     * 历史本金币种与标签币种不一致时禁止将渠道币种带入商户结算。
     */
    @Test
    void shouldRejectPrincipalCurrencyDifferentFromLabelCurrency() {
        SettlementTransactionClearingDetailDO principal = principalDetail();
        principal.setCurrency("USD");

        assertPrincipalLabelMismatch(principal);
    }

    /**
     * 本金币种精度与标签币种精度不一致时必须阻断，不能依赖后续舍入掩盖事实冲突。
     */
    @Test
    void shouldRejectPrincipalExponentDifferentFromLabelExponent() {
        SettlementTransactionClearingDetailDO principal = principalDetail();
        principal.setLabelCurrencyExponent(0);

        assertPrincipalLabelMismatch(principal);
    }

    /**
     * 加载单个交易清分事实，复用正式批次入口验证本金口径。
     */
    private SettlementBatchFacts loadTransactionDetail(SettlementTransactionClearingDetailDO detail) {
        SettlementBatchDO batch = batch(1);
        when(mapper.selectClaimedCandidates(batch.getSettlementBatchNo()))
                .thenReturn(List.of(candidate()));
        when(mapper.selectTransactionDetails(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(detail));
        when(mapper.selectReserveDetails(org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of());
        return service.load(batch);
    }

    /**
     * 断言本金标签口径冲突使用稳定、不可重试的事实加载失败语义。
     */
    private void assertPrincipalLabelMismatch(SettlementTransactionClearingDetailDO principal) {
        assertThatThrownBy(() -> loadTransactionDetail(principal))
                .isInstanceOfSatisfying(SettlementProcessingException.class, failure -> {
                    assertThat(failure.getStage()).isEqualTo(SettlementFailureStage.FACT_LOADING);
                    assertThat(failure.getFailureCode())
                            .isEqualTo("SETTLEMENT_PRINCIPAL_LABEL_MISMATCH");
                    assertThat(failure.isRetryable()).isFalse();
                });
    }

    private SettlementBatchDO batch(int count) {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setMerchantId("M-1");
        row.setSettlementProfileId(11L);
        row.setTargetCurrency("GBP");
        row.setTargetCurrencyExponent(2);
        row.setBatchType("REGULAR");
        row.setCandidateCount(count);
        return row;
    }

    private SettlementCandidateDO candidate() {
        SettlementCandidateDO row = new SettlementCandidateDO();
        row.setId(101L);
        row.setSourceType("CLEARING_REVISION");
        row.setSourceBusinessId("FS-1");
        row.setSourceRevision(2);
        row.setSourceTransactionId("TX-1");
        row.setSourceTransactionDateTime(LocalDateTime.of(2026, 8, 26, 9, 0));
        row.setMerchantId("M-1");
        row.setSettlementProfileId(11L);
        row.setTargetCurrency("GBP");
        row.setTargetCurrencyExponent(2);
        row.setCandidateStatus("CLAIMED");
        row.setShadowMode(0);
        row.setSettlementBatchNo("SB20260826-00000001");
        return row;
    }

    private SettlementTransactionClearingDetailDO transactionDetail() {
        SettlementTransactionClearingDetailDO row = new SettlementTransactionClearingDetailDO();
        row.setClearingDetailNo("CD-1");
        row.setFinanceStateId("FS-1");
        row.setTransactionId("TX-1");
        row.setMerchantId("M-1");
        row.setPaymentType("BANK_CARD");
        row.setPaymentMethod("VISA");
        row.setTransactionType("PAYMENT");
        row.setClearingRevision(2);
        row.setLineNo(1);
        row.setItemType("PLATFORM_FEE");
        row.setDirection("DEBIT");
        row.setAmount(new BigDecimal("2.00"));
        row.setCurrency("EUR");
        row.setCurrencyExponent(2);
        row.setMaximumAmountUsd(new BigDecimal("10.00"));
        row.setRoundingMode("HALF_UP");
        row.setRecordStatus("ACTIVE");
        row.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 9, 0));
        return row;
    }

    /**
     * 创建标签金额、币种和精度完全一致的本金清分事实。
     */
    private SettlementTransactionClearingDetailDO principalDetail() {
        SettlementTransactionClearingDetailDO row = transactionDetail();
        row.setItemType("PRINCIPAL");
        row.setDirection("CREDIT");
        row.setLabelAmount(new BigDecimal("100.00"));
        row.setLabelCurrency("EUR");
        row.setLabelCurrencyExponent(2);
        row.setAmount(new BigDecimal("100.00"));
        row.setCurrency("EUR");
        row.setCurrencyExponent(2);
        row.setMaximumAmountUsd(null);
        return row;
    }

    private SettlementReserveClearingDetailDO reserveDetail() {
        SettlementReserveClearingDetailDO row = new SettlementReserveClearingDetailDO();
        row.setReserveClearingDetailNo("RD-1");
        row.setFinanceStateId("FS-1");
        row.setTransactionId("TX-1");
        row.setMerchantId("M-1");
        row.setPaymentType("BANK_CARD");
        row.setPaymentMethod("VISA");
        row.setTransactionType("PAYMENT");
        row.setClearingRevision(2);
        row.setLineNo(1);
        row.setReserveActionType("HOLD");
        row.setDirection("DEBIT");
        row.setReserveCurrency("EUR");
        row.setReserveCurrencyExponent(2);
        row.setRetainedAmount(new BigDecimal("10.00"));
        row.setRoundingMode("HALF_UP");
        row.setRecordStatus("ACTIVE");
        row.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 9, 0));
        return row;
    }
}
