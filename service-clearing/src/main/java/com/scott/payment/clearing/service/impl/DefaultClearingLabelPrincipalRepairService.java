package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.entity.ClearingLabelPrincipalRepairDO;
import com.scott.payment.clearing.entity.ClearingLabelPrincipalRepairDependencyAuditDO;
import com.scott.payment.clearing.entity.ClearingLabelPrincipalRepairReserveStateAuditDO;
import com.scott.payment.clearing.entity.ClearingReserveDetailDO;
import com.scott.payment.clearing.entity.ClearingReserveStateDO;
import com.scott.payment.clearing.entity.ClearingSettlementCandidateDO;
import com.scott.payment.clearing.entity.ClearingSettlementCandidateDependencyDO;
import com.scott.payment.clearing.entity.ClearingTransactionDetailDO;
import com.scott.payment.clearing.entity.ClearingTransactionEventOutboxDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.mapper.ClearingLabelPrincipalRepairMapper;
import com.scott.payment.clearing.mapper.ClearingLabelPrincipalRepairDependencyAuditMapper;
import com.scott.payment.clearing.mapper.ClearingLabelPrincipalRepairReserveStateAuditMapper;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.mapper.ClearingSettlementCandidateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionDetailMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionEventOutboxMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingLabelPrincipalRepairService;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService.LabelPrincipalRepairCandidateReplacement;
import com.scott.payment.clearing.support.ClearingTimeSupport;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 未结算历史标签本金修复实现；审批通过后原子追加新清分修订、替换候选并写入可靠事件。
 */
@Service
public class DefaultClearingLabelPrincipalRepairService implements ClearingLabelPrincipalRepairService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            DefaultClearingLabelPrincipalRepairService.class);
    private static final String PENDING_REVIEW = "PENDING_REVIEW";
    private static final String EXECUTED = "EXECUTED";
    private static final String REJECTED = "REJECTED";
    private static final Pattern REPAIR_NO_PATTERN = Pattern.compile("^LPR[0-9a-f]{32}$");

    private final ClearingLabelPrincipalRepairMapper repairMapper;
    private final ClearingLabelPrincipalRepairDependencyAuditMapper dependencyAuditMapper;
    private final ClearingLabelPrincipalRepairReserveStateAuditMapper reserveStateAuditMapper;
    private final ClearingTransactionFinanceStateMapper financeStateMapper;
    private final ClearingTransactionOperationMapper operationMapper;
    private final ClearingTransactionDetailMapper detailMapper;
    private final ClearingReserveMapper reserveMapper;
    private final ClearingSettlementCandidateMapper candidateMapper;
    private final ClearingSettlementCandidateService candidateService;
    private final ClearingTransactionEventOutboxMapper outboxMapper;

    public DefaultClearingLabelPrincipalRepairService(
            ClearingLabelPrincipalRepairMapper repairMapper,
            ClearingLabelPrincipalRepairDependencyAuditMapper dependencyAuditMapper,
            ClearingLabelPrincipalRepairReserveStateAuditMapper reserveStateAuditMapper,
            ClearingTransactionFinanceStateMapper financeStateMapper,
            ClearingTransactionOperationMapper operationMapper,
            ClearingTransactionDetailMapper detailMapper,
            ClearingReserveMapper reserveMapper,
            ClearingSettlementCandidateMapper candidateMapper,
            ClearingSettlementCandidateService candidateService,
            ClearingTransactionEventOutboxMapper outboxMapper) {
        this.repairMapper = repairMapper;
        this.dependencyAuditMapper = dependencyAuditMapper;
        this.reserveStateAuditMapper = reserveStateAuditMapper;
        this.financeStateMapper = financeStateMapper;
        this.operationMapper = operationMapper;
        this.detailMapper = detailMapper;
        this.reserveMapper = reserveMapper;
        this.candidateMapper = candidateMapper;
        this.candidateService = candidateService;
        this.outboxMapper = outboxMapper;
    }

    /**
     * 冻结当前未结算修订、候选版本和异常本金快照，提交阶段不修改任何资金事实。
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public RepairResult submit(SubmitCommand command) {
        validateSubmit(command);
        String repairNo = stableId("LPR", command.requestKey().trim());
        ClearingLabelPrincipalRepairDO duplicate = repairMapper.selectByRequestKeyForUpdate(
                command.requestKey().trim());
        if (duplicate != null) {
            validateDuplicate(duplicate, repairNo, command);
            return result(duplicate);
        }

        ClearingTransactionFinanceStateDO state = financeStateMapper.selectForUpdate(
                command.transactionId().trim(), command.transactionDateTime());
        ClearingTransactionOperationDO operation = operationMapper.selectByTransaction(
                command.transactionId().trim(), command.transactionDateTime());
        ClearingSettlementCandidateDO candidate = candidateMapper.selectForUpdate(
                state == null ? "" : state.getFinanceStateId(), command.expectedClearingRevision());
        List<ClearingTransactionDetailDO> details = detailMapper.selectActiveRevision(
                command.transactionId().trim(), command.transactionDateTime(),
                command.expectedClearingRevision());
        RepairableFacts facts = validateRepairable(
                command, state, operation, candidate, details);
        ClearingTransactionDetailDO principal = facts.principal();

        LocalDateTime now = ClearingTimeSupport.fromInstant(command.requestedInstant());
        ClearingLabelPrincipalRepairDO row = new ClearingLabelPrincipalRepairDO();
        row.setRepairNo(repairNo);
        row.setRequestKey(command.requestKey().trim());
        row.setTransactionId(state.getTransactionId());
        row.setTransactionDateTime(state.getTransactionDateTime());
        row.setFinanceStateId(state.getFinanceStateId());
        row.setMerchantId(state.getMerchantId());
        row.setExpectedFinanceStateVersion(state.getVersion());
        row.setExpectedClearingRevision(state.getClearingRevision());
        row.setCandidateId(candidate.getId());
        row.setExpectedCandidateVersion(candidate.getVersion());
        row.setPrincipalDetailNo(principal.getClearingDetailNo());
        row.setPrincipalAmountBefore(principal.getAmount());
        row.setPrincipalCurrencyBefore(principal.getCurrency());
        row.setPrincipalCurrencyExponentBefore(principal.getCurrencyExponent());
        row.setLabelAmount(principal.getLabelAmount());
        row.setLabelCurrency(principal.getLabelCurrency());
        row.setLabelCurrencyExponent(principal.getLabelCurrencyExponent());
        row.setSourceSnapshotHash(snapshotHash(details));
        row.setReserveFactCount(facts.reserveDetails().size());
        row.setReserveSnapshotHash(reserveSnapshotHash(facts.reserveDetails()));
        row.setReserveStateCount(facts.reserveState() == null ? 0 : 1);
        row.setReserveStateSnapshotHash(reserveStateSnapshotHash(facts.reserveState()));
        row.setDependencyCount(facts.dependency().count());
        row.setDependencySnapshotHash(dependencySnapshotHash(facts.dependency()));
        row.setReason(normalized(command.reason(), "reason", 400));
        row.setSubmitOperator(normalized(command.submitOperator(), "submit operator", 128));
        row.setRepairStatus(PENDING_REVIEW);
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        repairMapper.insertIdempotent(row);
        ClearingLabelPrincipalRepairDO persisted = repairMapper.selectByRequestKeyForUpdate(row.getRequestKey());
        validateDuplicate(persisted, repairNo, command);
        LOGGER.info("历史标签本金修复申请已提交, event: CLEARING_LABEL_PRINCIPAL_REPAIR_SUBMITTED, repairNo: {}, transactionId: {}",
                repairNo, row.getTransactionId());
        return result(persisted);
    }

    /**
     * 双人复核修复申请；批准时全部事实、状态、候选和 Outbox 在同一事务提交。
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public RepairResult review(ReviewCommand command) {
        validateReview(command);
        String reviewer = normalized(command.reviewOperator(), "review operator", 128);
        String comment = normalized(command.reviewComment(), "review comment", 400);
        ClearingLabelPrincipalRepairDO request = repairMapper.selectForUpdate(command.repairNo().trim());
        if (request == null) {
            throw new IllegalStateException("label principal repair request does not exist");
        }
        if (terminalReplay(request, command, reviewer, comment)) {
            return result(request);
        }
        if (!PENDING_REVIEW.equals(request.getRepairStatus()) || request.getVersion() == null
                || request.getVersion() != command.expectedRequestVersion()) {
            throw new IllegalStateException("label principal repair request is stale or not reviewable");
        }
        if (Objects.equals(request.getSubmitOperator(), reviewer)) {
            throw new IllegalStateException("label principal repair submitter and reviewer must be different");
        }
        LocalDateTime now = ClearingTimeSupport.fromInstant(command.reviewInstant());
        if (command.decision() == ReviewDecision.REJECT) {
            requireOne(repairMapper.markRejected(request.getRepairNo(), request.getVersion(),
                    reviewer, comment, now), "label principal repair reject CAS");
            return new RepairResult(request.getRepairNo(), REJECTED, request.getTransactionId(),
                    request.getTransactionDateTime(), request.getExpectedClearingRevision(), null,
                    null, request.getVersion() + 1);
        }
        return approve(request, reviewer, comment, now);
    }

    /**
     * 重新校验冻结事实后复制清分和保证金快照，仅纠正本金口径，不重新求值费用、保证金或汇率。
     */
    private RepairResult approve(ClearingLabelPrincipalRepairDO request,
                                 String reviewer,
                                 String comment,
                                 LocalDateTime now) {
        ClearingTransactionFinanceStateDO state = financeStateMapper.selectForUpdate(
                request.getTransactionId(), request.getTransactionDateTime());
        ClearingTransactionOperationDO operation = operationMapper.selectByTransaction(
                request.getTransactionId(), request.getTransactionDateTime());
        ClearingSettlementCandidateDO candidate = candidateMapper.selectForUpdate(
                request.getFinanceStateId(), request.getExpectedClearingRevision());
        List<ClearingTransactionDetailDO> details = detailMapper.selectActiveRevision(
                request.getTransactionId(), request.getTransactionDateTime(),
                request.getExpectedClearingRevision());
        RepairableFacts facts = validateFrozenRequest(request, state, operation, candidate, details);

        int newRevision = Math.addExact(request.getExpectedClearingRevision(), 1);
        List<ClearingTransactionDetailDO> repaired = details.stream()
                .map(detail -> repairedDetail(request, detail, newRevision, now))
                .toList();
        requireRows(detailMapper.insertBatch(repaired), repaired.size(),
                "repaired clearing detail insert");
        List<ClearingReserveDetailDO> repairedReserves = facts.reserveDetails().stream()
                .map(reserve -> repairedReserveDetail(request, reserve, newRevision, now))
                .toList();
        for (ClearingReserveDetailDO reserveDetail : repairedReserves) {
            requireOne(reserveMapper.insertDetail(reserveDetail), "repaired reserve detail insert");
        }
        persistReserveStateMigration(
                request, facts.reserveDetails(), repairedReserves, facts.reserveState(), now);
        requireRows(detailMapper.supersedeActiveRevision(
                request.getTransactionId(), request.getTransactionDateTime(),
                request.getExpectedClearingRevision(), now), details.size(),
                "old clearing revision supersede");
        if (!facts.reserveDetails().isEmpty()) {
            requireRows(reserveMapper.supersedeActiveRevision(
                    request.getTransactionId(), request.getTransactionDateTime(),
                    request.getExpectedClearingRevision(), now), facts.reserveDetails().size(),
                    "old reserve revision supersede");
        }
        requireOne(financeStateMapper.completeLabelPrincipalRepair(
                state.getId(), request.getTransactionId(), request.getTransactionDateTime(),
                request.getExpectedFinanceStateVersion(), request.getExpectedClearingRevision(),
                newRevision, now), "label principal repair finance state CAS");
        LabelPrincipalRepairCandidateReplacement replacement =
                candidateService.replaceForLabelPrincipalRepair(
                request.getFinanceStateId(), request.getExpectedClearingRevision(), newRevision,
                request.getCandidateId(), request.getExpectedCandidateVersion(),
                facts.dependency().count(), now);
        persistDependencyMigration(request, facts.dependency(), replacement, now);
        persistCompletionOutbox(request, operation, newRevision, now);
        requireOne(repairMapper.markExecuted(request.getRepairNo(), request.getVersion(), reviewer,
                comment, now, newRevision, replacement.newCandidateNo(),
                replacement.newCandidateId(), now), "label principal repair execute CAS");
        LOGGER.info("历史标签本金修复已执行, event: CLEARING_LABEL_PRINCIPAL_REPAIR_EXECUTED, repairNo: {}, transactionId: {}, newRevision: {}",
                request.getRepairNo(), request.getTransactionId(), newRevision);
        return new RepairResult(request.getRepairNo(), EXECUTED, request.getTransactionId(),
                request.getTransactionDateTime(), request.getExpectedClearingRevision(), newRevision,
                replacement.newCandidateNo(), request.getVersion() + 1);
    }

    /**
     * 申请和执行阶段共用的可修复门禁，仅支持单条 HOLD 保证金和单条保证金释放入向依赖。
     */
    private RepairableFacts validateRepairable(
            SubmitCommand command,
            ClearingTransactionFinanceStateDO state,
            ClearingTransactionOperationDO operation,
            ClearingSettlementCandidateDO candidate,
            List<ClearingTransactionDetailDO> details) {
        if (state == null || state.getId() == null || operation == null || candidate == null
                || !Objects.equals(state.getTransactionId(), command.transactionId().trim())
                || !Objects.equals(state.getTransactionDateTime(), command.transactionDateTime())
                || !Objects.equals(state.getOperationId(), operation.getOperationId())
                || !Objects.equals(state.getMerchantId(), operation.getMerchantId())
                || !"CLEARED".equals(state.getClearingStatus())
                || !"NOT_SETTLED".equals(state.getSettlementStatus())
                || !Objects.equals(state.getVersion(), command.expectedFinanceStateVersion())
                || !Objects.equals(state.getClearingRevision(), command.expectedClearingRevision())) {
            throw new IllegalStateException("label principal repair finance state is stale or ineligible");
        }
        validateCandidate(state, candidate, command.expectedCandidateVersion());
        ClearingTransactionDetailDO principal = validateDetails(state, operation, details);
        List<ClearingReserveDetailDO> reserves = reserveMapper.selectActiveRevision(
                state.getTransactionId(), state.getTransactionDateTime(), state.getClearingRevision());
        ClearingReserveStateDO reserveState = reserveMapper.selectStateForUpdate(
                state.getTransactionId(), state.getTransactionDateTime());
        validateReserveFacts(state, operation, reserves, reserveState);
        SupportedDependency dependency = validateDependency(state, candidate);
        return new RepairableFacts(principal, reserves, reserveState, dependency);
    }

    /**
     * 执行前要求申请冻结的版本、明细快照和异常本金仍完全一致。
     */
    private RepairableFacts validateFrozenRequest(ClearingLabelPrincipalRepairDO request,
                                                  ClearingTransactionFinanceStateDO state,
                                                  ClearingTransactionOperationDO operation,
                                                  ClearingSettlementCandidateDO candidate,
                                                  List<ClearingTransactionDetailDO> details) {
        SubmitCommand frozen = new SubmitCommand(request.getRequestKey(), request.getTransactionId(),
                request.getTransactionDateTime(), request.getExpectedFinanceStateVersion(),
                request.getExpectedClearingRevision(), request.getExpectedCandidateVersion(),
                request.getReason(), request.getSubmitOperator(),
                ClearingTimeSupport.toInstant(request.getCreateTime()));
        RepairableFacts facts = validateRepairable(
                frozen, state, operation, candidate, details);
        ClearingTransactionDetailDO principal = facts.principal();
        if (!Objects.equals(request.getCandidateId(), candidate.getId())
                || !Objects.equals(request.getPrincipalDetailNo(), principal.getClearingDetailNo())
                || !sameAmount(request.getPrincipalAmountBefore(), principal.getAmount())
                || !Objects.equals(request.getPrincipalCurrencyBefore(), principal.getCurrency())
                || !Objects.equals(request.getPrincipalCurrencyExponentBefore(), principal.getCurrencyExponent())
                || !sameAmount(request.getLabelAmount(), principal.getLabelAmount())
                || !Objects.equals(request.getLabelCurrency(), principal.getLabelCurrency())
                || !Objects.equals(request.getLabelCurrencyExponent(), principal.getLabelCurrencyExponent())
                || !Objects.equals(request.getSourceSnapshotHash(), snapshotHash(details))
                || !Objects.equals(request.getReserveFactCount(), facts.reserveDetails().size())
                || !Objects.equals(request.getReserveSnapshotHash(),
                    reserveSnapshotHash(facts.reserveDetails()))
                || !Objects.equals(request.getReserveStateCount(),
                    facts.reserveState() == null ? 0 : 1)
                || !Objects.equals(request.getReserveStateSnapshotHash(),
                    reserveStateSnapshotHash(facts.reserveState()))
                || !Objects.equals(request.getDependencyCount(), facts.dependency().count())
                || !Objects.equals(request.getDependencySnapshotHash(),
                    dependencySnapshotHash(facts.dependency()))) {
            throw new IllegalStateException("label principal repair frozen facts have changed");
        }
        return facts;
    }

    /**
     * 候选必须仍是同一未认领 READY 修订，且版本与申请冻结值一致。
     */
    private void validateCandidate(ClearingTransactionFinanceStateDO state,
                                   ClearingSettlementCandidateDO candidate,
                                   long expectedCandidateVersion) {
        if (candidate.getId() == null || !"CLEARING_REVISION".equals(candidate.getSourceType())
                || !Objects.equals(candidate.getSourceBusinessId(), state.getFinanceStateId())
                || !Objects.equals(candidate.getSourceRevision(), state.getClearingRevision())
                || !Objects.equals(candidate.getSourceTransactionId(), state.getTransactionId())
                || !Objects.equals(candidate.getSourceTransactionDateTime(), state.getTransactionDateTime())
                || !Objects.equals(candidate.getMerchantId(), state.getMerchantId())
                || !"READY".equals(candidate.getCandidateStatus())
                || candidate.getSettlementBatchNo() != null
                || !Objects.equals(candidate.getVersion(), expectedCandidateVersion)) {
            throw new IllegalStateException("label principal repair candidate is stale or already claimed");
        }
    }

    /**
     * 保证金扩展只允许无保证金，或一条属于当前修订的 ACTIVE HOLD；其他生命周期形态继续失败关闭。
     */
    private void validateReserveFacts(ClearingTransactionFinanceStateDO state,
                                      ClearingTransactionOperationDO operation,
                                      List<ClearingReserveDetailDO> reserves,
                                      ClearingReserveStateDO reserveState) {
        if (reserves == null || reserves.size() > 1) {
            throw new IllegalStateException("label principal repair reserve fact shape is unsupported");
        }
        if (reserves.isEmpty()) {
            if (reserveState != null) {
                throw new IllegalStateException("label principal repair reserve state has no active HOLD");
            }
            return;
        }
        ClearingReserveDetailDO reserve = reserves.get(0);
        if (reserve.getId() == null || !"ACTIVE".equals(reserve.getRecordStatus())
                || !"HOLD".equals(reserve.getReserveActionType())
                || reserve.getSourceReserveDetailNo() != null
                || !StringUtils.hasText(reserve.getOriginalTransactionId())
                || reserve.getOriginalTransactionDateTime() == null
                || !Objects.equals(reserve.getFinanceStateId(), state.getFinanceStateId())
                || !Objects.equals(reserve.getTransactionId(), state.getTransactionId())
                || !Objects.equals(reserve.getOperationId(), state.getOperationId())
                || !Objects.equals(reserve.getMerchantId(), state.getMerchantId())
                || !Objects.equals(reserve.getTransactionType(), operation.getTransactionType())
                || !Objects.equals(reserve.getClearingRevision(), state.getClearingRevision())
                || !Objects.equals(reserve.getTransactionDateTime(), state.getTransactionDateTime())) {
            throw new IllegalStateException("label principal repair reserve facts are inconsistent");
        }
        if (reserveState == null || reserveState.getId() == null
                || !StringUtils.hasText(reserveState.getReserveStateId())
                || reserveState.getVersion() == null || reserveState.getVersion() < 0
                || !Objects.equals(reserveState.getOriginalTransactionId(), reserve.getOriginalTransactionId())
                || !Objects.equals(reserveState.getTransactionDateTime(), reserve.getOriginalTransactionDateTime())
                || !Objects.equals(reserveState.getOperationId(), reserve.getOperationId())
                || !Objects.equals(reserveState.getOriginalFinanceStateId(), reserve.getFinanceStateId())
                || !Objects.equals(reserveState.getOriginalHoldDetailNo(), reserve.getReserveClearingDetailNo())
                || !Objects.equals(reserveState.getOriginalFeePlanVersionId(), reserve.getFeePlanVersionId())
                || !Objects.equals(reserveState.getOriginalReserveSnapshotHash(), reserve.getReserveSnapshotHash())
                || !Objects.equals(reserveState.getMerchantId(), reserve.getMerchantId())
                || !Objects.equals(reserveState.getReserveCurrency(), reserve.getReserveCurrency())
                || !Objects.equals(reserveState.getReserveCurrencyExponent(), reserve.getReserveCurrencyExponent())
                || !sameAmount(reserveState.getOriginalBasisAmount(), reserve.getBasisAmount())
                || !sameAmount(reserveState.getOriginalReserveRate(), reserve.getReserveRate())
                || !Objects.equals(reserveState.getOriginalRoundingMode(), reserve.getRoundingMode())
                || !sameAmount(reserveState.getRetainedAmount(), reserve.getRetainedAmount())) {
            throw new IllegalStateException("label principal repair reserve state is inconsistent");
        }
    }

    /**
     * 依赖扩展只允许旧清分候选作为被依赖方的一条保证金释放依赖。
     */
    private SupportedDependency validateDependency(ClearingTransactionFinanceStateDO state,
                                                   ClearingSettlementCandidateDO candidate) {
        List<ClearingSettlementCandidateDependencyDO> dependencies =
                candidateMapper.selectDependencyReferencesForUpdate(candidate.getId());
        if (dependencies == null || dependencies.isEmpty()) {
            return SupportedDependency.none();
        }
        if (dependencies.size() != 1) {
            throw new IllegalStateException("label principal repair candidate dependency shape is unsupported");
        }
        ClearingSettlementCandidateDependencyDO relation = dependencies.get(0);
        if (relation.getId() == null || relation.getCandidateId() == null
                || !Objects.equals(relation.getDependsOnCandidateId(), candidate.getId())
                || !"RESERVE_HOLD_POSTED".equals(relation.getDependencyType())) {
            throw new IllegalStateException("label principal repair candidate dependency is unsupported");
        }
        ClearingSettlementCandidateDO dependent =
                candidateMapper.selectByIdForUpdate(relation.getCandidateId());
        if (dependent == null || dependent.getId() == null || dependent.getVersion() == null
                || !Objects.equals(dependent.getId(), relation.getCandidateId())
                || !"RESERVE_RELEASE".equals(dependent.getSourceType())
                || !"READY".equals(dependent.getCandidateStatus())
                || dependent.getSettlementBatchNo() != null
                || dependent.getClaimedTime() != null
                || dependent.getPostedTime() != null
                || !Objects.equals(dependent.getMerchantId(), state.getMerchantId())) {
            throw new IllegalStateException(
                    "label principal repair dependent reserve candidate is stale or claimed");
        }
        return new SupportedDependency(relation, dependent);
    }

    /**
     * 校验当前修订只有一条本金，且本金标签事实与权威动作一致并确实存在历史口径偏差。
     */
    private ClearingTransactionDetailDO validateDetails(ClearingTransactionFinanceStateDO state,
                                                        ClearingTransactionOperationDO operation,
                                                        List<ClearingTransactionDetailDO> details) {
        if (details == null || details.isEmpty() || !StringUtils.hasText(operation.getLabelCurrency())
                || operation.getLabelAmount() == null) {
            throw new IllegalStateException("label principal repair clearing facts are incomplete");
        }
        String labelCurrency = operation.getLabelCurrency().trim().toUpperCase(Locale.ROOT);
        Integer labelExponent = operation.getLabelCurrencyExponent();
        try {
            Currency.getInstance(labelCurrency);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("label principal repair currency is invalid", exception);
        }
        if (labelExponent == null || labelExponent < 0 || labelExponent > 8) {
            throw new IllegalStateException("label principal repair currency exponent is unsupported");
        }
        List<ClearingTransactionDetailDO> principals = details.stream()
                .filter(detail -> "PRINCIPAL".equals(detail.getItemType()))
                .toList();
        if (principals.size() != 1) {
            throw new IllegalStateException("label principal repair requires exactly one principal fact");
        }
        for (ClearingTransactionDetailDO detail : details) {
            if (!"ACTIVE".equals(detail.getRecordStatus())
                    || !Objects.equals(detail.getFinanceStateId(), state.getFinanceStateId())
                    || !Objects.equals(detail.getTransactionId(), state.getTransactionId())
                    || !Objects.equals(detail.getOperationId(), state.getOperationId())
                    || !Objects.equals(detail.getMerchantId(), state.getMerchantId())
                    || !Objects.equals(detail.getClearingRevision(), state.getClearingRevision())
                    || !Objects.equals(detail.getTransactionDateTime(), state.getTransactionDateTime())) {
                throw new IllegalStateException("label principal repair detail identity is inconsistent");
            }
        }
        ClearingTransactionDetailDO principal = principals.get(0);
        if (!sameAmount(principal.getLabelAmount(), operation.getLabelAmount())
                || !Objects.equals(principal.getLabelCurrency(), labelCurrency)
                || !Objects.equals(principal.getLabelCurrencyExponent(), labelExponent)) {
            throw new IllegalStateException("label principal repair label snapshot is inconsistent");
        }
        boolean alreadyCorrect = sameAmount(principal.getAmount(), principal.getLabelAmount())
                && Objects.equals(principal.getCurrency(), principal.getLabelCurrency())
                && Objects.equals(principal.getCurrencyExponent(), principal.getLabelCurrencyExponent());
        if (alreadyCorrect) {
            throw new IllegalStateException("label principal repair is not required");
        }
        return principal;
    }

    /**
     * 复制旧修订完整审计字段；只有本金金额口径、业务号、修订和审计时间发生变化。
     */
    private ClearingTransactionDetailDO repairedDetail(ClearingLabelPrincipalRepairDO request,
                                                        ClearingTransactionDetailDO source,
                                                        int newRevision,
                                                        LocalDateTime now) {
        ClearingTransactionDetailDO row = new ClearingTransactionDetailDO();
        BeanUtils.copyProperties(source, row);
        row.setId(null);
        row.setClearingDetailNo(stableId("CD", request.getRepairNo() + "|"
                + source.getClearingDetailNo() + "|" + newRevision));
        row.setClearingRevision(newRevision);
        row.setRecordStatus("ACTIVE");
        row.setCreateTime(now);
        row.setUpdateTime(now);
        if ("PRINCIPAL".equals(source.getItemType())) {
            row.setBasisAmount(request.getLabelAmount());
            row.setBasisCurrency(request.getLabelCurrency());
            row.setBasisCurrencyExponent(request.getLabelCurrencyExponent());
            row.setAmount(request.getLabelAmount());
            row.setCurrency(request.getLabelCurrency());
            row.setCurrencyExponent(request.getLabelCurrencyExponent());
            row.setFormulaSnapshot("historical repair: merchant principal = frozen label amount and currency");
        }
        return row;
    }

    /**
     * 原样复制冻结的 HOLD 保证金事实；只替换业务号、修订和审计时间。
     */
    private ClearingReserveDetailDO repairedReserveDetail(ClearingLabelPrincipalRepairDO request,
                                                           ClearingReserveDetailDO source,
                                                           int newRevision,
                                                           LocalDateTime now) {
        ClearingReserveDetailDO row = new ClearingReserveDetailDO();
        BeanUtils.copyProperties(source, row);
        row.setId(null);
        row.setReserveClearingDetailNo(stableId("RD", request.getRepairNo() + "|"
                + source.getReserveClearingDetailNo() + "|" + newRevision));
        row.setClearingRevision(newRevision);
        row.setRecordStatus("ACTIVE");
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /**
     * 以旧 HOLD 身份和状态版本执行 CAS 引用迁移，并追加不可变审计；不修改任何保证金金额或状态。
     */
    private void persistReserveStateMigration(ClearingLabelPrincipalRepairDO request,
                                              List<ClearingReserveDetailDO> sourceReserves,
                                              List<ClearingReserveDetailDO> repairedReserves,
                                              ClearingReserveStateDO reserveState,
                                              LocalDateTime now) {
        if (sourceReserves.isEmpty()) {
            if (reserveState != null || !repairedReserves.isEmpty()) {
                throw new IllegalStateException("label principal repair reserve state shape is inconsistent");
            }
            return;
        }
        if (sourceReserves.size() != 1 || repairedReserves.size() != 1 || reserveState == null) {
            throw new IllegalStateException("label principal repair reserve state migration shape is unsupported");
        }
        ClearingReserveDetailDO source = sourceReserves.get(0);
        ClearingReserveDetailDO repaired = repairedReserves.get(0);
        requireOne(reserveMapper.migrateHoldReference(
                reserveState.getId(), reserveState.getReserveStateId(),
                reserveState.getOriginalTransactionId(), reserveState.getTransactionDateTime(),
                source.getReserveClearingDetailNo(), repaired.getReserveClearingDetailNo(),
                reserveState.getVersion(), now),
                "label principal repair reserve state migration CAS");

        ClearingLabelPrincipalRepairReserveStateAuditDO audit =
                new ClearingLabelPrincipalRepairReserveStateAuditDO();
        audit.setRepairNo(request.getRepairNo());
        audit.setReserveStateRowId(reserveState.getId());
        audit.setReserveStateId(reserveState.getReserveStateId());
        audit.setOldHoldDetailNo(source.getReserveClearingDetailNo());
        audit.setNewHoldDetailNo(repaired.getReserveClearingDetailNo());
        audit.setOldStateVersion(reserveState.getVersion());
        audit.setNewStateVersion(Math.addExact(reserveState.getVersion(), 1L));
        audit.setStateSnapshotHash(request.getReserveStateSnapshotHash());
        audit.setMigratedTime(now);
        audit.setCreateTime(now);
        requireOne(reserveStateAuditMapper.insert(audit),
                "label principal repair reserve state audit insert");
    }

    /**
     * 将保证金释放候选依赖迁移到新清分候选，并追加不可变迁移审计。
     */
    private void persistDependencyMigration(ClearingLabelPrincipalRepairDO request,
                                            SupportedDependency dependency,
                                            LabelPrincipalRepairCandidateReplacement replacement,
                                            LocalDateTime now) {
        if (dependency.count() == 0) {
            return;
        }
        ClearingSettlementCandidateDependencyDO relation = dependency.relation();
        ClearingSettlementCandidateDO dependent = dependency.dependentCandidate();
        if (!Objects.equals(replacement.oldCandidateId(), request.getCandidateId())) {
            throw new IllegalStateException("label principal repair candidate replacement is inconsistent");
        }
        requireOne(candidateMapper.migrateReserveHoldDependency(
                relation.getId(), dependent.getId(), replacement.oldCandidateId(),
                replacement.newCandidateId(), dependent.getVersion()),
                "label principal repair dependency migration CAS");

        ClearingLabelPrincipalRepairDependencyAuditDO audit =
                new ClearingLabelPrincipalRepairDependencyAuditDO();
        audit.setRepairNo(request.getRepairNo());
        audit.setDependencyId(relation.getId());
        audit.setDependentCandidateId(dependent.getId());
        audit.setOldDependsOnCandidateId(replacement.oldCandidateId());
        audit.setNewDependsOnCandidateId(replacement.newCandidateId());
        audit.setDependencyType(relation.getDependencyType());
        audit.setDependentCandidateVersion(dependent.getVersion());
        audit.setMigratedTime(now);
        audit.setCreateTime(now);
        requireOne(dependencyAuditMapper.insert(audit),
                "label principal repair dependency audit insert");
    }

    /**
     * 新修订提交后写入完成事件，使查询缓存按既有可靠事件链路失效。
     */
    private void persistCompletionOutbox(ClearingLabelPrincipalRepairDO request,
                                         ClearingTransactionOperationDO operation,
                                         int newRevision,
                                         LocalDateTime now) {
        String eventNo = stableId("CE", "LABEL_PRINCIPAL_REPAIR|" + request.getRepairNo());
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId(eventNo);
        message.setCreatedAt(now);
        message.setRetryCount(0);
        message.setTransactionId(operation.getTransactionId());
        message.setOperationId(operation.getOperationId());
        message.setMerchantId(operation.getMerchantId());
        message.setMerchantOrderNo(operation.getMerchantOrderNo());
        message.setTransactionType(operation.getTransactionType());
        message.setTransactionStatus(operation.getTransactionStatus());
        message.setEventType(MqTag.TRANSACTION_CLEARING_COMPLETED);
        message.setTransactionDateTime(operation.getTransactionDateTime());

        ClearingTransactionEventOutboxDO row = new ClearingTransactionEventOutboxDO();
        row.setEventNo(eventNo);
        row.setAggregateType("TRANSACTION_CLEARING_REPAIR");
        row.setAggregateNo(request.getFinanceStateId() + ":" + newRevision);
        row.setTransactionId(operation.getTransactionId());
        row.setOperationId(operation.getOperationId());
        row.setMerchantId(operation.getMerchantId());
        row.setMerchantOrderNo(operation.getMerchantOrderNo());
        row.setTransactionType(operation.getTransactionType());
        row.setEventType(MqTag.TRANSACTION_CLEARING_COMPLETED);
        row.setEventStatus("INIT");
        row.setTopic(MqTopic.PAYMENT_TRANSACTION_FIFO);
        row.setTag(MqTag.TRANSACTION_CLEARING_COMPLETED);
        row.setMessageKey(eventNo);
        row.setMessageGroup(operation.getOperationId());
        row.setDeliveryMode("ORDERLY");
        row.setPayloadJson(JsonUtils.toJsonString(message));
        row.setRetryCount(0);
        row.setMaxRetryCount(10);
        row.setNextRetryTime(now);
        row.setEventTime(now);
        row.setTransactionDateTime(operation.getTransactionDateTime());
        row.setTransactionUtcTime(operation.getTransactionUtcTime());
        row.setTransactionTimeZone(operation.getTransactionTimeZone());
        row.setVersion(0);
        row.setDeleted(0);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        requireOne(outboxMapper.insertLogical(row), "label principal repair outbox insert");
    }

    /**
     * 重复提交必须与首次冻结身份完全一致，禁止复用请求键改变交易或审计主体。
     */
    private void validateDuplicate(ClearingLabelPrincipalRepairDO row,
                                   String repairNo,
                                   SubmitCommand command) {
        if (row == null || !Objects.equals(row.getRepairNo(), repairNo)
                || !Objects.equals(row.getRequestKey(), command.requestKey().trim())
                || !Objects.equals(row.getTransactionId(), command.transactionId().trim())
                || !Objects.equals(row.getTransactionDateTime(), command.transactionDateTime())
                || !Objects.equals(row.getExpectedFinanceStateVersion(), command.expectedFinanceStateVersion())
                || !Objects.equals(row.getExpectedClearingRevision(), command.expectedClearingRevision())
                || !Objects.equals(row.getExpectedCandidateVersion(), command.expectedCandidateVersion())
                || !Objects.equals(row.getReason(), normalized(command.reason(), "reason", 400))
                || !Objects.equals(row.getSubmitOperator(),
                    normalized(command.submitOperator(), "submit operator", 128))) {
            throw new IllegalStateException("label principal repair request key is already in use");
        }
    }

    /**
     * 已执行或拒绝申请只允许同一复核命令幂等读取。
     */
    private boolean terminalReplay(ClearingLabelPrincipalRepairDO row,
                                   ReviewCommand command,
                                   String reviewer,
                                   String comment) {
        if (!EXECUTED.equals(row.getRepairStatus()) && !REJECTED.equals(row.getRepairStatus())) {
            return false;
        }
        String expectedStatus = command.decision() == ReviewDecision.APPROVE ? EXECUTED : REJECTED;
        if (!expectedStatus.equals(row.getRepairStatus())
                || !Objects.equals(row.getReviewOperator(), reviewer)
                || !Objects.equals(row.getReviewComment(), comment)) {
            throw new IllegalStateException("label principal repair has a different terminal decision");
        }
        return true;
    }

    private void validateSubmit(SubmitCommand command) {
        if (command == null || !validText(command.requestKey(), 128)
                || !validText(command.transactionId(), 64) || command.transactionDateTime() == null
                || command.expectedFinanceStateVersion() < 0 || command.expectedClearingRevision() < 1
                || command.expectedCandidateVersion() < 0 || !validText(command.reason(), 400)
                || !validText(command.submitOperator(), 128) || command.requestedInstant() == null) {
            throw new IllegalArgumentException("label principal repair submit command is invalid");
        }
    }

    private void validateReview(ReviewCommand command) {
        if (command == null || !validRepairNo(command.repairNo())
                || command.expectedRequestVersion() < 0 || command.decision() == null
                || !validText(command.reviewComment(), 400)
                || !validText(command.reviewOperator(), 128) || command.reviewInstant() == null) {
            throw new IllegalArgumentException("label principal repair review command is invalid");
        }
    }

    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.trim().length() <= maxLength;
    }

    private boolean validRepairNo(String value) {
        return value != null && REPAIR_NO_PATTERN.matcher(value.trim()).matches();
    }

    private String normalized(String value, String field, int maxLength) {
        if (!validText(value, maxLength)) {
            throw new IllegalArgumentException(field + " is missing or too long");
        }
        return value.trim().replace('\r', ' ').replace('\n', ' ');
    }

    private boolean sameAmount(java.math.BigDecimal left, java.math.BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private String snapshotHash(List<ClearingTransactionDetailDO> details) {
        return sha256(JsonUtils.toJsonString(details));
    }

    private String reserveSnapshotHash(List<ClearingReserveDetailDO> reserves) {
        return sha256(JsonUtils.toJsonString(reserves));
    }

    private String reserveStateSnapshotHash(ClearingReserveStateDO reserveState) {
        return reserveState == null ? sha256("NONE") : sha256(JsonUtils.toJsonString(reserveState));
    }

    private String dependencySnapshotHash(SupportedDependency dependency) {
        if (dependency.count() == 0) {
            return sha256("NONE");
        }
        ClearingSettlementCandidateDependencyDO relation = dependency.relation();
        ClearingSettlementCandidateDO dependent = dependency.dependentCandidate();
        String canonical = String.join("|",
                String.valueOf(relation.getId()),
                String.valueOf(relation.getCandidateId()),
                String.valueOf(relation.getDependsOnCandidateId()),
                String.valueOf(relation.getDependencyType()),
                String.valueOf(relation.getCreateTime()),
                String.valueOf(dependent.getId()),
                String.valueOf(dependent.getCandidateNo()),
                String.valueOf(dependent.getSourceType()),
                String.valueOf(dependent.getSourceBusinessId()),
                String.valueOf(dependent.getSourceRevision()),
                String.valueOf(dependent.getSourceTransactionId()),
                String.valueOf(dependent.getSourceTransactionDateTime()),
                String.valueOf(dependent.getMerchantId()),
                String.valueOf(dependent.getCandidateStatus()),
                String.valueOf(dependent.getSettlementBatchNo()),
                String.valueOf(dependent.getVersion()));
        return sha256(canonical);
    }

    private String stableId(String prefix, String identity) {
        return prefix + sha256(identity).substring(0, 32);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private RepairResult result(ClearingLabelPrincipalRepairDO row) {
        return new RepairResult(row.getRepairNo(), row.getRepairStatus(), row.getTransactionId(),
                row.getTransactionDateTime(), row.getExpectedClearingRevision(),
                row.getNewClearingRevision(), row.getNewCandidateNo(),
                row.getVersion() == null ? 0L : row.getVersion());
    }

    private record RepairableFacts(ClearingTransactionDetailDO principal,
                                   List<ClearingReserveDetailDO> reserveDetails,
                                   ClearingReserveStateDO reserveState,
                                   SupportedDependency dependency) {
    }

    private record SupportedDependency(ClearingSettlementCandidateDependencyDO relation,
                                       ClearingSettlementCandidateDO dependentCandidate) {
        private static SupportedDependency none() {
            return new SupportedDependency(null, null);
        }

        private int count() {
            return relation == null ? 0 : 1;
        }
    }

    private void requireOne(int affected, String operation) {
        if (affected != 1) {
            throw new IllegalStateException(operation + " did not affect the expected row");
        }
    }

    private void requireRows(int affected, int expected, String operation) {
        if (affected != expected) {
            throw new IllegalStateException(operation + " did not affect the expected rows");
        }
    }
}
