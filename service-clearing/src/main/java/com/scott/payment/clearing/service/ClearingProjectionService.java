package com.scott.payment.clearing.service;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.LocatorFacts;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;

import java.time.LocalDateTime;

/**
 * 在调用方清分事务内同步动作和生命周期查询投影；不拥有权威清分状态、交易状态或结算状态。
 */
public interface ClearingProjectionService {

    /**
     * 使用准备阶段已经校验的当前 locator 更新动作和生命周期投影。
     *
     * @param operation 当前动作权威事实
     * @param currentLocator 当前动作及根主单真实分片定位
     * @param authoritativeStatus 本次已提交或准备提交的权威清分状态
     * @param failureCode 最近失败码；成功或无需清分时为空
     * @param now 本次事务统一平台本地时间
     */
    void updateWithLocator(ClearingOperationFacts operation,
                           LocatorFacts currentLocator,
                           ClearingStateEnum authoritativeStatus,
                           String failureCode,
                           LocalDateTime now);

    /**
     * 在受控失败事务中重新读取当前 locator 后更新动作和生命周期投影。
     *
     * @param operation 当前动作权威事实
     * @param authoritativeStatus 本次已提交或准备提交的权威清分状态
     * @param failureCode 本次稳定失败码
     * @param now 本次事务统一平台本地时间
     */
    void updateResolvingLocator(ClearingOperationFacts operation,
                                ClearingStateEnum authoritativeStatus,
                                String failureCode,
                                LocalDateTime now);
}
