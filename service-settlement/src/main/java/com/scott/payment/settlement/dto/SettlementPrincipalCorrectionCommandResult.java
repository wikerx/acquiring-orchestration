package com.scott.payment.settlement.dto;

import java.math.BigDecimal;

/**
 * 已入账历史标签本金差额调整命令结果。
 *
 * @param correctionNo 调整单号
 * @param correctionStatus 调整单状态
 * @param originalBatchNo 原结算批次号
 * @param merchantId 平台商户号
 * @param currency 调整资金币种
 * @param correctionDirection CREDIT 或 DEBIT
 * @param correctionAmount 非负调整金额
 * @param itemCount 冻结异常本金条数
 * @param projectionCompletedCount 已完成交易投影修正条数
 * @param version 调整单版本
 */
public record SettlementPrincipalCorrectionCommandResult(
        String correctionNo,
        String correctionStatus,
        String originalBatchNo,
        String merchantId,
        String currency,
        String correctionDirection,
        BigDecimal correctionAmount,
        int itemCount,
        int projectionCompletedCount,
        long version) {
}
