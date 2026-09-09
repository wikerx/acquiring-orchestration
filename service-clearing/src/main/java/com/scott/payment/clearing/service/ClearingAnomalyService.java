package com.scott.payment.clearing.service;

import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingAnomalyTypeEnum;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingAnomalyService
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 持久化和关闭清分异常案件，不改变交易或清分权威状态。
 * @status : update
 */
public interface ClearingAnomalyService {

    /**
     * 记录或累加同一修订、类别和失败码的异常案件。
     *
     * @param operation 数据库权威动作事实
     * @param financeStateId 动作财务状态号
     * @param revision 清分修订号
     * @param anomalyType 低基数异常分类
     * @param failureCode 稳定失败码
     * @param summary 已脱敏且不含异常堆栈的摘要
     * @param now UTC 审计时间
     */
    void record(ClearingOperationFacts operation, String financeStateId, int revision,
                ClearingAnomalyTypeEnum anomalyType, String failureCode,
                String summary, LocalDateTime now);

    /**
     * 清分恢复完成后关闭同一交易分片和来源修订的活动案件。
     *
     * @param transactionId 动作交易号
     * @param transactionDateTime 动作季度分片时间
     * @param referenceId 来源财务状态号与修订组合
     * @param now UTC 审计时间
     */
    void resolve(String transactionId, LocalDateTime transactionDateTime,
                 String referenceId, LocalDateTime now);
}
