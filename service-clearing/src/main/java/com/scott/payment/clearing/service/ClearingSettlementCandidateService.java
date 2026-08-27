package com.scott.payment.clearing.service;

import com.scott.payment.clearing.domain.model.ClearingOperationFacts;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 清分到结算的候选输出边界；不认领批次、不换汇、不写余额。 */
public interface ClearingSettlementCandidateService {

    /** 为交易动作当前清分修订创建结算候选。 */
    void create(String financeStateId,
                int revision,
                ClearingOperationFacts operation,
                String settlementCurrency,
                LocalDate eligibleDate,
                LocalDateTime now);

    /** 用新清分修订替换尚未被结算认领的旧候选。 */
    void replace(String financeStateId,
                 int oldRevision,
                 int newRevision,
                 ClearingOperationFacts operation,
                 String settlementCurrency,
                 LocalDate eligibleDate,
                 LocalDateTime now);

    /** 期间重放只能替换准备阶段已冻结为 REPLAY_HOLD 的旧修订候选。 */
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

    /** 为经复核的独立保证金差额调整事实创建结算候选。 */
    void createAdjustment(String adjustmentNo,
                          int sourceRevision,
                          String adjustmentTransactionId,
                          LocalDateTime adjustmentTransactionDateTime,
                          String merchantId,
                          String settlementCurrency,
                          LocalDate eligibleDate,
                          LocalDateTime now);
}
