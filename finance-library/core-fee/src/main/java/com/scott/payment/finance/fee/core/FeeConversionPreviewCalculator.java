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

    /**
     * 财务计算统一 MathContext，约束中间计算精度并避免过早舍入。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
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

    /**
     * 使用调用方提供的一单位源币种对应目标币种的直接汇率执行 Admin 预览换算。
     *
     * @param source 清分保存的原币种费用组件
     * @param command 目标币种、exponent 和直接汇率集合
     * @return 采用 DECIMAL128 计算且尚未按目标币种舍入的预览金额
     */
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

    /**
     * 将可空的 USD 最低或最高费用换算为未舍入目标币种预览值。
     *
     * @param limit USD 限额，未配置时为空
     * @param command Admin 预览命令
     * @return 未舍入目标币种限额，未配置时返回 null
     */
    private BigDecimal convertedLimit(Money limit, FeeConversionPreviewCommand command) {
        return limit == null ? null : convert(limit, command);
    }

    /**
     * 将未舍入预览值封装为目标币种金额；Money 不隐式舍入，调用方仍可审计完整计算精度。
     *
     * @param amount 未舍入目标币种金额
     * @param command 目标币种和 exponent
     * @return 保留原计算精度的目标币种预览金额
     */
    private Money targetAmount(BigDecimal amount, FeeConversionPreviewCommand command) {
        return new Money(amount, command.targetCurrency(), command.targetExponent());
    }
}
