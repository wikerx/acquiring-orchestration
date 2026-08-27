package com.scott.payment.finance.settlement.model;

import com.scott.payment.finance.money.model.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCalculationModels
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 定义批次原币种金额换汇和跨币种费用组限额求值的不可变输入输出，不重新计算清分费用组件。
 * @status : create
 */
public final class SettlementCalculationModels {

    private SettlementCalculationModels() {
    }

    /** 商户视角的目标净额方向。 */
    public enum AmountDirection {
        CREDIT,
        DEBIT
    }

    /** 清分费用原子组件类型；限额调整仅由结算结果生成，不作为输入。 */
    public enum FeeComponentKind {
        PERCENTAGE,
        FIXED
    }

    /** 结算统一求值后命中的费用边界。 */
    public enum AppliedLimit {
        NONE,
        MINIMUM,
        MAXIMUM
    }

    /**
     * 单条待结算原币种金额。
     *
     * @param lineNo 稳定来源明细号
     * @param sourceAmount 非负原币种金额
     * @param direction 商户视角方向
     */
    public record AmountLine(String lineNo, Money sourceAmount, AmountDirection direction) {

        public AmountLine {
            lineNo = requireText(lineNo, "amount line number");
            requireNonNegative(sourceAmount, "source amount");
            Objects.requireNonNull(direction, "amount direction is required");
        }
    }

    /**
     * 批次目标净额计算命令。
     *
     * @param lines 原币种金额明细
     * @param targetCurrency 商户目标结算币种
     * @param targetCurrencyExponent 目标币种 ISO 小数位
     * @param roundingMode 最终目标净额舍入规则
     */
    public record ConversionCommand(List<AmountLine> lines,
                                    String targetCurrency,
                                    int targetCurrencyExponent,
                                    RoundingMode roundingMode) {

        public ConversionCommand {
            lines = List.copyOf(Objects.requireNonNull(lines, "amount lines are required"));
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("amount lines must not be empty");
            }
            targetCurrency = normalizeCurrency(targetCurrency);
            requireExponent(targetCurrencyExponent);
            Objects.requireNonNull(roundingMode, "rounding mode is required");
        }
    }

    /**
     * 单条原币种金额按批次汇率计算出的未舍入审计结果。
     *
     * @param lineNo 稳定来源明细号
     * @param sourceAmount 原币种金额
     * @param direction 商户视角方向
     * @param directRate 使用的批次直接汇率
     * @param unroundedTargetAmount 未舍入目标币种绝对金额
     */
    public record ConvertedAmountLine(String lineNo,
                                      Money sourceAmount,
                                      AmountDirection direction,
                                      BigDecimal directRate,
                                      BigDecimal unroundedTargetAmount) {

        public ConvertedAmountLine {
            lineNo = requireText(lineNo, "amount line number");
            requireNonNegative(sourceAmount, "source amount");
            Objects.requireNonNull(direction, "amount direction is required");
            requirePositive(directRate, "direct rate");
            requireNonNegative(unroundedTargetAmount, "unrounded target amount");
        }
    }

    /**
     * 批次目标净额计算结果。
     *
     * @param convertedLines 保留每行未舍入值的审计结果
     * @param unroundedTargetNetAmount 方向求和后的目标币种未舍入净额，可为负数
     * @param targetNetAmount 最终只舍入一次的目标币种净额
     */
    public record ConversionResult(List<ConvertedAmountLine> convertedLines,
                                   BigDecimal unroundedTargetNetAmount,
                                   Money targetNetAmount) {

        public ConversionResult {
            convertedLines = List.copyOf(Objects.requireNonNull(convertedLines, "converted lines are required"));
            Objects.requireNonNull(unroundedTargetNetAmount, "unrounded target net amount is required");
            Objects.requireNonNull(targetNetAmount, "target net amount is required");
        }
    }

    /**
     * 清分阶段已形成的费用原子组件。
     *
     * @param componentNo 清分费用组件稳定编号
     * @param componentKind 百分比标签币种组件或 USD 固定组件
     * @param sourceAmount 非负原币种费用金额
     */
    public record FeeComponentInput(String componentNo,
                                    FeeComponentKind componentKind,
                                    Money sourceAmount) {

        public FeeComponentInput {
            componentNo = requireText(componentNo, "fee component number");
            Objects.requireNonNull(componentKind, "fee component kind is required");
            requireNonNegative(sourceAmount, "fee component amount");
            if (componentKind == FeeComponentKind.FIXED && !"USD".equals(sourceAmount.currency())) {
                throw new IllegalArgumentException("fixed fee component must remain in USD");
            }
        }
    }

    /**
     * 单个跨币种费用组结算求值命令。
     *
     * @param feeGroupNo 清分费用逻辑组号
     * @param components 清分保存的标签币种百分比和 USD 固定费组件
     * @param minimumFeeUsd USD 最低费用事实；未配置时为空
     * @param maximumFeeUsd USD 最高费用事实；未配置时为空
     * @param targetCurrency 批次目标结算币种
     * @param targetCurrencyExponent 目标币种 ISO 小数位
     * @param roundingMode 最终费用舍入规则
     */
    public record FeeGroupCommand(String feeGroupNo,
                                  List<FeeComponentInput> components,
                                  Money minimumFeeUsd,
                                  Money maximumFeeUsd,
                                  String targetCurrency,
                                  int targetCurrencyExponent,
                                  RoundingMode roundingMode) {

        public FeeGroupCommand {
            feeGroupNo = requireText(feeGroupNo, "fee group number");
            components = List.copyOf(Objects.requireNonNull(components, "fee components are required"));
            if (components.isEmpty()) {
                throw new IllegalArgumentException("fee components must not be empty");
            }
            requireOptionalUsd(minimumFeeUsd, "minimum fee");
            requireOptionalUsd(maximumFeeUsd, "maximum fee");
            targetCurrency = normalizeCurrency(targetCurrency);
            requireExponent(targetCurrencyExponent);
            Objects.requireNonNull(roundingMode, "rounding mode is required");
        }
    }

    /**
     * 单条费用组件换算后的未舍入审计结果。
     *
     * @param componentNo 清分费用组件稳定编号
     * @param componentKind 组件类型
     * @param sourceAmount 原币种金额
     * @param directRate 使用的批次直接汇率
     * @param unroundedTargetAmount 未舍入目标币种金额
     */
    public record ConvertedFeeComponent(String componentNo,
                                        FeeComponentKind componentKind,
                                        Money sourceAmount,
                                        BigDecimal directRate,
                                        BigDecimal unroundedTargetAmount) {

        public ConvertedFeeComponent {
            componentNo = requireText(componentNo, "fee component number");
            Objects.requireNonNull(componentKind, "fee component kind is required");
            requireNonNegative(sourceAmount, "fee component amount");
            requirePositive(directRate, "direct rate");
            requireNonNegative(unroundedTargetAmount, "unrounded target amount");
        }
    }

    /**
     * 跨币种费用组按批次统一汇率求值后的结果。
     *
     * @param feeGroupNo 清分费用逻辑组号
     * @param convertedComponents 保持原清分组件不变的未舍入换算结果
     * @param unroundedCalculatedAmount 限额前目标币种费用
     * @param unroundedMinimumAmount USD 最低费换算后的目标币种值；未配置时为空
     * @param unroundedMaximumAmount USD 最高费换算后的目标币种值；未配置时为空
     * @param appliedLimit 实际命中的边界
     * @param limitAdjustmentAmount 限额相对组件合计的有符号调整金额
     * @param finalFee 只在最终结果处舍入一次的目标币种费用
     */
    public record FeeGroupResult(String feeGroupNo,
                                 List<ConvertedFeeComponent> convertedComponents,
                                 BigDecimal unroundedCalculatedAmount,
                                 BigDecimal unroundedMinimumAmount,
                                 BigDecimal unroundedMaximumAmount,
                                 AppliedLimit appliedLimit,
                                 BigDecimal limitAdjustmentAmount,
                                 Money finalFee) {

        public FeeGroupResult {
            feeGroupNo = requireText(feeGroupNo, "fee group number");
            convertedComponents = List.copyOf(
                    Objects.requireNonNull(convertedComponents, "converted fee components are required"));
            requireNonNegative(unroundedCalculatedAmount, "unrounded calculated fee");
            requireOptionalNonNegative(unroundedMinimumAmount, "unrounded minimum fee");
            requireOptionalNonNegative(unroundedMaximumAmount, "unrounded maximum fee");
            Objects.requireNonNull(appliedLimit, "applied limit is required");
            Objects.requireNonNull(limitAdjustmentAmount, "limit adjustment amount is required");
            requireNonNegative(finalFee, "final fee");
        }
    }

    private static void requireOptionalUsd(Money value, String fieldName) {
        if (value == null) {
            return;
        }
        requireNonNegative(value, fieldName);
        if (!"USD".equals(value.currency())) {
            throw new IllegalArgumentException(fieldName + " must remain in USD");
        }
    }

    private static void requireNonNegative(Money value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value.amount().signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private static void requireNonNegative(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private static void requireOptionalNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private static void requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeCurrency(String value) {
        String normalized = requireText(value, "target currency").toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("target currency must be an ISO 4217 alpha-3 code");
        }
        return normalized;
    }

    private static void requireExponent(int value) {
        if (value < 0 || value > 8) {
            throw new IllegalArgumentException("target currency exponent must be between 0 and 8");
        }
    }
}
