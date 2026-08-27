package com.scott.payment.settlement.service.impl;

import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.settlement.dto.SettlementCurrency;
import com.scott.payment.settlement.entity.SettlementRateQuoteDO;
import com.scott.payment.settlement.mapper.SettlementRateQuoteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementRateResolutionServiceTest
 * @date : 2026-08-26 22:40
 * @email : scott_x@163.com
 * @description : 验证批次汇率批量解析支持恒等、直接和反向报价，并拒绝为缺失跨币种汇率默认补 1。
 * @status : create
 */
class DefaultSettlementRateResolutionServiceTest {

    private SettlementRateQuoteMapper quoteMapper;
    private DefaultSettlementRateResolutionService service;

    @BeforeEach
    void setUp() {
        quoteMapper = mock(SettlementRateQuoteMapper.class);
        service = new DefaultSettlementRateResolutionService(quoteMapper);
    }

    /** 来源优先级相同时应优先直接报价，并同时生成目标币种恒等行。 */
    @Test
    void shouldPreferDirectQuoteWhenSourceRankingIsEquivalent() {
        LocalDateTime valuationTime = LocalDateTime.of(2026, 8, 26, 1, 0);
        SettlementRateQuoteDO direct = quote(11L, "BOC", "EUR", "USD", "1.200000000000");
        SettlementRateQuoteDO inverse = quote(12L, "BOC", "USD", "EUR", "0.800000000000");
        when(quoteMapper.selectEffectiveQuotes(anyList(), eq(valuationTime)))
                .thenReturn(List.of(inverse, direct));

        RateMatrix matrix = service.resolve(
                Set.of(new SettlementCurrency("EUR", 2), new SettlementCurrency("USD", 2)),
                "USD", 2, valuationTime);

        assertThat(matrix.require("EUR", "USD").directRate())
                .isEqualByComparingTo("1.200000000000");
        assertThat(matrix.require("EUR", "USD").sourceQuoteDirection())
                .isEqualTo(QuoteDirection.DIRECT);
        assertThat(matrix.require("USD", "USD").rateSource()).isEqualTo("SYSTEM_IDENTITY");
    }

    /** 缺少跨币种报价时必须中止，不能静默使用恒等汇率。 */
    @Test
    void shouldRejectMissingCrossCurrencyRate() {
        LocalDateTime valuationTime = LocalDateTime.of(2026, 8, 26, 1, 0);
        when(quoteMapper.selectEffectiveQuotes(anyList(), eq(valuationTime))).thenReturn(List.of());

        assertThatThrownBy(() -> service.resolve(
                Set.of(new SettlementCurrency("EUR", 2)), "USD", 2, valuationTime))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EUR/USD");
    }

    private SettlementRateQuoteDO quote(Long id,
                                        String sourceCode,
                                        String baseCurrency,
                                        String quoteCurrency,
                                        String rate) {
        SettlementRateQuoteDO row = new SettlementRateQuoteDO();
        row.setId(id);
        row.setSourceCode(sourceCode);
        row.setBaseCurrency(baseCurrency);
        row.setQuoteCurrency(quoteCurrency);
        row.setFinalRate(new BigDecimal(rate));
        row.setEffectiveTime(LocalDateTime.of(2026, 8, 26, 0, 0));
        row.setDefaultSource(1);
        row.setSourcePriority(10);
        return row;
    }
}
