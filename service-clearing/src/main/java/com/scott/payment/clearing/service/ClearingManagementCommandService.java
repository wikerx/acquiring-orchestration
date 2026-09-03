package com.scott.payment.clearing.service;

import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingCommandResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecalculateRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRetryRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingReviewRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingManagementCommandService
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分管理命令边界，所有写操作均要求真实分片时间、预期版本、原因和操作人。
 * @status : update
 */
public interface ClearingManagementCommandService {

    /**
     * 对可重试失败重新排期，不直接把清分状态改成成功。
     *
     * @param transactionId 动作交易号
     * @param request 包含真实分片时间、预期版本、原因和可信操作人的重试命令
     * @return 已持久化的重试排期结果；重复命令返回稳定结果
     * @throws IllegalArgumentException 命令身份或审计信息不完整时抛出
     * @throws IllegalStateException 当前状态、版本或动作事实不允许重试时抛出
     */
    ClearingCommandResponse retry(String transactionId, ClearingRetryRequest request);

    /**
     * 将人工复核结论按固定状态机提交，浏览器不能指定任意目标状态。
     *
     * @param transactionId 动作交易号
     * @param request 包含复核决定、预期版本、原因和可信操作人的命令
     * @return 状态机提交后的清分结果
     * @throws IllegalArgumentException 命令身份或审计信息不完整时抛出
     * @throws IllegalStateException 状态已终结、版本过期或复核决定不适用时抛出
     */
    ClearingCommandResponse review(String transactionId, ClearingReviewRequest request);

    /**
     * 编排未结算动作的指定费用版本重算，并保留旧修订审计事实。
     *
     * @param transactionId 动作交易号
     * @param request 包含目标费用版本、当前修订、预期版本、原因和可信操作人的命令
     * @return 新清分修订的提交结果
     * @throws IllegalArgumentException 目标版本或审计参数不完整时抛出
     * @throws IllegalStateException 动作已结算、版本过期或权威事实不一致时抛出
     */
    ClearingCommandResponse recalculate(String transactionId, ClearingRecalculateRequest request);
}
