package com.scott.payment.settlement.application;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementPrincipalCorrectionStatus;
import com.scott.payment.settlement.dto.SettlementClearingLocator;
import com.scott.payment.settlement.dto.SettlementOperatorSnapshot;
import com.scott.payment.settlement.dto.SettlementPrincipalCorrectionCommandResult;
import com.scott.payment.settlement.dto.SettlementPrincipalCorrectionDecisionCommand;
import com.scott.payment.settlement.dto.SettlementPrincipalCorrectionSubmitCommand;
import com.scott.payment.settlement.entity.MerchantFundAccountDO;
import com.scott.payment.settlement.entity.MerchantFundLedgerDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementBatchRateDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementOperationIdentityDO;
import com.scott.payment.settlement.entity.SettlementPrincipalCorrectionItemDO;
import com.scott.payment.settlement.entity.SettlementPrincipalCorrectionOrderDO;
import com.scott.payment.settlement.entity.SettlementProjectionTaskDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.entity.SettlementTransactionClearingDetailDO;
import com.scott.payment.settlement.exception.SettlementPrincipalCorrectionProjectionException;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementBatchRateMapper;
import com.scott.payment.settlement.mapper.SettlementClearingFactMapper;
import com.scott.payment.settlement.mapper.SettlementFundMapper;
import com.scott.payment.settlement.mapper.SettlementPrincipalCorrectionMapper;
import com.scott.payment.settlement.mapper.SettlementPrincipalCorrectionProjectionMapper;
import com.scott.payment.settlement.mapper.SettlementProjectionMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 已入账历史标签本金差额调整编排。
 *
 * <p>申请阶段从原批次不可变事实重算并冻结逐笔差额；审批阶段重新锁读和校验 SHA-256 快照，
 * 仅追加一条差额资金流水。原清分、结算结果、费用、保证金、汇率和历史流水始终保持只读。</p>
 */
@Service
public class SettlementPrincipalCorrectionApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SettlementPrincipalCorrectionApplicationService.class);
    private static final String PROJECTION_FAILURE_CODE = "PRINCIPAL_CORRECTION_PROJECTION_FAILED";
    private static final int MAX_BACKOFF_MINUTES = 60;
    private static final int MAX_CURRENCY_EXPONENT = 8;
    private static final int MONEY_INTEGER_DIGITS = 16;
    private static final int MONEY_SCALE = 8;
    private static final int RATE_INTEGER_DIGITS = 12;
    private static final int RATE_SCALE = 12;
    private static final Pattern ISO_CURRENCY = Pattern.compile("[A-Z]{3}");

    private final SettlementPrincipalCorrectionMapper correctionMapper;
    private final SettlementPrincipalCorrectionProjectionMapper correctionProjectionMapper;
    private final SettlementBatchMapper batchMapper;
    private final SettlementBatchRateMapper batchRateMapper;
    private final SettlementClearingFactMapper clearingFactMapper;
    private final SettlementResultMapper resultMapper;
    private final SettlementFundMapper fundMapper;
    private final SettlementProjectionMapper projectionMapper;
    private final Clock clock;

    @Autowired
    public SettlementPrincipalCorrectionApplicationService(
            SettlementPrincipalCorrectionMapper correctionMapper,
            SettlementPrincipalCorrectionProjectionMapper correctionProjectionMapper,
            SettlementBatchMapper batchMapper,
            SettlementBatchRateMapper batchRateMapper,
            SettlementClearingFactMapper clearingFactMapper,
            SettlementResultMapper resultMapper,
            SettlementFundMapper fundMapper,
            SettlementProjectionMapper projectionMapper) {
        this(correctionMapper, correctionProjectionMapper, batchMapper, batchRateMapper,
                clearingFactMapper, resultMapper, fundMapper, projectionMapper, Clock.systemUTC());
    }

    SettlementPrincipalCorrectionApplicationService(
            SettlementPrincipalCorrectionMapper correctionMapper,
            SettlementPrincipalCorrectionProjectionMapper correctionProjectionMapper,
            SettlementBatchMapper batchMapper,
            SettlementBatchRateMapper batchRateMapper,
            SettlementClearingFactMapper clearingFactMapper,
            SettlementResultMapper resultMapper,
            SettlementFundMapper fundMapper,
            SettlementProjectionMapper projectionMapper,
            Clock clock) {
        this.correctionMapper = correctionMapper;
        this.correctionProjectionMapper = correctionProjectionMapper;
        this.batchMapper = batchMapper;
        this.batchRateMapper = batchRateMapper;
        this.clearingFactMapper = clearingFactMapper;
        this.resultMapper = resultMapper;
        this.fundMapper = fundMapper;
        this.projectionMapper = projectionMapper;
        this.clock = clock;
    }

    /**
     * 冻结原批次全部异常本金、历史结果汇率、标签修正汇率、结算结果和投影身份，创建待复核调整单。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public SettlementPrincipalCorrectionCommandResult submit(
            SettlementPrincipalCorrectionSubmitCommand command) {
        Objects.requireNonNull(command, "principal correction command is required");
        SettlementPrincipalCorrectionOrderDO replay = correctionMapper
                .selectBySubmitRequestKeyForUpdate(command.requestKey());
        if (replay != null) {
            verifySubmitReplay(replay, command);
            LOGGER.info("历史标签本金差额调整申请幂等返回, event: SETTLEMENT_PRINCIPAL_CORRECTION_SUBMIT_REPLAY, correctionNo: {}, originalBatchNo: {}, correctionStatus: {}",
                    replay.getCorrectionNo(), replay.getOriginalBatchNo(), replay.getCorrectionStatus());
            return result(replay);
        }

        SettlementBatchDO batch = requirePostedRegularBatch(
                command.originalBatchNo(), command.expectedBatchVersion());
        if (correctionMapper.selectByOriginalBatchForUpdate(batch.getSettlementBatchNo()) != null) {
            throw new IllegalStateException("settlement batch already has a principal correction order");
        }
        FrozenSource source = freeze(batch);
        LocalDateTime now = LocalDateTime.now(clock);
        String correctionNo = stableId("SPC", command.requestKey());
        SettlementPrincipalCorrectionOrderDO order = order(
                correctionNo, batch, source, command, now);
        List<SettlementPrincipalCorrectionItemDO> items = source.items().stream()
                .map(item -> item(correctionNo, item, now)).toList();
        if (correctionMapper.insertOrder(order) != 1
                || correctionMapper.insertItems(items) != items.size()) {
            throw new IllegalStateException("settlement principal correction facts insert failed");
        }
        LOGGER.info("历史标签本金差额调整申请已冻结, event: SETTLEMENT_PRINCIPAL_CORRECTION_SUBMITTED, correctionNo: {}, originalBatchNo: {}, itemCount: {}, correctionDirection: {}, correctionAmount: {}, currency: {}",
                correctionNo, batch.getSettlementBatchNo(), items.size(),
                order.getCorrectionDirection(), order.getCorrectionAmount(), order.getTargetCurrency());
        return result(order);
    }

    /**
     * 由不同账号复核调整单；批准时在同一事务追加差额流水、更新账户并激活投影修正任务。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public SettlementPrincipalCorrectionCommandResult decide(
            String correctionNo,
            SettlementPrincipalCorrectionDecisionCommand command) {
        if (correctionNo == null || correctionNo.isBlank()) {
            throw new IllegalArgumentException("principal correction number is required");
        }
        Objects.requireNonNull(command, "principal correction decision is required");
        String normalizedNo = correctionNo.trim();
        SettlementPrincipalCorrectionOrderDO replay = correctionMapper
                .selectByDecisionRequestKeyForUpdate(command.requestKey());
        if (replay != null) {
            verifyDecisionReplay(replay, normalizedNo, command);
            LOGGER.info("历史标签本金差额调整复核幂等返回, event: SETTLEMENT_PRINCIPAL_CORRECTION_DECISION_REPLAY, correctionNo: {}, decisionAction: {}, correctionStatus: {}",
                    replay.getCorrectionNo(), replay.getDecisionAction(), replay.getCorrectionStatus());
            return result(replay);
        }

        SettlementPrincipalCorrectionOrderDO order = correctionMapper
                .selectByCorrectionNoForUpdate(normalizedNo);
        if (order == null
                || !SettlementPrincipalCorrectionStatus.PENDING_REVIEW.name()
                .equals(order.getCorrectionStatus())
                || order.getVersion() == null || order.getVersion() != command.expectedVersion()) {
            throw new IllegalStateException("principal correction is not pending at the expected version");
        }
        if (Objects.equals(order.getSubmittedByAccountId(), command.operator().accountId())) {
            throw new IllegalStateException("principal correction Maker and Checker must be different accounts");
        }
        applyDecision(order, command);
        if ("REJECT".equals(command.decision())) {
            int cancelled = correctionMapper.cancelItems(order.getCorrectionNo(), command.operator().operationTime());
            if (cancelled != order.getItemCount()
                    || correctionMapper.reject(order, command.expectedVersion()) != 1) {
                throw new IllegalStateException("principal correction rejection CAS failed");
            }
            order.setCorrectionStatus(SettlementPrincipalCorrectionStatus.REJECTED.name());
            order.setVersion(order.getVersion() + 1);
            LOGGER.info("历史标签本金差额调整申请已拒绝, event: SETTLEMENT_PRINCIPAL_CORRECTION_REJECTED, correctionNo: {}, originalBatchNo: {}, itemCount: {}",
                    order.getCorrectionNo(), order.getOriginalBatchNo(), order.getItemCount());
            return result(order);
        }

        SettlementBatchDO batch = requirePostedRegularBatch(
                order.getOriginalBatchNo(), order.getOriginalBatchVersion());
        FrozenSource source = freeze(batch);
        verifyFrozenSource(order, batch, source);
        List<SettlementPrincipalCorrectionItemDO> storedItems = correctionMapper
                .selectItemsForUpdate(order.getCorrectionNo());
        verifyStoredItems(storedItems, source.items());
        order.setCorrectionLedgerId(postLedger(order, command.operator().operationTime()));
        if (correctionMapper.activateItems(order.getCorrectionNo(), command.operator().operationTime())
                != order.getItemCount()
                || correctionMapper.execute(order, command.expectedVersion()) != 1) {
            throw new IllegalStateException("principal correction execution CAS failed");
        }
        order.setCorrectionStatus(SettlementPrincipalCorrectionStatus.EXECUTED.name());
        order.setVersion(order.getVersion() + 1);
        LOGGER.info("历史标签本金差额调整已执行, event: SETTLEMENT_PRINCIPAL_CORRECTION_EXECUTED, correctionNo: {}, originalBatchNo: {}, correctionDirection: {}, correctionAmount: {}, currency: {}, itemCount: {}",
                order.getCorrectionNo(), order.getOriginalBatchNo(), order.getCorrectionDirection(),
                order.getCorrectionAmount(), order.getTargetCurrency(), order.getItemCount());
        return result(order);
    }

    /**
     * 领取一个已批准调整项并以冻结旧值 CAS 修正财务状态、动作单和当前生命周期主单。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public boolean processNextProjection(LocalDateTime now) {
        Objects.requireNonNull(now, "principal correction projection time is required");
        SettlementPrincipalCorrectionItemDO item = correctionMapper.selectNextProjectionForUpdate(now);
        if (item == null) {
            return false;
        }
        if (item.getVersion() == null || item.getRetryCount() == null
                || correctionMapper.markProjectionProcessing(
                item.getCorrectionItemNo(), item.getVersion(), now) != 1) {
            throw new IllegalStateException("principal correction projection claim CAS failed");
        }
        long rollbackVersion = item.getVersion();
        int rollbackRetryCount = item.getRetryCount();
        item.setVersion(item.getVersion() + 1);
        try {
            SettlementOperationIdentityDO operation = projectionMapper.selectOperationIdentity(
                    item.getTransactionId(), item.getTransactionDateTime());
            validateProjectionIdentity(item, operation);
            SettlementPrincipalCorrectionOrderDO order = correctionMapper
                    .selectByCorrectionNoForUpdate(item.getCorrectionNo());
            if (order == null || !SettlementPrincipalCorrectionStatus.EXECUTED.name()
                    .equals(order.getCorrectionStatus())) {
                throw new IllegalStateException("principal correction order is not executable");
            }
            correctProjection(item, operation, order.getOriginalBatchNo(), now);
            if (correctionMapper.markProjectionCompleted(
                    item.getCorrectionItemNo(), item.getVersion(), now) != 1
                    || correctionMapper.incrementProjectionCompleted(item.getCorrectionNo(), now) != 1) {
                throw new IllegalStateException("principal correction projection completion CAS failed");
            }
            LOGGER.info("历史标签本金交易投影修正完成, event: SETTLEMENT_PRINCIPAL_CORRECTION_PROJECTION_COMPLETED, correctionNo: {}, correctionItemNo: {}, transactionId: {}",
                    item.getCorrectionNo(), item.getCorrectionItemNo(), item.getTransactionId());
            return true;
        } catch (RuntimeException exception) {
            throw new SettlementPrincipalCorrectionProjectionException(
                    item.getCorrectionItemNo(), rollbackVersion, rollbackRetryCount,
                    PROJECTION_FAILURE_CODE, exception);
        }
    }

    /**
     * 投影主事务回滚后，在独立事务记录有上限指数退避，不覆盖并发完成状态。
     */
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean recordProjectionFailure(SettlementPrincipalCorrectionProjectionException failure,
                                           LocalDateTime now) {
        int retryCount = Math.max(0, failure.getExpectedRetryCount());
        long delayMinutes = Math.min(1L << Math.min(retryCount, 6), MAX_BACKOFF_MINUTES);
        boolean recorded = correctionMapper.recordProjectionFailure(
                failure.getCorrectionItemNo(), retryCount, failure.getExpectedVersion(),
                failure.getFailureCode(), now.plusMinutes(delayMinutes), now) == 1;
        if (recorded) {
            LOGGER.warn("历史标签本金投影修正失败已进入退避, event: SETTLEMENT_PRINCIPAL_CORRECTION_PROJECTION_RETRY_SCHEDULED, correctionItemNo: {}, failureCode: {}, retryCount: {}, delayMinutes: {}",
                    failure.getCorrectionItemNo(), failure.getFailureCode(), retryCount + 1, delayMinutes);
        }
        return recorded;
    }

    /** 锁定并验证仍处于申请时版本的已入账常规批次。 */
    private SettlementBatchDO requirePostedRegularBatch(String batchNo, long expectedVersion) {
        SettlementBatchDO batch = batchMapper.selectByBatchNoForUpdate(batchNo);
        if (batch == null || batch.getVersion() == null || batch.getBusinessDate() == null
                || batch.getSettlementAccountId() == null || batch.getSettlementAccountId() <= 0
                || batch.getMerchantId() == null || batch.getMerchantId().isBlank()
                || !SettlementBatchStatus.POSTED.name().equals(batch.getBatchStatus())
                || !"REGULAR".equals(batch.getBatchType())) {
            throw new IllegalStateException("only a complete posted regular batch can be corrected");
        }
        if (batch.getVersion() != expectedVersion) {
            throw new IllegalStateException("principal correction uses a stale batch version");
        }
        requireCurrency(batch.getTargetCurrency(), "principal correction batch target currency");
        requireExponent(batch.getTargetCurrencyExponent(), "principal correction batch target exponent");
        return batch;
    }

    /**
     * 从不可变来源事实计算逐笔期望标签本金、目标金额和批次净差额。
     */
    private FrozenSource freeze(SettlementBatchDO batch) {
        SettlementResultItemDO net = resultMapper.selectNetPostingForUpdate(batch.getSettlementBatchNo());
        if (net == null || net.getLedgerIdempotencyKey() == null) {
            throw new IllegalStateException("principal correction original net result is missing");
        }
        MerchantFundLedgerDO originalLedger = fundMapper.selectLedgerByIdempotencyForUpdate(
                net.getLedgerIdempotencyKey());
        if (!validOriginalPosting(net, originalLedger, batch)) {
            throw new IllegalStateException("principal correction original fund ledger is invalid");
        }
        List<SettlementCandidateDO> candidates = safe(clearingFactMapper
                .selectPostedTransactionCandidatesForUpdate(batch.getSettlementBatchNo()));
        if (candidates.isEmpty()) {
            throw new IllegalStateException("principal correction batch has no posted transaction candidates");
        }
        List<SettlementClearingLocator> locators = candidates.stream()
                .map(candidate -> new SettlementClearingLocator(candidate.getSourceTransactionId(),
                        candidate.getSourceTransactionDateTime(), candidate.getSourceRevision()))
                .toList();
        List<SettlementTransactionClearingDetailDO> details = safe(
                clearingFactMapper.selectTransactionDetails(locators));
        List<SettlementResultItemDO> results = safe(resultMapper.selectFinancialItemsByBatch(
                batch.getSettlementBatchNo()));
        List<SettlementBatchRateDO> rates = safe(batchRateMapper.selectByBatchNo(
                batch.getSettlementBatchNo()));
        List<SettlementProjectionTaskDO> tasks = safe(projectionMapper.selectTasksByBatch(
                batch.getSettlementBatchNo()));

        Map<FactKey, SettlementCandidateDO> candidateByFact = uniqueCandidates(candidates);
        Map<String, SettlementResultItemDO> resultByDetail = uniquePrincipalResults(results);
        RateIndex rateIndex = uniqueRates(rates, batch);
        Map<Long, SettlementProjectionTaskDO> taskByCandidate = uniqueTasks(tasks);
        List<FrozenItem> frozen = new ArrayList<>();
        java.util.Set<Long> correctedCandidateIds = new java.util.HashSet<>();
        for (SettlementTransactionClearingDetailDO detail : details.stream()
                .filter(row -> "ACTIVE".equals(row.getRecordStatus()))
                .filter(row -> "PRINCIPAL".equals(row.getItemType()))
                .filter(this::isLabelMismatch)
                .sorted(Comparator.comparing(SettlementTransactionClearingDetailDO::getClearingDetailNo))
                .toList()) {
            FactKey key = new FactKey(detail.getTransactionId(), detail.getTransactionDateTime(),
                    detail.getClearingRevision());
            SettlementCandidateDO candidate = candidateByFact.get(key);
            SettlementResultItemDO result = resultByDetail.get(detail.getClearingDetailNo());
            SettlementBatchRateDO originalRate = result == null ? null
                    : rateIndex.byId().get(result.getSettlementBatchRateId());
            SettlementBatchRateDO labelRate = rateIndex.bySourceCurrency().get(detail.getLabelCurrency());
            SettlementProjectionTaskDO task = candidate == null ? null : taskByCandidate.get(candidate.getId());
            validateFrozenIdentity(batch, detail, candidate, result, originalRate, labelRate, task);
            if (!correctedCandidateIds.add(candidate.getId())) {
                throw new IllegalStateException(
                        "principal correction requires one principal result per transaction candidate");
            }
            RoundingMode roundingMode = requireRoundingMode(result.getRoundingMode());
            BigDecimal expected = detail.getLabelAmount().multiply(labelRate.getDirectRate())
                    .setScale(batch.getTargetCurrencyExponent(), roundingMode);
            BigDecimal signedDelta = signed(result.getDirection(), expected)
                    .subtract(signed(result.getDirection(), result.getTargetAmount()));
            BigDecimal correctedProjection = task.getSettlementAmount().add(signedDelta)
                    .setScale(batch.getTargetCurrencyExponent());
            requireMoney(expected, batch.getTargetCurrencyExponent(), false,
                    "principal correction expected target amount");
            requireMoney(signedDelta, batch.getTargetCurrencyExponent(), true,
                    "principal correction signed delta amount");
            requireMoney(correctedProjection, batch.getTargetCurrencyExponent(), true,
                    "principal correction corrected projection amount");
            frozen.add(new FrozenItem(detail, candidate, result, originalRate, labelRate, task,
                    expected, signedDelta, correctedProjection));
        }
        if (frozen.isEmpty()) {
            throw new IllegalStateException("principal correction batch has no label principal mismatch");
        }
        BigDecimal signedTotal = frozen.stream().map(FrozenItem::signedDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(batch.getTargetCurrencyExponent());
        String direction = signedTotal.signum() < 0 ? "DEBIT" : "CREDIT";
        BigDecimal amount = signedTotal.abs();
        requireMoney(amount, batch.getTargetCurrencyExponent(), false,
                "principal correction total amount");
        return new FrozenSource(originalLedger.getId(), List.copyOf(frozen), direction, amount,
                fingerprint(batch, originalLedger, frozen, direction, amount));
    }

    private void validateFrozenIdentity(SettlementBatchDO batch,
                                        SettlementTransactionClearingDetailDO detail,
                                        SettlementCandidateDO candidate,
                                        SettlementResultItemDO result,
                                        SettlementBatchRateDO originalRate,
                                        SettlementBatchRateDO labelRate,
                                        SettlementProjectionTaskDO task) {
        boolean valid = detail != null && candidate != null && result != null
                && originalRate != null && labelRate != null && task != null
                && candidate.getId() != null && candidate.getId() > 0
                && result.getId() != null && result.getId() > 0
                && task.getId() != null && task.getId() > 0
                && Objects.equals(candidate.getMerchantId(), batch.getMerchantId())
                && Objects.equals(candidate.getSettlementBatchNo(), batch.getSettlementBatchNo())
                && Objects.equals(candidate.getTargetCurrency(), batch.getTargetCurrency())
                && Objects.equals(candidate.getTargetCurrencyExponent(), batch.getTargetCurrencyExponent())
                && Objects.equals(detail.getMerchantId(), batch.getMerchantId())
                && Objects.equals(result.getCandidateId(), candidate.getId())
                && Objects.equals(result.getSettlementBatchNo(), batch.getSettlementBatchNo())
                && Objects.equals(result.getMerchantId(), batch.getMerchantId())
                && Objects.equals(result.getSettlementAccountId(), batch.getSettlementAccountId())
                && Objects.equals(result.getSourceDetailNo(), detail.getClearingDetailNo())
                && "PRINCIPAL".equals(result.getResultItemType())
                && "FINANCIAL_COMPONENT".equals(result.getResultRole())
                && ("CREDIT".equals(result.getDirection()) || "DEBIT".equals(result.getDirection()))
                && Objects.equals(result.getSettlementBatchRateId(), originalRate.getId())
                && result.getSourceAmount() != null && detail.getAmount() != null
                && result.getSourceAmount().compareTo(detail.getAmount()) == 0
                && Objects.equals(result.getSourceCurrency(), detail.getCurrency())
                && Objects.equals(result.getSourceCurrencyExponent(), detail.getCurrencyExponent())
                && result.getTargetAmount() != null && result.getTargetAmount().signum() >= 0
                && Objects.equals(result.getTargetCurrency(), batch.getTargetCurrency())
                && Objects.equals(result.getTargetCurrencyExponent(), batch.getTargetCurrencyExponent())
                && detail.getLabelAmount() != null && detail.getLabelAmount().signum() >= 0
                && validLockedRate(originalRate, batch, detail.getCurrency(), detail.getCurrencyExponent())
                && validLockedRate(labelRate, batch, detail.getLabelCurrency(),
                detail.getLabelCurrencyExponent())
                && Objects.equals(task.getSettlementBatchNo(), batch.getSettlementBatchNo())
                && "SETTLE".equals(task.getProjectionAction())
                && Objects.equals(task.getCandidateId(), candidate.getId())
                && Objects.equals(task.getTransactionId(), detail.getTransactionId())
                && Objects.equals(task.getTransactionDateTime(), detail.getTransactionDateTime())
                && Objects.equals(task.getClearingRevision(), detail.getClearingRevision())
                && Objects.equals(task.getOperationId(), detail.getOperationId())
                && Objects.equals(task.getMerchantId(), detail.getMerchantId())
                && Objects.equals(task.getSettlementCurrency(), batch.getTargetCurrency())
                && Objects.equals(task.getSettlementDate(), batch.getBusinessDate())
                && task.getSettlementAmount() != null && "COMPLETED".equals(task.getTaskStatus());
        if (!valid) {
            throw new IllegalStateException("principal correction frozen source identity is inconsistent");
        }
        requireCurrency(detail.getCurrency(), "principal correction source currency");
        requireExponent(detail.getCurrencyExponent(), "principal correction source exponent");
        requireMoney(detail.getAmount(), detail.getCurrencyExponent(), false,
                "principal correction source amount");
        requireCurrency(detail.getLabelCurrency(), "principal correction label currency");
        requireExponent(detail.getLabelCurrencyExponent(), "principal correction label exponent");
        requireMoney(detail.getLabelAmount(), detail.getLabelCurrencyExponent(), false,
                "principal correction label amount");
        requireCurrency(result.getTargetCurrency(), "principal correction target currency");
        requireExponent(result.getTargetCurrencyExponent(), "principal correction target exponent");
        requireMoney(result.getTargetAmount(), result.getTargetCurrencyExponent(), false,
                "principal correction original target amount");
        requireMoney(task.getSettlementAmount(), result.getTargetCurrencyExponent(), true,
                "principal correction original projection amount");
        requireRate(originalRate.getDirectRate());
        requireRate(labelRate.getDirectRate());
        requireRoundingMode(result.getRoundingMode());
    }

    /** 校验一条原批次汇率的归一方向、币种精度及不可变状态。 */
    private boolean validLockedRate(SettlementBatchRateDO rate,
                                    SettlementBatchDO batch,
                                    String sourceCurrency,
                                    Integer sourceCurrencyExponent) {
        return rate.getId() != null && rate.getId() > 0
                && Objects.equals(rate.getSettlementBatchNo(), batch.getSettlementBatchNo())
                && Objects.equals(rate.getSourceCurrency(), sourceCurrency)
                && Objects.equals(rate.getTargetCurrency(), batch.getTargetCurrency())
                && Objects.equals(rate.getSourceCurrencyExponent(), sourceCurrencyExponent)
                && Objects.equals(rate.getTargetCurrencyExponent(), batch.getTargetCurrencyExponent())
                && rate.getDirectRate() != null && rate.getDirectRate().signum() > 0
                && "SETTLEMENT".equals(rate.getRateType())
                && "LOCKED".equals(rate.getRateStatus());
    }

    /**
     * 核对原批唯一净入账结果及流水身份，避免错误幂等键把其他商户或批次资金纳入修复。
     */
    private boolean validOriginalPosting(SettlementResultItemDO net,
                                         MerchantFundLedgerDO ledger,
                                         SettlementBatchDO batch) {
        if (net == null || ledger == null || ledger.getId() == null || ledger.getId() <= 0
                || net.getTargetAmount() == null || net.getTargetAmount().signum() < 0) {
            return false;
        }
        requireMoney(net.getTargetAmount(), batch.getTargetCurrencyExponent(), false,
                "principal correction original net amount");
        return Objects.equals(net.getSettlementBatchNo(), batch.getSettlementBatchNo())
                && Objects.equals(net.getMerchantId(), batch.getMerchantId())
                && Objects.equals(net.getSettlementAccountId(), batch.getSettlementAccountId())
                && "LEDGER_POSTING".equals(net.getResultRole())
                && "NET_SETTLEMENT".equals(net.getResultItemType())
                && ("CREDIT".equals(net.getDirection()) || "DEBIT".equals(net.getDirection()))
                && Objects.equals(net.getTargetCurrency(), batch.getTargetCurrency())
                && Objects.equals(net.getTargetCurrencyExponent(), batch.getTargetCurrencyExponent())
                && Objects.equals(net.getLedgerIdempotencyKey(),
                        "SETTLEMENT:" + batch.getSettlementBatchNo())
                && Objects.equals(ledger.getAccountId(), batch.getSettlementAccountId())
                && Objects.equals(ledger.getMerchantId(), batch.getMerchantId())
                && "TRANSACTION_SETTLEMENT".equals(ledger.getBusinessType())
                && Objects.equals(ledger.getBusinessNo(), batch.getSettlementBatchNo())
                && Objects.equals(ledger.getSettlementBatchNo(), batch.getSettlementBatchNo())
                && Objects.equals(ledger.getCurrency(), batch.getTargetCurrency())
                && Objects.equals(ledger.getDirection(), net.getDirection())
                && decimalEquals(ledger.getAmount(), net.getTargetAmount())
                && Objects.equals(ledger.getIdempotencyKey(), net.getLedgerIdempotencyKey());
    }

    /**
     * 在账户行锁内追加批次唯一差额流水并以旧余额、账户版本双条件更新余额。
     */
    private Long postLedger(SettlementPrincipalCorrectionOrderDO order, LocalDateTime now) {
        if (order.getCorrectionAmount().signum() == 0) {
            return null;
        }
        MerchantFundAccountDO account = fundMapper.selectAccountForUpdate(order.getSettlementAccountId());
        if (account == null || account.getAvailableBalance() == null || account.getAccountVersion() == null
                || !Objects.equals(account.getMerchantId(), order.getMerchantId())
                || !Objects.equals(account.getSettlementCurrency(), order.getTargetCurrency())
                || !"NORMAL".equals(account.getAccountStatus())) {
            throw new IllegalStateException("principal correction fund account is invalid");
        }
        String idempotencyKey = "SETTLEMENT_PRINCIPAL_CORRECTION:" + order.getCorrectionNo();
        MerchantFundLedgerDO existing = fundMapper.selectLedgerByIdempotencyForUpdate(idempotencyKey);
        if (existing != null) {
            verifyCorrectionLedger(existing, order);
            return existing.getId();
        }
        long sequence = Objects.requireNonNullElse(
                fundMapper.selectMaxAccountSequence(account.getId()), 0L) + 1L;
        BigDecimal before = account.getAvailableBalance();
        BigDecimal after = "CREDIT".equals(order.getCorrectionDirection())
                ? before.add(order.getCorrectionAmount()) : before.subtract(order.getCorrectionAmount());
        requireMoney(before, order.getTargetCurrencyExponent(), true,
                "principal correction balance before");
        requireMoney(after, order.getTargetCurrencyExponent(), true,
                "principal correction balance after");
        MerchantFundLedgerDO ledger = correctionLedger(order, before, after, sequence,
                idempotencyKey, now);
        if (fundMapper.insertLedger(ledger) != 1
                || fundMapper.updateAccountBalance(account.getId(), after, before,
                account.getAccountVersion(), now) != 1) {
            throw new IllegalStateException("principal correction fund posting failed");
        }
        MerchantFundLedgerDO stored = fundMapper.selectLedgerByIdempotencyForUpdate(idempotencyKey);
        verifyCorrectionLedger(stored, order);
        return stored.getId();
    }

    /** 构造包含 Maker、Checker、余额前后值和原批次引用的不可变差额流水。 */
    private MerchantFundLedgerDO correctionLedger(SettlementPrincipalCorrectionOrderDO order,
                                                   BigDecimal before,
                                                   BigDecimal after,
                                                   long sequence,
                                                   String idempotencyKey,
                                                   LocalDateTime now) {
        MerchantFundLedgerDO row = new MerchantFundLedgerDO();
        row.setLedgerNo(stableId("SLPC", order.getCorrectionNo()));
        row.setLedgerGroupNo(order.getOriginalBatchNo());
        row.setAccountId(order.getSettlementAccountId());
        row.setMerchantId(order.getMerchantId());
        row.setBusinessType("SETTLEMENT_PRINCIPAL_CORRECTION");
        row.setSummary("Approved historical label principal difference correction");
        row.setBusinessNo(order.getCorrectionNo());
        row.setSettlementBatchNo(order.getOriginalBatchNo());
        row.setCurrency(order.getTargetCurrency());
        row.setDirection(order.getCorrectionDirection());
        row.setAmount(order.getCorrectionAmount());
        row.setBalanceBefore(before);
        row.setBalanceAfter(after);
        row.setAccountSequence(sequence);
        row.setOperationMode("MANUAL");
        row.setOperatorId(order.getSubmittedByAccountId());
        row.setOperatorName(order.getSubmittedByAccountName());
        row.setReviewerId(order.getDecidedByAccountId());
        row.setReviewerName(order.getDecidedByAccountName());
        row.setOperationReason(order.getSubmitReason());
        row.setReviewComment(order.getDecisionComment());
        row.setBusinessTime(order.getSubmittedTime());
        row.setSubmitTime(order.getSubmittedTime());
        row.setReviewTime(order.getDecisionTime());
        row.setPostedTime(now);
        row.setRequestId(order.getDecisionRequestKey());
        row.setIdempotencyKey(idempotencyKey);
        row.setCreateTime(now);
        return row;
    }

    /**
     * 三层投影均使用冻结旧值 CAS；更新为零时只接受完全一致重放或更晚动作已覆盖主单。
     */
    private void correctProjection(SettlementPrincipalCorrectionItemDO item,
                                   SettlementOperationIdentityDO operation,
                                   String originalBatchNo,
                                   LocalDateTime now) {
        int financeUpdated = correctionProjectionMapper.correctFinanceState(
                item.getTransactionId(), item.getTransactionDateTime(), item.getClearingRevision(),
                item.getTargetCurrency(), item.getOriginalProjectionAmount(),
                item.getCorrectedProjectionAmount(), item.getLabelLockedDirectRate(),
                item.getSettlementDate(), originalBatchNo, now);
        if (financeUpdated == 0 && correctionProjectionMapper.countCorrectedFinanceState(
                item.getTransactionId(), item.getTransactionDateTime(), item.getClearingRevision(),
                item.getTargetCurrency(), item.getCorrectedProjectionAmount(),
                item.getLabelLockedDirectRate(),
                item.getSettlementDate(), originalBatchNo) != 1) {
            throw new IllegalStateException("principal correction finance projection CAS failed");
        }
        int operationUpdated = correctionProjectionMapper.correctOperation(
                item.getTransactionId(), item.getTransactionDateTime(), item.getTargetCurrency(),
                item.getOriginalProjectionAmount(), item.getCorrectedProjectionAmount(),
                item.getLabelLockedDirectRate(), item.getSettlementDate(), originalBatchNo, now);
        if (operationUpdated == 0 && correctionProjectionMapper.countCorrectedOperation(
                item.getTransactionId(), item.getTransactionDateTime(), item.getTargetCurrency(),
                item.getCorrectedProjectionAmount(), item.getLabelLockedDirectRate(),
                item.getSettlementDate(),
                originalBatchNo) != 1) {
            throw new IllegalStateException("principal correction operation projection CAS failed");
        }
        int orderUpdated = correctionProjectionMapper.correctOrder(
                item.getOperationId(), operation.getRootTransactionDateTime(), item.getTransactionId(),
                item.getTransactionDateTime(), item.getTargetCurrency(), item.getOriginalProjectionAmount(),
                item.getCorrectedProjectionAmount(), item.getLabelLockedDirectRate(),
                item.getSettlementDate(),
                originalBatchNo, now);
        if (orderUpdated == 0 && correctionProjectionMapper.countCorrectedOrder(
                item.getOperationId(), operation.getRootTransactionDateTime(), item.getTransactionId(),
                item.getTransactionDateTime(), item.getTargetCurrency(), item.getCorrectedProjectionAmount(),
                item.getLabelLockedDirectRate(), item.getSettlementDate(), originalBatchNo) != 1
                && projectionMapper.countNewerOrderSettlement(
                item.getOperationId(), operation.getRootTransactionDateTime(), item.getTransactionId(),
                item.getTransactionDateTime()) != 1) {
            throw new IllegalStateException("principal correction order projection CAS failed");
        }
    }

    /** 投影执行前重新确认动作单仍属于同一商户、生命周期和标签币种。 */
    private void validateProjectionIdentity(SettlementPrincipalCorrectionItemDO item,
                                            SettlementOperationIdentityDO operation) {
        if (operation == null || operation.getRootTransactionDateTime() == null
                || !Objects.equals(operation.getTransactionId(), item.getTransactionId())
                || !Objects.equals(operation.getOperationId(), item.getOperationId())
                || !Objects.equals(operation.getMerchantId(), item.getMerchantId())
                || !Objects.equals(operation.getTransactionCurrency(), item.getLabelCurrency())
                || !"SETTLED".equals(operation.getSettlementStatus())) {
            throw new IllegalStateException("principal correction projection identity is inconsistent");
        }
    }

    /** 将审批后不可重算的批次汇总和 Maker 审计冻结为调整主单。 */
    private SettlementPrincipalCorrectionOrderDO order(
            String correctionNo,
            SettlementBatchDO batch,
            FrozenSource source,
            SettlementPrincipalCorrectionSubmitCommand command,
            LocalDateTime now) {
        SettlementPrincipalCorrectionOrderDO row = new SettlementPrincipalCorrectionOrderDO();
        row.setCorrectionNo(correctionNo);
        row.setSubmitRequestKey(command.requestKey());
        row.setOriginalBatchNo(batch.getSettlementBatchNo());
        row.setMerchantId(batch.getMerchantId());
        row.setSettlementAccountId(batch.getSettlementAccountId());
        row.setTargetCurrency(batch.getTargetCurrency());
        row.setTargetCurrencyExponent(batch.getTargetCurrencyExponent());
        row.setOriginalBatchVersion(batch.getVersion());
        row.setOriginalFundLedgerId(source.originalFundLedgerId());
        row.setItemCount(source.items().size());
        row.setCorrectionDirection(source.direction());
        row.setCorrectionAmount(source.amount());
        row.setSourceFingerprint(source.fingerprint());
        row.setCorrectionStatus(SettlementPrincipalCorrectionStatus.PENDING_REVIEW.name());
        row.setSubmittedByAccountId(command.operator().accountId());
        row.setSubmittedByAccountName(command.operator().accountName());
        row.setSubmittedRoleSnapshot(command.operator().roleSnapshot());
        row.setSubmitClientIp(command.operator().clientIp());
        row.setSubmitUserAgent(command.operator().userAgent());
        row.setSubmitReason(command.reason());
        row.setSubmittedTime(command.operator().operationTime());
        row.setProjectionCompletedCount(0);
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 将逐笔金额、汇率、舍入和投影旧值冻结为可审计修正任务。 */
    private SettlementPrincipalCorrectionItemDO item(String correctionNo,
                                                      FrozenItem frozen,
                                                      LocalDateTime now) {
        SettlementTransactionClearingDetailDO detail = frozen.detail();
        SettlementResultItemDO result = frozen.result();
        SettlementProjectionTaskDO task = frozen.task();
        SettlementPrincipalCorrectionItemDO row = new SettlementPrincipalCorrectionItemDO();
        row.setCorrectionItemNo(stableId("SPCI", correctionNo + "|" + result.getId()));
        row.setCorrectionNo(correctionNo);
        row.setOriginalResultItemId(result.getId());
        row.setOriginalProjectionTaskId(task.getId());
        row.setOriginalProjectionTaskNo(task.getTaskNo());
        row.setCandidateId(frozen.candidate().getId());
        row.setClearingDetailNo(detail.getClearingDetailNo());
        row.setTransactionId(detail.getTransactionId());
        row.setTransactionDateTime(detail.getTransactionDateTime());
        row.setClearingRevision(detail.getClearingRevision());
        row.setOperationId(detail.getOperationId());
        row.setMerchantId(detail.getMerchantId());
        row.setDirection(result.getDirection());
        row.setOriginalSourceAmount(detail.getAmount());
        row.setOriginalSourceCurrency(detail.getCurrency());
        row.setOriginalSourceCurrencyExponent(detail.getCurrencyExponent());
        row.setLabelAmount(detail.getLabelAmount());
        row.setLabelCurrency(detail.getLabelCurrency());
        row.setLabelCurrencyExponent(detail.getLabelCurrencyExponent());
        row.setOriginalLockedRateId(frozen.originalRate().getId());
        row.setOriginalLockedDirectRate(frozen.originalRate().getDirectRate());
        row.setLabelLockedRateId(frozen.labelRate().getId());
        row.setLabelLockedDirectRate(frozen.labelRate().getDirectRate());
        row.setTargetCurrency(result.getTargetCurrency());
        row.setTargetCurrencyExponent(result.getTargetCurrencyExponent());
        row.setRoundingMode(result.getRoundingMode());
        row.setOriginalTargetAmount(result.getTargetAmount());
        row.setExpectedTargetAmount(frozen.expectedTarget());
        row.setSignedDeltaAmount(frozen.signedDelta());
        row.setOriginalProjectionAmount(task.getSettlementAmount());
        row.setCorrectedProjectionAmount(frozen.correctedProjection());
        row.setSettlementDate(task.getSettlementDate());
        row.setProjectionStatus("WAITING_REVIEW");
        row.setRetryCount(0);
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 在状态 CAS 前把可信 Checker 决策快照写入待持久化主单。 */
    private void applyDecision(SettlementPrincipalCorrectionOrderDO order,
                               SettlementPrincipalCorrectionDecisionCommand command) {
        SettlementOperatorSnapshot operator = command.operator();
        order.setDecidedByAccountId(operator.accountId());
        order.setDecidedByAccountName(operator.accountName());
        order.setDecidedRoleSnapshot(operator.roleSnapshot());
        order.setDecisionClientIp(operator.clientIp());
        order.setDecisionUserAgent(operator.userAgent());
        order.setDecisionAction(command.decision());
        order.setDecisionRequestKey(command.requestKey());
        order.setDecisionComment(command.comment());
        order.setDecisionTime(operator.operationTime());
    }

    /** 审批时对比重新锁读来源的总额、条数、原流水及完整指纹。 */
    private void verifyFrozenSource(SettlementPrincipalCorrectionOrderDO order,
                                    SettlementBatchDO batch,
                                    FrozenSource source) {
        if (!Objects.equals(order.getOriginalBatchNo(), batch.getSettlementBatchNo())
                || !Objects.equals(order.getOriginalBatchVersion(), batch.getVersion())
                || !Objects.equals(order.getMerchantId(), batch.getMerchantId())
                || !Objects.equals(order.getSettlementAccountId(), batch.getSettlementAccountId())
                || !Objects.equals(order.getTargetCurrency(), batch.getTargetCurrency())
                || !Objects.equals(order.getTargetCurrencyExponent(), batch.getTargetCurrencyExponent())
                || !Objects.equals(order.getOriginalFundLedgerId(), source.originalFundLedgerId())
                || !Objects.equals(order.getItemCount(), source.items().size())
                || !Objects.equals(order.getCorrectionDirection(), source.direction())
                || !decimalEquals(order.getCorrectionAmount(), source.amount())
                || !Objects.equals(order.getSourceFingerprint(), source.fingerprint())) {
            throw new IllegalStateException("principal correction source changed after submission");
        }
    }

    /**
     * 逐字段核对数据库冻结项与重新计算来源，覆盖所有后续记账或投影使用的字段。
     */
    private void verifyStoredItems(List<SettlementPrincipalCorrectionItemDO> stored,
                                   List<FrozenItem> current) {
        if (stored == null || stored.size() != current.size()) {
            throw new IllegalStateException("principal correction stored item count changed");
        }
        for (int index = 0; index < stored.size(); index++) {
            SettlementPrincipalCorrectionItemDO left = stored.get(index);
            FrozenItem right = current.get(index);
            String expectedItemNo = stableId("SPCI",
                    left.getCorrectionNo() + "|" + right.result().getId());
            if (!Objects.equals(left.getCorrectionItemNo(), expectedItemNo)
                    || !Objects.equals(left.getOriginalResultItemId(), right.result().getId())
                    || !Objects.equals(left.getOriginalProjectionTaskId(), right.task().getId())
                    || !Objects.equals(left.getOriginalProjectionTaskNo(), right.task().getTaskNo())
                    || !Objects.equals(left.getCandidateId(), right.candidate().getId())
                    || !Objects.equals(left.getClearingDetailNo(), right.detail().getClearingDetailNo())
                    || !Objects.equals(left.getTransactionId(), right.detail().getTransactionId())
                    || !Objects.equals(left.getTransactionDateTime(), right.detail().getTransactionDateTime())
                    || !Objects.equals(left.getClearingRevision(), right.detail().getClearingRevision())
                    || !Objects.equals(left.getOperationId(), right.detail().getOperationId())
                    || !Objects.equals(left.getMerchantId(), right.detail().getMerchantId())
                    || !Objects.equals(left.getDirection(), right.result().getDirection())
                    || !decimalEquals(left.getOriginalSourceAmount(), right.detail().getAmount())
                    || !Objects.equals(left.getOriginalSourceCurrency(), right.detail().getCurrency())
                    || !Objects.equals(left.getOriginalSourceCurrencyExponent(),
                            right.detail().getCurrencyExponent())
                    || !decimalEquals(left.getLabelAmount(), right.detail().getLabelAmount())
                    || !Objects.equals(left.getLabelCurrency(), right.detail().getLabelCurrency())
                    || !Objects.equals(left.getLabelCurrencyExponent(),
                            right.detail().getLabelCurrencyExponent())
                    || !Objects.equals(left.getOriginalLockedRateId(), right.originalRate().getId())
                    || !decimalEquals(left.getOriginalLockedDirectRate(),
                            right.originalRate().getDirectRate())
                    || !Objects.equals(left.getLabelLockedRateId(), right.labelRate().getId())
                    || !decimalEquals(left.getLabelLockedDirectRate(),
                            right.labelRate().getDirectRate())
                    || !Objects.equals(left.getTargetCurrency(), right.result().getTargetCurrency())
                    || !Objects.equals(left.getTargetCurrencyExponent(),
                            right.result().getTargetCurrencyExponent())
                    || !Objects.equals(left.getRoundingMode(), right.result().getRoundingMode())
                    || !decimalEquals(left.getOriginalTargetAmount(), right.result().getTargetAmount())
                    || !decimalEquals(left.getExpectedTargetAmount(), right.expectedTarget())
                    || !decimalEquals(left.getSignedDeltaAmount(), right.signedDelta())
                    || !decimalEquals(left.getOriginalProjectionAmount(),
                            right.task().getSettlementAmount())
                    || !decimalEquals(left.getCorrectedProjectionAmount(), right.correctedProjection())
                    || !Objects.equals(left.getSettlementDate(), right.task().getSettlementDate())
                    || !initialProjectionState(left)) {
                throw new IllegalStateException("principal correction stored item changed after submission");
            }
        }
    }

    /**
     * 审批只能激活从未处理过的冻结项，避免失败任务或完成事实被重新置为初始状态。
     */
    private boolean initialProjectionState(SettlementPrincipalCorrectionItemDO item) {
        return "WAITING_REVIEW".equals(item.getProjectionStatus())
                && Objects.equals(item.getRetryCount(), 0)
                && item.getNextRetryTime() == null
                && item.getLastFailureCode() == null
                && item.getCompletedTime() == null
                && Objects.equals(item.getVersion(), 0L);
    }

    /** 对差额流水幂等重放核对完整资金身份、余额关系和 Maker-Checker 审计。 */
    private void verifyCorrectionLedger(MerchantFundLedgerDO ledger,
                                        SettlementPrincipalCorrectionOrderDO order) {
        boolean matches = ledger != null && ledger.getId() != null
                && Objects.equals(ledger.getAccountId(), order.getSettlementAccountId())
                && Objects.equals(ledger.getMerchantId(), order.getMerchantId())
                && Objects.equals(ledger.getBusinessType(), "SETTLEMENT_PRINCIPAL_CORRECTION")
                && Objects.equals(ledger.getBusinessNo(), order.getCorrectionNo())
                && Objects.equals(ledger.getSettlementBatchNo(), order.getOriginalBatchNo())
                && Objects.equals(ledger.getCurrency(), order.getTargetCurrency())
                && Objects.equals(ledger.getDirection(), order.getCorrectionDirection())
                && ledger.getAmount() != null
                && ledger.getAmount().compareTo(order.getCorrectionAmount()) == 0
                && ledger.getBalanceBefore() != null && ledger.getBalanceAfter() != null
                && decimalEquals(ledger.getBalanceAfter(), "CREDIT".equals(ledger.getDirection())
                        ? ledger.getBalanceBefore().add(ledger.getAmount())
                        : ledger.getBalanceBefore().subtract(ledger.getAmount()))
                && ledger.getAccountSequence() != null && ledger.getAccountSequence() > 0
                && "MANUAL".equals(ledger.getOperationMode())
                && Objects.equals(ledger.getOperatorId(), order.getSubmittedByAccountId())
                && Objects.equals(ledger.getOperatorName(), order.getSubmittedByAccountName())
                && Objects.equals(ledger.getReviewerId(), order.getDecidedByAccountId())
                && Objects.equals(ledger.getReviewerName(), order.getDecidedByAccountName())
                && Objects.equals(ledger.getOperationReason(), order.getSubmitReason())
                && Objects.equals(ledger.getReviewComment(), order.getDecisionComment())
                && Objects.equals(ledger.getBusinessTime(), order.getSubmittedTime())
                && Objects.equals(ledger.getSubmitTime(), order.getSubmittedTime())
                && Objects.equals(ledger.getReviewTime(), order.getDecisionTime())
                && Objects.equals(ledger.getRequestId(), order.getDecisionRequestKey())
                && Objects.equals(ledger.getIdempotencyKey(),
                        "SETTLEMENT_PRINCIPAL_CORRECTION:" + order.getCorrectionNo());
        if (!matches) {
            throw new IllegalStateException("principal correction ledger identity is inconsistent");
        }
    }

    /** 同一 Maker 幂等键只能表达相同批次、版本、原因和操作账号。 */
    private void verifySubmitReplay(SettlementPrincipalCorrectionOrderDO order,
                                    SettlementPrincipalCorrectionSubmitCommand command) {
        if (!Objects.equals(order.getOriginalBatchNo(), command.originalBatchNo())
                || !Objects.equals(order.getOriginalBatchVersion(), command.expectedBatchVersion())
                || !Objects.equals(order.getSubmitReason(), command.reason())
                || !Objects.equals(order.getSubmittedByAccountId(), command.operator().accountId())) {
            throw new IllegalStateException("principal correction submit idempotency conflict");
        }
    }

    /** 同一 Checker 幂等键只能表达同一调整单、动作、意见和操作账号。 */
    private void verifyDecisionReplay(SettlementPrincipalCorrectionOrderDO order,
                                      String correctionNo,
                                      SettlementPrincipalCorrectionDecisionCommand command) {
        if (!Objects.equals(order.getCorrectionNo(), correctionNo)
                || !Objects.equals(order.getDecisionAction(), command.decision())
                || !Objects.equals(order.getDecisionComment(), command.comment())
                || !Objects.equals(order.getDecidedByAccountId(), command.operator().accountId())) {
            throw new IllegalStateException("principal correction decision idempotency conflict");
        }
    }

    private SettlementPrincipalCorrectionCommandResult result(
            SettlementPrincipalCorrectionOrderDO order) {
        return new SettlementPrincipalCorrectionCommandResult(
                order.getCorrectionNo(), order.getCorrectionStatus(), order.getOriginalBatchNo(),
                order.getMerchantId(), order.getTargetCurrency(), order.getCorrectionDirection(),
                order.getCorrectionAmount(), order.getItemCount(), order.getProjectionCompletedCount(),
                order.getVersion());
    }

    private Map<FactKey, SettlementCandidateDO> uniqueCandidates(List<SettlementCandidateDO> rows) {
        Map<FactKey, SettlementCandidateDO> result = new HashMap<>();
        for (SettlementCandidateDO row : rows) {
            FactKey key = new FactKey(row.getSourceTransactionId(), row.getSourceTransactionDateTime(),
                    row.getSourceRevision());
            if (result.put(key, row) != null) {
                throw new IllegalStateException("principal correction candidate identity is duplicated");
            }
        }
        return result;
    }

    private Map<String, SettlementResultItemDO> uniquePrincipalResults(List<SettlementResultItemDO> rows) {
        Map<String, SettlementResultItemDO> result = new HashMap<>();
        for (SettlementResultItemDO row : rows) {
            if (!"PRINCIPAL".equals(row.getResultItemType())) {
                continue;
            }
            if (row.getSourceDetailNo() == null || result.put(row.getSourceDetailNo(), row) != null) {
                throw new IllegalStateException("principal correction result identity is duplicated");
            }
        }
        return result;
    }

    private RateIndex uniqueRates(List<SettlementBatchRateDO> rows,
                                  SettlementBatchDO batch) {
        Map<Long, SettlementBatchRateDO> byId = new HashMap<>();
        Map<String, SettlementBatchRateDO> bySourceCurrency = new HashMap<>();
        for (SettlementBatchRateDO row : rows) {
            if (row == null || row.getId() == null || row.getId() <= 0
                    || !Objects.equals(row.getTargetCurrency(), batch.getTargetCurrency())) {
                throw new IllegalStateException("principal correction batch rate target is inconsistent");
            }
            if (byId.put(row.getId(), row) != null
                    || bySourceCurrency.put(row.getSourceCurrency(), row) != null) {
                throw new IllegalStateException("principal correction batch rate is duplicated");
            }
        }
        return new RateIndex(Map.copyOf(byId), Map.copyOf(bySourceCurrency));
    }

    private Map<Long, SettlementProjectionTaskDO> uniqueTasks(List<SettlementProjectionTaskDO> rows) {
        Map<Long, SettlementProjectionTaskDO> result = new HashMap<>();
        for (SettlementProjectionTaskDO row : rows) {
            if (result.put(row.getCandidateId(), row) != null) {
                throw new IllegalStateException("principal correction projection task is duplicated");
            }
        }
        return result;
    }

    private boolean isLabelMismatch(SettlementTransactionClearingDetailDO row) {
        return row.getAmount() == null || row.getLabelAmount() == null
                || !Objects.equals(row.getCurrency(), row.getLabelCurrency())
                || row.getAmount().compareTo(row.getLabelAmount()) != 0
                || !Objects.equals(row.getCurrencyExponent(), row.getLabelCurrencyExponent());
    }

    private void requireCurrency(String currency, String field) {
        if (currency == null || !ISO_CURRENCY.matcher(currency).matches()) {
            throw new IllegalStateException(field + " is invalid");
        }
    }

    private void requireExponent(Integer exponent, String field) {
        if (exponent == null || exponent < 0 || exponent > MAX_CURRENCY_EXPONENT) {
            throw new IllegalStateException(field + " is invalid");
        }
    }

    /**
     * 金额必须可无损落入 DECIMAL(24,8)，并且不得携带超过所属币种 exponent 的有效小数。
     */
    private void requireMoney(BigDecimal value,
                              int exponent,
                              boolean signed,
                              String field) {
        if (value == null || (!signed && value.signum() < 0)
                || !fitsDecimal(value, MONEY_INTEGER_DIGITS, MONEY_SCALE)) {
            throw new IllegalStateException(field + " is invalid");
        }
        try {
            value.setScale(exponent, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(field + " exceeds currency exponent", exception);
        }
    }

    /**
     * 锁定直接汇率必须保持正数并可无损落入 DECIMAL(24,12)，禁止审批时隐式截断。
     */
    private void requireRate(BigDecimal value) {
        if (value == null || value.signum() <= 0
                || !fitsDecimal(value, RATE_INTEGER_DIGITS, RATE_SCALE)) {
            throw new IllegalStateException("principal correction locked direct rate is invalid");
        }
    }

    private boolean fitsDecimal(BigDecimal value, int integerDigits, int scale) {
        BigDecimal normalized = value.stripTrailingZeros();
        int effectiveIntegerDigits = Math.max(0, normalized.precision() - normalized.scale());
        int effectiveScale = Math.max(0, normalized.scale());
        return effectiveIntegerDigits <= integerDigits && effectiveScale <= scale;
    }

    private boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private RoundingMode requireRoundingMode(String value) {
        try {
            RoundingMode mode = RoundingMode.valueOf(value);
            if (mode != RoundingMode.HALF_UP && mode != RoundingMode.HALF_EVEN
                    && mode != RoundingMode.DOWN) {
                throw new IllegalArgumentException("unsupported rounding mode");
            }
            return mode;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("principal correction rounding mode is invalid", exception);
        }
    }

    private BigDecimal signed(String direction, BigDecimal amount) {
        return switch (direction) {
            case "CREDIT" -> amount;
            case "DEBIT" -> amount.negate();
            default -> throw new IllegalStateException("principal correction direction is invalid");
        };
    }

    private String fingerprint(SettlementBatchDO batch,
                               MerchantFundLedgerDO ledger,
                               List<FrozenItem> items,
                               String direction,
                               BigDecimal amount) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, batch.getSettlementBatchNo(), batch.getVersion(), batch.getMerchantId(),
                batch.getSettlementAccountId(), batch.getTargetCurrency(), batch.getTargetCurrencyExponent(),
                ledger.getId(), ledger.getIdempotencyKey(), direction, amount, items.size());
        for (FrozenItem item : items) {
            append(canonical, item.detail().getClearingDetailNo(), item.candidate().getId(),
                    item.result().getId(), item.task().getId(), item.task().getTaskNo(),
                    item.detail().getTransactionId(), item.detail().getTransactionDateTime(),
                    item.detail().getClearingRevision(), item.detail().getOperationId(),
                    item.detail().getMerchantId(), item.result().getDirection(),
                    item.detail().getAmount(), item.detail().getCurrency(),
                    item.detail().getCurrencyExponent(), item.detail().getLabelAmount(),
                    item.detail().getLabelCurrency(), item.detail().getLabelCurrencyExponent(),
                    item.result().getSettlementBatchRateId());
            appendRateIdentity(canonical, item.originalRate());
            appendRateIdentity(canonical, item.labelRate());
            append(canonical,
                    item.result().getTargetAmount(), item.expectedTarget(), item.signedDelta(),
                    item.task().getSettlementAmount(), item.correctedProjection(),
                    item.task().getSettlementDate(), item.result().getRoundingMode());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** 将汇率完整业务身份加入不可变来源指纹，避免只比较金额而遗漏汇率来源漂移。 */
    private void appendRateIdentity(StringBuilder target, SettlementBatchRateDO rate) {
        append(target, rate.getId(), rate.getSettlementBatchNo(), rate.getSourceCurrency(),
                rate.getTargetCurrency(), rate.getRateType(), rate.getDirectRate(),
                rate.getSourceCurrencyExponent(), rate.getTargetCurrencyExponent(),
                rate.getRateSource(), rate.getQuoteId(), rate.getSourceQuoteDirection(),
                rate.getEffectiveTime(), rate.getLockedTime(), rate.getLockedBy(), rate.getRateStatus());
    }

    private void append(StringBuilder target, Object... values) {
        for (Object value : values) {
            String text = value instanceof BigDecimal decimal
                    ? decimal.toPlainString() : String.valueOf(value);
            target.append(text.length()).append(':').append(text);
        }
    }

    private String stableId(String prefix, String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private record FactKey(String transactionId, LocalDateTime transactionDateTime, Integer revision) {
    }

    private record FrozenItem(SettlementTransactionClearingDetailDO detail,
                              SettlementCandidateDO candidate,
                              SettlementResultItemDO result,
                              SettlementBatchRateDO originalRate,
                              SettlementBatchRateDO labelRate,
                              SettlementProjectionTaskDO task,
                              BigDecimal expectedTarget,
                              BigDecimal signedDelta,
                              BigDecimal correctedProjection) {
    }

    private record RateIndex(Map<Long, SettlementBatchRateDO> byId,
                             Map<String, SettlementBatchRateDO> bySourceCurrency) {
    }

    private record FrozenSource(Long originalFundLedgerId,
                                List<FrozenItem> items,
                                String direction,
                                BigDecimal amount,
                                String fingerprint) {
    }
}
