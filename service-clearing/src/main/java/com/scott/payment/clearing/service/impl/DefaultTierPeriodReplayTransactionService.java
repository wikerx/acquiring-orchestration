package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.entity.ClearingFeeTierAccumulatorDO;
import com.scott.payment.clearing.entity.ClearingSettlementCandidateDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemFactsDO;
import com.scott.payment.clearing.mapper.ClearingFeeTierAccumulatorMapper;
import com.scott.payment.clearing.mapper.ClearingSettlementCandidateMapper;
import com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper;
import com.scott.payment.clearing.service.TierPeriodReplayService.ReplayResult;
import com.scott.payment.clearing.service.TierPeriodReplayService.ReviewCommand;
import com.scott.payment.clearing.service.TierPeriodReplayService.ReviewDecision;
import com.scott.payment.clearing.service.TierPeriodReplayTransactionService;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTierPeriodReplayTransactionService
 * @date : 2026-08-26 19:35
 * @email : scott_x@163.com
 * @description : 以累计行锁、结算候选冻结和双人复核原子准备整月阶梯重放；任一不可逆事实会整体转人工复核。
 * @status : create
 */
@Service
public class DefaultTierPeriodReplayTransactionService implements TierPeriodReplayTransactionService {

    private static final String PENDING_REVIEW = "PENDING_REVIEW";
    private static final String MANUAL_REVIEW = "MANUAL_REVIEW";

    private final ClearingTierPeriodReplayMapper replayMapper;
    private final ClearingFeeTierAccumulatorMapper accumulatorMapper;
    private final ClearingSettlementCandidateMapper candidateMapper;

    public DefaultTierPeriodReplayTransactionService(ClearingTierPeriodReplayMapper replayMapper,
                                                     ClearingFeeTierAccumulatorMapper accumulatorMapper,
                                                     ClearingSettlementCandidateMapper candidateMapper) {
        this.replayMapper = replayMapper;
        this.accumulatorMapper = accumulatorMapper;
        this.candidateMapper = candidateMapper;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReplayResult approve(ReviewCommand command, List<Long> tierRuleIds, LocalDateTime now) {
        validateReview(command, ReviewDecision.APPROVE, now);
        List<Long> stableRuleIds = stableRuleIds(tierRuleIds);
        ClearingTierPeriodReplayDO replay = lockReviewable(command);
        String reviewer = normalized(command.reviewOperator(), "review operator", 128);
        if (Objects.equals(replay.getSubmitOperator(), reviewer)) {
            throw new IllegalStateException("tier replay submitter and reviewer must be different");
        }
        requireOne(replayMapper.markPreparing(replay.getReplayNo(), replay.getVersion(), reviewer,
                normalized(command.reviewComment(), "review comment", 400), now), "tier replay preparing CAS");
        long preparingVersion = replay.getVersion() + 1;

        accumulatorMapper.insertIfAbsentBatch(replay.getMerchantId(), replay.getFeePlanVersionId(),
                stableRuleIds, replay.getPeriodKey(), now);
        List<ClearingFeeTierAccumulatorDO> accumulators = accumulatorMapper.selectForUpdateBatch(
                replay.getMerchantId(), replay.getFeePlanVersionId(), stableRuleIds, replay.getPeriodKey());
        validateAccumulatorClosure(stableRuleIds, accumulators);

        List<ClearingTierPeriodReplayItemFactsDO> facts = replayMapper.selectPeriodItems(
                replay.getMerchantId(), replay.getFeePlanVersionId(), replay.getPeriodKey(),
                replay.getPeriodStart(), replay.getPeriodEnd());
        String gateError = periodGateError(facts);
        if (gateError == null && replayMapper.countActiveReserveDetails(
                replay.getMerchantId(), replay.getFeePlanVersionId(),
                replay.getPeriodStart(), replay.getPeriodEnd()) > 0) {
            gateError = "RESERVE_FACTS_PRESENT";
        }
        if (gateError != null) {
            requireOne(replayMapper.markManualReview(replay.getReplayNo(), preparingVersion,
                    gateError, manualMessage(gateError), now), "tier replay manual review CAS");
            return new ReplayResult(replay.getReplayNo(), MANUAL_REVIEW,
                    facts == null ? 0 : facts.size(), 0, preparingVersion + 1);
        }

        List<ClearingSettlementCandidateDO> candidates = candidateMapper.selectForTierReplay(facts);
        if (!replaceableCandidates(facts, candidates)) {
            requireOne(replayMapper.markManualReview(replay.getReplayNo(), preparingVersion,
                    "CANDIDATE_NOT_READY", manualMessage("CANDIDATE_NOT_READY"), now),
                    "tier replay candidate manual review CAS");
            return new ReplayResult(replay.getReplayNo(), MANUAL_REVIEW, facts.size(), 0,
                    preparingVersion + 1);
        }
        requireRows(candidateMapper.holdForTierReplay(candidates, now), candidates.size(),
                "tier replay candidate hold CAS");

        List<ClearingTierPeriodReplayItemDO> items = replayItems(replay.getReplayNo(), facts, now);
        requireRows(replayMapper.insertItems(items), items.size(), "tier replay item insert");
        requireRows(accumulatorMapper.resetPeriod(replay.getMerchantId(), replay.getFeePlanVersionId(),
                stableRuleIds, replay.getPeriodKey(), now), stableRuleIds.size(),
                "tier accumulator period reset");
        requireOne(replayMapper.markRunning(replay.getReplayNo(), preparingVersion, items.size(), now),
                "tier replay running CAS");
        return new ReplayResult(replay.getReplayNo(), "RUNNING", items.size(), 0, preparingVersion + 1);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReplayResult reject(ReviewCommand command, LocalDateTime now) {
        validateReview(command, ReviewDecision.REJECT, now);
        ClearingTierPeriodReplayDO replay = lockReviewable(command);
        String reviewer = normalized(command.reviewOperator(), "review operator", 128);
        if (Objects.equals(replay.getSubmitOperator(), reviewer)) {
            throw new IllegalStateException("tier replay submitter and reviewer must be different");
        }
        requireOne(replayMapper.markRejected(replay.getReplayNo(), replay.getVersion(), reviewer,
                normalized(command.reviewComment(), "review comment", 400), now),
                "tier replay reject CAS");
        return new ReplayResult(replay.getReplayNo(), "REJECTED", 0, 0, replay.getVersion() + 1);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReplayResult recordFailure(String replayNo,
                                      ClearingTierPeriodReplayItemDO expectedItem,
                                      String errorCode,
                                      String errorMessage,
                                      LocalDateTime now) {
        if (!StringUtils.hasText(replayNo) || expectedItem == null || now == null
                || !Objects.equals(replayNo, expectedItem.getReplayNo())) {
            throw new IllegalArgumentException("tier replay failure identity is required");
        }
        ClearingTierPeriodReplayDO replay = replayMapper.selectForUpdate(replayNo);
        ClearingTierPeriodReplayItemDO item = replayMapper.selectNextItemForUpdate(replayNo, now);
        if (replay == null || !"RUNNING".equals(replay.getReplayStatus()) || replay.getVersion() == null
                || item == null || !Objects.equals(item.getSequenceNo(), expectedItem.getSequenceNo())
                || !Objects.equals(item.getTransactionId(), expectedItem.getTransactionId())
                || item.getVersion() == null || item.getAttemptCount() == null) {
            throw new IllegalStateException("tier replay failure target is stale or no longer next");
        }
        int attemptsAfter = item.getAttemptCount() + 1;
        boolean exhausted = attemptsAfter >= 8 || Set.of(
                "SETTLED_PERIOD", "RESERVE_FACTS_PRESENT", "CANDIDATE_NOT_READY",
                "FEE_VERSION_NOT_IMMUTABLE", "CLEARING_CAS_CONFLICT").contains(errorCode);
        long delayMinutes = Math.min(60L, 1L << Math.min(6, item.getAttemptCount()));
        String safeCode = normalized(errorCode, "replay error code", 64);
        String safeMessage = normalized(errorMessage, "replay error message", 400);
        requireOne(replayMapper.markItemFailed(replayNo, item.getSequenceNo(), item.getVersion(),
                now.plus(Duration.ofMinutes(delayMinutes)), safeCode, safeMessage, now),
                "tier replay item failure CAS");
        String targetStatus = exhausted ? MANUAL_REVIEW : "RUNNING";
        requireOne(replayMapper.recordItemFailure(replayNo, replay.getVersion(), targetStatus,
                safeCode, safeMessage, now), "tier replay failure control CAS");
        return new ReplayResult(replayNo, targetStatus,
                replay.getItemCount() == null ? 0 : replay.getItemCount(),
                replay.getCompletedCount() == null ? 0 : replay.getCompletedCount(), replay.getVersion() + 1);
    }

    private ClearingTierPeriodReplayDO lockReviewable(ReviewCommand command) {
        ClearingTierPeriodReplayDO replay = replayMapper.selectForUpdate(command.replayNo());
        if (replay == null || !PENDING_REVIEW.equals(replay.getReplayStatus())
                || replay.getVersion() == null || replay.getVersion() != command.expectedRequestVersion()) {
            throw new IllegalStateException("tier replay request is missing, stale or not reviewable");
        }
        return replay;
    }

    private List<Long> stableRuleIds(List<Long> tierRuleIds) {
        if (tierRuleIds == null || tierRuleIds.isEmpty() || tierRuleIds.stream().anyMatch(id -> id == null || id < 1)) {
            throw new IllegalArgumentException("complete tier rule closure is required");
        }
        List<Long> stable = tierRuleIds.stream().distinct().sorted().toList();
        if (stable.size() != tierRuleIds.size()) {
            throw new IllegalArgumentException("tier rule closure contains duplicates");
        }
        return stable;
    }

    private void validateAccumulatorClosure(List<Long> ruleIds,
                                            List<ClearingFeeTierAccumulatorDO> accumulators) {
        if (accumulators == null || accumulators.size() != ruleIds.size()) {
            throw new IllegalStateException("tier accumulator closure is incomplete");
        }
        Set<Long> actual = new HashSet<>();
        for (ClearingFeeTierAccumulatorDO row : accumulators) {
            if (row == null || row.getFeeRuleId() == null || row.getVersion() == null
                    || !actual.add(row.getFeeRuleId())) {
                throw new IllegalStateException("tier accumulator closure contains invalid rows");
            }
        }
        if (!actual.equals(Set.copyOf(ruleIds))) {
            throw new IllegalStateException("tier accumulator closure does not match immutable fee rules");
        }
    }

    private String periodGateError(List<ClearingTierPeriodReplayItemFactsDO> facts) {
        if (facts == null || facts.isEmpty()) {
            return "EMPTY_PERIOD";
        }
        Set<String> identities = new HashSet<>();
        for (ClearingTierPeriodReplayItemFactsDO item : facts) {
            if (item == null || !StringUtils.hasText(item.getFinanceStateId())
                    || !StringUtils.hasText(item.getTransactionId()) || item.getTransactionDateTime() == null
                    || item.getClearingCompleteTime() == null || item.getClearingRevision() == null
                    || item.getClearingRevision() < 1 || item.getFinanceStateVersion() == null
                    || item.getFinanceStateVersion() < 0
                    || !identities.add(item.getFinanceStateId() + "|" + item.getClearingRevision())) {
                return "PERIOD_FACTS_INVALID";
            }
            if (!"NOT_SETTLED".equals(item.getSettlementStatus())) {
                return "SETTLED_PERIOD";
            }
        }
        return null;
    }

    private boolean replaceableCandidates(List<ClearingTierPeriodReplayItemFactsDO> facts,
                                          List<ClearingSettlementCandidateDO> candidates) {
        if (candidates == null || candidates.size() != facts.size()) {
            return false;
        }
        Map<String, ClearingSettlementCandidateDO> bySource = new HashMap<>();
        for (ClearingSettlementCandidateDO candidate : candidates) {
            if (candidate == null || !"CLEARING_REVISION".equals(candidate.getSourceType())
                    || !"READY".equals(candidate.getCandidateStatus())
                    || candidate.getSettlementBatchNo() != null || candidate.getVersion() == null
                    || bySource.putIfAbsent(candidate.getSourceBusinessId() + "|"
                            + candidate.getSourceRevision(), candidate) != null) {
                return false;
            }
        }
        return facts.stream().allMatch(item -> bySource.containsKey(
                item.getFinanceStateId() + "|" + item.getClearingRevision()));
    }

    private List<ClearingTierPeriodReplayItemDO> replayItems(
            String replayNo, List<ClearingTierPeriodReplayItemFactsDO> facts, LocalDateTime now) {
        List<ClearingTierPeriodReplayItemDO> rows = new ArrayList<>(facts.size());
        int sequence = 0;
        for (ClearingTierPeriodReplayItemFactsDO fact : facts) {
            ClearingTierPeriodReplayItemDO row = new ClearingTierPeriodReplayItemDO();
            row.setReplayNo(replayNo);
            row.setSequenceNo(++sequence);
            row.setFinanceStateId(fact.getFinanceStateId());
            row.setTransactionId(fact.getTransactionId());
            row.setTransactionDateTime(fact.getTransactionDateTime());
            row.setExpectedClearingRevision(fact.getClearingRevision());
            row.setExpectedFinanceStateVersion(fact.getFinanceStateVersion());
            row.setClearingCompleteTime(fact.getClearingCompleteTime());
            row.setItemStatus("PENDING");
            row.setAttemptCount(0);
            row.setVersion(0L);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            rows.add(row);
        }
        return rows;
    }

    private String manualMessage(String errorCode) {
        return switch (errorCode) {
            case "EMPTY_PERIOD" -> "no completed clearing actions exist in the requested tier period";
            case "SETTLED_PERIOD" -> "the tier period contains settled clearing facts";
            case "RESERVE_FACTS_PRESENT" -> "the tier period contains reserve facts requiring adjustment workflow";
            case "CANDIDATE_NOT_READY" -> "one or more clearing candidates are missing, claimed or not ready";
            default -> "the tier period contains inconsistent replay facts";
        };
    }

    private void validateReview(ReviewCommand command, ReviewDecision expected, LocalDateTime now) {
        if (command == null || !StringUtils.hasText(command.replayNo())
                || command.expectedRequestVersion() < 0 || command.decision() != expected
                || command.reviewInstant() == null || now == null) {
            throw new IllegalArgumentException("complete tier replay review command is required");
        }
        normalized(command.reviewOperator(), "review operator", 128);
        normalized(command.reviewComment(), "review comment", 400);
    }

    private String normalized(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value) || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(field + " is missing or too long");
        }
        return value.trim();
    }

    private void requireOne(int affectedRows, String operation) {
        requireRows(affectedRows, 1, operation);
    }

    private void requireRows(int affectedRows, int expectedRows, String operation) {
        if (affectedRows != expectedRows) {
            throw new IllegalStateException(operation + " did not affect the expected rows");
        }
    }
}
