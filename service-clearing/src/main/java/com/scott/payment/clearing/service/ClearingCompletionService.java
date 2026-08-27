package com.scott.payment.clearing.service;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionResult;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCompletionService
 * @date : 2026-08-26 12:00
 * @email : scott_x@163.com
 * @description : 清分 Stage B 原子提交边界，在 transaction 复合数据源同一事务中写入明细、状态、幂等和 Outbox。
 * @status : create
 */
public interface ClearingCompletionService {

    /**
     * 完成已取得租约动作的当前清分修订。
     *
     * @param command 事务外已经校验的非敏感交易和费用快照事实
     * @param now 本次事务统一完成时间
     * @return 已提交状态、修订号和两类明细数量
     */
    CompletionResult complete(CompletionCommand command, LocalDateTime now);

    /**
     * 对尚未结算且候选未被认领的完成态动作生成下一修订；旧修订保留并标记为 SUPERSEDED。
     */
    CompletionResult recalculate(CompletionCommand command,
                                 int expectedVersion,
                                 int expectedRevision,
                                 LocalDateTime now);

    /**
     * 按控制表稳定下一项执行阶梯期间重算；新修订、累计、候选和重放游标必须在同一短事务提交。
     */
    CompletionResult recalculateTierPeriod(CompletionCommand command,
                                           String replayNo,
                                           int sequenceNo,
                                           int expectedVersion,
                                           int expectedRevision,
                                           LocalDateTime now);
}
