package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.domain.order.FrozenRateFollowUpAmountCalculator;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 后续资金动作冻结汇率版本追溯测试。
 *
 * <p>后续动作只能复用原交易已经冻结的同一业务汇率版本；数值、来源或生效时间漂移时必须
 * 在渠道外呼前失败关闭。</p>
 */
class FrozenTransactionRateResolverSupportTests {

    private static final LocalDateTime RATE_TIME = LocalDateTime.of(2026, 10, 31, 10, 30);

    /**
     * 查询结果与冻结事实完全一致时应返回原业务汇率版本。
     */
    @Test
    void shouldResolveMatchingFrozenRateVersion() {
        PaymentExchangeRateDTO expected = rate(new BigDecimal("0.70000521"), "PLATFORM", RATE_TIME);
        PaymentExchangeRateService exchangeRateService =
                (baseCurrency, quoteCurrency, atTime) -> Optional.of(expected);

        PaymentExchangeRateDTO actual = FrozenTransactionRateResolverSupport.resolve(
                exchangeRateService, amountPlan());

        assertThat(actual).isSameAs(expected);
    }

    /**
     * 查询到其他历史版本时必须失败关闭，避免汇率快照与实际金额计算不一致。
     */
    @Test
    void shouldFailClosedWhenResolvedRateDoesNotMatchFrozenFact() {
        PaymentExchangeRateDTO differentVersion =
                rate(new BigDecimal("0.70000522"), "PLATFORM", RATE_TIME);
        PaymentExchangeRateService exchangeRateService =
                (baseCurrency, quoteCurrency, atTime) -> Optional.of(differentVersion);

        assertThatThrownBy(() -> FrozenTransactionRateResolverSupport.resolve(
                exchangeRateService, amountPlan()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("can not be traced");
    }

    /**
     * 构造原交易冻结金额和汇率上下文。
     *
     * @return 后续动作金额方案
     */
    private FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan() {
        return new FrozenRateFollowUpAmountCalculator.AmountPlan(
                "AUD",
                new BigDecimal("85.55"),
                "USD",
                new BigDecimal("59.89"),
                new BigDecimal("0.70000521"),
                "PLATFORM",
                RATE_TIME,
                0,
                1);
    }

    /**
     * 构造可追溯业务汇率版本。
     *
     * @param finalRate 最终交易汇率
     * @param sourceCode 汇率来源
     * @param effectiveTime 汇率生效时间
     * @return 业务汇率版本
     */
    private PaymentExchangeRateDTO rate(BigDecimal finalRate,
                                        String sourceCode,
                                        LocalDateTime effectiveTime) {
        PaymentExchangeRateDTO rate = new PaymentExchangeRateDTO();
        rate.setRateId(1001L);
        rate.setRawRateId(901L);
        rate.setRuleId(801L);
        rate.setBaseCurrency("AUD");
        rate.setQuoteCurrency("USD");
        rate.setFinalRate(finalRate);
        rate.setSourceCode(sourceCode);
        rate.setEffectiveTime(effectiveTime);
        return rate;
    }
}
