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

    /**
     * 财务计算统一 MathContext，约束中间计算精度并避免过早舍入。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
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

    /**
     * 使用批次锁定的 USD 直接汇率换算最低或最高费用，保留未舍入值供统一限额比较。
     *
     * @param usdAmount 可空的 USD 费用限额
     * @param command 当前费用组目标币种口径
     * @param rateMatrix 同批次不可变汇率矩阵
     * @return DECIMAL128 未舍入目标币种限额，未配置时返回 null
     */
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
