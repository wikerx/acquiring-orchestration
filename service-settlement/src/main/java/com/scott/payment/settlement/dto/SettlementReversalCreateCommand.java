package com.scott.payment.settlement.dto;

import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalCreateCommand
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 表示可信 Admin 操作人提交的结算冲正申请；请求键提供创建幂等，原批次版本用于阻止基于过期页面状态发起冲正。
 * @status : create
 * @param requestKey 冲正创建请求数据库幂等键
 * @param originalBatchNo 待冲正的已入账正式结算批次号
 * @param expectedBatchVersion service-admin 页面读取的原批次版本
 * @param reason Maker 冲正申请原因
 * @param operator service-admin 注入的可信 Maker 快照
 */
public record SettlementReversalCreateCommand(
        String requestKey,
        String originalBatchNo,
        long expectedBatchVersion,
        String reason,
        SettlementOperatorSnapshot operator) {

    public SettlementReversalCreateCommand {
        requestKey = requireText(requestKey, 128, "reversal create request key");
        originalBatchNo = requireText(originalBatchNo, 19, "original settlement batch number");
        if (expectedBatchVersion < 0) {
            throw new IllegalArgumentException("original settlement batch version is invalid");
        }
        reason = requireText(reason, 400, "reversal reason");
        Objects.requireNonNull(operator, "reversal maker is required");
    }

    private static String requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        return value.trim();
    }
}
