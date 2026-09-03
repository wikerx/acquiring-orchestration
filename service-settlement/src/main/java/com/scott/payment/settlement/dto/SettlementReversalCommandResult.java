package com.scott.payment.settlement.dto;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalCommandResult
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 返回冲正申请或复核后的稳定身份、原/反向批次关联及冻结净额；净额单位由 currency 的 ISO exponent 决定。
 * @status : create
 * @param reversalOrderNo 冲正申请单号
 * @param reversalStatus 冲正当前或终态状态
 * @param originalBatchNo 被冲正原正式批次号
 * @param reversalBatchNo 批准后生成的独立反向批次号，其他状态为空
 * @param merchantId 原批次所属平台商户号
 * @param currency 原批次目标 ISO 结算币种
 * @param netDirection 原净结果 CREDIT 或 DEBIT 方向
 * @param netAmount 原净结果非负金额
 * @param version 冲正单当前乐观锁版本
 */
public record SettlementReversalCommandResult(
        String reversalOrderNo,
        String reversalStatus,
        String originalBatchNo,
        String reversalBatchNo,
        String merchantId,
        String currency,
        String netDirection,
        BigDecimal netAmount,
        long version) {
}
