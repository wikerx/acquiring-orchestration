package com.scott.payment.settlement.dto;

import java.util.Objects;

/**
 * 已入账历史标签本金差额调整申请命令。
 *
 * @param requestKey Maker 请求幂等键
 * @param originalBatchNo 原已入账结算批次号
 * @param expectedBatchVersion 页面读取到的原批次版本
 * @param reason 调整原因
 * @param operator 可信 Maker 审计快照
 */
public record SettlementPrincipalCorrectionSubmitCommand(
        String requestKey,
        String originalBatchNo,
        long expectedBatchVersion,
        String reason,
        SettlementOperatorSnapshot operator) {

    public SettlementPrincipalCorrectionSubmitCommand {
        requestKey = requireText(requestKey, 128, "principal correction request key");
        originalBatchNo = requireText(originalBatchNo, 19, "principal correction batch number");
        reason = requireText(reason, 400, "principal correction reason");
        if (expectedBatchVersion < 0) {
            throw new IllegalArgumentException("principal correction batch version must not be negative");
        }
        Objects.requireNonNull(operator, "principal correction operator is required");
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
