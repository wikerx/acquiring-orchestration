package com.scott.payment.settlement.dto;

import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;

import java.util.Map;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementLockedRateMatrix
 * @date : 2026-08-26 23:20
 * @email : scott_x@163.com
 * @description : 已回读验证的批次汇率矩阵及其数据库行 ID，用于结果明细精确引用实际锁定行。
 * @status : create
 * @param matrix 纯计算使用的不可变矩阵
 * @param rateIdsBySourceCurrency 原币种到数据库汇率行 ID
 */
public record SettlementLockedRateMatrix(RateMatrix matrix,
                                         Map<String, Long> rateIdsBySourceCurrency) {

    public SettlementLockedRateMatrix {
        Objects.requireNonNull(matrix, "settlement rate matrix is required");
        rateIdsBySourceCurrency = Map.copyOf(Objects.requireNonNull(
                rateIdsBySourceCurrency, "settlement rate ids are required"));
        if (rateIdsBySourceCurrency.size() != matrix.rates().size()) {
            throw new IllegalArgumentException("settlement rate ids do not cover the complete matrix");
        }
    }

    /**
     * 取得指定原币种的实际汇率行 ID。
     *
     * @param sourceCurrency 原币种
     * @return 批次汇率主键
     */
    public long requireRateId(String sourceCurrency) {
        Long id = rateIdsBySourceCurrency.get(sourceCurrency);
        if (id == null) {
            throw new IllegalArgumentException("settlement batch rate id is missing for " + sourceCurrency);
        }
        return id;
    }
}
