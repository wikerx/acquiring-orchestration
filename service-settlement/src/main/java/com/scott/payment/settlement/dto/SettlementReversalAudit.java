package com.scott.payment.settlement.dto;

import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalAudit
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 承载冲正批准后写入反向资金流水的完整 Maker-Checker 审计，强制申请人与复核人为不同账户。
 * @status : create
 * @param reversalOrderNo 已批准冲正申请单号
 * @param makerReason Maker 提交原因
 * @param maker 提交时可信主体和客户端快照
 * @param checkerComment Checker 决策意见
 * @param checker 决策时可信主体和客户端快照，账户必须异于 Maker
 */
public record SettlementReversalAudit(
        String reversalOrderNo,
        String makerReason,
        SettlementOperatorSnapshot maker,
        String checkerComment,
        SettlementOperatorSnapshot checker) {

    public SettlementReversalAudit {
        reversalOrderNo = requireText(reversalOrderNo, 20, "reversal order number");
        makerReason = requireText(makerReason, 400, "reversal maker reason");
        checkerComment = requireText(checkerComment, 400, "reversal checker comment");
        Objects.requireNonNull(maker, "reversal maker is required");
        Objects.requireNonNull(checker, "reversal checker is required");
        if (Objects.equals(maker.accountId(), checker.accountId())) {
            throw new IllegalArgumentException("reversal Maker and Checker must be different accounts");
        }
    }

    private static String requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        return value.trim();
    }
}
