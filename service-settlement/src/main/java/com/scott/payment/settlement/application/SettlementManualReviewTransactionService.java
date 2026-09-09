package com.scott.payment.settlement.application;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.settlement.domain.model.SettlementCandidateStatus;
import com.scott.payment.settlement.domain.model.SettlementReviewStatus;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementCalculationPreview;
import com.scott.payment.settlement.dto.SettlementCurrency;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.PreviewCommand;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.PreviewLine;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.StartCommand;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.TaskResult;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementManualReviewCurrencyDO;
import com.scott.payment.settlement.entity.SettlementManualReviewPreviewLineDO;
import com.scott.payment.settlement.entity.SettlementManualReviewProfileDO;
import com.scott.payment.settlement.entity.SettlementManualReviewTaskDO;
import com.scott.payment.settlement.entity.SettlementResultSummaryDO;
import com.scott.payment.settlement.entity.SettlementReviewCandidateDO;
import com.scott.payment.settlement.entity.SettlementReviewDailySequenceDO;
import com.scott.payment.settlement.entity.SettlementReviewOrderDO;
import com.scott.payment.settlement.entity.SettlementReviewRateDO;
import com.scott.payment.settlement.entity.SettlementReviewSegmentDO;
import com.scott.payment.settlement.entity.SettlementReviewSummaryDO;
import com.scott.payment.settlement.exception.SettlementManualReviewProcessingException;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementManualReviewTaskMapper;
import com.scott.payment.settlement.mapper.SettlementReviewCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementReviewDailySequenceMapper;
import com.scott.payment.settlement.mapper.SettlementReviewOrderMapper;
import com.scott.payment.settlement.mapper.SettlementReviewRateMapper;
import com.scott.payment.settlement.mapper.SettlementReviewSegmentMapper;
import com.scott.payment.settlement.mapper.SettlementReviewSummaryMapper;
import com.scott.payment.settlement.service.SettlementClearingFactService;
import com.scott.payment.settlement.service.SettlementRateResolutionService;
import com.scott.payment.settlement.service.SettlementResultCalculationService;
import com.scott.payment.settlement.support.SettlementReviewFingerprintService;
import com.scott.payment.settlement.support.SettlementReviewNumberFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 手动交易或保证金结算预览、分段冻结、汇总和失败补偿的本地事务边界。 */
@Service
public class SettlementManualReviewTransactionService {

    private static final int MAX_DAILY_SEQUENCE = 99_999_999;
    private static final Set<String> REVIEW_TYPES = Set.of("REGULAR", "RESERVE_RELEASE");

    private final SettlementManualReviewTaskMapper taskMapper;
    private final SettlementReviewDailySequenceMapper sequenceMapper;
    private final SettlementReviewOrderMapper orderMapper;
    private final SettlementReviewCandidateMapper reviewCandidateMapper;
    private final SettlementReviewRateMapper reviewRateMapper;
    private final SettlementReviewSummaryMapper reviewSummaryMapper;
    private final SettlementReviewSegmentMapper segmentMapper;
    private final SettlementCandidateMapper candidateMapper;
    private final SettlementClearingFactService factService;
    private final SettlementRateResolutionService rateResolutionService;
    private final SettlementResultCalculationService calculationService;
    private final SettlementReviewFingerprintService fingerprintService;
    private final SettlementReviewNumberFormatter numberFormatter;
    private final Clock clock;

    @Autowired
    public SettlementManualReviewTransactionService(
            SettlementManualReviewTaskMapper taskMapper,
            SettlementReviewDailySequenceMapper sequenceMapper,
            SettlementReviewOrderMapper orderMapper,
            SettlementReviewCandidateMapper reviewCandidateMapper,
            SettlementReviewRateMapper reviewRateMapper,
            SettlementReviewSummaryMapper reviewSummaryMapper,
            SettlementReviewSegmentMapper segmentMapper,
            SettlementCandidateMapper candidateMapper,
            SettlementClearingFactService factService,
            SettlementRateResolutionService rateResolutionService,
            SettlementResultCalculationService calculationService,
            SettlementReviewFingerprintService fingerprintService,
            SettlementReviewNumberFormatter numberFormatter) {
        this(taskMapper, sequenceMapper, orderMapper, reviewCandidateMapper, reviewRateMapper,
                reviewSummaryMapper, segmentMapper, candidateMapper, factService,
                rateResolutionService, calculationService, fingerprintService, numberFormatter,
                Clock.systemUTC());
    }

    SettlementManualReviewTransactionService(
            SettlementManualReviewTaskMapper taskMapper,
            SettlementReviewDailySequenceMapper sequenceMapper,
            SettlementReviewOrderMapper orderMapper,
            SettlementReviewCandidateMapper reviewCandidateMapper,
            SettlementReviewRateMapper reviewRateMapper,
            SettlementReviewSummaryMapper reviewSummaryMapper,
            SettlementReviewSegmentMapper segmentMapper,
            SettlementCandidateMapper candidateMapper,
            SettlementClearingFactService factService,
            SettlementRateResolutionService rateResolutionService,
            SettlementResultCalculationService calculationService,
            SettlementReviewFingerprintService fingerprintService,
            SettlementReviewNumberFormatter numberFormatter,
            Clock clock) {
        this.taskMapper = taskMapper;
        this.sequenceMapper = sequenceMapper;
        this.orderMapper = orderMapper;
        this.reviewCandidateMapper = reviewCandidateMapper;
        this.reviewRateMapper = reviewRateMapper;
        this.reviewSummaryMapper = reviewSummaryMapper;
        this.segmentMapper = segmentMapper;
        this.candidateMapper = candidateMapper;
        this.factService = factService;
        this.rateResolutionService = rateResolutionService;
        this.calculationService = calculationService;
        this.fingerprintService = fingerprintService;
        this.numberFormatter = numberFormatter;
        this.clock = Objects.requireNonNull(clock, "settlement manual review clock is required");
    }

    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public TaskResult preview(PreviewCommand command) {
        Objects.requireNonNull(command, "manual settlement preview command is required");
        requireReviewType(command.reviewType());
        SettlementManualReviewTaskDO replay = taskMapper.selectByRequestKeyForUpdate(command.requestKey());
        if (replay != null) {
            verifyPreviewReplay(replay, command);
            return result(replay);
        }
        SettlementManualReviewProfileDO profile = taskMapper.selectProfile(
                command.settlementProfileId(), command.merchantId());
        validateProfile(profile, command);
        if (taskMapper.countActiveByProfile(profile.getSettlementProfileId()) != 0) {
            throw failure("MANUAL_REVIEW_PROFILE_BUSY", false,
                    "the settlement profile already has an active manual settlement task");
        }

        Window window = maturedWindow(profile, clock.instant());
        Long snapshotMaxCandidateId = taskMapper.selectSnapshotMaxCandidateId(
                command.merchantId(), command.settlementProfileId(), profile.getTargetCurrency(),
                window.businessDate(), window.cutoffEndUtc(), command.reviewType(),
                normalize(command.paymentType()),
                normalize(command.paymentMethod()));
        long maxCandidateId = snapshotMaxCandidateId == null ? 0L : snapshotMaxCandidateId;
        if (maxCandidateId <= 0) {
            SettlementBatchDO blockingBatch = taskMapper.selectBlockingManualReviewBatch(
                    profile.getSettlementProfileId(), command.reviewType());
            if (blockingBatch != null) {
                throw failure("MANUAL_REVIEW_BLOCKED_BATCH", false,
                        "unresolved settlement batch blocks manual preview: batchNo="
                                + blockingBatch.getSettlementBatchNo()
                                + "; failureStage=" + Objects.toString(
                                blockingBatch.getLastFailureStage(), "-")
                                + "; failureCode=" + Objects.toString(
                                blockingBatch.getLastFailureCode(), "-"));
            }
            throw failure("MANUAL_REVIEW_CANDIDATE_EMPTY", false,
                    "no matured transaction settlement candidates match the selected scope");
        }
        List<SettlementManualReviewPreviewLineDO> previewRows = safe(taskMapper.selectPreviewLines(
                command.merchantId(), command.settlementProfileId(), profile.getTargetCurrency(),
                window.businessDate(), window.cutoffEndUtc(), maxCandidateId,
                command.reviewType(), normalize(command.paymentType()), normalize(command.paymentMethod())));
        List<PreviewLine> preview = previewRows.stream().map(this::previewLine).toList();
        long expected = preview.stream().mapToLong(PreviewLine::transactionCount).sum();
        if (expected <= 0 || expected > Integer.MAX_VALUE) {
            throw failure("MANUAL_REVIEW_CANDIDATE_COUNT_INVALID", false,
                    "manual settlement candidate count is invalid");
        }

        LocalDateTime now = nowUtc();
        SettlementManualReviewTaskDO task = newTask(command, profile, window, maxCandidateId,
                Math.toIntExact(expected), preview, nextReviewOrderNo(window.businessDate()), now);
        taskMapper.insertIdempotent(task);
        SettlementManualReviewTaskDO stored = taskMapper.selectByRequestKeyForUpdate(command.requestKey());
        verifyStoredTask(stored, task);
        return result(stored);
    }

    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public TaskResult start(String taskNo, StartCommand command, String expectedReviewType) {
        requireTaskNo(taskNo);
        requireReviewType(expectedReviewType);
        Objects.requireNonNull(command, "manual settlement start command is required");
        SettlementManualReviewTaskDO replay = taskMapper.selectBySubmitRequestKey(command.requestKey());
        if (replay != null) {
            if (!Objects.equals(replay.getTaskNo(), taskNo.trim())) {
                throw failure("MANUAL_REVIEW_START_KEY_CONFLICT", false,
                        "manual settlement start request key belongs to another task");
            }
            requireTaskReviewType(replay, expectedReviewType);
            return result(replay);
        }
        SettlementManualReviewTaskDO task = requireTask(taskMapper.selectByTaskNoForUpdate(taskNo.trim()));
        requireTaskReviewType(task, expectedReviewType);
        if (!"PREVIEWED".equals(task.getTaskStatus())
                || task.getVersion() == null || task.getVersion() != command.expectedVersion()) {
            throw failure("MANUAL_REVIEW_START_STATE_STALE", false,
                    "manual settlement preview state or version is stale");
        }
        LocalDateTime now = nowUtc();
        if (taskMapper.start(task.getTaskNo(), command.requestKey(), command.expectedVersion(), now) != 1) {
            throw failure("MANUAL_REVIEW_START_CAS_FAILED", true,
                    "manual settlement start state CAS failed");
        }
        task.setSubmitRequestKey(command.requestKey());
        task.setTaskStatus("QUEUED");
        task.setVersion(task.getVersion() + 1);
        task.setUpdateTime(now);
        return result(task);
    }

    @DS(DataSourceName.TRANSACTION)
    public TaskResult get(String taskNo, String expectedReviewType) {
        requireTaskNo(taskNo);
        requireReviewType(expectedReviewType);
        SettlementManualReviewTaskDO task = requireTask(taskMapper.selectByTaskNo(taskNo.trim()));
        requireTaskReviewType(task, expectedReviewType);
        return result(task);
    }

    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public Optional<SettlementManualReviewTaskDO> claimNext(String owner,
                                                            LocalDateTime now,
                                                            LocalDateTime deadline) {
        requireOwner(owner, now, deadline);
        SettlementManualReviewTaskDO task = taskMapper.selectNextDueForUpdate(now);
        if (task == null) {
            return Optional.empty();
        }
        if (task.getVersion() == null || taskMapper.markProcessing(task.getTaskNo(), task.getVersion(),
                owner.trim(), deadline, now) != 1) {
            throw failure("MANUAL_REVIEW_LEASE_CAS_FAILED", true,
                    "manual settlement processing lease CAS failed");
        }
        if (!"CANCELLING".equals(task.getTaskStatus())) {
            task.setTaskStatus("PROCESSING");
        }
        task.setProcessingOwner(owner.trim());
        task.setProcessingDeadline(deadline);
        task.setStartedTime(task.getStartedTime() == null ? now : task.getStartedTime());
        task.setVersion(task.getVersion() + 1);
        return Optional.of(task);
    }

    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public void processClaimed(String taskNo, String owner, int chunkSize, LocalDateTime now) {
        SettlementManualReviewTaskDO task = requireClaimedTask(taskNo, owner, now, "PROCESSING");
        List<SettlementReviewRateDO> rates = lockRates(task, now);
        List<SettlementCandidateDO> candidates = safe(taskMapper.selectNextCandidatesForUpdate(task, chunkSize));
        if (candidates.isEmpty()) {
            if (!Objects.equals(task.getLockedCandidateCount(), task.getExpectedCandidateCount())) {
                throw failure("MANUAL_REVIEW_FROZEN_SCOPE_CHANGED", false,
                        "one or more candidates left the frozen manual settlement scope");
            }
            finalizeTask(task, now);
            return;
        }

        if (candidateMapper.lockForReview(candidates, task.getReviewOrderNo(),
                task.getSettlementProfileId(), now) != candidates.size()) {
            throw failure("MANUAL_REVIEW_CANDIDATE_LOCK_CAS_FAILED", true,
                    "manual settlement candidate lock CAS affected an unexpected row count");
        }
        candidates.forEach(candidate -> {
            candidate.setCandidateStatus(SettlementCandidateStatus.REVIEW_LOCKED.name());
            candidate.setReviewOrderNo(task.getReviewOrderNo());
            candidate.setReviewLockedTime(now);
            candidate.setVersion(candidate.getVersion() + 1);
        });

        SettlementReviewOrderDO chunkOrder = chunkOrder(task, candidates.size());
        SettlementBatchFacts facts = factService.loadReviewSelection(chunkOrder, candidates);
        SettlementCalculationPreview preview = calculationService.preview(previewBatch(chunkOrder), facts,
                toLockedRates(rates), now);
        int sequence = Math.addExact(safeInt(task.getProcessedCandidateCount()) / chunkSize, 1);
        SettlementReviewSegmentDO segment = segment(task, candidates, facts, preview, sequence, now);
        if (segmentMapper.insertIdempotent(segment) != 1) {
            throw failure("MANUAL_REVIEW_SEGMENT_IDEMPOTENCY_CONFLICT", false,
                    "manual settlement segment already exists with a conflicting cursor");
        }
        List<SettlementReviewCandidateDO> relations = reviewCandidates(
                task.getReviewOrderNo(), candidates, facts, now);
        if (reviewCandidateMapper.insertBatchIdempotent(relations) != relations.size()) {
            throw failure("MANUAL_REVIEW_RELATION_INSERT_INCOMPLETE", false,
                    "manual settlement candidate snapshot insert is incomplete");
        }
        List<SettlementReviewSummaryDO> summaries = reviewSummaries(
                task.getReviewOrderNo(), preview.summaries(), now);
        if (!summaries.isEmpty()) {
            reviewSummaryMapper.addBatch(summaries);
        }

        long lastCandidateId = candidates.get(candidates.size() - 1).getId();
        long oldVersion = task.getVersion();
        if (taskMapper.advance(task.getTaskNo(), oldVersion, owner.trim(), candidates.size(),
                lastCandidateId, now) != 1) {
            throw failure("MANUAL_REVIEW_PROGRESS_CAS_FAILED", true,
                    "manual settlement task progress CAS failed");
        }
        task.setProcessedCandidateCount(safeInt(task.getProcessedCandidateCount()) + candidates.size());
        task.setLockedCandidateCount(safeInt(task.getLockedCandidateCount()) + candidates.size());
        task.setLastCandidateId(lastCandidateId);
        task.setVersion(oldVersion + 1);
        if (Objects.equals(task.getLockedCandidateCount(), task.getExpectedCandidateCount())) {
            finalizeTask(task, now);
        }
    }

    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public void compensateClaimed(String taskNo, String owner, LocalDateTime now) {
        SettlementManualReviewTaskDO task = requireClaimedTask(taskNo, owner, now, "CANCELLING");
        SettlementReviewSegmentDO segment = segmentMapper.selectNextLockedForUpdate(task.getReviewOrderNo());
        if (segment == null) {
            if (taskMapper.finishFailureCompensation(task.getTaskNo(), task.getVersion(),
                    owner.trim(), now) != 1) {
                throw failure("MANUAL_REVIEW_COMPENSATION_FINALIZE_CAS_FAILED", true,
                        "manual settlement failure compensation finalization CAS failed");
            }
            return;
        }
        List<SettlementReviewCandidateDO> relations = safe(reviewCandidateMapper.selectRangeForUpdate(
                task.getReviewOrderNo(), segment.getFirstCandidateId(), segment.getLastCandidateId()));
        List<Long> ids = relations.stream().map(SettlementReviewCandidateDO::getCandidateId).toList();
        List<SettlementCandidateDO> candidates = ids.isEmpty()
                ? List.of() : safe(candidateMapper.selectByIdsForUpdate(ids));
        if (candidates.size() != relations.size()
                || candidateMapper.releaseReviewLock(candidates, task.getReviewOrderNo(), now)
                != candidates.size()
                || reviewCandidateMapper.markRangeReleased(task.getReviewOrderNo(),
                segment.getFirstCandidateId(), segment.getLastCandidateId(), now) != relations.size()
                || segmentMapper.markReleased(segment.getSegmentNo(), segment.getVersion(), now) != 1) {
            throw failure("MANUAL_REVIEW_COMPENSATION_INCOMPLETE", true,
                    "manual settlement failure compensation is incomplete");
        }
    }

    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public void recordFailure(String taskNo,
                              RuntimeException exception,
                              Duration maximumDuration,
                              LocalDateTime now) {
        SettlementManualReviewTaskDO task = requireTask(taskMapper.selectByTaskNoForUpdate(taskNo));
        if (!Set.of("QUEUED", "PROCESSING", "FINALIZING").contains(task.getTaskStatus())) {
            return;
        }
        String code = exception instanceof SettlementManualReviewProcessingException processing
                ? processing.getFailureCode() : "MANUAL_REVIEW_UNEXPECTED_FAILURE";
        boolean retryable = exception instanceof SettlementManualReviewProcessingException processing
                && processing.isRetryable();
        String message = sanitize(exception.getMessage());
        LocalDateTime started = task.getStartedTime() == null ? task.getCreateTime() : task.getStartedTime();
        boolean timedOut = started == null || !now.isBefore(started.plus(maximumDuration));
        if (!retryable || timedOut) {
            if (taskMapper.beginFailureCompensation(task.getTaskNo(), task.getVersion(),
                    code, message, now) != 1) {
                throw failure("MANUAL_REVIEW_FAILURE_STATE_CAS_FAILED", true,
                        "manual settlement terminal failure state CAS failed");
            }
            return;
        }
        int retry = safeInt(task.getRetryCount()) + 1;
        long delaySeconds = Math.min(60L, 1L << Math.min(retry, 6));
        if (taskMapper.reschedule(task.getTaskNo(), task.getVersion(), code, message,
                now.plusSeconds(delaySeconds), now) != 1) {
            throw failure("MANUAL_REVIEW_RETRY_STATE_CAS_FAILED", true,
                    "manual settlement retry state CAS failed");
        }
    }

    private void finalizeTask(SettlementManualReviewTaskDO task, LocalDateTime now) {
        if (!"FINALIZING".equals(task.getTaskStatus())) {
            if (taskMapper.markFinalizing(task.getTaskNo(), task.getVersion(), now) != 1) {
                throw failure("MANUAL_REVIEW_FINALIZING_CAS_FAILED", true,
                        "manual settlement finalizing state CAS failed");
            }
            task.setTaskStatus("FINALIZING");
            task.setVersion(task.getVersion() + 1);
        }
        List<SettlementReviewSegmentDO> segments = safe(segmentMapper.selectByOrderNo(task.getReviewOrderNo()));
        int candidateCount = segments.stream().mapToInt(row -> safeInt(row.getCandidateCount())).sum();
        if (segments.isEmpty() || candidateCount != task.getExpectedCandidateCount()
                || segments.stream().anyMatch(row -> !"LOCKED".equals(row.getSegmentStatus()))) {
            throw failure("MANUAL_REVIEW_SEGMENT_SET_INCOMPLETE", false,
                    "manual settlement segment set is incomplete");
        }
        if (candidateMapper.countUnresolvedDependenciesOutsideReview(task.getReviewOrderNo()) != 0) {
            throw failure("MANUAL_REVIEW_DEPENDENCY_UNRESOLVED", false,
                    "manual settlement contains a dependency outside the frozen review scope");
        }
        List<SettlementReviewRateDO> rates = safe(reviewRateMapper.selectByOrderNo(task.getReviewOrderNo()));
        List<SettlementReviewSummaryDO> summaries = safe(
                reviewSummaryMapper.selectByOrderNo(task.getReviewOrderNo()));
        SettlementReviewOrderDO order = finalOrder(task, segments, rates, summaries, now);
        orderMapper.insertIdempotent(order);
        SettlementReviewOrderDO stored = orderMapper.selectByCreateRequestKeyForUpdate(task.getRequestKey());
        if (stored == null || !Objects.equals(stored.getReviewOrderNo(), order.getReviewOrderNo())
                || !Objects.equals(stored.getSourceFingerprint(), order.getSourceFingerprint())
                || !Objects.equals(stored.getRateFingerprint(), order.getRateFingerprint())
                || !Objects.equals(stored.getResultFingerprint(), order.getResultFingerprint())) {
            throw failure("MANUAL_REVIEW_ORDER_IDEMPOTENCY_CONFLICT", false,
                    "manual settlement review order snapshot is inconsistent");
        }
        if (taskMapper.markCompleted(task.getTaskNo(), task.getVersion(), now) != 1) {
            throw failure("MANUAL_REVIEW_COMPLETION_CAS_FAILED", true,
                    "manual settlement task completion CAS failed");
        }
    }

    private List<SettlementReviewRateDO> lockRates(SettlementManualReviewTaskDO task,
                                                   LocalDateTime now) {
        List<SettlementReviewRateDO> stored = safe(reviewRateMapper.selectByOrderNo(task.getReviewOrderNo()));
        if (!stored.isEmpty()) {
            return stored;
        }
        Set<SettlementCurrency> currencies = safe(taskMapper.selectCurrencies(task)).stream()
                .map(this::currency).collect(Collectors.toUnmodifiableSet());
        RateMatrix matrix = rateResolutionService.resolve(currencies, task.getTargetCurrency(),
                task.getTargetCurrencyExponent(), task.getOperationTime());
        List<SettlementReviewRateDO> expected = reviewRates(task, matrix, now);
        if (reviewRateMapper.insertBatchIdempotent(expected) != expected.size()) {
            throw failure("MANUAL_REVIEW_RATE_INSERT_INCOMPLETE", false,
                    "manual settlement rate snapshot insert is incomplete");
        }
        stored = safe(reviewRateMapper.selectByOrderNo(task.getReviewOrderNo()));
        if (stored.size() != expected.size()) {
            throw failure("MANUAL_REVIEW_RATE_MATRIX_INCOMPLETE", false,
                    "manual settlement rate snapshot is incomplete");
        }
        return stored;
    }

    private SettlementManualReviewTaskDO newTask(PreviewCommand command,
                                                 SettlementManualReviewProfileDO profile,
                                                 Window window,
                                                 long maxCandidateId,
                                                 int candidateCount,
                                                 List<PreviewLine> preview,
                                                 String reviewOrderNo,
                                                 LocalDateTime now) {
        SettlementOperatorSnapshot operator = command.operator();
        SettlementManualReviewTaskDO row = new SettlementManualReviewTaskDO();
        row.setTaskNo(stableId("MT", command.requestKey()));
        row.setRequestKey(command.requestKey());
        row.setReviewOrderNo(reviewOrderNo);
        row.setReviewType(command.reviewType());
        row.setMerchantId(command.merchantId());
        row.setSettlementProfileId(profile.getSettlementProfileId());
        row.setSettlementAccountId(profile.getSettlementAccountId());
        row.setTargetCurrency(profile.getTargetCurrency());
        row.setTargetCurrencyExponent(profile.getTargetCurrencyExponent());
        row.setPaymentType(normalize(command.paymentType()));
        row.setPaymentMethod(normalize(command.paymentMethod()));
        row.setBusinessDate(window.businessDate());
        row.setBusinessTimeZone(profile.getBusinessTimeZone());
        LocalDateTime lastPostedCutoff = taskMapper.selectLastPostedCutoffEnd(
                profile.getSettlementProfileId(), command.reviewType());
        row.setCutoffBeginTime(lastPostedCutoff != null && lastPostedCutoff.isBefore(window.cutoffEndUtc())
                ? lastPostedCutoff : window.cutoffBeginUtc());
        row.setCutoffEndTime(window.cutoffEndUtc());
        row.setSnapshotMaxCandidateId(maxCandidateId);
        row.setExpectedCandidateCount(candidateCount);
        row.setProcessedCandidateCount(0);
        row.setLockedCandidateCount(0);
        row.setLastCandidateId(0L);
        if ("REGULAR".equals(command.reviewType())) {
            row.setInitialDelayUnit(profile.getInitialDelayUnit());
            row.setInitialDelayDays(profile.getInitialDelayDays());
            row.setRegularDelayDays(profile.getRegularDelayDays());
            row.setSettlementFrequency(profile.getSettlementFrequency());
            row.setFrequencyDay(profile.getFrequencyDay());
        }
        row.setPreviewJson(JsonUtils.toJsonString(preview));
        row.setTaskStatus("PREVIEWED");
        row.setSubmittedByAccountId(operator.accountId());
        row.setSubmittedByAccountName(operator.accountName());
        row.setSubmittedRoleSnapshot(operator.roleSnapshot());
        row.setSubmitClientIp(operator.clientIp());
        row.setSubmitUserAgent(operator.userAgent());
        row.setSubmitReason(command.reason().trim());
        row.setOperationTime(operator.operationTime());
        row.setRetryCount(0);
        row.setNextRetryTime(now);
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private SettlementReviewOrderDO finalOrder(SettlementManualReviewTaskDO task,
                                               List<SettlementReviewSegmentDO> segments,
                                               List<SettlementReviewRateDO> rates,
                                               List<SettlementReviewSummaryDO> summaries,
                                               LocalDateTime now) {
        BigDecimal signed = segments.stream().map(SettlementReviewSegmentDO::getNetSignedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        SettlementReviewOrderDO row = chunkOrder(task, task.getExpectedCandidateCount());
        row.setCreateRequestKey(task.getRequestKey());
        row.setSelectionFingerprint(scopeFingerprint(task));
        row.setCreateMode("MANUAL_ASYNC");
        row.setProjectableCandidateCount(segments.stream()
                .mapToInt(value -> safeInt(value.getProjectableCandidateCount())).sum());
        row.setSourceFingerprint(segmentFingerprint("source-segments-v1", segments,
                SettlementReviewSegmentDO::getSourceFingerprint));
        row.setRateFingerprint(fingerprintService.rates(rates));
        row.setResultFingerprint(resultFingerprint(segments, summaries));
        row.setNetDirection(signed.signum() < 0 ? "DEBIT" : "CREDIT");
        row.setNetAmount(signed.abs().setScale(task.getTargetCurrencyExponent()));
        row.setReviewStatus(SettlementReviewStatus.PENDING_APPROVAL.name());
        row.setCreatedByAccountId(task.getSubmittedByAccountId());
        row.setCreatedByAccountName(task.getSubmittedByAccountName());
        row.setSubmittedByAccountId(task.getSubmittedByAccountId());
        row.setSubmittedByAccountName(task.getSubmittedByAccountName());
        row.setSubmittedRoleSnapshot(task.getSubmittedRoleSnapshot());
        row.setSubmitClientIp(task.getSubmitClientIp());
        row.setSubmitUserAgent(task.getSubmitUserAgent());
        row.setSubmitReason(task.getSubmitReason());
        row.setSubmittedTime(task.getOperationTime());
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private SettlementReviewOrderDO chunkOrder(SettlementManualReviewTaskDO task, int candidateCount) {
        SettlementReviewOrderDO row = new SettlementReviewOrderDO();
        row.setReviewOrderNo(task.getReviewOrderNo());
        row.setReviewType(task.getReviewType());
        row.setMerchantId(task.getMerchantId());
        row.setSettlementProfileId(task.getSettlementProfileId());
        row.setSettlementAccountId(task.getSettlementAccountId());
        row.setTargetCurrency(task.getTargetCurrency());
        row.setTargetCurrencyExponent(task.getTargetCurrencyExponent());
        row.setBusinessDate(task.getBusinessDate());
        row.setBusinessTimeZone(task.getBusinessTimeZone());
        row.setCutoffBeginTime(task.getCutoffBeginTime());
        row.setCutoffEndTime(task.getCutoffEndTime());
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

    private SettlementReviewSegmentDO segment(SettlementManualReviewTaskDO task,
                                              List<SettlementCandidateDO> candidates,
                                              SettlementBatchFacts facts,
                                              SettlementCalculationPreview preview,
                                              int sequence,
                                              LocalDateTime now) {
        SettlementReviewSegmentDO row = new SettlementReviewSegmentDO();
        row.setSegmentNo(stableId("SG", task.getReviewOrderNo() + "|" + sequence));
        row.setReviewOrderNo(task.getReviewOrderNo());
        row.setSequenceNo(sequence);
        row.setFirstCandidateId(candidates.get(0).getId());
        row.setLastCandidateId(candidates.get(candidates.size() - 1).getId());
        row.setCandidateCount(candidates.size());
        row.setProjectableCandidateCount("REGULAR".equals(task.getReviewType())
                ? candidates.size() : 0);
        row.setSourceFingerprint(fingerprintService.source(facts));
        row.setResultFingerprint(fingerprintService.result(preview));
        row.setNetSignedAmount("DEBIT".equals(preview.netDirection())
                ? preview.netAmount().negate() : preview.netAmount());
        row.setSegmentStatus("LOCKED");
        row.setProcessedTime(now);
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    private List<SettlementReviewCandidateDO> reviewCandidates(String reviewOrderNo,
                                                               List<SettlementCandidateDO> candidates,
                                                               SettlementBatchFacts facts,
                                                               LocalDateTime now) {
        List<SettlementReviewCandidateDO> rows = new ArrayList<>(candidates.size());
        for (SettlementCandidateDO candidate : candidates) {
            SettlementReviewCandidateDO row = new SettlementReviewCandidateDO();
            row.setReviewCandidateNo(stableId("RC", reviewOrderNo + "|" + candidate.getId()));
            row.setReviewOrderNo(reviewOrderNo);
            row.setCandidateId(candidate.getId());
            row.setCandidateNo(candidate.getCandidateNo());
            row.setSourceType(candidate.getSourceType());
            row.setSourceBusinessId(candidate.getSourceBusinessId());
            row.setSourceRevision(candidate.getSourceRevision());
            row.setSourceTransactionId(candidate.getSourceTransactionId());
            row.setSourceTransactionDateTime(candidate.getSourceTransactionDateTime());
            row.setLockedCandidateVersion(candidate.getVersion());
            row.setClearingFingerprint(fingerprintService.candidateSource(facts, candidate));
            row.setRelationStatus("LOCKED");
            row.setLockedTime(now);
            row.setVersion(0L);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            rows.add(row);
        }
        return rows;
    }

    private List<SettlementReviewSummaryDO> reviewSummaries(String reviewOrderNo,
                                                            List<SettlementResultSummaryDO> values,
                                                            LocalDateTime now) {
        return values.stream().map(value -> {
            SettlementReviewSummaryDO row = new SettlementReviewSummaryDO();
            row.setReviewOrderNo(reviewOrderNo);
            row.setMerchantId(value.getMerchantId());
            row.setPaymentType(value.getPaymentType());
            row.setPaymentMethod(value.getPaymentMethod());
            row.setTransactionType(value.getTransactionType());
            row.setResultItemType(value.getResultItemType());
            row.setFeeCategory(value.getFeeCategory());
            row.setDirection(value.getDirection());
            row.setSourceCurrency(value.getSourceCurrency());
            row.setTargetCurrency(value.getTargetCurrency());
            row.setTransactionCount(value.getTransactionCount());
            row.setSourceAmount(value.getSourceAmount());
            row.setTargetAmount(value.getTargetAmount());
            row.setCreateTime(now);
            return row;
        }).toList();
    }

    private List<SettlementReviewRateDO> reviewRates(SettlementManualReviewTaskDO task,
                                                     RateMatrix matrix,
                                                     LocalDateTime now) {
        return matrix.rates().stream().sorted(Comparator.comparing(rate -> rate.pair().sourceCurrency()))
                .map(rate -> {
                    SettlementReviewRateDO row = new SettlementReviewRateDO();
                    row.setReviewOrderNo(task.getReviewOrderNo());
                    row.setSourceCurrency(rate.pair().sourceCurrency());
                    row.setTargetCurrency(rate.pair().targetCurrency());
                    row.setDirectRate(rate.directRate());
                    row.setSourceCurrencyExponent(rate.sourceCurrencyExponent());
                    row.setTargetCurrencyExponent(rate.targetCurrencyExponent());
                    row.setRateSource(rate.rateSource());
                    row.setQuoteId(rate.quoteId());
                    row.setSourceQuoteDirection(rate.sourceQuoteDirection().name());
                    row.setEffectiveTime(rate.effectiveTime());
                    row.setLockedTime(now);
                    row.setLockedBy("admin-account:" + task.getSubmittedByAccountId());
                    row.setCreateTime(now);
                    return row;
                }).toList();
    }

    private SettlementLockedRateMatrix toLockedRates(List<SettlementReviewRateDO> rows) {
        List<LockedRate> rates = new ArrayList<>(rows.size());
        Map<String, Long> ids = new LinkedHashMap<>();
        for (SettlementReviewRateDO row : rows) {
            if (row.getId() == null || row.getId() <= 0) {
                throw failure("MANUAL_REVIEW_RATE_ID_MISSING", false,
                        "manual settlement rate row identity is missing");
            }
            rates.add(new LockedRate(new CurrencyPair(row.getSourceCurrency(), row.getTargetCurrency()),
                    row.getDirectRate(), row.getSourceCurrencyExponent(), row.getTargetCurrencyExponent(),
                    row.getRateSource(), row.getQuoteId(),
                    QuoteDirection.valueOf(row.getSourceQuoteDirection()), row.getEffectiveTime()));
            ids.put(row.getSourceCurrency(), row.getId());
        }
        return new SettlementLockedRateMatrix(RateMatrix.of(rates), ids);
    }

    private TaskResult result(SettlementManualReviewTaskDO task) {
        List<PreviewLine> preview = task.getPreviewJson() == null
                ? List.of() : JsonUtils.parseArray(task.getPreviewJson(), PreviewLine.class);
        int expected = safeInt(task.getExpectedCandidateCount());
        int processed = safeInt(task.getProcessedCandidateCount());
        int progress = expected == 0 ? 0 : Math.min(100,
                Math.toIntExact((long) processed * 100L / expected));
        return new TaskResult(task.getTaskNo(), task.getReviewOrderNo(), task.getTaskStatus(),
                task.getReviewType(),
                task.getMerchantId(), task.getSettlementProfileId(), task.getSettlementAccountId(),
                task.getTargetCurrency(), safeInt(task.getTargetCurrencyExponent()),
                task.getPaymentType(), task.getPaymentMethod(), task.getSubmitReason(),
                task.getBusinessDate(),
                task.getCutoffEndTime(), value(task.getSnapshotMaxCandidateId()), expected,
                processed, safeInt(task.getLockedCandidateCount()), progress,
                task.getInitialDelayUnit(), safeInt(task.getInitialDelayDays()),
                safeInt(task.getRegularDelayDays()), task.getSettlementFrequency(),
                task.getFrequencyDay(), preview, safeInt(task.getRetryCount()),
                task.getLastFailureCode(), task.getLastFailureMessage(), task.getStartedTime(),
                task.getCompletedTime(), value(task.getVersion()));
    }

    private PreviewLine previewLine(SettlementManualReviewPreviewLineDO row) {
        return new PreviewLine(row.getSourceCurrency(), safeInt(row.getSourceCurrencyExponent()),
                value(row.getTransactionCount()), zero(row.getGrossAmount()), row.getPlatformFeeAmount(),
                zero(row.getReserveAmount()), zero(row.getReleasedReserveAmount()),
                row.getNetSettlementAmount(), value(row.getPendingFeeCount()), row.getReserveDelayUnit(),
                row.getMinimumReserveDelayDays(), row.getMaximumReserveDelayDays(),
                row.getEarliestExpectedReleaseDate(), row.getLatestExpectedReleaseDate());
    }

    private SettlementCurrency currency(SettlementManualReviewCurrencyDO row) {
        if (row == null || row.getCurrency() == null || row.getCurrencyExponent() == null) {
            throw failure("MANUAL_REVIEW_CURRENCY_INVALID", false,
                    "manual settlement source currency is invalid");
        }
        return new SettlementCurrency(row.getCurrency(), row.getCurrencyExponent());
    }

    private String nextReviewOrderNo(LocalDate businessDate) {
        sequenceMapper.insertIfAbsent(businessDate);
        SettlementReviewDailySequenceDO sequence = sequenceMapper.selectForUpdate(businessDate);
        if (sequence == null || sequence.getCurrentSequence() == null || sequence.getVersion() == null
                || sequence.getCurrentSequence() < 0 || sequence.getCurrentSequence() >= MAX_DAILY_SEQUENCE) {
            throw failure("MANUAL_REVIEW_SEQUENCE_INVALID", false,
                    "settlement review daily sequence is unavailable");
        }
        int next = sequence.getCurrentSequence() + 1;
        if (sequenceMapper.increment(businessDate, sequence.getCurrentSequence(), sequence.getVersion()) != 1) {
            throw failure("MANUAL_REVIEW_SEQUENCE_CAS_FAILED", true,
                    "settlement review daily sequence CAS failed");
        }
        return numberFormatter.storageNumber(businessDate, next);
    }

    private Window maturedWindow(SettlementManualReviewProfileDO profile, Instant now) {
        ZoneId zone = ZoneId.of(profile.getBusinessTimeZone());
        ZonedDateTime localNow = now.atZone(zone);
        LocalDate businessDate = localNow.toLocalTime().isBefore(profile.getDailyCutoffTime())
                ? localNow.toLocalDate().minusDays(1) : localNow.toLocalDate();
        ZonedDateTime cutoffEnd = businessDate.atTime(profile.getDailyCutoffTime()).atZone(zone);
        ZonedDateTime cutoffBegin = businessDate.minusDays(1)
                .atTime(profile.getDailyCutoffTime()).atZone(zone);
        return new Window(businessDate,
                LocalDateTime.ofInstant(cutoffBegin.toInstant(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(cutoffEnd.toInstant(), ZoneOffset.UTC));
    }

    private String scopeFingerprint(SettlementManualReviewTaskDO task) {
        return digest("manual-scope-v2", task.getReviewType(), task.getMerchantId(), task.getSettlementProfileId(),
                task.getSettlementAccountId(), task.getTargetCurrency(), task.getPaymentType(),
                task.getPaymentMethod(), task.getBusinessDate(), task.getCutoffEndTime(),
                task.getSnapshotMaxCandidateId(), task.getExpectedCandidateCount());
    }

    private String resultFingerprint(List<SettlementReviewSegmentDO> segments,
                                     List<SettlementReviewSummaryDO> summaries) {
        List<Object> values = new ArrayList<>();
        segments.stream().sorted(Comparator.comparing(SettlementReviewSegmentDO::getSequenceNo))
                .forEach(row -> {
                    values.add(row.getSequenceNo());
                    values.add(row.getResultFingerprint());
                    values.add(row.getNetSignedAmount());
                });
        summaries.stream().sorted(Comparator.comparing(SettlementReviewSummaryDO::getId))
                .forEach(row -> {
                    values.add(row.getPaymentType());
                    values.add(row.getPaymentMethod());
                    values.add(row.getTransactionType());
                    values.add(row.getResultItemType());
                    values.add(row.getFeeCategory());
                    values.add(row.getDirection());
                    values.add(row.getSourceCurrency());
                    values.add(row.getTargetCurrency());
                    values.add(row.getTransactionCount());
                    values.add(row.getSourceAmount());
                    values.add(row.getTargetAmount());
                });
        return digest("result-segments-v1", values.toArray());
    }

    private String segmentFingerprint(String version,
                                      List<SettlementReviewSegmentDO> segments,
                                      java.util.function.Function<SettlementReviewSegmentDO, String> value) {
        List<Object> values = new ArrayList<>();
        segments.stream().sorted(Comparator.comparing(SettlementReviewSegmentDO::getSequenceNo))
                .forEach(row -> {
                    values.add(row.getSequenceNo());
                    values.add(row.getFirstCandidateId());
                    values.add(row.getLastCandidateId());
                    values.add(value.apply(row));
                });
        return digest(version, values.toArray());
    }

    private String digest(String version, Object... values) {
        StringBuilder canonical = new StringBuilder(version);
        for (Object value : values) {
            String normalized = value == null ? "<null>" : value instanceof BigDecimal decimal
                    ? decimal.stripTrailingZeros().toPlainString() : value.toString();
            canonical.append('|').append(normalized.length()).append(':').append(normalized);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateProfile(SettlementManualReviewProfileDO profile, PreviewCommand command) {
        if (profile == null || profile.getSettlementProfileId() == null
                || profile.getSettlementAccountId() == null
                || !Objects.equals(profile.getMerchantId(), command.merchantId())
                || profile.getTargetCurrency() == null || profile.getTargetCurrencyExponent() == null
                || profile.getBusinessTimeZone() == null || profile.getDailyCutoffTime() == null
                || ("REGULAR".equals(command.reviewType())
                    && (!Set.of("T", "D").contains(profile.getInitialDelayUnit())
                        || profile.getInitialDelayDays() == null || profile.getInitialDelayDays() < 1
                        || profile.getRegularDelayDays() == null || profile.getRegularDelayDays() < 1
                        || profile.getSettlementFrequency() == null))) {
            throw failure("MANUAL_REVIEW_PROFILE_INVALID", false,
                    "manual settlement profile, account, or settlement cycle is unavailable");
        }
    }

    private SettlementManualReviewTaskDO requireClaimedTask(String taskNo,
                                                            String owner,
                                                            LocalDateTime now,
                                                            String expectedStatus) {
        SettlementManualReviewTaskDO task = requireTask(taskMapper.selectByTaskNoForUpdate(taskNo));
        if (!expectedStatus.equals(task.getTaskStatus()) || task.getVersion() == null
                || !Objects.equals(task.getProcessingOwner(), owner.trim())
                || task.getProcessingDeadline() == null || !task.getProcessingDeadline().isAfter(now)) {
            throw failure("MANUAL_REVIEW_LEASE_LOST", true,
                    "manual settlement processing lease is unavailable or expired");
        }
        return task;
    }

    private void verifyPreviewReplay(SettlementManualReviewTaskDO task, PreviewCommand command) {
        if (!Objects.equals(task.getMerchantId(), command.merchantId())
                || !Objects.equals(task.getReviewType(), command.reviewType())
                || !Objects.equals(task.getSettlementProfileId(), command.settlementProfileId())
                || !Objects.equals(task.getPaymentType(), normalize(command.paymentType()))
                || !Objects.equals(task.getPaymentMethod(), normalize(command.paymentMethod()))
                || !Objects.equals(task.getSubmitReason(), command.reason().trim())
                || !Objects.equals(task.getSubmittedByAccountId(), command.operator().accountId())) {
            throw failure("MANUAL_REVIEW_PREVIEW_KEY_CONFLICT", false,
                    "manual settlement preview request key has mismatched immutable identity");
        }
    }

    private void verifyStoredTask(SettlementManualReviewTaskDO actual,
                                  SettlementManualReviewTaskDO expected) {
        if (actual == null || !Objects.equals(actual.getTaskNo(), expected.getTaskNo())
                || !Objects.equals(actual.getReviewType(), expected.getReviewType())
                || !Objects.equals(actual.getReviewOrderNo(), expected.getReviewOrderNo())
                || !Objects.equals(actual.getSnapshotMaxCandidateId(), expected.getSnapshotMaxCandidateId())
                || !Objects.equals(actual.getExpectedCandidateCount(), expected.getExpectedCandidateCount())) {
            throw failure("MANUAL_REVIEW_TASK_IDEMPOTENCY_CONFLICT", false,
                    "manual settlement preview task snapshot is inconsistent");
        }
    }

    private SettlementManualReviewTaskDO requireTask(SettlementManualReviewTaskDO task) {
        if (task == null) {
            throw failure("MANUAL_REVIEW_TASK_NOT_FOUND", false,
                    "manual settlement task does not exist");
        }
        return task;
    }

    private void requireReviewType(String reviewType) {
        if (!REVIEW_TYPES.contains(reviewType)) {
            throw new IllegalArgumentException("manual settlement review type is invalid");
        }
    }

    private void requireTaskReviewType(SettlementManualReviewTaskDO task, String expectedReviewType) {
        if (!Objects.equals(task.getReviewType(), expectedReviewType)) {
            throw failure("MANUAL_REVIEW_TASK_TYPE_MISMATCH", false,
                    "manual settlement task does not belong to the requested settlement type");
        }
    }

    private void requireTaskNo(String taskNo) {
        if (taskNo == null || !taskNo.trim().matches("MT[0-9a-f]{32}")) {
            throw new IllegalArgumentException("manual settlement task number is invalid");
        }
    }

    private void requireOwner(String owner, LocalDateTime now, LocalDateTime deadline) {
        if (owner == null || owner.isBlank() || owner.trim().length() > 128
                || now == null || deadline == null || !deadline.isAfter(now)) {
            throw new IllegalArgumentException("manual settlement processing lease is invalid");
        }
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

    private String sanitize(String value) {
        String message = value == null || value.isBlank() ? "manual settlement task failed" : value.trim();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : List.copyOf(rows);
    }

    private SettlementManualReviewProcessingException failure(String code,
                                                              boolean retryable,
                                                              String message) {
        return new SettlementManualReviewProcessingException(code, retryable, message);
    }

    private record Window(LocalDate businessDate,
                          LocalDateTime cutoffBeginUtc,
                          LocalDateTime cutoffEndUtc) {
    }
}
