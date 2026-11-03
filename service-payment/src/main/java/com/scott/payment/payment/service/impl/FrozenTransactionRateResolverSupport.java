package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.domain.order.FrozenRateFollowUpAmountCalculator;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;

import java.util.Objects;

/**
 * 后续资金动作冻结交易汇率版本解析支持。
 *
 * <p>请款、预授权完成和增量授权必须继续使用首笔交易已经冻结的汇率。解析结果除币对外还要
 * 与原交易保存的汇率值、来源和生效时间完全一致，禁止用查询时点的其他历史版本替代。</p>
 */
final class FrozenTransactionRateResolverSupport {

    private FrozenTransactionRateResolverSupport() {
    }

    /**
     * 读取并校验金额方案对应的原始业务汇率版本。
     *
     * @param exchangeRateService 交易汇率只读服务
     * @param amountPlan 使用首笔冻结汇率形成的后续动作金额方案
     * @return 可追溯且与金额方案完全一致的业务汇率
     */
    static PaymentExchangeRateDTO resolve(PaymentExchangeRateService exchangeRateService,
                                          FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan) {
        if (exchangeRateService == null || amountPlan == null || amountPlan.rateTime() == null) {
            throw unavailable();
        }
        PaymentExchangeRateDTO rate = exchangeRateService.findTransactionRate(
                        amountPlan.labelCurrency(),
                        amountPlan.transactionCurrency(),
                        amountPlan.rateTime())
                .orElseThrow(FrozenTransactionRateResolverSupport::unavailable);
        boolean sameFrozenFact = rate.getRateId() != null
                && rate.getRateId() > 0
                && rate.getFinalRate() != null
                && amountPlan.transactionRate() != null
                && rate.getFinalRate().compareTo(amountPlan.transactionRate()) == 0
                && Objects.equals(rate.getSourceCode(), amountPlan.rateSource())
                && Objects.equals(rate.getEffectiveTime(), amountPlan.rateTime());
        if (!sameFrozenFact) {
            throw unavailable();
        }
        return rate;
    }

    /**
     * 构造统一的失败关闭异常，避免无法追溯的汇率继续形成渠道资金动作。
     */
    private static ServiceException unavailable() {
        return new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE.getCode(),
                "source frozen transaction rate can not be traced to an immutable business rate");
    }
}
