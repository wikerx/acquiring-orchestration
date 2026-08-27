package com.scott.payment.finance.settlement.core;

import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.AppliedLimit;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.ConvertedFeeComponent;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.FeeGroupCommand;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.FeeGroupResult;
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
 * @classname : SettlementFeeGroupCalculator
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 在目标结算币种统一求值标签币种百分比组件、USD 固定费和 USD 上下限，不修改原清分费用事实。
 * @status : create
 */
public final class SettlementFeeGroupCalculator {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    /**
     * 使用同一批次汇率计算跨币种费用组最终目标币种费用。
     *
     * @param command 清分组件和 USD 上下限事实
     * @param rateMatrix 同批次不可变汇率矩阵
     * @return 未舍入组件、限额命中、调整额和最终费用
     */
    public FeeGroupResult calculate(FeeGroupCommand command, RateMatrix rateMatrix) {
        Objects.requireNonNull(command, "fee group command is required");
        SettlementAmountCalculator.requireTarget(
                command.targetCurrency(), command.targetCurrencyExponent(), rateMatrix);

        List<ConvertedFeeComponent> converted = new ArrayList<>();
        BigDecimal calculated = BigDecimal.ZERO;
        for (var component : command.components()) {
            LockedRate rate = SettlementAmountCalculator.requireRate(
                    component.sourceAmount(), command.targetCurrency(),
                    command.targetCurrencyExponent(), rateMatrix);
            BigDecimal unrounded = component.sourceAmount().amount()
                    .multiply(rate.directRate(), CALCULATION_CONTEXT);
            converted.add(new ConvertedFeeComponent(
                    component.componentNo(), component.componentKind(), component.sourceAmount(),
                    rate.directRate(), unrounded));
            calculated = calculated.add(unrounded, CALCULATION_CONTEXT);
        }

        BigDecimal minimum = convertOptionalUsd(command.minimumFeeUsd(), command, rateMatrix);
        BigDecimal maximum = convertOptionalUsd(command.maximumFeeUsd(), command, rateMatrix);
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("minimum fee must not exceed maximum fee");
        }

        AppliedLimit appliedLimit = AppliedLimit.NONE;
        BigDecimal selected = calculated;
        if (minimum != null && selected.compareTo(minimum) < 0) {
            selected = minimum;
            appliedLimit = AppliedLimit.MINIMUM;
        } else if (maximum != null && selected.compareTo(maximum) > 0) {
            selected = maximum;
            appliedLimit = AppliedLimit.MAXIMUM;
        }

        BigDecimal adjustment = selected.subtract(calculated, CALCULATION_CONTEXT);
        Money finalFee = new Money(selected, command.targetCurrency(), command.targetCurrencyExponent())
                .rounded(command.roundingMode());
        return new FeeGroupResult(command.feeGroupNo(), converted, calculated, minimum, maximum,
                appliedLimit, adjustment, finalFee);
    }

    private BigDecimal convertOptionalUsd(Money usdAmount,
                                          FeeGroupCommand command,
                                          RateMatrix rateMatrix) {
        if (usdAmount == null) {
            return null;
        }
        LockedRate rate = SettlementAmountCalculator.requireRate(
                usdAmount, command.targetCurrency(), command.targetCurrencyExponent(), rateMatrix);
        return usdAmount.amount().multiply(rate.directRate(), CALCULATION_CONTEXT);
    }
}
