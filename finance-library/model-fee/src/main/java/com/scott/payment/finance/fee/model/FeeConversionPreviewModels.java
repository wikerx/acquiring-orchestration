package com.scott.payment.finance.fee.model;

import com.scott.payment.finance.fee.model.FeeCalculationModels.AppliedLimit;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeCalculationResult;
import com.scott.payment.finance.money.model.Money;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeConversionPreviewModels
 * @date : 2026-08-25 19:40
 * @email : scott_x@163.com
 * @description : 定义管理后台跨币种费用预览契约；汇率由调用方显式提供，不能作为清分结果或结算批次事实使用。
 * @status : create
 */
public final class FeeConversionPreviewModels {

    private FeeConversionPreviewModels() {
    }

    /**
     * 目标币种费用预览命令。
     *
     * @param feeResult 按标签币种百分比和 USD 固定费拆分的费用计算结果
     * @param targetCurrency Admin 预览目标币种
     * @param targetExponent 目标币种 exponent
     * @param directRates 每个源币种到目标币种的直接汇率
     */
    public record FeeConversionPreviewCommand(FeeCalculationResult feeResult,
                                           String targetCurrency,
                                           int targetExponent,
                                           Map<String, BigDecimal> directRates) {

        public FeeConversionPreviewCommand {
            Objects.requireNonNull(feeResult, "fee result is required");
            targetCurrency = new Money(BigDecimal.ZERO, targetCurrency, targetExponent).currency();
            Map<String, BigDecimal> normalizedRates = new LinkedHashMap<>();
            if (directRates != null) {
                directRates.forEach((currency, rate) -> {
                    String normalizedCurrency = normalizeCurrency(currency);
                    if (rate == null || rate.signum() <= 0) {
                        throw new IllegalArgumentException("direct preview rate must be positive");
                    }
                    normalizedRates.put(normalizedCurrency, rate);
                });
            }
            directRates = Map.copyOf(normalizedRates);
        }
    }

    /**
     * 目标币种费用预览结果。
     *
     * @param rawFee 原组件换算后的目标币种费用，未应用上下限
     * @param finalFee 应用目标币种上下限后的预览费用
     * @param appliedLimit 预览阶段实际应用的上下限
     */
    public record FeeConversionPreviewResult(Money rawFee,
                                          Money finalFee,
                                          AppliedLimit appliedLimit) {

        public FeeConversionPreviewResult {
            Objects.requireNonNull(rawFee, "raw preview fee is required");
            Objects.requireNonNull(finalFee, "final preview fee is required");
            Objects.requireNonNull(appliedLimit, "applied preview limit is required");
            if (rawFee.amount().signum() < 0 || finalFee.amount().signum() < 0) {
                throw new IllegalArgumentException("preview fee must not be negative");
            }
        }
    }

    /**
     * 解析币种，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 仅返回规范化或计算结果，不直接提交交易状态。
     * </p>
     * @param currency 币种代码，格式为 ISO 4217 三位大写字母
     * @return 构造、转换或解析后的业务值
     */
    private static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("preview rate currency is required");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("preview rate currency must be an ISO 4217 alpha-3 code");
        }
        return normalized;
    }
}
