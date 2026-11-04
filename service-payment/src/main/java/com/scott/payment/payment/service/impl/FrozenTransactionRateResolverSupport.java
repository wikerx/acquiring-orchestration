package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.domain.order.FrozenRateFollowUpAmountCalculator;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import org.springframework.util.StringUtils;

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
        return validate(rate, amountPlan);
    }

    /**
     * 在原订单短事务建立前追溯首次交易冻结的业务汇率版本。
     *
     * <p>后续动作金额仍在主单锁内按最新累计事实计算；这里仅提前读取不可变的汇率版本，避免持锁期间
     * 访问汇率表。同币种或未启用 DCC/EDC 时不产生汇率使用事件，因此返回 {@code null}。</p>
     *
     * @param exchangeRateService 交易汇率只读服务
     * @param sourceOrder 原交易主单快照
     * @return 已校验的冻结汇率；不涉及换汇时返回 {@code null}
     */
    static PaymentExchangeRateDTO resolveForSourceOrder(PaymentExchangeRateService exchangeRateService,
                                                        TransactionOrderDO sourceOrder) {
        if (sourceOrder == null) {
            throw unavailable();
        }
        String transactionCurrency = normalizeCurrency(sourceOrder.getTransactionCurrency());
        String labelCurrency = StringUtils.hasText(sourceOrder.getLabelCurrency())
                ? normalizeCurrency(sourceOrder.getLabelCurrency())
                : transactionCurrency;
        boolean conversionEnabled = Integer.valueOf(1).equals(sourceOrder.getEdcEnabled())
                || Integer.valueOf(1).equals(sourceOrder.getDccEnabled());
        if (!conversionEnabled || Objects.equals(labelCurrency, transactionCurrency)) {
            return null;
        }
        if (exchangeRateService == null || sourceOrder.getRateTime() == null) {
            throw unavailable();
        }
        PaymentExchangeRateDTO rate = exchangeRateService.findTransactionRate(
                        labelCurrency, transactionCurrency, sourceOrder.getRateTime())
                .orElseThrow(FrozenTransactionRateResolverSupport::unavailable);
        return validate(rate,
                sourceOrder.getTransactionRate(),
                sourceOrder.getRateSource(),
                sourceOrder.getRateTime());
    }

    /**
     * 事务外规划冻结汇率读取，并把异常延迟到原有汇率使用事件位置处理。
     *
     * <p>这样既能缩短主单锁时间，也不会让汇率配置异常抢在非法状态或金额越界前返回，保持既有
     * 错误优先级。</p>
     *
     * @param exchangeRateService 交易汇率只读服务
     * @param sourceOrder 原交易主单快照
     * @return 冻结汇率规划结果
     */
    static Resolution planForSourceOrder(PaymentExchangeRateService exchangeRateService,
                                         TransactionOrderDO sourceOrder) {
        try {
            return Resolution.resolved(resolveForSourceOrder(exchangeRateService, sourceOrder));
        } catch (RuntimeException exception) {
            return Resolution.failed(exception);
        }
    }

    /**
     * 确认事务外读取的汇率仍与锁内金额方案引用同一不可变版本。
     *
     * @param rate 事务外读取的业务汇率
     * @param amountPlan 锁内基于权威累计事实形成的金额方案
     * @return 原样返回已校验汇率
     */
    static PaymentExchangeRateDTO validate(PaymentExchangeRateDTO rate,
                                           FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan) {
        if (amountPlan == null) {
            throw unavailable();
        }
        return validate(rate,
                amountPlan.transactionRate(),
                amountPlan.rateSource(),
                amountPlan.rateTime());
    }

    /**
     * 在原有汇率使用事件位置消费事务外规划结果。
     */
    static PaymentExchangeRateDTO validate(Resolution resolution,
                                           FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan) {
        if (resolution == null) {
            throw unavailable();
        }
        return validate(resolution.requireRate(), amountPlan);
    }

    /**
     * 校验读取结果与原交易冻结的币对、数值、来源和生效时间完全一致。
     */
    private static PaymentExchangeRateDTO validate(PaymentExchangeRateDTO rate,
                                                   java.math.BigDecimal transactionRate,
                                                   String rateSource,
                                                   java.time.LocalDateTime rateTime) {
        boolean sameFrozenFact = rate != null
                && rate.getRateId() != null
                && rate.getRateId() > 0
                && rate.getFinalRate() != null
                && transactionRate != null
                && rate.getFinalRate().compareTo(transactionRate) == 0
                && Objects.equals(rate.getSourceCode(), rateSource)
                && Objects.equals(rate.getEffectiveTime(), rateTime);
        if (!sameFrozenFact) {
            throw unavailable();
        }
        return rate;
    }

    /**
     * 规范化币种以消除持久化大小写差异。
     */
    private static String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * 冻结汇率的事务外规划结果。
     *
     * @param rate 已追溯的业务汇率；不涉及换汇时为空
     * @param failure 事务外查询异常；由锁内原有汇率使用位置重新抛出
     */
    record Resolution(PaymentExchangeRateDTO rate, RuntimeException failure) {

        /**
         * 构造成功规划结果。
         */
        private static Resolution resolved(PaymentExchangeRateDTO rate) {
            return new Resolution(rate, null);
        }

        /**
         * 构造延迟失败规划结果。
         */
        private static Resolution failed(RuntimeException failure) {
            return new Resolution(null, failure);
        }

        /**
         * 返回已规划汇率，或按原有业务顺序重新抛出查询异常。
         */
        private PaymentExchangeRateDTO requireRate() {
            if (failure != null) {
                throw failure;
            }
            return rate;
        }
    }

    /**
     * 构造统一的失败关闭异常，避免无法追溯的汇率继续形成渠道资金动作。
     */
    private static ServiceException unavailable() {
        return new ServiceException(ApiResultEnum.PAYMENT_CORE_CONFIG_UNAVAILABLE.getCode(),
                "source frozen transaction rate can not be traced to an immutable business rate");
    }
}
