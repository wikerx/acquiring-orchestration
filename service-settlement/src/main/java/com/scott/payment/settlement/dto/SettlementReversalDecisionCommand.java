package com.scott.payment.settlement.dto;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalDecisionCommand
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 表示冲正批准或拒绝命令；决策请求键和期望版本共同保护终态幂等，操作人必须通过 Maker-Checker 校验。
 * @status : create
 * @param requestKey 冲正决策数据库幂等键
 * @param expectedVersion service-admin 页面读取的冲正单版本
 * @param decision APPROVE 或 REJECT
 * @param comment Checker 决策意见
 * @param operator service-admin 注入的可信 Checker 快照
 */
public record SettlementReversalDecisionCommand(
        String requestKey,
        long expectedVersion,
        String decision,
        String comment,
        SettlementOperatorSnapshot operator) {

    private static final Set<String> DECISIONS = Set.of("APPROVE", "REJECT");

    public SettlementReversalDecisionCommand {
        requestKey = requireText(requestKey, 128, "reversal decision request key");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("reversal decision version is invalid");
        }
        decision = requireText(decision, 16, "reversal decision").toUpperCase(Locale.ROOT);
        if (!DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("reversal decision is unsupported");
        }
        comment = requireText(comment, 400, "reversal decision comment");
        Objects.requireNonNull(operator, "reversal checker is required");
    }

    private static String requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        return value.trim();
    }
}
