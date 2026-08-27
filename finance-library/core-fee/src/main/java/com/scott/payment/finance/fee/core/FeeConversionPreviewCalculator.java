package com.scott.payment.finance.fee.core;

import com.scott.payment.finance.fee.model.FeeCalculationModels.AppliedLimit;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeComponentType;
import com.scott.payment.finance.fee.model.FeeConversionPreviewModels.FeeConversionPreviewCommand;
import com.scott.payment.finance.fee.model.FeeConversionPreviewModels.FeeConversionPreviewResult;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeConversionPreviewCalculator
 * @date : 2026-08-25 19:40
 * @email : scott_x@163.com
 * @description : 使用调用方提供的直接汇率生成 Admin 目标币种费用预览，不修改原清分事实也不承担批次汇率锁定职责。
 * @status : create
 */
public class FeeConversionPreviewCalculator {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    /**
     * 将标签币种百分比组件和 USD 固定费转换到预览目标币种，并统一应用 USD 最低和最高费用。
     *
     * @param command 原费用结果、目标币种和直接汇率
     * @return 仅用于 Admin 展示的目标币种费用结果
     */
    public FeeConversionPreviewResult calculate(FeeConversionPreviewCommand command) {
        BigDecimal rawValue = command.feeResult().components().stream()
                .filter(component -> component.componentType() != FeeComponentType.LIMIT_ADJUSTMENT)
                .map(component -> {
                    BigDecimal converted = convert(component.amount(), command);
                    return component.direction() == EntryDirection.DEBIT ? converted : converted.negate();
                })
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, CALCULATION_CONTEXT));
        Money rawFee = targetAmount(rawValue, command);

        BigDecimal finalValue = rawFee.amount();
        AppliedLimit appliedLimit = AppliedLimit.NONE;
        BigDecimal minimum = convertedLimit(command.feeResult().minimumFeeUsd(), command);
        BigDecimal maximum = convertedLimit(command.feeResult().maximumFeeUsd(), command);
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("minimum preview fee must not exceed maximum preview fee");
        }
        if (minimum != null) {
            if (finalValue.compareTo(minimum) < 0) {
                finalValue = minimum;
                appliedLimit = AppliedLimit.MINIMUM;
            }
        }
        if (maximum != null) {
            if (finalValue.compareTo(maximum) > 0) {
                finalValue = maximum;
                appliedLimit = AppliedLimit.MAXIMUM;
            }
        }
        return new FeeConversionPreviewResult(rawFee, targetAmount(finalValue, command), appliedLimit);
    }

    private BigDecimal convert(Money source, FeeConversionPreviewCommand command) {
        BigDecimal rate;
        if (source.currency().equals(command.targetCurrency())) {
            rate = BigDecimal.ONE;
        } else {
            rate = command.directRates().get(source.currency());
            if (rate == null) {
                throw new IllegalArgumentException("direct preview rate is missing for " + source.currency());
            }
        }
        return source.amount().multiply(rate, CALCULATION_CONTEXT);
    }

    private BigDecimal convertedLimit(Money limit, FeeConversionPreviewCommand command) {
        return limit == null ? null : convert(limit, command);
    }

    private Money targetAmount(BigDecimal amount, FeeConversionPreviewCommand command) {
        return new Money(amount, command.targetCurrency(), command.targetExponent());
    }
}
