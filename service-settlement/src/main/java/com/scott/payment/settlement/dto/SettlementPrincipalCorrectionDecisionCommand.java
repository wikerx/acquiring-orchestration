package com.scott.payment.settlement.dto;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 已入账历史标签本金差额调整复核命令。
 *
 * @param requestKey Checker 请求幂等键
 * @param expectedVersion 调整单期望版本
 * @param decision APPROVE 或 REJECT
 * @param comment 复核意见
 * @param operator 可信 Checker 审计快照
 */
public record SettlementPrincipalCorrectionDecisionCommand(
        String requestKey,
        long expectedVersion,
        String decision,
        String comment,
        SettlementOperatorSnapshot operator) {

    private static final Set<String> DECISIONS = Set.of("APPROVE", "REJECT");

    public SettlementPrincipalCorrectionDecisionCommand {
        requestKey = requireText(requestKey, 128, "principal correction decision request key");
        comment = requireText(comment, 400, "principal correction decision comment");
        decision = decision == null ? null : decision.trim().toUpperCase(Locale.ROOT);
        if (expectedVersion < 0 || !DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("principal correction decision is invalid");
        }
        Objects.requireNonNull(operator, "principal correction decision operator is required");
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
