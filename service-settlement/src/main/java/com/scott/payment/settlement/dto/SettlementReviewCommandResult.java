package com.scott.payment.settlement.dto;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewCommandResult
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 返回预审提交或终态决策后的单号、正式批次关联和冻结净额；金额按目标币种及其 ISO exponent 表达。
 * @status : create
 * @param reviewOrderNo 结算预审单号
 * @param reviewStatus 预审当前或终态状态
 * @param settlementBatchNo 批准后创建的正式批次号，其他状态为空
 * @param candidateCount 预审冻结候选总数
 * @param targetCurrency 统一目标 ISO 结算币种
 * @param targetCurrencyExponent 目标币种 ISO 小数位
 * @param netDirection 冻结净结果 CREDIT 或 DEBIT 方向
 * @param netAmount 冻结目标币种非负净额
 * @param version 预审单当前乐观锁版本
 */
public record SettlementReviewCommandResult(
        String reviewOrderNo,
        String reviewStatus,
        String settlementBatchNo,
        int candidateCount,
        String targetCurrency,
        int targetCurrencyExponent,
        String netDirection,
        BigDecimal netAmount,
        long version) {
}
