package com.scott.payment.settlement.dto;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewDecisionCommand
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 表示预审批准、拒绝或取消命令；请求键和期望版本保护终态幂等，批准必须由异于 Maker 的可信 Checker 执行。
 * @status : create
 * @param requestKey 预审决策数据库幂等键
 * @param expectedVersion service-admin 页面读取的预审单版本
 * @param decision APPROVE、REJECT 或 CANCEL
 * @param comment Checker 决策意见
 * @param operator service-admin 注入的可信 Checker 快照
 */
public record SettlementReviewDecisionCommand(
        String requestKey,
        long expectedVersion,
        String decision,
        String comment,
        SettlementOperatorSnapshot operator) {

    private static final Set<String> DECISIONS = Set.of("APPROVE", "REJECT", "CANCEL");

    public SettlementReviewDecisionCommand {
        requestKey = requireText(requestKey, 128, "review decision request key");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("review decision expected version is invalid");
        }
        decision = requireText(decision, 16, "review decision").toUpperCase(Locale.ROOT);
        if (!DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("review decision is unsupported");
        }
        comment = requireText(comment, 400, "review decision comment");
        Objects.requireNonNull(operator, "review decision operator is required");
    }

    private static String requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        return value.trim();
    }
}
