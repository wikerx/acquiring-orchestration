package com.scott.payment.clearing.dto;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 单条阶梯累计批量更新参数。
 *
 * @param feeRuleId 冻结阶梯费用规则 ID
 * @param expectedVersion 加锁读取到的累计行版本
 * @param amountDelta 当前动作已冻结的 USD 归一金额；COUNT 规则为零
 */
public record ClearingFeeTierAccumulatorDelta(Long feeRuleId,
                                              long expectedVersion,
                                              BigDecimal amountDelta) {

    public ClearingFeeTierAccumulatorDelta {
        if (feeRuleId == null || feeRuleId < 1 || expectedVersion < 0) {
            throw new IllegalArgumentException("tier accumulator identity and version are invalid");
        }
        Objects.requireNonNull(amountDelta, "tier accumulator amount delta is required");
        if (amountDelta.signum() < 0) {
            throw new IllegalArgumentException("tier accumulator amount delta must not be negative");
        }
    }
}
