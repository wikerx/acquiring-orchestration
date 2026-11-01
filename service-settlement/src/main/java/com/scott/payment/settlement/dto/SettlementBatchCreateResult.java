package com.scott.payment.settlement.dto;

/**
 * 结算批次创建结果，明确存储批次号、展示号和是否复用既有幂等结果。
 * @param batchId 结算批次数据库主键
 * @param settlementBatchNo SByyyyMMdd-NNNNNNNN 存储批次号
 * @param displayBatchNo yyyy-MM-dd NNNNNNNN 展示批次号
 * @param reused 是否由 create_request_key 复用既有批次
 */
public record SettlementBatchCreateResult(Long batchId,
                                          String settlementBatchNo,
                                          String displayBatchNo,
                                          boolean reused) {
}
