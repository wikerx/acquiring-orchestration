package com.scott.payment.settlement.application;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.settlement.domain.model.SettlementCandidateStatus;
import com.scott.payment.settlement.domain.model.SettlementReviewStatus;
import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementCalculationPreview;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementReviewDecisionCommand;
import com.scott.payment.settlement.dto.SettlementReviewDecisionModels.TaskResult;
import com.scott.payment.settlement.entity.MerchantSettlementProfileDO;
import com.scott.payment.settlement.entity.SettlementBatchCandidateDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementBatchRateDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReviewCandidateDO;
import com.scott.payment.settlement.entity.SettlementReviewDecisionTaskDO;
import com.scott.payment.settlement.entity.SettlementReviewOrderDO;
import com.scott.payment.settlement.entity.SettlementReviewRateDO;
import com.scott.payment.settlement.entity.SettlementReviewSegmentDO;
import com.scott.payment.settlement.exception.SettlementReviewDecisionProcessingException;
import com.scott.payment.settlement.mapper.MerchantSettlementProfileMapper;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementBatchRateMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementReviewCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementReviewDecisionTaskMapper;
import com.scott.payment.settlement.mapper.SettlementReviewOrderMapper;
import com.scott.payment.settlement.mapper.SettlementReviewRateMapper;
import com.scott.payment.settlement.mapper.SettlementReviewSegmentMapper;
import com.scott.payment.settlement.service.SettlementBatchCreationService;
import com.scott.payment.settlement.service.SettlementClearingFactService;
import com.scott.payment.settlement.service.SettlementResultCalculationService;
import com.scott.payment.settlement.support.SettlementReviewFingerprintService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 大预审单按分段批准、释放和最终状态收口的本地事务边界。 */
@Service
public class SettlementReviewDecisionTransactionService {

    private final SettlementReviewDecisionTaskMapper taskMapper;
    private final SettlementReviewOrderMapper orderMapper;
    private final SettlementReviewSegmentMapper segmentMapper;
    private final SettlementReviewCandidateMapper reviewCandidateMapper;
    private final SettlementReviewRateMapper reviewRateMapper;
    private final SettlementCandidateMapper candidateMapper;
    private final MerchantSettlementProfileMapper profileMapper;
    private final SettlementBatchCreationService batchCreationService;
    private final SettlementBatchMapper batchMapper;
    private final SettlementBatchCandidateMapper batchCandidateMapper;
    private final SettlementBatchRateMapper batchRateMapper;
    private final SettlementClearingFactService factService;
    private final SettlementResultCalculationService calculationService;
    private final SettlementReviewFingerprintService fingerprintService;
    private final Clock clock;

    @Autowired
    public SettlementReviewDecisionTransactionService(
            SettlementReviewDecisionTaskMapper taskMapper,
            SettlementReviewOrderMapper orderMapper,
            SettlementReviewSegmentMapper segmentMapper,
            SettlementReviewCandidateMapper reviewCandidateMapper,
            SettlementReviewRateMapper reviewRateMapper,
            SettlementCandidateMapper candidateMapper,
            MerchantSettlementProfileMapper profileMapper,
            SettlementBatchCreationService batchCreationService,
            SettlementBatchMapper batchMapper,
            SettlementBatchCandidateMapper batchCandidateMapper,
            SettlementBatchRateMapper batchRateMapper,
            SettlementClearingFactService factService,
            SettlementResultCalculationService calculationService,
            SettlementReviewFingerprintService fingerprintService) {
        this(taskMapper, orderMapper, segmentMapper, reviewCandidateMapper, reviewRateMapper,
                candidateMapper, profileMapper, batchCreationService, batchMapper,
                batchCandidateMapper, batchRateMapper, factService, calculationService,
                fingerprintService, Clock.systemUTC());
    }

    SettlementReviewDecisionTransactionService(
            SettlementReviewDecisionTaskMapper taskMapper,
            SettlementReviewOrderMapper orderMapper,
            SettlementReviewSegmentMapper segmentMapper,
            SettlementReviewCandidateMapper reviewCandidateMapper,
            SettlementReviewRateMapper reviewRateMapper,
            SettlementCandidateMapper candidateMapper,
            MerchantSettlementProfileMapper profileMapper,
            SettlementBatchCreationService batchCreationService,
            SettlementBatchMapper batchMapper,
            SettlementBatchCandidateMapper batchCandidateMapper,
            SettlementBatchRateMapper batchRateMapper,
            SettlementClearingFactService factService,
            SettlementResultCalculationService calculationService,
            SettlementReviewFingerprintService fingerprintService,
            Clock clock) {
        this.taskMapper = taskMapper;
        this.orderMapper = orderMapper;
        this.segmentMapper = segmentMapper;
        this.reviewCandidateMapper = reviewCandidateMapper;
        this.reviewRateMapper = reviewRateMapper;
        this.candidateMapper = candidateMapper;
        this.profileMapper = profileMapper;
        this.batchCreationService = batchCreationService;
        this.batchMapper = batchMapper;
        this.batchCandidateMapper = batchCandidateMapper;
        this.batchRateMapper = batchRateMapper;
        this.factService = factService;
        this.calculationService = calculationService;
        this.fingerprintService = fingerprintService;
        this.clock = Objects.requireNonNull(clock, "settlement review decision clock is required");
    }

    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public TaskResult submit(String reviewOrderNo, SettlementReviewDecisionCommand command) {
        String orderNo = requireReviewOrderNo(reviewOrderNo);
        Objects.requireNonNull(command, "settlement review decision command is required");
        SettlementReviewDecisionTaskDO replay = taskMapper.selectByRequestKeyForUpdate(command.requestKey());
        if (replay != null) {
            verifyReplay(replay, orderNo, command);
            return result(replay);
        }
        SettlementReviewOrderDO order = orderMapper.selectByReviewOrderNoForUpdate(orderNo);
        requirePendingAsyncOrder(order, command);
        validateMakerChecker(order, command);
        int segmentCount = segmentMapper.countByOrderNo(orderNo);
        if (segmentCount < 1 || segmentMapper.countByOrderNoAndStatus(orderNo, "LOCKED") != segmentCount) {
            throw failure("SETTLEMENT_REVIEW_DECISION_SEGMENTS_NOT_READY", false,
                    "settlement review segments are not ready for decision");
        }
        LocalDateTime now = nowUtc();
        SettlementReviewDecisionTaskDO task = newTask(order, command, segmentCount, now);
        taskMapper.insertIdempotent(task);
        SettlementReviewDecisionTaskDO stored = taskMapper.selectByRequestKeyForUpdate(command.requestKey());
        verifyStored(stored, task);
        return result(stored);
    }

    @DS(DataSourceName.TRANSACTION)
    public TaskResult get(String taskNo) {
        return result(requireTask(taskMapper.selectByTaskNo(requireTaskNo(taskNo))));
    }

    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public Optional<SettlementReviewDecisionTaskDO> claimNext(String owner,
                                                              LocalDateTime now,
                                                              LocalDateTime deadline) {
        if (owner == null || owner.isBlank() || now == null || deadline == null || !deadline.isAfter(now)) {
            throw new IllegalArgumentException("settlement review decision lease is invalid");
        }
        SettlementReviewDecisionTaskDO task = taskMapper.selectNextDueForUpdate(now);
        if (task == null) {
            return Optional.empty();
        }
        if (task.getVersion() == null || taskMapper.markProcessing(task.getTaskNo(), task.getVersion(),
                owner.trim(), deadline, now) != 1) {
            throw failure("SETTLEMENT_REVIEW_DECISION_LEASE_CAS_FAILED", true,
                    "settlement review decision lease CAS failed");
        }
        task.setTaskStatus("PROCESSING");
        task.setProcessingOwner(owner.trim());
        task.setProcessingDeadline(deadline);
        task.setStartedTime(task.getStartedTime() == null ? now : task.getStartedTime());
        task.setVersion(task.getVersion() + 1);
        return Optional.of(task);
    }

    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public void processClaimed(String taskNo, String owner, LocalDateTime now) {
        SettlementReviewDecisionTaskDO task = requireClaimed(taskNo, owner, now);
        SettlementReviewOrderDO order = orderMapper.selectByReviewOrderNoForUpdate(task.getReviewOrderNo());
        requirePendingAsyncOrder(order, command(task));
        SettlementReviewSegmentDO segment = segmentMapper.selectNextLockedForUpdate(order.getReviewOrderNo());
        if (segment == null) {
            finalizeDecision(task, order, owner.trim(), now);
            return;
        }
        String batchNo = switch (task.getDecisionAction()) {
            case "APPROVE" -> approveSegment(task, order, segment, now);
            case "REJECT", "CANCEL" -> {
                releaseSegment(order, segment, now);
                yield null;
            }
            default -> throw failure("SETTLEMENT_REVIEW_DECISION_ACTION_INVALID", false,
                    "settlement review decision action is invalid");
        };
        int batchDelta = batchNo == null ? 0 : 1;
        if (taskMapper.advance(task.getTaskNo(), task.getVersion(), owner.trim(), batchDelta,
                batchNo, now) != 1) {
            throw failure("SETTLEMENT_REVIEW_DECISION_PROGRESS_CAS_FAILED", true,
                    "settlement review decision progress CAS failed");
        }
    }

    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(String taskNo,
                              RuntimeException exception,
                              Duration maximumDuration,
                              LocalDateTime now) {
        SettlementReviewDecisionTaskDO task = requireTask(
                taskMapper.selectByTaskNoForUpdate(requireTaskNo(taskNo)));
        if (!"PROCESSING".equals(task.getTaskStatus())) {
            return;
        }
        String code = exception instanceof SettlementReviewDecisionProcessingException processing
                ? processing.getFailureCode() : "SETTLEMENT_REVIEW_DECISION_UNEXPECTED_FAILURE";
        boolean retryable = exception instanceof SettlementReviewDecisionProcessingException processing
                && processing.isRetryable();
        LocalDateTime started = task.getStartedTime() == null ? task.getCreateTime() : task.getStartedTime();
        boolean timedOut = started == null || !now.isBefore(started.plus(maximumDuration));
        String message = sanitize(exception.getMessage());
        if (!retryable || timedOut) {
            if (taskMapper.markFailed(task.getTaskNo(), task.getVersion(), code, message, now) != 1) {
                throw failure("SETTLEMENT_REVIEW_DECISION_FAILURE_CAS_FAILED", true,
                        "settlement review decision failure state CAS failed");
            }
            return;
        }
        int retry = value(task.getRetryCount()) + 1;
        long delaySeconds = Math.min(60L, 1L << Math.min(retry, 6));
        if (taskMapper.reschedule(task.getTaskNo(), task.getVersion(), code, message,
                now.plusSeconds(delaySeconds), now) != 1) {
            throw failure("SETTLEMENT_REVIEW_DECISION_RETRY_CAS_FAILED", true,
                    "settlement review decision retry CAS failed");
        }
    }

    private String approveSegment(SettlementReviewDecisionTaskDO task,
                                  SettlementReviewOrderDO order,
                                  SettlementReviewSegmentDO segment,
                                  LocalDateTime now) {
        MerchantSettlementProfileDO profile = profileMapper.selectReviewEligibleProfileForUpdate(
                order.getSettlementProfileId(), order.getBusinessDate());
        validateProfile(order, profile);
        List<SettlementReviewCandidateDO> relations = safe(reviewCandidateMapper.selectRangeForUpdate(
                order.getReviewOrderNo(), segment.getFirstCandidateId(), segment.getLastCandidateId()));
        List<SettlementCandidateDO> candidates = lockAndValidateCandidates(order, segment, relations);
        SettlementReviewOrderDO chunkOrder = chunkOrder(order, segment.getCandidateCount());
        SettlementBatchFacts facts = factService.loadReviewSelection(chunkOrder, candidates);
        if (!Objects.equals(segment.getSourceFingerprint(), fingerprintService.source(facts))) {
            throw failure("SETTLEMENT_REVIEW_DECISION_SOURCE_CHANGED", false,
                    "settlement review segment source facts changed after submission");
        }
        List<SettlementReviewRateDO> rates = safe(reviewRateMapper.selectByOrderNo(order.getReviewOrderNo()));
        if (rates.isEmpty() || !Objects.equals(order.getRateFingerprint(), fingerprintService.rates(rates))) {
            throw failure("SETTLEMENT_REVIEW_DECISION_RATE_CHANGED", false,
                    "settlement review locked rate matrix changed after submission");
        }
        SettlementCalculationPreview preview = calculationService.preview(previewBatch(chunkOrder), facts,
                toLockedRates(rates), now);
        BigDecimal signed = "DEBIT".equals(preview.netDirection())
                ? preview.netAmount().negate() : preview.netAmount();
        if (!Objects.equals(segment.getResultFingerprint(), fingerprintService.result(preview))
                || segment.getNetSignedAmount() == null
                || segment.getNetSignedAmount().compareTo(signed) != 0) {
            throw failure("SETTLEMENT_REVIEW_DECISION_RESULT_CHANGED", false,
                    "settlement review segment financial result changed after submission");
        }

        SettlementBatchCreateResult created = batchCreationService.create(new SettlementBatchCreateCommand(
                "review:" + order.getReviewOrderNo() + ":segment:" + segment.getSequenceNo(),
                order.getBusinessDate(), order.getBusinessTimeZone(), order.getMerchantId(),
                order.getSettlementProfileId(), order.getSettlementAccountId(), order.getTargetCurrency(),
                order.getTargetCurrencyExponent(),
                com.scott.payment.settlement.domain.model.SettlementBatchType.valueOf(order.getReviewType()),
                null, order.getCutoffBeginTime(), order.getCutoffEndTime()));
        SettlementBatchDO batch = batchMapper.selectByBatchNoForUpdate(created.settlementBatchNo());
        bindBatchAudit(batch, task, order, segment, now);
        if (batchMapper.bindAsyncApprovedReview(batch, batch.getVersion()) != 1) {
            throw failure("SETTLEMENT_REVIEW_DECISION_BATCH_BIND_CAS_FAILED", true,
                    "settlement review segment batch audit CAS failed");
        }
        List<SettlementBatchRateDO> batchRates = batchRates(batch.getSettlementBatchNo(), rates, now);
        if (batchRateMapper.insertBatchIdempotent(batchRates) != batchRates.size()
                || safe(batchRateMapper.selectByBatchNo(batch.getSettlementBatchNo())).size()
                != batchRates.size()) {
            throw failure("SETTLEMENT_REVIEW_DECISION_RATE_COPY_INCOMPLETE", false,
                    "settlement review segment rate copy is incomplete");
        }
        if (candidateMapper.consumeReviewLock(candidates, order.getReviewOrderNo(),
                batch.getSettlementBatchNo(), now) != candidates.size()) {
            throw failure("SETTLEMENT_REVIEW_DECISION_CANDIDATE_CONSUME_FAILED", true,
                    "settlement review segment candidate consume CAS failed");
        }
        List<SettlementBatchCandidateDO> batchRelations = batchRelations(
                batch.getSettlementBatchNo(), candidates, now);
        if (batchCandidateMapper.insertBatchIdempotent(batchRelations) != batchRelations.size()
                || reviewCandidateMapper.markRangeConsumed(order.getReviewOrderNo(),
                segment.getFirstCandidateId(), segment.getLastCandidateId(), now) != relations.size()
                || segmentMapper.markConsumed(segment.getSegmentNo(), segment.getVersion(),
                batch.getSettlementBatchNo(), now) != 1) {
            throw failure("SETTLEMENT_REVIEW_DECISION_SEGMENT_CONSUME_INCOMPLETE", true,
                    "settlement review segment consume is incomplete");
        }
        return batch.getSettlementBatchNo();
    }

    private void releaseSegment(SettlementReviewOrderDO order,
                                SettlementReviewSegmentDO segment,
                                LocalDateTime now) {
        List<SettlementReviewCandidateDO> relations = safe(reviewCandidateMapper.selectRangeForUpdate(
                order.getReviewOrderNo(), segment.getFirstCandidateId(), segment.getLastCandidateId()));
        List<SettlementCandidateDO> candidates = lockAndValidateCandidates(order, segment, relations);
        if (candidateMapper.releaseReviewLock(candidates, order.getReviewOrderNo(), now) != candidates.size()
                || reviewCandidateMapper.markRangeReleased(order.getReviewOrderNo(),
                segment.getFirstCandidateId(), segment.getLastCandidateId(), now) != relations.size()
                || segmentMapper.markReleased(segment.getSegmentNo(), segment.getVersion(), now) != 1) {
            throw failure("SETTLEMENT_REVIEW_DECISION_SEGMENT_RELEASE_INCOMPLETE", true,
                    "settlement review segment release is incomplete");
        }
    }

    private void finalizeDecision(SettlementReviewDecisionTaskDO task,
                                  SettlementReviewOrderDO order,
                                  String owner,
                                  LocalDateTime now) {
        int total = segmentMapper.countByOrderNo(order.getReviewOrderNo());
        if (total != value(task.getTotalSegmentCount())
                || value(task.getProcessedSegmentCount()) != total) {
            throw failure("SETTLEMENT_REVIEW_DECISION_PROGRESS_INCOMPLETE", true,
                    "settlement review decision progress is incomplete");
        }
        applyDecision(order, task);
        if ("APPROVE".equals(task.getDecisionAction())) {
            if (task.getFirstSettlementBatchNo() == null
                    || value(task.getResultBatchCount()) != total
                    || segmentMapper.countByOrderNoAndStatus(order.getReviewOrderNo(), "CONSUMED") != total) {
                throw failure("SETTLEMENT_REVIEW_DECISION_BATCH_SET_INCOMPLETE", true,
                        "settlement review approved batch set is incomplete");
            }
            order.setSettlementBatchNo(task.getFirstSettlementBatchNo());
            if (orderMapper.approve(order, task.getExpectedReviewVersion()) != 1
                    || batchMapper.activateAsyncApprovedReviewBatches(order.getReviewOrderNo(), now) != total) {
                throw failure("SETTLEMENT_REVIEW_DECISION_FINAL_APPROVAL_CAS_FAILED", true,
                        "settlement review final approval CAS failed");
            }
        } else {
            String terminalStatus = "REJECT".equals(task.getDecisionAction())
                    ? SettlementReviewStatus.REJECTED.name() : SettlementReviewStatus.CANCELLED.name();
            if (segmentMapper.countByOrderNoAndStatus(order.getReviewOrderNo(), "RELEASED") != total
                    || orderMapper.terminate(order, terminalStatus, task.getExpectedReviewVersion()) != 1) {
                throw failure("SETTLEMENT_REVIEW_DECISION_FINAL_TERMINATION_CAS_FAILED", true,
                        "settlement review final termination CAS failed");
            }
        }
        if (taskMapper.markCompleted(task.getTaskNo(), task.getVersion(), owner, now) != 1) {
            throw failure("SETTLEMENT_REVIEW_DECISION_COMPLETION_CAS_FAILED", true,
                    "settlement review decision completion CAS failed");
        }
    }

    private List<SettlementCandidateDO> lockAndValidateCandidates(
            SettlementReviewOrderDO order,
            SettlementReviewSegmentDO segment,
            List<SettlementReviewCandidateDO> relations) {
        if (relations.size() != segment.getCandidateCount()
                || relations.stream().anyMatch(row -> !"LOCKED".equals(row.getRelationStatus()))) {
            throw failure("SETTLEMENT_REVIEW_DECISION_RELATIONS_INCOMPLETE", false,
                    "settlement review segment relations are incomplete");
        }
        List<Long> ids = relations.stream().map(SettlementReviewCandidateDO::getCandidateId).sorted().toList();
        List<SettlementCandidateDO> candidates = ids.isEmpty()
                ? List.of() : safe(candidateMapper.selectByIdsForUpdate(ids));
        Map<Long, SettlementReviewCandidateDO> relationByCandidate = new HashMap<>();
        relations.forEach(row -> relationByCandidate.put(row.getCandidateId(), row));
        for (SettlementCandidateDO candidate : candidates) {
            SettlementReviewCandidateDO relation = relationByCandidate.get(candidate.getId());
            if (relation == null || !SettlementCandidateStatus.REVIEW_LOCKED.name()
                    .equals(candidate.getCandidateStatus()) || candidate.getSettlementBatchNo() != null
                    || !Objects.equals(candidate.getReviewOrderNo(), order.getReviewOrderNo())
                    || !Objects.equals(candidate.getVersion(), relation.getLockedCandidateVersion())) {
                throw failure("SETTLEMENT_REVIEW_DECISION_OWNERSHIP_CHANGED", false,
                        "settlement review no longer exclusively owns the segment candidates");
            }
        }
        if (candidates.size() != relations.size()) {
            throw failure("SETTLEMENT_REVIEW_DECISION_CANDIDATES_INCOMPLETE", false,
                    "settlement review segment candidate set is incomplete");
        }
        return candidates;
    }

    private SettlementReviewOrderDO chunkOrder(SettlementReviewOrderDO order, int candidateCount) {
        SettlementReviewOrderDO row = new SettlementReviewOrderDO();
        row.setReviewOrderNo(order.getReviewOrderNo());
        row.setReviewType(order.getReviewType());
        row.setMerchantId(order.getMerchantId());
        row.setSettlementProfileId(order.getSettlementProfileId());
        row.setSettlementAccountId(order.getSettlementAccountId());
        row.setTargetCurrency(order.getTargetCurrency());
        row.setTargetCurrencyExponent(order.getTargetCurrencyExponent());
        row.setBusinessDate(order.getBusinessDate());
        row.setBusinessTimeZone(order.getBusinessTimeZone());
        row.setCutoffBeginTime(order.getCutoffBeginTime());
        row.setCutoffEndTime(order.getCutoffEndTime());
        row.setCandidateCount(candidateCount);
        return row;
    }

    private SettlementBatchDO previewBatch(SettlementReviewOrderDO order) {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setSettlementBatchNo(order.getReviewOrderNo());
        row.setMerchantId(order.getMerchantId());
        row.setSettlementProfileId(order.getSettlementProfileId());
        row.setSettlementAccountId(order.getSettlementAccountId());
        row.setTargetCurrency(order.getTargetCurrency());
        row.setTargetCurrencyExponent(order.getTargetCurrencyExponent());
        row.setBatchType(order.getReviewType());
        row.setBusinessDate(order.getBusinessDate());
        row.setCandidateCount(order.getCandidateCount());
        return row;
    }

    private SettlementLockedRateMatrix toLockedRates(List<SettlementReviewRateDO> rows) {
        List<LockedRate> rates = new ArrayList<>(rows.size());
        Map<String, Long> ids = new LinkedHashMap<>();
        for (SettlementReviewRateDO row : rows.stream()
                .sorted(Comparator.comparing(SettlementReviewRateDO::getSourceCurrency)).toList()) {
            if (row.getId() == null || row.getId() <= 0) {
                throw failure("SETTLEMENT_REVIEW_DECISION_RATE_ID_MISSING", false,
                        "settlement review rate row identity is missing");
            }
            rates.add(new LockedRate(new CurrencyPair(row.getSourceCurrency(), row.getTargetCurrency()),
                    row.getDirectRate(), row.getSourceCurrencyExponent(), row.getTargetCurrencyExponent(),
                    row.getRateSource(), row.getQuoteId(),
                    QuoteDirection.valueOf(row.getSourceQuoteDirection()), row.getEffectiveTime()));
            ids.put(row.getSourceCurrency(), row.getId());
        }
        return new SettlementLockedRateMatrix(RateMatrix.of(rates), ids);
    }

    private void bindBatchAudit(SettlementBatchDO batch,
                                SettlementReviewDecisionTaskDO task,
                                SettlementReviewOrderDO order,
                                SettlementReviewSegmentDO segment,
                                LocalDateTime now) {
        batch.setReviewOrderNo(order.getReviewOrderNo());
        batch.setCandidateCount(segment.getCandidateCount());
        batch.setProjectableCandidateCount(segment.getProjectableCandidateCount());
        batch.setResultFingerprint(segment.getResultFingerprint());
        batch.setMakerAccountId(order.getSubmittedByAccountId());
        batch.setMakerAccountName(order.getSubmittedByAccountName());
        batch.setMakerRoleSnapshot(order.getSubmittedRoleSnapshot());
        batch.setMakerClientIp(order.getSubmitClientIp());
        batch.setMakerUserAgent(order.getSubmitUserAgent());
        batch.setMakerReason(order.getSubmitReason());
        batch.setMakerTime(order.getSubmittedTime());
        batch.setCheckerAccountId(task.getOperatorAccountId());
        batch.setCheckerAccountName(task.getOperatorAccountName());
        batch.setCheckerRoleSnapshot(task.getOperatorRoleSnapshot());
        batch.setCheckerClientIp(task.getClientIp());
        batch.setCheckerUserAgent(task.getUserAgent());
        batch.setCheckerComment(task.getDecisionComment());
        batch.setCheckerTime(task.getOperationTime());
        batch.setUpdateTime(now);
    }

    private List<SettlementBatchRateDO> batchRates(String batchNo,
                                                  List<SettlementReviewRateDO> rates,
                                                  LocalDateTime now) {
        return rates.stream().map(rate -> {
            SettlementBatchRateDO row = new SettlementBatchRateDO();
            row.setSettlementBatchNo(batchNo);
            row.setReviewRateId(rate.getId());
            row.setSourceCurrency(rate.getSourceCurrency());
            row.setTargetCurrency(rate.getTargetCurrency());
            row.setRateType("SETTLEMENT");
            row.setDirectRate(rate.getDirectRate());
            row.setSourceCurrencyExponent(rate.getSourceCurrencyExponent());
            row.setTargetCurrencyExponent(rate.getTargetCurrencyExponent());
            row.setRateSource(rate.getRateSource());
            row.setQuoteId(rate.getQuoteId());
            row.setSourceQuoteDirection(rate.getSourceQuoteDirection());
            row.setEffectiveTime(rate.getEffectiveTime());
            row.setLockedTime(now);
            row.setLockedBy("settlement-review:" + rate.getReviewOrderNo());
            row.setRateStatus("LOCKED");
            row.setCreateTime(now);
            return row;
        }).toList();
    }

    private List<SettlementBatchCandidateDO> batchRelations(
            String batchNo, List<SettlementCandidateDO> candidates, LocalDateTime now) {
        return candidates.stream().map(candidate -> {
            SettlementBatchCandidateDO row = new SettlementBatchCandidateDO();
            row.setBatchCandidateNo(stableId("BC", batchNo + "|" + candidate.getId()));
            row.setSettlementBatchNo(batchNo);
            row.setCandidateId(candidate.getId());
            row.setSourceType(candidate.getSourceType());
            row.setSourceBusinessId(candidate.getSourceBusinessId());
            row.setSourceRevision(candidate.getSourceRevision());
            row.setRelationStatus("CLAIMED");
            row.setClaimedTime(now);
            row.setVersion(0L);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            return row;
        }).toList();
    }

    private void applyDecision(SettlementReviewOrderDO order,
                               SettlementReviewDecisionTaskDO task) {
        order.setDecidedByAccountId(task.getOperatorAccountId());
        order.setDecidedByAccountName(task.getOperatorAccountName());
        order.setDecidedRoleSnapshot(task.getOperatorRoleSnapshot());
        order.setDecisionClientIp(task.getClientIp());
        order.setDecisionUserAgent(task.getUserAgent());
        order.setDecisionAction(task.getDecisionAction());
        order.setDecisionRequestKey(task.getRequestKey());
        order.setReviewComment(task.getDecisionComment());
        order.setDecisionTime(task.getOperationTime());
    }

    private void validateProfile(SettlementReviewOrderDO order, MerchantSettlementProfileDO profile) {
        if (profile == null || !Objects.equals(profile.getId(), order.getSettlementProfileId())
                || !Objects.equals(profile.getMerchantId(), order.getMerchantId())
                || !Objects.equals(profile.getSettlementAccountId(), order.getSettlementAccountId())
                || !Objects.equals(profile.getTargetCurrency(), order.getTargetCurrency())
                || !Objects.equals(profile.getTargetCurrencyExponent(), order.getTargetCurrencyExponent())) {
            throw failure("SETTLEMENT_REVIEW_DECISION_PROFILE_UNAVAILABLE", false,
                    "settlement review profile or account is unavailable");
        }
    }

    private void requirePendingAsyncOrder(SettlementReviewOrderDO order,
                                          SettlementReviewDecisionCommand command) {
        if (order == null || !"MANUAL_ASYNC".equals(order.getCreateMode())
                || !SettlementReviewStatus.PENDING_APPROVAL.name().equals(order.getReviewStatus())
                || order.getVersion() == null || order.getVersion() != command.expectedVersion()) {
            throw failure("SETTLEMENT_REVIEW_DECISION_STATE_STALE", false,
                    "settlement review state or expected version is stale");
        }
    }

    private void validateMakerChecker(SettlementReviewOrderDO order,
                                      SettlementReviewDecisionCommand command) {
        long operatorId = command.operator().accountId();
        if ("CANCEL".equals(command.decision())) {
            if (!Objects.equals(order.getSubmittedByAccountId(), operatorId)) {
                throw failure("SETTLEMENT_REVIEW_DECISION_CANCEL_FORBIDDEN", false,
                        "only the settlement review maker may cancel it");
            }
        } else if (Objects.equals(order.getSubmittedByAccountId(), operatorId)) {
            throw failure("SETTLEMENT_REVIEW_DECISION_SELF_REVIEW_FORBIDDEN", false,
                    "settlement review maker and checker accounts must differ");
        }
    }

    private SettlementReviewDecisionTaskDO newTask(SettlementReviewOrderDO order,
                                                   SettlementReviewDecisionCommand command,
                                                   int segmentCount,
                                                   LocalDateTime now) {
        SettlementOperatorSnapshot operator = command.operator();
        SettlementReviewDecisionTaskDO row = new SettlementReviewDecisionTaskDO();
        row.setTaskNo(stableId("DT", command.requestKey()));
        row.setRequestKey(command.requestKey());
        row.setReviewOrderNo(order.getReviewOrderNo());
        row.setExpectedReviewVersion(command.expectedVersion());
        row.setDecisionAction(command.decision());
        row.setDecisionComment(command.comment());
        row.setOperatorAccountId(operator.accountId());
        row.setOperatorAccountName(operator.accountName());
        row.setOperatorRoleSnapshot(operator.roleSnapshot());
        row.setClientIp(operator.clientIp());
        row.setUserAgent(operator.userAgent());
        row.setOperationTime(operator.operationTime());
        row.setTotalSegmentCount(segmentCount);
        row.setProcessedSegmentCount(0);
        row.setResultBatchCount(0);
        row.setTaskStatus("QUEUED");
        row.setRetryCount(0);
        row.setNextRetryTime(now);
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private SettlementReviewDecisionCommand command(SettlementReviewDecisionTaskDO task) {
        return new SettlementReviewDecisionCommand(task.getRequestKey(), task.getExpectedReviewVersion(),
                task.getDecisionAction(), task.getDecisionComment(), new SettlementOperatorSnapshot(
                task.getOperatorAccountId(), task.getOperatorAccountName(), task.getOperatorRoleSnapshot(),
                task.getClientIp(), task.getUserAgent(), task.getOperationTime()));
    }

    private TaskResult result(SettlementReviewDecisionTaskDO task) {
        int total = value(task.getTotalSegmentCount());
        int processed = value(task.getProcessedSegmentCount());
        int progress = total == 0 ? 0 : Math.min(100, Math.toIntExact((long) processed * 100L / total));
        return new TaskResult(task.getTaskNo(), task.getReviewOrderNo(), task.getDecisionAction(),
                task.getTaskStatus(), total, processed, value(task.getResultBatchCount()), progress,
                task.getFirstSettlementBatchNo(), value(task.getRetryCount()), task.getLastFailureCode(),
                task.getLastFailureMessage(), task.getStartedTime(), task.getCompletedTime(),
                task.getVersion() == null ? 0L : task.getVersion());
    }

    private void verifyReplay(SettlementReviewDecisionTaskDO task,
                              String reviewOrderNo,
                              SettlementReviewDecisionCommand command) {
        if (!Objects.equals(task.getReviewOrderNo(), reviewOrderNo)
                || !Objects.equals(task.getExpectedReviewVersion(), command.expectedVersion())
                || !Objects.equals(task.getDecisionAction(), command.decision())
                || !Objects.equals(task.getDecisionComment(), command.comment())
                || !Objects.equals(task.getOperatorAccountId(), command.operator().accountId())) {
            throw failure("SETTLEMENT_REVIEW_DECISION_REQUEST_CONFLICT", false,
                    "settlement review decision request key contains mismatched identity");
        }
    }

    private void verifyStored(SettlementReviewDecisionTaskDO actual,
                              SettlementReviewDecisionTaskDO expected) {
        if (actual == null || !Objects.equals(actual.getTaskNo(), expected.getTaskNo())
                || !Objects.equals(actual.getRequestKey(), expected.getRequestKey())
                || !Objects.equals(actual.getReviewOrderNo(), expected.getReviewOrderNo())
                || !Objects.equals(actual.getDecisionAction(), expected.getDecisionAction())
                || !Objects.equals(actual.getOperatorAccountId(), expected.getOperatorAccountId())) {
            throw failure("SETTLEMENT_REVIEW_DECISION_IDEMPOTENCY_CONFLICT", false,
                    "settlement review decision task is inconsistent");
        }
    }

    private SettlementReviewDecisionTaskDO requireClaimed(String taskNo,
                                                          String owner,
                                                          LocalDateTime now) {
        SettlementReviewDecisionTaskDO task = requireTask(
                taskMapper.selectByTaskNoForUpdate(requireTaskNo(taskNo)));
        if (owner == null || owner.isBlank() || !"PROCESSING".equals(task.getTaskStatus())
                || !Objects.equals(task.getProcessingOwner(), owner.trim())
                || task.getProcessingDeadline() == null || !task.getProcessingDeadline().isAfter(now)
                || task.getVersion() == null) {
            throw failure("SETTLEMENT_REVIEW_DECISION_LEASE_LOST", true,
                    "settlement review decision task lease is no longer valid");
        }
        return task;
    }

    private SettlementReviewDecisionTaskDO requireTask(SettlementReviewDecisionTaskDO task) {
        if (task == null) {
            throw failure("SETTLEMENT_REVIEW_DECISION_TASK_NOT_FOUND", false,
                    "settlement review decision task does not exist");
        }
        return task;
    }

    private String requireReviewOrderNo(String value) {
        if (value == null || !value.trim().matches("SO\\d{8}-\\d{8}")) {
            throw new IllegalArgumentException("settlement review order number is invalid");
        }
        return value.trim();
    }

    private String requireTaskNo(String value) {
        if (value == null || !value.trim().matches("DT[0-9a-f]{32}")) {
            throw new IllegalArgumentException("settlement review decision task number is invalid");
        }
        return value.trim();
    }

    private String stableId(String prefix, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "settlement review decision processing failed";
        }
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private SettlementReviewDecisionProcessingException failure(String code,
                                                                boolean retryable,
                                                                String message) {
        return new SettlementReviewDecisionProcessingException(code, retryable, message);
    }
}
