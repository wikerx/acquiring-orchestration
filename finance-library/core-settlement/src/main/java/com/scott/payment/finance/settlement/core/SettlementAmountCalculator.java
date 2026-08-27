package com.scott.payment.finance.settlement.core;

import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.AmountDirection;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.ConversionCommand;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.ConversionResult;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.ConvertedAmountLine;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementAmountCalculator
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 使用同一批次不可变汇率矩阵折算原币种明细，保留行级未舍入值并只舍入最终目标净额一次。
 * @status : create
 */
public final class SettlementAmountCalculator {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    /**
     * 计算一个批次范围内的目标币种有符号净额。
     *
     * @param command 原币种明细和目标币种规则
     * @param rateMatrix 同批次锁定汇率矩阵
     * @return 行级审计值、未舍入净额和最终目标币种金额
     */
    public ConversionResult calculate(ConversionCommand command, RateMatrix rateMatrix) {
        Objects.requireNonNull(command, "conversion command is required");
        requireTarget(command.targetCurrency(), command.targetCurrencyExponent(), rateMatrix);
        List<ConvertedAmountLine> converted = new ArrayList<>();
        BigDecimal netAmount = BigDecimal.ZERO;
        for (var line : command.lines()) {
            LockedRate rate = requireRate(line.sourceAmount(), command.targetCurrency(),
                    command.targetCurrencyExponent(), rateMatrix);
            BigDecimal unrounded = line.sourceAmount().amount()
                    .multiply(rate.directRate(), CALCULATION_CONTEXT);
            converted.add(new ConvertedAmountLine(line.lineNo(), line.sourceAmount(), line.direction(),
                    rate.directRate(), unrounded));
            netAmount = line.direction() == AmountDirection.CREDIT
                    ? netAmount.add(unrounded, CALCULATION_CONTEXT)
                    : netAmount.subtract(unrounded, CALCULATION_CONTEXT);
        }
        Money targetNet = new Money(netAmount, command.targetCurrency(), command.targetCurrencyExponent())
                .rounded(command.roundingMode());
        return new ConversionResult(converted, netAmount, targetNet);
    }

    static LockedRate requireRate(Money sourceAmount,
                                  String targetCurrency,
                                  int targetCurrencyExponent,
                                  RateMatrix rateMatrix) {
        Objects.requireNonNull(rateMatrix, "rate matrix is required");
        LockedRate rate = rateMatrix.require(sourceAmount.currency(), targetCurrency);
        if (rate.sourceCurrencyExponent() != sourceAmount.exponent()) {
            throw new IllegalArgumentException("source currency exponent differs from locked batch rate");
        }
        if (rate.targetCurrencyExponent() != targetCurrencyExponent) {
            throw new IllegalArgumentException("target currency exponent differs from locked batch rate");
        }
        return rate;
    }

    static void requireTarget(String targetCurrency, int targetCurrencyExponent, RateMatrix rateMatrix) {
        Objects.requireNonNull(rateMatrix, "rate matrix is required");
        if (!targetCurrency.equals(rateMatrix.targetCurrency())) {
            throw new IllegalArgumentException("calculation target currency differs from batch rate matrix");
        }
        rateMatrix.rates().forEach(rate -> {
            if (rate.targetCurrencyExponent() != targetCurrencyExponent) {
                throw new IllegalArgumentException("target currency exponent differs inside batch rate matrix");
            }
        });
    }
}
