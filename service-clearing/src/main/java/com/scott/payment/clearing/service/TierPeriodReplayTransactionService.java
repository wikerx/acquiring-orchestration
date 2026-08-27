package com.scott.payment.clearing.service;

import com.scott.payment.clearing.service.TierPeriodReplayService.ReplayResult;
import com.scott.payment.clearing.service.TierPeriodReplayService.ReviewCommand;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TierPeriodReplayTransactionService
 * @date : 2026-08-26 19:30
 * @email : scott_x@163.com
 * @description : 阶梯重放短事务边界；准备、单项提交和失败记账均独立提交，避免整月长事务和部分不可恢复覆盖。
 * @status : create
 */
public interface TierPeriodReplayTransactionService {

    /** 双人复核通过后锁定累计、冻结候选和稳定动作清单。 */
    ReplayResult approve(ReviewCommand command, List<Long> tierRuleIds, LocalDateTime now);

    /** 拒绝尚未处理的申请。 */
    ReplayResult reject(ReviewCommand command, LocalDateTime now);

    /** 将可重试失败写回稳定下一项；第八次失败把整个闭包转人工复核。 */
    ReplayResult recordFailure(String replayNo,
                               ClearingTierPeriodReplayItemDO expectedItem,
                               String errorCode,
                               String errorMessage,
                               LocalDateTime now);
}
