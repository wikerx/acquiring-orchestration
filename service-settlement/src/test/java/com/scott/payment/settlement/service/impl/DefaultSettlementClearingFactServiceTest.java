package com.scott.payment.settlement.service.impl;

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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementClearingFactServiceTest
 * @date : 2026-08-26 23:55
 * @email : scott_x@163.com
 * @description : 验证结算以三字段 locator 批量加载清分事实、保留支付维度，并为 USD 限额补齐汇率币种。
 * @status : create
 */
class DefaultSettlementClearingFactServiceTest {

    private SettlementClearingFactMapper mapper;
    private DefaultSettlementClearingFactService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SettlementClearingFactMapper.class);
        service = new DefaultSettlementClearingFactService(mapper);
    }

    /** 单个候选只执行两次批量明细查询，USD 上限即使没有固定组件也必须进入矩阵。 */
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

    /** 批次候选数量与关系事实不一致时必须整批阻断。 */
    @Test
    void shouldRejectMissingCandidateRelations() {
        SettlementBatchDO batch = batch(2);
        when(mapper.selectClaimedCandidates(batch.getSettlementBatchNo()))
                .thenReturn(List.of(candidate()));

        assertThatThrownBy(() -> service.load(batch))
                .isInstanceOf(SettlementProcessingException.class)
                .hasMessageContaining("candidate relation count");
    }

    /** 独立保证金释放候选只能读取 RELEASE 明细，不能依赖原交易清分修订。 */
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

    /** 调整来源如果伪装为交易费用事实必须整批拒绝。 */
    @Test
    void shouldRejectTransactionDetailForAdjustmentSource() {
        SettlementBatchDO batch = batch(1);
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
