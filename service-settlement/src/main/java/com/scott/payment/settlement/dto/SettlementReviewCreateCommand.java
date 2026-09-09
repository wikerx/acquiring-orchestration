package com.scott.payment.settlement.dto;

import com.scott.payment.settlement.domain.model.SettlementBatchType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewCreateCommand
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 表示人工结算预审提交；候选引用携带页面读取版本，候选集合必须同商户、账户、目标币种和来源类型且不超过一千条。
 * @status : create
 * @param requestKey 预审创建请求数据库幂等键
 * @param reviewType REGULAR、RESERVE_RELEASE 或 ADJUSTMENT
 * @param businessDate 结算日历业务日期
 * @param cutoffBeginTime 候选窗口闭区间起点
 * @param cutoffEndTime 候选窗口开区间终点
 * @param candidates 待锁定候选及其页面期望版本，不超过一千条
 * @param reason Maker 提交原因
 * @param submitter service-admin 注入的可信 Maker 快照
 */
public record SettlementReviewCreateCommand(
        String requestKey,
        SettlementBatchType reviewType,
        LocalDate businessDate,
        LocalDateTime cutoffBeginTime,
        LocalDateTime cutoffEndTime,
        List<CandidateReference> candidates,
        String reason,
        SettlementOperatorSnapshot submitter) {

    public SettlementReviewCreateCommand {
        requestKey = requireText(requestKey, 128, "review request key");
        Objects.requireNonNull(reviewType, "settlement review type is required");
        if (reviewType == SettlementBatchType.REVERSAL) {
            throw new IllegalArgumentException("reversal cannot use settlement review order");
        }
        Objects.requireNonNull(businessDate, "settlement review business date is required");
        Objects.requireNonNull(cutoffBeginTime, "settlement review cutoff begin time is required");
        Objects.requireNonNull(cutoffEndTime, "settlement review cutoff end time is required");
        if (!cutoffEndTime.isAfter(cutoffBeginTime)) {
            throw new IllegalArgumentException("settlement review cutoff is invalid");
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (candidates.isEmpty() || candidates.size() > 1000) {
            throw new IllegalArgumentException("settlement review candidate count is invalid");
        }
        Set<Long> identities = new HashSet<>();
        if (candidates.stream().anyMatch(row -> row == null || !identities.add(row.candidateId()))) {
            throw new IllegalArgumentException("settlement review candidates are duplicated");
        }
        reason = requireText(reason, 400, "settlement review reason");
        Objects.requireNonNull(submitter, "settlement review submitter is required");
    }

    /**
     * @param candidateId 待预审候选数据库主键
     * @param expectedVersion service-admin 页面读取的候选版本
     */
    public record CandidateReference(Long candidateId, long expectedVersion) {
        public CandidateReference {
            if (candidateId == null || candidateId <= 0 || expectedVersion < 0) {
                throw new IllegalArgumentException("settlement review candidate reference is invalid");
            }
        }
    }

    private static String requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " is invalid");
        }
        return value.trim();
    }
}
