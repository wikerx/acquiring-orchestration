package com.scott.payment.settlement.dto;

import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.entity.SettlementResultSummaryDO;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCalculationPreview
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 承载不落正式结果表的确定性结算计算快照；用于预审提交与审批复算比对，金额沿用目标币种精度和统一锁定汇率。
 * @status : create
 * @param items 不落库结果明细的不可变副本
 * @param summaries 按业务和币种维度汇总的不可变副本
 * @param netDirection 净结果 CREDIT 或 DEBIT 方向
 * @param netAmount 目标币种非负净额，精度由预审目标币种 exponent 决定
 */
public record SettlementCalculationPreview(
        List<SettlementResultItemDO> items,
        List<SettlementResultSummaryDO> summaries,
        String netDirection,
        BigDecimal netAmount) {

    public SettlementCalculationPreview {
        items = List.copyOf(items);
        summaries = List.copyOf(summaries);
    }
}
