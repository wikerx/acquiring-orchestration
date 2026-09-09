package com.scott.payment.clearing.service;

import com.scott.payment.clearing.domain.model.ClearingOperationFacts;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingSettlementCandidateService
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分到结算的候选输出边界；不认领批次、不换汇、不写余额。
 * @status : update
 */
public interface ClearingSettlementCandidateService {

    /**
     * 为交易动作当前清分修订创建幂等候选，不在清分阶段计算汇率或入账。
     *
     * @param financeStateId 动作财务状态号
     * @param revision 当前有效清分修订
     * @param operation 数据库权威动作事实
     * @param settlementCurrency 档案目标结算币种
     * @param eligibleDate 最早可结算业务日
     * @param now UTC 审计时间
     * @throws IllegalStateException 唯一键已存在但来源身份不一致时抛出
     */
    void create(String financeStateId,
                int revision,
                ClearingOperationFacts operation,
                String settlementCurrency,
                LocalDate eligibleDate,
                LocalDateTime now);

    /**
     * 用新修订替换尚未被批次认领的 READY 候选，旧候选只做状态终结不删除。
     *
     * @param financeStateId 动作财务状态号
     * @param oldRevision 被替换的旧修订
     * @param newRevision 新清分修订
     * @param operation 数据库权威动作事实
     * @param settlementCurrency 档案目标结算币种
     * @param eligibleDate 最早可结算业务日
     * @param now UTC 审计时间
     * @throws IllegalStateException 旧候选已认领、状态不可替换或 CAS 失败时抛出
     */
    void replace(String financeStateId,
                 int oldRevision,
                 int newRevision,
                 ClearingOperationFacts operation,
                 String settlementCurrency,
                 LocalDate eligibleDate,
                 LocalDateTime now);

    /**
     * 期间重放完成后替换已冻结为 REPLAY_HOLD 的旧候选，避免重放期间被并发结算。
     *
     * @param financeStateId 动作财务状态号
     * @param oldRevision 重放前修订
     * @param newRevision 重放后修订
     * @param operation 数据库权威动作事实
     * @param settlementCurrency 档案目标结算币种
     * @param eligibleDate 最早可结算业务日
     * @param now UTC 审计时间
     * @throws IllegalStateException 旧候选未冻结、已认领或版本 CAS 失败时抛出
     */
    void replaceReplayHeld(String financeStateId,
                           int oldRevision,
                           int newRevision,
                           ClearingOperationFacts operation,
                           String settlementCurrency,
                           LocalDate eligibleDate,
                           LocalDateTime now);

    /**
     * 为独立保证金到期释放事实创建候选，来源业务号固定为 reserveStateId。
     *
     * @param reserveStateId 原保证金状态业务号
     * @param sourceRevision 保证金状态成功 CAS 后版本
     * @param releaseTransactionId 独立释放动作号
     * @param releaseTransactionDateTime 释放事实季度路由时间
     * @param merchantId 平台商户号
     * @param settlementCurrency 当前活动档案目标币种
     * @param eligibleDate 最早结算业务日
     * @param now UTC审计时间
     */
    void createReserveRelease(String reserveStateId,
                              int sourceRevision,
                              String releaseTransactionId,
                              LocalDateTime releaseTransactionDateTime,
                              String merchantId,
                              String settlementCurrency,
                              LocalDate eligibleDate,
                              LocalDateTime now);

    /**
     * 为经双人复核且已资金化的保证金差额事实创建候选；该候选不代表真实交易。
     *
     * @param adjustmentNo 保证金调整申请号
     * @param sourceRevision 保证金状态资金化修订
     * @param adjustmentTransactionId 独立调整动作号
     * @param adjustmentTransactionDateTime 调整事实季度路由时间
     * @param merchantId 平台商户号
     * @param settlementCurrency 档案目标结算币种
     * @param eligibleDate 最早可结算业务日
     * @param now UTC 审计时间
     * @throws IllegalStateException 唯一键已存在但来源身份不一致时抛出
     */
    void createAdjustment(String adjustmentNo,
                          int sourceRevision,
                          String adjustmentTransactionId,
                          LocalDateTime adjustmentTransactionDateTime,
                          String merchantId,
                          String settlementCurrency,
                          LocalDate eligibleDate,
                          LocalDateTime now);
}
