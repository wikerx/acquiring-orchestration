package com.scott.payment.settlement.dto;

/**
 * 经历史修复审计证明的实际责任引用；原 RELEASE 清分事实及幂等身份保持不变。
 * @param holdDetailNo 实际资金化的新 HOLD 业务号
 * @param reserveItemId 实际责任行主键
 * @param reserveItemVersion 解析时冻结的责任版本，入账锁读后必须再次核对
 * @param repairNo 提供来源证明的历史修复单号
 */
public record SettlementReserveSourceBinding(String holdDetailNo, Long reserveItemId,
                                             Long reserveItemVersion, String repairNo) {
}
