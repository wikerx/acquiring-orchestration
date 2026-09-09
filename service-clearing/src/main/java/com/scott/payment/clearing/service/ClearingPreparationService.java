package com.scott.payment.clearing.service;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingPreparationService
 * @date : 2026-08-26 11:20
 * @email : scott_x@163.com
 * @description : 清分事务外准备边界，负责校验定位事实并加载确切费用版本，不执行费用落库、状态提交或消息发送。
 * @status : create
 */
public interface ClearingPreparationService {

    /**
     * 为已取得 PROCESSING 租约的动作准备阶段 B 命令。
     *
     * @param message 已通过基础校验的交易终态事件
     * @param claim 阶段 A 返回的数据库权威动作和租约版本
     * @param processingOwner 当前租约持有者
     * @return 只包含非敏感事实和已验证费用快照的阶段 B 命令
     */
    CompletionCommand prepare(PaymentTransactionEventMessage message,
                              ClearingClaimResult claim,
                              String processingOwner);

    /** 使用调用方已经按明确版本验证的目标快照准备人工重算，不读取或覆盖原动作冻结快照。 */
    CompletionCommand prepareForRecalculation(PaymentTransactionEventMessage message,
                                              ClearingClaimResult claim,
                                              String processingOwner,
                                              FeeVersionSnapshot targetSnapshot);
}
