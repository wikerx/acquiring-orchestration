package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.domain.model.ClearingCalculationModels.CalculatedFee;
import com.scott.payment.clearing.domain.model.ClearingCalculationModels.ClearingCalculationCommand;
import com.scott.payment.clearing.domain.model.ClearingCalculationModels.ClearingCalculationResult;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionResult;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.FinanceSummary;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.LocatorFacts;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.SourceContext;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.domain.service.ClearingFeeRuleMatcher;
import com.scott.payment.clearing.dto.ClearingFeeTierAccumulatorDelta;
import com.scott.payment.clearing.entity.ClearingFeeTierAccumulatorDO;
import com.scott.payment.clearing.entity.ClearingReserveDetailDO;
import com.scott.payment.clearing.entity.ClearingReserveStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionDetailDO;
import com.scott.payment.clearing.entity.ClearingTransactionEventOutboxDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionLocatorDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayDO;
import com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingFeeTierAccumulatorMapper;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionContextMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionDetailMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionEventOutboxMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionIdempotencyMapper;
import com.scott.payment.clearing.mapper.ClearingTierPeriodReplayMapper;
import com.scott.payment.clearing.service.ClearingCalculationService;
import com.scott.payment.clearing.service.ClearingAnomalyService;
import com.scott.payment.clearing.service.ClearingCompletionService;
import com.scott.payment.clearing.service.ClearingProjectionService;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.clearing.support.ClearingItemNameResolver;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeComponent;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeComponentType;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeEvaluationStatus;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierContext;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierMetric;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.RefundFeeReturnPolicy;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundCommand;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundComponent;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundPolicy;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.RefundableFeeComponent;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveActionType;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveCalculationResult;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveReturnCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingCompletionService
 * @date : 2026-08-26 12:00
 * @email : scott_x@163.com
 * @description : Stage B 默认实现，以数据库行锁、CAS 和唯一键原子提交原币种清分事实，不访问 Redis、Slave、汇率或商户余额。
 * @status : create
 */
@Service
public class DefaultClearingCompletionService implements ClearingCompletionService {

    /**
     * {@code IDEMPOTENCY_KEY_PREFIX}常量，统一 {@code DefaultClearingCompletionService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String IDEMPOTENCY_KEY_PREFIX = "service-clearing-transaction-status:";
    /**
     * {@code ACTIVE}常量，统一 {@code DefaultClearingCompletionService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String ACTIVE = "ACTIVE";
    /**
     * {@code EVENT_STATUS_INIT}，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String EVENT_STATUS_INIT = "INIT";
    /**
     * 退款常量，统一 {@code DefaultClearingCompletionService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String REFUND = "REFUND";
    /**
     * 成功常量，统一 {@code DefaultClearingCompletionService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String SUCCESS = "SUCCESS";
    /**
     * 财务计算统一 MathContext，约束中间计算精度并避免过早舍入。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    /**
     * {@code TIER_PERIOD_FORMATTER}常量，统一 {@code DefaultClearingCompletionService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter TIER_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 锁定并完成动作级清分权威状态。 */
    private final ClearingTransactionFinanceStateMapper financeStateMapper;
    /** 持久化本金与费用原子事实。 */
    private final ClearingTransactionDetailMapper detailMapper;
    /** 持久化独立保证金事实和并发状态。 */
    private final ClearingReserveMapper reserveMapper;
    /** 串行化月累计阶梯计费事实。 */
    private final ClearingFeeTierAccumulatorMapper tierAccumulatorMapper;
    /** 读取退款 locator 等生命周期上下文。 */
    private final ClearingTransactionContextMapper contextMapper;
    /** 统一 CAS 更新动作和生命周期清分查询投影。 */
    private final ClearingProjectionService projectionService;
    /** 与清分事实同事务写入 MQ 成功消费幂等。 */
    private final ClearingTransactionIdempotencyMapper idempotencyMapper;
    /** 与清分事实同事务写入可靠完成事件。 */
    private final ClearingTransactionEventOutboxMapper outboxMapper;
    /** 使用已锁定阶梯事实运行无外部依赖的金额计算。 */
    private final ClearingCalculationService calculationService;
    /** 生成明细、保证金状态和事件的全局业务编号。 */
    private final GlobalIdGenerator idGenerator;
    /** 形成清分修订级影子结算候选，不执行认领或余额入账。 */
    private final ClearingSettlementCandidateService candidateService;
    /** 成功提交后关闭同一交易分片上的活动清分异常案件。 */
    private final ClearingAnomalyService anomalyService;
    /** 阶梯锁等待等不含业务标识的运行指标。 */
    private final ClearingOperationalMetrics metrics;
    /** 阶梯期间重放门禁、稳定下一项和事务内游标。 */
    private final ClearingTierPeriodReplayMapper tierReplayMapper;

    /**
     * 创建 Stage B 原子提交服务。
     *
     * @param financeStateMapper 动作财务状态 Mapper
     * @param detailMapper 交易清分明细 Mapper
     * @param reserveMapper 保证金明细与状态 Mapper
     * @param tierAccumulatorMapper 阶梯累计 Mapper
     * @param contextMapper 生命周期上下文 Mapper
     * @param projectionService 动作和生命周期查询投影服务
     * @param idempotencyMapper MQ 消费幂等 Mapper
     * @param outboxMapper 交易 Outbox Mapper
     * @param calculationService 清分纯计算服务
     * @param idGenerator 全局业务号生成器
     */
    public DefaultClearingCompletionService(ClearingTransactionFinanceStateMapper financeStateMapper,
                                            ClearingTransactionDetailMapper detailMapper,
                                            ClearingReserveMapper reserveMapper,
                                            ClearingFeeTierAccumulatorMapper tierAccumulatorMapper,
                                            ClearingTransactionContextMapper contextMapper,
                                            ClearingProjectionService projectionService,
                                            ClearingTransactionIdempotencyMapper idempotencyMapper,
                                            ClearingTransactionEventOutboxMapper outboxMapper,
                                            ClearingCalculationService calculationService,
                                            GlobalIdGenerator idGenerator,
                                            ClearingSettlementCandidateService candidateService,
                                            ClearingAnomalyService anomalyService,
                                            ClearingOperationalMetrics metrics,
                                            ClearingTierPeriodReplayMapper tierReplayMapper) {
        this.financeStateMapper = financeStateMapper;
        this.detailMapper = detailMapper;
        this.reserveMapper = reserveMapper;
        this.tierAccumulatorMapper = tierAccumulatorMapper;
        this.contextMapper = contextMapper;
        this.projectionService = projectionService;
        this.idempotencyMapper = idempotencyMapper;
        this.outboxMapper = outboxMapper;
        this.calculationService = calculationService;
        this.idGenerator = idGenerator;
        this.candidateService = candidateService;
        this.anomalyService = anomalyService;
        this.metrics = metrics;
        this.tierReplayMapper = tierReplayMapper;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public CompletionResult complete(CompletionCommand command, LocalDateTime now) {
        requireCommand(command, now);
        ClearingOperationFacts operation = command.claim().operation();
        ClearingTransactionFinanceStateDO state = financeStateMapper.selectForUpdate(
                operation.transactionId(), operation.transactionDateTime());
        validateLease(command, state, now);
        int revision = state.getClearingRevision() + 1;

        RefundStageContext refundContext = prepareRefundContext(command);
        Map<Long, LockedTier> lockedTiers = lockTiers(command, now);
        Map<Long, TierContext> tierContexts = new LinkedHashMap<>();
        lockedTiers.forEach((ruleId, value) -> tierContexts.put(ruleId, value.context()));
        ClearingCalculationResult calculation = calculationService.calculate(new ClearingCalculationCommand(
                operation, command.feeSnapshot(), command.paymentType(), command.paymentMethod(),
                command.occurredRiskServices(), tierContexts, refundContext.feeRefundCommand(),
                refundContext.reserveReturnCommand()));
        String targetStatus = calculation.required()
                ? ClearingStateEnum.CLEARED.name() : ClearingStateEnum.NOT_REQUIRED.name();

        List<ClearingTransactionDetailDO> details = transactionDetails(
                command, state.getFinanceStateId(), revision, calculation, refundContext, now);
        if (!details.isEmpty()) {
            requireRows(detailMapper.insertBatch(details), details.size(), "transaction clearing detail insert");
        }
        int reserveDetailCount = persistReserve(
                command, state.getFinanceStateId(), revision, calculation.reserve(), refundContext, now);
        applyTierDeltas(command, revision, calculation, lockedTiers, now);

        FinanceSummary summary = financeSummary(command, revision, targetStatus, calculation);
        requireOne(financeStateMapper.completeProcessing(
                operation.transactionId(), operation.transactionDateTime(), command.processingOwner(),
                command.claim().financeStateVersion(), summary, now), "finance state completion CAS");
        projectionService.updateWithLocator(
                operation, command.currentLocator(), ClearingStateEnum.valueOf(targetStatus), null, now);
        persistSuccessIdempotency(command, targetStatus, revision, now);
        persistCompletionOutbox(command, targetStatus, revision, now);
        if (ClearingStateEnum.CLEARED.name().equals(targetStatus)) {
            candidateService.create(state.getFinanceStateId(), revision, operation,
                    command.feeSnapshot().settlementCurrency(), command.settlementEligibleDate(), now);
        }
        anomalyService.resolve(operation.transactionId(), operation.transactionDateTime(),
                state.getFinanceStateId() + ":" + revision, now);
        return new CompletionResult(targetStatus, revision, details.size(), reserveDetailCount);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public CompletionResult recalculate(CompletionCommand command,
                                        int expectedVersion,
                                        int expectedRevision,
                                        LocalDateTime now) {
        requireCommand(command, now);
        if (expectedVersion < 0 || expectedRevision < 1) {
            throw new IllegalArgumentException("recalculation expected version and revision are invalid");
        }
        if (command.feeSnapshot().rules().stream().anyMatch(rule -> !rule.tiers().isEmpty())) {
            throw new IllegalStateException(
                    "tiered fee recalculation requires a period-level recalculation workflow");
        }
        ClearingOperationFacts operation = command.claim().operation();
        ClearingTransactionFinanceStateDO state = financeStateMapper.selectForUpdate(
                operation.transactionId(), operation.transactionDateTime());
        validateRecalculationState(command, state, expectedVersion, expectedRevision);

        RefundStageContext refundContext = prepareRefundContext(command);
        ClearingCalculationResult calculation = calculationService.calculate(new ClearingCalculationCommand(
                operation, command.feeSnapshot(), command.paymentType(), command.paymentMethod(),
                command.occurredRiskServices(), Map.of(), refundContext.feeRefundCommand(),
                refundContext.reserveReturnCommand()));
        if (calculation.reserve() != null
                || !reserveMapper.selectActiveRevision(operation.transactionId(),
                        operation.transactionDateTime(), expectedRevision).isEmpty()) {
            throw new IllegalStateException(
                    "reserve-affecting recalculation requires an adjustment workflow");
        }

        int revision = expectedRevision + 1;
        String targetStatus = calculation.required()
                ? ClearingStateEnum.CLEARED.name() : ClearingStateEnum.NOT_REQUIRED.name();
        List<ClearingTransactionDetailDO> existing = detailMapper.selectActiveRevision(
                operation.transactionId(), operation.transactionDateTime(), expectedRevision);
        List<ClearingTransactionDetailDO> details = transactionDetails(
                command, state.getFinanceStateId(), revision, calculation, refundContext, now);
        if (!existing.isEmpty()) {
            requireRows(detailMapper.supersedeActiveRevision(operation.transactionId(),
                    operation.transactionDateTime(), expectedRevision, now), existing.size(),
                    "old clearing revision supersede");
        }
        if (!details.isEmpty()) {
            requireRows(detailMapper.insertBatch(details), details.size(),
                    "recalculated transaction clearing detail insert");
        }

        FinanceSummary summary = financeSummary(command, revision, targetStatus, calculation);
        requireOne(financeStateMapper.completeRecalculation(
                operation.transactionId(), operation.transactionDateTime(), expectedVersion,
                expectedRevision, summary, now), "finance state recalculation CAS");
        if (ClearingStateEnum.CLEARED.name().equals(targetStatus)) {
            candidateService.replace(state.getFinanceStateId(), expectedRevision, revision, operation,
                    command.feeSnapshot().settlementCurrency(), command.settlementEligibleDate(), now);
        } else {
            throw new IllegalStateException(
                    "recalculation cannot remove all financial facts; use a reviewed adjustment action");
        }
        persistCompletionOutbox(command, targetStatus, revision, now);
        anomalyService.resolve(operation.transactionId(), operation.transactionDateTime(),
                state.getFinanceStateId() + ":" + revision, now);
        return new CompletionResult(targetStatus, revision, details.size(), 0);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public CompletionResult recalculateTierPeriod(CompletionCommand command,
                                                  String replayNo,
                                                  int sequenceNo,
                                                  int expectedVersion,
                                                  int expectedRevision,
                                                  LocalDateTime now) {
        requireCommand(command, now);
        if (!StringUtils.hasText(replayNo) || sequenceNo < 1 || expectedVersion < 0 || expectedRevision < 1) {
            throw new IllegalArgumentException("tier replay identity, sequence and expected state are required");
        }
        ClearingOperationFacts operation = command.claim().operation();
        ClearingTierPeriodReplayDO replay = tierReplayMapper.selectForUpdate(replayNo);
        ClearingTierPeriodReplayItemDO item = tierReplayMapper.selectNextItemForUpdate(replayNo, now);
        validateTierReplayIdentity(command, replay, item, sequenceNo, expectedVersion, expectedRevision);

        ClearingTransactionFinanceStateDO state = financeStateMapper.selectForUpdate(
                operation.transactionId(), operation.transactionDateTime());
        validateRecalculationState(command, state, expectedVersion, expectedRevision);
        RefundStageContext refundContext = prepareRefundContext(command);
        Map<Long, LockedTier> lockedTiers = lockTiers(command, now, false);
        Map<Long, TierContext> tierContexts = new LinkedHashMap<>();
        lockedTiers.forEach((ruleId, value) -> tierContexts.put(ruleId, value.context()));
        ClearingCalculationResult calculation = calculationService.calculate(new ClearingCalculationCommand(
                operation, command.feeSnapshot(), command.paymentType(), command.paymentMethod(),
                command.occurredRiskServices(), tierContexts, refundContext.feeRefundCommand(),
                refundContext.reserveReturnCommand()));
        if (calculation.reserve() != null
                || !reserveMapper.selectActiveRevision(operation.transactionId(),
                        operation.transactionDateTime(), expectedRevision).isEmpty()) {
            throw new IllegalStateException("tier replay cannot replace reserve-affecting clearing facts");
        }

        int revision = expectedRevision + 1;
        String targetStatus = calculation.required()
                ? ClearingStateEnum.CLEARED.name() : ClearingStateEnum.NOT_REQUIRED.name();
        if (!ClearingStateEnum.CLEARED.name().equals(targetStatus)) {
            throw new IllegalStateException("tier replay cannot remove all financial facts");
        }
        List<ClearingTransactionDetailDO> existing = detailMapper.selectActiveRevision(
                operation.transactionId(), operation.transactionDateTime(), expectedRevision);
        List<ClearingTransactionDetailDO> details = transactionDetails(
                command, state.getFinanceStateId(), revision, calculation, refundContext, now);
        if (!existing.isEmpty()) {
            requireRows(detailMapper.supersedeActiveRevision(operation.transactionId(),
                    operation.transactionDateTime(), expectedRevision, now), existing.size(),
                    "tier replay old clearing revision supersede");
        }
        if (!details.isEmpty()) {
            requireRows(detailMapper.insertBatch(details), details.size(),
                    "tier replay transaction clearing detail insert");
        }
        applyTierDeltas(command, revision, calculation, lockedTiers, now);

        FinanceSummary summary = financeSummary(command, revision, targetStatus, calculation);
        requireOne(financeStateMapper.completeRecalculation(
                operation.transactionId(), operation.transactionDateTime(), expectedVersion,
                expectedRevision, summary, now), "tier replay finance state CAS");
        candidateService.replaceReplayHeld(state.getFinanceStateId(), expectedRevision, revision, operation,
                command.feeSnapshot().settlementCurrency(), command.settlementEligibleDate(), now);
        persistCompletionOutbox(command, targetStatus, revision, now);
        anomalyService.resolve(operation.transactionId(), operation.transactionDateTime(),
                state.getFinanceStateId() + ":" + revision, now);
        requireOne(tierReplayMapper.markItemCompleted(replayNo, sequenceNo, item.getVersion(), revision, now),
                "tier replay item completion CAS");
        requireOne(tierReplayMapper.advanceAfterItem(replayNo, replay.getVersion(), sequenceNo,
                item.getClearingCompleteTime(), item.getTransactionId(), now),
                "tier replay progress CAS");
        return new CompletionResult(targetStatus, revision, details.size(), 0);
    }

    /** 阶梯重放项必须仍匹配冻结申请、动作身份、修订和未结算门禁。 */
    private void validateTierReplayIdentity(CompletionCommand command,
                                            ClearingTierPeriodReplayDO replay,
                                            ClearingTierPeriodReplayItemDO item,
                                            int sequenceNo,
                                            int expectedVersion,
                                            int expectedRevision) {
        ClearingOperationFacts operation = command.claim().operation();
        String periodKey = operation.transactionDateTime().format(TIER_PERIOD_FORMATTER);
        if (replay == null || !"RUNNING".equals(replay.getReplayStatus()) || replay.getVersion() == null
                || item == null || item.getVersion() == null
                || !Objects.equals(item.getSequenceNo(), sequenceNo)
                || !Objects.equals(item.getFinanceStateId(), command.claim().financeStateId())
                || !Objects.equals(item.getTransactionId(), operation.transactionId())
                || !Objects.equals(item.getTransactionDateTime(), operation.transactionDateTime())
                || !Objects.equals(item.getExpectedFinanceStateVersion(), expectedVersion)
                || !Objects.equals(item.getExpectedClearingRevision(), expectedRevision)
                || !Objects.equals(replay.getMerchantId(), operation.merchantId())
                || !Objects.equals(replay.getFeePlanId(), command.feeSnapshot().feePlanId())
                || !Objects.equals(replay.getFeePlanVersionId(), command.feeSnapshot().feePlanVersionId())
                || !Objects.equals(replay.getPeriodKey(), periodKey)) {
            throw new IllegalStateException("tier replay control, item and clearing command identities are inconsistent");
        }
    }

    /** 单笔重算只能基于未结算的当前有效修订和调用方预期版本。 */
    private void validateRecalculationState(CompletionCommand command,
                                            ClearingTransactionFinanceStateDO state,
                                            int expectedVersion,
                                            int expectedRevision) {
        ClearingOperationFacts operation = command.claim().operation();
        if (state == null || !Objects.equals(state.getFinanceStateId(), command.claim().financeStateId())
                || !Objects.equals(state.getTransactionId(), operation.transactionId())
                || !Objects.equals(state.getOperationId(), operation.operationId())
                || !Objects.equals(state.getMerchantId(), operation.merchantId())
                || !Objects.equals(state.getTransactionDateTime(), operation.transactionDateTime())
                || !Set.of("CLEARED", "NOT_REQUIRED").contains(state.getClearingStatus())
                || !"NOT_SETTLED".equals(state.getSettlementStatus())
                || !Objects.equals(state.getVersion(), expectedVersion)
                || !Objects.equals(state.getClearingRevision(), expectedRevision)) {
            throw failure(ClearingFailureCodeEnum.CLEARING_CAS_CONFLICT,
                    "clearing recalculation state is stale, claimed or settled");
        }
    }

    private void requireCommand(CompletionCommand command, LocalDateTime now) {
        if (command == null || now == null || command.claim() == null || !command.claim().acquired()
                || command.claim().operation() == null || command.feeSnapshot() == null
                || command.currentLocator() == null || !StringUtils.hasText(command.processingOwner())) {
            throw new IllegalArgumentException("complete clearing command and time are required");
        }
    }

    /** Stage B 提交必须仍持有 Stage A 领取的 PROCESSING owner、版本和有效租约。 */
    private void validateLease(CompletionCommand command,
                               ClearingTransactionFinanceStateDO state,
                               LocalDateTime now) {
        ClearingOperationFacts operation = command.claim().operation();
        if (state == null
                || !Objects.equals(state.getFinanceStateId(), command.claim().financeStateId())
                || !Objects.equals(state.getTransactionId(), operation.transactionId())
                || !Objects.equals(state.getOperationId(), operation.operationId())
                || !Objects.equals(state.getMerchantId(), operation.merchantId())
                || !Objects.equals(state.getTransactionDateTime(), operation.transactionDateTime())
                || !ClearingStateEnum.PROCESSING.name().equals(state.getClearingStatus())
                || !Objects.equals(state.getProcessingOwner(), command.processingOwner())
                || state.getProcessingDeadline() == null || state.getProcessingDeadline().isBefore(now)
                || !Objects.equals(state.getVersion(), command.claim().financeStateVersion())
                || state.getClearingRevision() == null
                || state.getClearingRevision() != command.claim().clearingRevision()) {
            throw failure(ClearingFailureCodeEnum.CLEARING_CAS_CONFLICT,
                    "clearing processing lease is missing, expired or inconsistent");
        }
    }

    /**
     * 在当前动作租约内锁定原支付清分状态，串行化同一原支付的并发退款，并构建纯计算输入。
     */
    private RefundStageContext prepareRefundContext(CompletionCommand command) {
        ClearingOperationFacts refund = command.claim().operation();
        if (!REFUND.equals(refund.transactionType()) || !SUCCESS.equals(refund.transactionStatus())) {
            return RefundStageContext.empty();
        }
        SourceContext source = validateRefundSource(command);
        ClearingTransactionFinanceStateDO sourceState = financeStateMapper.selectForUpdate(
                source.operation().transactionId(), source.operation().transactionDateTime());
        validateSourceFinanceState(source, sourceState);

        List<LocatorFacts> refundLocators = refundLocators(command, source);
        List<ClearingTransactionDetailDO> refundFacts = detailMapper.selectRefundFacts(
                source.operation().transactionId(), refundLocators);
        Money originalLabelAmount = labelMoney(source.operation());
        Money refundLabelAmount = labelMoney(refund);
        if (!originalLabelAmount.sameCurrency(refundLabelAmount)) {
            throw failure(ClearingFailureCodeEnum.FEE_COMPONENT_CURRENCY_INVALID,
                    "refund label amount must use the original transaction label currency");
        }
        BigDecimal refundedBeforeValue = refundedLabelAmountBefore(
                command, source, refundLocators, refundFacts);
        if (originalLabelAmount.amount().signum() <= 0
                || refundedBeforeValue.add(refundLabelAmount.amount(), CALCULATION_CONTEXT)
                .compareTo(originalLabelAmount.amount()) > 0) {
            throw failure(ClearingFailureCodeEnum.RESERVE_RETURN_EXCEEDED,
                    "cumulative successful refund exceeds the original label amount");
        }
        Money refundedLabelAmountBefore = new Money(
                refundedBeforeValue,
                originalLabelAmount.currency(), originalLabelAmount.exponent());

        FeeRefundCommand feeRefundCommand = null;
        Map<String, SourceFeeGroup> sourceFeeGroups = Map.of();
        FeeRefundPolicy refundPolicy = refundPolicy(source.feeSnapshot().refundFeeReturnPolicy());
        if (refundPolicy != FeeRefundPolicy.NONE) {
            List<ClearingTransactionDetailDO> sourceDetails = detailMapper.selectActiveRevision(
                    source.operation().transactionId(), source.operation().transactionDateTime(),
                    sourceState.getClearingRevision());
            sourceFeeGroups = sourceFeeGroups(command, source, sourceState, sourceDetails, refundFacts);
            List<RefundableFeeComponent> refundable = sourceFeeGroups.values().stream()
                    .map(SourceFeeGroup::refundableComponent)
                    .toList();
            feeRefundCommand = new FeeRefundCommand(refundPolicy, refundLabelAmount, originalLabelAmount,
                    refundedLabelAmountBefore, refundable, source.feeSnapshot().roundingMode());
        }

        ClearingReserveStateDO reserveState = null;
        ReserveReturnCommand reserveReturnCommand = null;
        BigDecimal originalHoldAmount = originalReserveHoldAmount(source);
        if (originalHoldAmount.signum() > 0) {
            reserveState = reserveMapper.selectStateForUpdate(
                    source.operation().transactionId(), source.operation().transactionDateTime());
            validateReserveState(source, sourceState, reserveState, originalHoldAmount);
            reserveReturnCommand = new ReserveReturnCommand(
                    refundLabelAmount, originalLabelAmount, refundedLabelAmountBefore,
                    reserveState.getOriginalReserveRate(),
                    money(reserveState.getRetainedAmount(), reserveState),
                    money(reserveState.getReturnedAmount(), reserveState),
                    source.feeSnapshot().roundingMode());
        }
        return new RefundStageContext(feeRefundCommand, reserveReturnCommand,
                sourceFeeGroups, reserveState);
    }

    /** 退款来源必须是同商户、同生命周期且已完成清分的原支付动作。 */
    private SourceContext validateRefundSource(CompletionCommand command) {
        ClearingOperationFacts refund = command.claim().operation();
        SourceContext source = command.source();
        if (source == null || source.operation() == null || source.locator() == null
                || source.feeSnapshot() == null
                || !Objects.equals(refund.sourceTransactionId(), source.operation().transactionId())
                || !Objects.equals(source.operation().transactionId(), source.locator().transactionId())
                || !Objects.equals(source.operation().transactionDateTime(), source.locator().transactionDateTime())
                || !Objects.equals(refund.operationId(), source.operation().operationId())
                || !Objects.equals(refund.merchantId(), source.operation().merchantId())
                || !Objects.equals(refund.merchantId(), source.feeSnapshot().merchantId())
                || !SUCCESS.equals(source.operation().transactionStatus())) {
            throw failure(ClearingFailureCodeEnum.SOURCE_CLEARING_NOT_FOUND,
                    "successful refund requires a consistent cleared source transaction");
        }
        return source;
    }

    /** 原支付 finance state 必须与源动作、快照和当前有效修订一致。 */
    private void validateSourceFinanceState(SourceContext source,
                                            ClearingTransactionFinanceStateDO state) {
        boolean completed = false;
        if (state != null && StringUtils.hasText(state.getClearingStatus())) {
            try {
                completed = ClearingStateEnum.valueOf(state.getClearingStatus()).isCompletedTerminal();
            } catch (IllegalArgumentException ignored) {
                completed = false;
            }
        }
        if (!completed || state.getClearingRevision() == null || state.getClearingRevision() < 1
                || !Objects.equals(state.getTransactionId(), source.operation().transactionId())
                || !Objects.equals(state.getOperationId(), source.operation().operationId())
                || !Objects.equals(state.getMerchantId(), source.operation().merchantId())
                || !Objects.equals(state.getTransactionDateTime(), source.operation().transactionDateTime())) {
            throw failure(ClearingFailureCodeEnum.SOURCE_CLEARING_PENDING,
                    "source transaction clearing state is unavailable or not completed");
        }
    }

    /** 按 locator 精确读取生命周期退款动作，禁止跨季度猜测或重复统计。 */
    private List<LocatorFacts> refundLocators(CompletionCommand command, SourceContext source) {
        ClearingOperationFacts refund = command.claim().operation();
        List<ClearingTransactionLocatorDO> rows = contextMapper.selectRefundLocators(
                refund.merchantId(), refund.operationId());
        if (rows == null || rows.isEmpty()) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "refund lifecycle locators are unavailable");
        }
        List<LocatorFacts> result = new ArrayList<>();
        Set<String> identities = new LinkedHashSet<>();
        boolean currentFound = false;
        for (ClearingTransactionLocatorDO row : rows) {
            if (row == null || !REFUND.equals(row.getTransactionType())
                    || !Objects.equals(row.getOperationId(), refund.operationId())
                    || !Objects.equals(row.getMerchantId(), refund.merchantId())
                    || !Objects.equals(row.getRootTransactionId(), source.locator().rootTransactionId())
                    || !Objects.equals(row.getRootTransactionDateTime(), source.locator().rootTransactionDateTime())
                    || !StringUtils.hasText(row.getTransactionId()) || row.getTransactionDateTime() == null
                    || !identities.add(row.getTransactionId())) {
                throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                        "refund lifecycle locator is inconsistent or duplicated");
            }
            currentFound |= Objects.equals(row.getTransactionId(), refund.transactionId())
                    && Objects.equals(row.getTransactionDateTime(), refund.transactionDateTime());
            result.add(new LocatorFacts(row.getTransactionId(), row.getOperationId(), row.getRootTransactionId(),
                    row.getMerchantId(), row.getMerchantOrderNo(), row.getTransactionType(),
                    row.getTransactionDateTime(), row.getRootTransactionDateTime()));
        }
        if (!currentFound) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "current refund locator is absent from lifecycle");
        }
        return List.copyOf(result);
    }

    /** 以已完成退款清分事实累计标签金额，作为本次返费比例分摊前值。 */
    private BigDecimal refundedLabelAmountBefore(CompletionCommand command,
                                                 SourceContext source,
                                                 List<LocatorFacts> locators,
                                                 List<ClearingTransactionDetailDO> facts) {
        Map<String, LocalDateTime> locatorTimes = new LinkedHashMap<>();
        locators.forEach(locator -> locatorTimes.put(locator.transactionId(), locator.transactionDateTime()));
        BigDecimal total = BigDecimal.ZERO;
        if (facts == null) {
            return total;
        }
        for (ClearingTransactionDetailDO row : facts) {
            validateRefundFact(command, source, locatorTimes, row);
            if (!"PRINCIPAL".equals(row.getItemType())) {
                continue;
            }
            if (!EntryDirection.DEBIT.name().equals(row.getDirection())
                    || row.getLabelAmount() == null || row.getLabelAmount().signum() < 0
                    || !Objects.equals(row.getLabelCurrency(), source.operation().labelCurrency())
                    || !Objects.equals(row.getLabelCurrencyExponent(), source.operation().currencyExponent())) {
                throw failure(ClearingFailureCodeEnum.AMOUNT_INVALID,
                        "historical refund principal label amount is inconsistent");
            }
            total = total.add(row.getLabelAmount(), CALCULATION_CONTEXT);
        }
        return total;
    }

    /** 每条历史退款事实必须同源支付、同标签币种且金额为正。 */
    private void validateRefundFact(CompletionCommand command,
                                    SourceContext source,
                                    Map<String, LocalDateTime> locatorTimes,
                                    ClearingTransactionDetailDO row) {
        if (row == null || !Objects.equals(row.getOperationId(), command.claim().operation().operationId())
                || !Objects.equals(row.getMerchantId(), command.claim().operation().merchantId())
                || !Objects.equals(row.getSourceTransactionId(), source.operation().transactionId())
                || !REFUND.equals(row.getTransactionType()) || !ACTIVE.equals(row.getRecordStatus())
                || !Objects.equals(locatorTimes.get(row.getTransactionId()), row.getTransactionDateTime())
                || !Set.of("PRINCIPAL", "FEE_REVERSAL").contains(row.getItemType())) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "historical refund clearing fact is inconsistent");
        }
    }

    /** 按原收费组重建可返费用组件，禁止用当前费用配置重新计算原收费。 */
    private Map<String, SourceFeeGroup> sourceFeeGroups(CompletionCommand command,
                                                       SourceContext source,
                                                       ClearingTransactionFinanceStateDO sourceState,
                                                       List<ClearingTransactionDetailDO> sourceDetails,
                                                       List<ClearingTransactionDetailDO> refundFacts) {
        Map<String, List<ClearingTransactionDetailDO>> rowsByGroup = new LinkedHashMap<>();
        if (sourceDetails != null) {
            sourceDetails.stream()
                    .filter(row -> "PLATFORM_FEE".equals(row.getItemType()))
                    .sorted(Comparator.comparing(ClearingTransactionDetailDO::getLineNo,
                            Comparator.nullsLast(Integer::compareTo)))
                    .forEach(row -> {
                        validateSourceFeeDetail(source, sourceState, row);
                        rowsByGroup.computeIfAbsent(row.getFeeGroupNo(), ignored -> new ArrayList<>()).add(row);
                    });
        }
        Map<String, RefundedFeeFact> refundedBefore = refundedFeeAmounts(refundFacts);
        Map<String, SourceFeeGroup> result = new LinkedHashMap<>();
        for (List<ClearingTransactionDetailDO> rows : rowsByGroup.values()) {
            if (pendingSettlementRate(rows)) {
                throw failure(ClearingFailureCodeEnum.SOURCE_SETTLEMENT_PENDING,
                        "source fee group requires its settlement result before refund fee reversal");
            }
            ClearingTransactionDetailDO representative = rows.stream()
                    .filter(row -> EntryDirection.DEBIT.name().equals(row.getDirection()))
                    .findFirst()
                    .orElseThrow(() -> failure(ClearingFailureCodeEnum.AMOUNT_INVALID,
                            "source fee group has no charged component"));
            BigDecimal charged = rows.stream()
                    .map(row -> EntryDirection.DEBIT.name().equals(row.getDirection())
                            ? row.getAmount() : row.getAmount().negate())
                    .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, CALCULATION_CONTEXT));
            if (charged.signum() < 0) {
                throw failure(ClearingFailureCodeEnum.AMOUNT_INVALID,
                        "source fee group net charged amount must not be negative");
            }
            if (charged.signum() == 0) {
                continue;
            }
            Money originalCharged = new Money(charged, representative.getCurrency(),
                    representative.getCurrencyExponent());
            RefundedFeeFact refunded = refundedBefore.get(representative.getClearingDetailNo());
            if (refunded != null && (!Objects.equals(refunded.currency(), representative.getCurrency())
                    || !Objects.equals(refunded.exponent(), representative.getCurrencyExponent()))) {
                throw failure(ClearingFailureCodeEnum.FEE_COMPONENT_CURRENCY_INVALID,
                        "historical fee reversal currency does not match the source fee group");
            }
            Money returned = new Money(refunded == null ? BigDecimal.ZERO : refunded.amount(),
                    representative.getCurrency(), representative.getCurrencyExponent());
            if (returned.amount().compareTo(originalCharged.amount()) > 0) {
                throw failure(ClearingFailureCodeEnum.AMOUNT_INVALID,
                        "historical fee reversal exceeds the source actual charged fee");
            }
            SourceFeeGroup group = new SourceFeeGroup(representative, originalCharged, returned);
            result.put(representative.getClearingDetailNo(), group);
        }
        if (!result.keySet().containsAll(refundedBefore.keySet())) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "historical fee reversal references an unavailable source fee group");
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    /** 原费用明细必须属于源动作当前修订，并保留原币种和规则快照。 */
    private void validateSourceFeeDetail(SourceContext source,
                                         ClearingTransactionFinanceStateDO sourceState,
                                         ClearingTransactionDetailDO row) {
        if (row == null || !StringUtils.hasText(row.getClearingDetailNo())
                || !StringUtils.hasText(row.getFeeGroupNo()) || row.getAmount() == null
                || row.getAmount().signum() < 0 || !StringUtils.hasText(row.getCurrency())
                || row.getCurrencyExponent() == null
                || !Set.of(EntryDirection.DEBIT.name(), EntryDirection.CREDIT.name()).contains(row.getDirection())
                || !Objects.equals(row.getTransactionId(), source.operation().transactionId())
                || !Objects.equals(row.getOperationId(), source.operation().operationId())
                || !Objects.equals(row.getMerchantId(), source.operation().merchantId())
                || !Objects.equals(row.getClearingRevision(), sourceState.getClearingRevision())
                || !Objects.equals(row.getTransactionDateTime(), source.operation().transactionDateTime())
                || !ACTIVE.equals(row.getRecordStatus())) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "source fee clearing detail is incomplete or inconsistent");
        }
    }

    /** 存在待结算汇率限额的原费用时暂停返费，避免使用未锁定换汇结果。 */
    private boolean pendingSettlementRate(List<ClearingTransactionDetailDO> rows) {
        Set<String> currencies = new LinkedHashSet<>();
        rows.forEach(row -> currencies.add(row.getCurrency() + ":" + row.getCurrencyExponent()));
        if (currencies.size() != 1 || rows.stream().anyMatch(
                row -> FeeEvaluationStatus.PENDING_SETTLEMENT_RATE.name().equals(row.getLimitEvaluationStatus()))) {
            return true;
        }
        boolean hasUsdBoundary = rows.stream().anyMatch(
                row -> row.getMinimumAmountUsd() != null || row.getMaximumAmountUsd() != null);
        return hasUsdBoundary && rows.stream().anyMatch(row -> !"USD".equals(row.getCurrency()));
    }

    /** 按源清分明细号和币种累计已返费用，阻止多次退款超额返还。 */
    private Map<String, RefundedFeeFact> refundedFeeAmounts(List<ClearingTransactionDetailDO> facts) {
        Map<String, RefundedFeeFact> result = new LinkedHashMap<>();
        if (facts == null) {
            return result;
        }
        for (ClearingTransactionDetailDO row : facts) {
            if (!"FEE_REVERSAL".equals(row.getItemType())) {
                continue;
            }
            if (!EntryDirection.CREDIT.name().equals(row.getDirection())
                    || !StringUtils.hasText(row.getSourceClearingDetailNo())
                    || row.getAmount() == null || row.getAmount().signum() < 0
                    || !StringUtils.hasText(row.getCurrency()) || row.getCurrencyExponent() == null) {
                throw failure(ClearingFailureCodeEnum.AMOUNT_INVALID,
                        "historical fee reversal amount is incomplete");
            }
            RefundedFeeFact existing = result.get(row.getSourceClearingDetailNo());
            if (existing != null && (!Objects.equals(existing.currency(), row.getCurrency())
                    || !Objects.equals(existing.exponent(), row.getCurrencyExponent()))) {
                throw failure(ClearingFailureCodeEnum.FEE_COMPONENT_CURRENCY_INVALID,
                        "historical fee reversals for one source use different currencies");
            }
            BigDecimal amount = existing == null ? row.getAmount()
                    : existing.amount().add(row.getAmount(), CALCULATION_CONTEXT);
            result.put(row.getSourceClearingDetailNo(),
                    new RefundedFeeFact(row.getCurrency(), row.getCurrencyExponent(), amount));
        }
        return result;
    }

    /** 原保证金状态必须与 HOLD 快照、币种、金额恒等式和版本一致。 */
    private void validateReserveState(SourceContext source,
                                      ClearingTransactionFinanceStateDO sourceState,
                                      ClearingReserveStateDO state,
                                      BigDecimal expectedHoldAmount) {
        BigDecimal accounted;
        if (state == null || state.getRetainedAmount() == null || state.getReturnedAmount() == null
                || state.getReleasedAmount() == null || state.getRemainingAmount() == null) {
            throw failure(ClearingFailureCodeEnum.RESERVE_SOURCE_NOT_FOUND,
                    "source reserve state is unavailable");
        }
        accounted = state.getReturnedAmount().add(state.getReleasedAmount(), CALCULATION_CONTEXT)
                .add(state.getRemainingAmount(), CALCULATION_CONTEXT);
        if (!Objects.equals(state.getOriginalTransactionId(), source.operation().transactionId())
                || !Objects.equals(state.getOperationId(), source.operation().operationId())
                || !Objects.equals(state.getOriginalFinanceStateId(), sourceState.getFinanceStateId())
                || !Objects.equals(state.getMerchantId(), source.operation().merchantId())
                || !Objects.equals(state.getTransactionDateTime(), source.operation().transactionDateTime())
                || !Objects.equals(state.getOriginalFeePlanVersionId(), source.feeSnapshot().feePlanVersionId())
                || !StringUtils.hasText(state.getOriginalHoldDetailNo())
                || !StringUtils.hasText(state.getOriginalReserveSnapshotHash())
                || !Objects.equals(state.getReserveCurrency(), source.operation().labelCurrency())
                || !Objects.equals(state.getReserveCurrencyExponent(), source.operation().currencyExponent())
                || state.getOriginalBasisAmount() == null
                || state.getOriginalBasisAmount().compareTo(source.operation().labelAmount()) != 0
                || state.getOriginalReserveRate() == null
                || state.getOriginalReserveRate().compareTo(source.feeSnapshot().reserve().reserveRate()) != 0
                || !Objects.equals(state.getOriginalRoundingMode(), source.feeSnapshot().roundingMode().name())
                || state.getVersion() == null || state.getReleasedAmount().signum() != 0
                || state.getRetainedAmount().signum() < 0 || state.getReturnedAmount().signum() < 0
                || state.getRemainingAmount().signum() < 0
                || state.getRetainedAmount().compareTo(expectedHoldAmount) != 0
                || state.getRetainedAmount().compareTo(accounted) != 0
                || !Set.of("OPEN", "FULLY_RETURNED").contains(state.getReserveStatus())) {
            throw failure(ClearingFailureCodeEnum.RESERVE_STATE_CONFLICT,
                    "source reserve state does not match the frozen source transaction facts");
        }
    }

    /** 将冻结配置枚举映射为 finance-library 返费策略，不接受未知值。 */
    private FeeRefundPolicy refundPolicy(RefundFeeReturnPolicy policy) {
        if (policy == null) {
            throw failure(ClearingFailureCodeEnum.FEE_SNAPSHOT_HASH_MISMATCH,
                    "source fee refund policy is unavailable");
        }
        return FeeRefundPolicy.valueOf(policy.name());
    }

    private Money labelMoney(ClearingOperationFacts operation) {
        if (operation.labelAmount() == null || operation.labelAmount().signum() < 0
                || !StringUtils.hasText(operation.labelCurrency()) || operation.currencyExponent() == null) {
            throw failure(ClearingFailureCodeEnum.AMOUNT_INVALID,
                    "transaction label amount is incomplete");
        }
        return new Money(operation.labelAmount(), operation.labelCurrency(), operation.currencyExponent());
    }

    /** 从原 HOLD 事实读取标签币种扣留金额，不使用查询摘要字段替代。 */
    private BigDecimal originalReserveHoldAmount(SourceContext source) {
        return source.operation().labelAmount()
                .multiply(source.feeSnapshot().reserve().reserveRate(), CALCULATION_CONTEXT)
                .divide(new BigDecimal("100"), CALCULATION_CONTEXT)
                .setScale(source.operation().currencyExponent(), source.feeSnapshot().roundingMode());
    }

    private Money money(BigDecimal amount, ClearingReserveStateDO state) {
        return new Money(amount, state.getReserveCurrency(), state.getReserveCurrencyExponent());
    }

    /** 按规则 ID 排序初始化并加锁全部阶梯累计行，避免多规则并发死锁。 */
    private Map<Long, LockedTier> lockTiers(CompletionCommand command, LocalDateTime now) {
        return lockTiers(command, now, true);
    }

    /** 使用重放冻结的累计基线锁定规则闭包，禁止只更新部分阶梯。 */
    private Map<Long, LockedTier> lockTiers(CompletionCommand command,
                                           LocalDateTime now,
                                           boolean enforceReplayGate) {
        Map<Long, LockedTier> result = new LinkedHashMap<>();
        String periodKey = command.claim().operation().transactionDateTime().format(TIER_PERIOD_FORMATTER);
        List<FeeRuleConfigurationSnapshot> applicableRules;
        try {
            applicableRules = command.feeSnapshot().rules().stream()
                    .filter(rule -> rule.calculationRule().feeMode() == FeeMode.TIER)
                    .filter(rule -> ClearingFeeRuleMatcher.matches(command.claim().operation(),
                            command.paymentType(), command.paymentMethod(), command.occurredRiskServices(),
                            command.feeSnapshot().settlementCurrency(), rule))
                    .sorted(Comparator.comparing(FeeRuleConfigurationSnapshot::ruleId))
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw failure(ClearingFailureCodeEnum.FEE_RULE_NOT_CONFIGURED, exception.getMessage());
        }
        if (applicableRules.isEmpty()) {
            return result;
        }
        if (enforceReplayGate && tierReplayMapper.countBlocking(
                command.claim().operation().merchantId(), command.feeSnapshot().feePlanVersionId(), periodKey) > 0) {
            metrics.recordTierReplay("BLOCKED_CLEARING");
            throw failure(ClearingFailureCodeEnum.TIER_ACCUMULATOR_CONFLICT,
                    "tier period replay blocks new clearing in the same accumulator closure");
        }
        List<Long> ruleIds = applicableRules.stream().map(FeeRuleConfigurationSnapshot::ruleId).toList();
        long lockStartNanos = System.nanoTime();
        List<ClearingFeeTierAccumulatorDO> lockedRows;
        try {
            tierAccumulatorMapper.insertIfAbsentBatch(
                    command.claim().operation().merchantId(), command.feeSnapshot().feePlanVersionId(),
                    ruleIds, periodKey, now);
            lockedRows = tierAccumulatorMapper.selectForUpdateBatch(
                    command.claim().operation().merchantId(), command.feeSnapshot().feePlanVersionId(),
                    ruleIds, periodKey);
            if (enforceReplayGate && tierReplayMapper.countBlocking(
                    command.claim().operation().merchantId(), command.feeSnapshot().feePlanVersionId(), periodKey) > 0) {
                metrics.recordTierReplay("BLOCKED_CLEARING");
                throw failure(ClearingFailureCodeEnum.TIER_ACCUMULATOR_CONFLICT,
                        "tier period replay began while waiting for accumulator locks");
            }
        } finally {
            long durationNanos = System.nanoTime() - lockStartNanos;
            applicableRules.stream()
                    .map(rule -> rule.calculationRule().tierMetric().name())
                    .distinct()
                    .forEach(ruleType -> metrics.recordTierLock(ruleType, durationNanos));
        }
        Map<Long, ClearingFeeTierAccumulatorDO> rowsByRuleId = new LinkedHashMap<>();
        if (lockedRows != null) {
            lockedRows.forEach(row -> {
                if (row == null || row.getFeeRuleId() == null
                        || rowsByRuleId.putIfAbsent(row.getFeeRuleId(), row) != null) {
                    throw failure(ClearingFailureCodeEnum.TIER_ACCUMULATOR_CONFLICT,
                            "fee tier accumulator batch contains an invalid or duplicated row");
                }
            });
        }
        if (rowsByRuleId.size() != applicableRules.size()) {
            throw failure(ClearingFailureCodeEnum.TIER_ACCUMULATOR_CONFLICT,
                    "fee tier accumulator batch is incomplete");
        }
        for (FeeRuleConfigurationSnapshot rule : applicableRules) {
            ClearingFeeTierAccumulatorDO row = rowsByRuleId.get(rule.ruleId());
            if (row == null || row.getAccumulatedCount() == null || row.getAccumulatedAmountUsd() == null
                    || row.getVersion() == null) {
                throw failure(ClearingFailureCodeEnum.TIER_ACCUMULATOR_CONFLICT,
                        "fee tier accumulator is unavailable after initialization");
            }
            BigDecimal currentAmountUsd = rule.calculationRule().tierMetric() == TierMetric.AMOUNT
                    ? currentUsdAmount(command.claim().operation()) : BigDecimal.ZERO;
            TierContext context = new TierContext(
                    row.getAccumulatedCount(), row.getAccumulatedAmountUsd(), currentAmountUsd);
            result.put(rule.ruleId(), new LockedTier(row, context, periodKey));
        }
        return result;
    }

    /** 阶梯 AMOUNT 指标只接受已经冻结的 USD 归一金额。 */
    private BigDecimal currentUsdAmount(ClearingOperationFacts operation) {
        if ("USD".equals(operation.labelCurrency()) && operation.labelAmount() != null) {
            return validatedUsdTierAmount(operation.labelAmount());
        }
        if ("USD".equals(operation.approvedCurrency()) && operation.approvedAmount() != null) {
            return validatedUsdTierAmount(operation.approvedAmount());
        }
        if ("USD".equals(operation.transactionCurrency()) && operation.transactionAmount() != null) {
            return validatedUsdTierAmount(operation.transactionAmount());
        }
        throw failure(ClearingFailureCodeEnum.FEE_COMPONENT_CURRENCY_INVALID,
                "USD amount tier requires a frozen transaction USD fact; clearing must not infer an FX rate");
    }

    /** USD 阶梯累计金额必须非负并保持高精度，不能过早按展示精度舍入。 */
    private BigDecimal validatedUsdTierAmount(BigDecimal amount) {
        if (amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2) {
            throw failure(ClearingFailureCodeEnum.AMOUNT_INVALID,
                    "frozen transaction USD tier amount is negative or exceeds USD exponent");
        }
        return amount;
    }

    /** 清分明细成功写入后以批量版本 CAS 推进全部命中阶梯累计。 */
    private void applyTierDeltas(CompletionCommand command,
                                 int revision,
                                 ClearingCalculationResult calculation,
                                 Map<Long, LockedTier> lockedTiers,
                                 LocalDateTime now) {
        Set<Long> appliedRuleIds = new LinkedHashSet<>();
        List<ClearingFeeTierAccumulatorDelta> deltas = new ArrayList<>();
        String periodKey = null;
        for (CalculatedFee fee : calculation.fees()) {
            LockedTier locked = lockedTiers.get(fee.rule().ruleId());
            if (locked == null || !appliedRuleIds.add(fee.rule().ruleId())) {
                continue;
            }
            ClearingFeeTierAccumulatorDO row = locked.row();
            if (periodKey != null && !periodKey.equals(locked.periodKey())) {
                throw failure(ClearingFailureCodeEnum.TIER_ACCUMULATOR_CONFLICT,
                        "fee tier accumulators use inconsistent periods");
            }
            periodKey = locked.periodKey();
            deltas.add(new ClearingFeeTierAccumulatorDelta(
                    fee.rule().ruleId(), row.getVersion(), locked.context().currentAmountUsd()));
        }
        if (deltas.size() != lockedTiers.size()) {
            throw failure(ClearingFailureCodeEnum.TIER_ACCUMULATOR_CONFLICT,
                    "calculated tier rules do not match locked accumulator rows");
        }
        if (!deltas.isEmpty()) {
            requireRows(tierAccumulatorMapper.applyDeltas(
                    command.claim().operation().merchantId(), command.feeSnapshot().feePlanVersionId(),
                    periodKey, deltas, command.claim().operation().transactionId(), revision,
                    command.claim().operation().transactionDateTime(), now), deltas.size(),
                    "fee tier accumulator batch CAS");
        }
    }

    private List<ClearingTransactionDetailDO> transactionDetails(CompletionCommand command,
                                                                 String financeStateId,
                                                                 int revision,
                                                                 ClearingCalculationResult calculation,
                                                                 RefundStageContext refundContext,
                                                                 LocalDateTime now) {
        List<ClearingTransactionDetailDO> rows = new ArrayList<>();
        if (calculation.principal() != null) {
            rows.add(principalDetail(command, financeStateId, revision, calculation, rows.size() + 1, now));
        }
        for (CalculatedFee fee : calculation.fees()) {
            String feeGroupNo = "FG" + idGenerator.nextId();
            int componentNo = 0;
            for (FeeComponent component : fee.result().components()) {
                componentNo++;
                rows.add(feeDetail(command, financeStateId, revision, fee, component,
                        feeGroupNo, componentNo, rows.size() + 1, now));
            }
        }
        if (calculation.feeRefund() != null) {
            int componentNo = 0;
            for (FeeRefundComponent component : calculation.feeRefund().components()) {
                componentNo++;
                SourceFeeGroup sourceGroup = refundContext.sourceFeeGroups().get(component.sourceComponentNo());
                if (sourceGroup == null) {
                    throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                            "calculated fee reversal source is unavailable");
                }
                rows.add(feeReversalDetail(command, financeStateId, revision, calculation,
                        component, sourceGroup, componentNo, rows.size() + 1, now));
            }
        }
        return List.copyOf(rows);
    }

    private ClearingTransactionDetailDO principalDetail(CompletionCommand command,
                                                        String financeStateId,
                                                        int revision,
                                                        ClearingCalculationResult calculation,
                                                        int lineNo,
                                                        LocalDateTime now) {
        ClearingOperationFacts operation = command.claim().operation();
        Money principal = calculation.principal();
        ClearingTransactionDetailDO row = baseDetail(command, financeStateId, revision, lineNo, now);
        row.setClearingDetailNo("CD" + idGenerator.nextId());
        row.setItemType("PRINCIPAL");
        row.setRiskServiceType("NONE");
        row.setItemCode("PRINCIPAL");
        row.setItemName(ClearingItemNameResolver.transaction("PRINCIPAL", null, null));
        row.setDirection(calculation.principalDirection().name());
        row.setComponentNo(1);
        row.setComponentType("PRINCIPAL");
        row.setBasisCurrency(principal.currency());
        row.setBasisAmount(principal.amount());
        row.setBasisCurrencyExponent(principal.exponent());
        row.setAmount(principal.amount());
        row.setCurrency(principal.currency());
        row.setCurrencyExponent(principal.exponent());
        row.setLimitEvaluationStatus("NOT_REQUIRED");
        row.setAppliedLimit("NONE");
        row.setRoundingMode(command.feeSnapshot().roundingMode().name());
        row.setFormulaSnapshot("principal = authoritative approved amount, falling back to transaction amount");
        row.setFeeSnapshotHash(null);
        return row;
    }

    /** 将一个费用组件固化为不可变原币种明细，保留标签百分比和 USD 固定费口径。 */
    private ClearingTransactionDetailDO feeDetail(CompletionCommand command,
                                                  String financeStateId,
                                                  int revision,
                                                  CalculatedFee calculatedFee,
                                                  FeeComponent component,
                                                  String feeGroupNo,
                                                  int componentNo,
                                                  int lineNo,
                                                  LocalDateTime now) {
        FeeRuleConfigurationSnapshot rule = calculatedFee.rule();
        ClearingTransactionDetailDO row = baseDetail(command, financeStateId, revision, lineNo, now);
        row.setClearingDetailNo("CD" + idGenerator.nextId());
        row.setItemType("PLATFORM_FEE");
        row.setFeeCategory(rule.feeCategory());
        row.setRiskServiceType("RISK_FEE".equals(rule.feeCategory()) ? rule.riskServiceType() : "NONE");
        row.setItemCode("FEE:" + rule.ruleId() + ":" + component.componentType().name() + ":" + componentNo);
        row.setItemName(ClearingItemNameResolver.transaction(
                "PLATFORM_FEE", rule.feeCategory(), row.getRiskServiceType()));
        row.setDirection(component.direction().name());
        row.setFeeGroupNo(feeGroupNo);
        row.setComponentNo(componentNo);
        row.setComponentType(component.componentType().name());
        Money basis = component.basisAmount() == null ? component.amount() : component.basisAmount();
        row.setBasisCurrency(basis.currency());
        row.setBasisAmount(basis.amount());
        row.setBasisCurrencyExponent(basis.exponent());
        row.setAmount(component.amount().amount());
        row.setCurrency(component.amount().currency());
        row.setCurrencyExponent(component.amount().exponent());
        row.setFeePlanId(command.feeSnapshot().feePlanId());
        row.setFeePlanVersionId(command.feeSnapshot().feePlanVersionId());
        row.setFeePlanVersionNo(command.feeSnapshot().feePlanVersionNo());
        row.setFeeRuleId(rule.ruleId());
        row.setFeeRuleTierId(component.tierId());
        row.setChargeTrigger(rule.chargeTrigger());
        row.setFeeMode(rule.calculationRule().feeMode().name());
        applyTierSnapshot(row, rule, calculatedFee.tierContext(), command);
        row.setPercentageRate(component.percentageRate());
        row.setFixedAmountUsd(component.componentType() == FeeComponentType.FIXED
                ? component.amount().amount() : null);
        row.setMinimumAmountUsd(amount(calculatedFee.result().minimumFeeUsd()));
        row.setMaximumAmountUsd(amount(calculatedFee.result().maximumFeeUsd()));
        row.setLimitEvaluationStatus(calculatedFee.result().limitEvaluationStatus().name());
        row.setAppliedLimit(calculatedFee.result().appliedLimit().name());
        row.setRoundingMode(command.feeSnapshot().roundingMode().name());
        row.setFormulaSnapshot(formula(component));
        row.setRuleSnapshotJson(JsonUtils.toJsonString(rule));
        row.setFeeSnapshotHash(command.feeSnapshot().snapshotHash());
        return row;
    }

    /** 返费明细引用原收费行并保留原币种，不重新应用当前规则或汇率。 */
    private ClearingTransactionDetailDO feeReversalDetail(CompletionCommand command,
                                                           String financeStateId,
                                                           int revision,
                                                           ClearingCalculationResult calculation,
                                                           FeeRefundComponent component,
                                                           SourceFeeGroup sourceGroup,
                                                           int componentNo,
                                                           int lineNo,
                                                           LocalDateTime now) {
        ClearingTransactionDetailDO source = sourceGroup.representative();
        ClearingTransactionDetailDO row = baseDetail(command, financeStateId, revision, lineNo, now);
        row.setClearingDetailNo("CD" + idGenerator.nextId());
        row.setSourceClearingDetailNo(source.getClearingDetailNo());
        row.setItemType("FEE_REVERSAL");
        row.setFeeCategory(source.getFeeCategory());
        row.setRiskServiceType(StringUtils.hasText(source.getRiskServiceType())
                ? source.getRiskServiceType() : "NONE");
        row.setItemCode("FEE_REVERSAL:" + componentNo);
        row.setItemName(ClearingItemNameResolver.transaction(
                "FEE_REVERSAL", row.getFeeCategory(), row.getRiskServiceType()));
        row.setDirection(component.direction().name());
        row.setFeeGroupNo("FRG" + idGenerator.nextId());
        row.setComponentNo(componentNo);
        row.setComponentType("REVERSAL");
        row.setBasisCurrency(command.claim().operation().labelCurrency());
        row.setBasisAmount(command.claim().operation().labelAmount());
        row.setBasisCurrencyExponent(command.claim().operation().currencyExponent());
        row.setAmount(component.amount().amount());
        row.setCurrency(component.amount().currency());
        row.setCurrencyExponent(component.amount().exponent());
        row.setFeePlanId(command.source().feeSnapshot().feePlanId());
        row.setFeePlanVersionId(command.source().feeSnapshot().feePlanVersionId());
        row.setFeePlanVersionNo(command.source().feeSnapshot().feePlanVersionNo());
        row.setFeeRuleId(source.getFeeRuleId());
        row.setFeeRuleTierId(source.getFeeRuleTierId());
        row.setChargeTrigger(source.getChargeTrigger());
        row.setFeeMode(source.getFeeMode());
        row.setLimitEvaluationStatus("NOT_REQUIRED");
        row.setAppliedLimit("NONE");
        row.setRoundingMode(command.source().feeSnapshot().roundingMode().name());
        row.setFormulaSnapshot("fee reversal = source actual charged fee * frozen "
                + calculation.feeRefund().policy().name() + " refund ratio "
                + calculation.feeRefund().appliedRatio().toPlainString());
        row.setRuleSnapshotJson(source.getRuleSnapshotJson());
        row.setFeeSnapshotHash(command.source().feeSnapshot().snapshotHash());
        return row;
    }

    private ClearingTransactionDetailDO baseDetail(CompletionCommand command,
                                                   String financeStateId,
                                                   int revision,
                                                   int lineNo,
                                                   LocalDateTime now) {
        ClearingOperationFacts operation = command.claim().operation();
        ClearingTransactionDetailDO row = new ClearingTransactionDetailDO();
        row.setFinanceStateId(financeStateId);
        row.setTransactionId(operation.transactionId());
        row.setOperationId(operation.operationId());
        row.setSourceTransactionId(operation.sourceTransactionId());
        row.setMerchantId(operation.merchantId());
        row.setPaymentType(command.paymentType());
        row.setPaymentMethod(command.paymentMethod());
        row.setTransactionType(operation.transactionType());
        row.setClearingRevision(revision);
        row.setLineNo(lineNo);
        row.setLabelCurrency(operation.labelCurrency());
        row.setLabelAmount(operation.labelAmount());
        row.setLabelCurrencyExponent(operation.currencyExponent());
        row.setSettlementEligibleDate(command.settlementEligibleDate());
        row.setRecordStatus(ACTIVE);
        row.setTransactionDateTime(operation.transactionDateTime());
        row.setTransactionUtcTime(operation.transactionUtcTime());
        row.setTransactionTimeZone(operation.transactionTimeZone());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 将计费时使用的阶梯 before/delta/after 事实写入明细审计快照。 */
    private void applyTierSnapshot(ClearingTransactionDetailDO row,
                                   FeeRuleConfigurationSnapshot rule,
                                   TierContext tier,
                                   CompletionCommand command) {
        if (rule.calculationRule().feeMode() != FeeMode.TIER) {
            return;
        }
        row.setTierPeriodKey(command.claim().operation().transactionDateTime().format(TIER_PERIOD_FORMATTER));
        row.setTierMetric(rule.calculationRule().tierMetric().name());
        row.setTierCountBefore(tier.countBefore());
        row.setTierCountDelta(1L);
        row.setTierCountAfter(tier.countBefore() + 1L);
        row.setTierAmountUsdBefore(tier.amountUsdBefore());
        row.setTierAmountUsdDelta(tier.currentAmountUsd());
        row.setTierAmountUsdAfter(tier.amountUsdBefore().add(tier.currentAmountUsd(), CALCULATION_CONTEXT));
    }

    /** 原支付保证金 HOLD 明细和并发状态在 Stage B 同一事务持久化。 */
    private int persistReserve(CompletionCommand command,
                               String financeStateId,
                               int revision,
                               ReserveCalculationResult reserve,
                               RefundStageContext refundContext,
                               LocalDateTime now) {
        if (reserve == null || reserve.amount().amount().signum() == 0) {
            return 0;
        }
        if (reserve.actionType() == ReserveActionType.RETURN) {
            return persistReserveReturn(
                    command, financeStateId, revision, reserve, refundContext, now);
        }
        if (reserve.actionType() != ReserveActionType.HOLD || refundContext.reserveState() != null) {
            throw failure(ClearingFailureCodeEnum.RESERVE_STATE_CONFLICT,
                    "reserve action does not match the locked clearing context");
        }
        String detailNo = "RD" + idGenerator.nextId();
        ClearingReserveDetailDO detail = reserveHoldDetail(
                command, financeStateId, revision, reserve, detailNo, now);
        requireOne(reserveMapper.insertDetail(detail), "reserve hold detail insert");
        requireOne(reserveMapper.insertState(
                reserveState(command, financeStateId, reserve, detailNo, now)), "reserve state insert");
        return 1;
    }

    /** 退款保证金 RETURN 明细与原状态版本 CAS 在同一事务提交。 */
    private int persistReserveReturn(CompletionCommand command,
                                     String financeStateId,
                                     int revision,
                                     ReserveCalculationResult reserve,
                                     RefundStageContext refundContext,
                                     LocalDateTime now) {
        ClearingReserveStateDO state = refundContext.reserveState();
        if (state == null || refundContext.reserveReturnCommand() == null) {
            throw failure(ClearingFailureCodeEnum.RESERVE_STATE_CONFLICT,
                    "reserve return requires a locked source reserve state");
        }
        String detailNo = "RD" + idGenerator.nextId();
        ClearingReserveDetailDO detail = reserveReturnDetail(
                command, financeStateId, revision, reserve, state, detailNo, now);
        requireOne(reserveMapper.insertDetail(detail), "reserve return detail insert");
        String reserveStatus = reserve.remainingAmount().amount().signum() == 0
                ? "FULLY_RETURNED" : "OPEN";
        requireOne(reserveMapper.applyReturn(
                state.getOriginalTransactionId(), state.getTransactionDateTime(), state.getVersion(),
                reserve.amount().amount(), reserve.remainingAmount().amount(), reserveStatus,
                command.claim().operation().transactionId(), command.claim().operation().transactionDateTime(), now),
                "reserve return state CAS");
        return 1;
    }

    /** 构造标签币种 HOLD 原子事实，不包含汇率或结算目标金额。 */
    private ClearingReserveDetailDO reserveHoldDetail(CompletionCommand command,
                                                      String financeStateId,
                                                      int revision,
                                                      ReserveCalculationResult reserve,
                                                      String detailNo,
                                                      LocalDateTime now) {
        ClearingOperationFacts operation = command.claim().operation();
        ClearingReserveDetailDO row = new ClearingReserveDetailDO();
        row.setReserveClearingDetailNo(detailNo);
        row.setFinanceStateId(financeStateId);
        row.setTransactionId(operation.transactionId());
        row.setOperationId(operation.operationId());
        row.setOriginalTransactionId(operation.transactionId());
        row.setOriginalTransactionDateTime(operation.transactionDateTime());
        row.setMerchantId(operation.merchantId());
        row.setPaymentType(command.paymentType());
        row.setPaymentMethod(command.paymentMethod());
        row.setTransactionType(operation.transactionType());
        row.setClearingRevision(revision);
        row.setLineNo(1);
        row.setReserveActionType("HOLD");
        row.setItemCode("RESERVE:HOLD");
        row.setItemName(ClearingItemNameResolver.reserve("HOLD"));
        row.setDirection(EntryDirection.DEBIT.name());
        row.setReserveCurrency(reserve.amount().currency());
        row.setReserveCurrencyExponent(reserve.amount().exponent());
        row.setBasisAmount(reserve.basisAmount().amount());
        row.setReserveRate(reserve.reserveRate());
        row.setRetainedAmount(reserve.amount().amount());
        row.setReturnedAmount(BigDecimal.ZERO);
        row.setReleasedAmount(BigDecimal.ZERO);
        row.setAdjustmentAmount(BigDecimal.ZERO);
        row.setRemainingAmount(reserve.remainingAmount().amount());
        applyReserveSnapshot(row, command.feeSnapshot());
        row.setFormulaSnapshot("hold = round(label amount * frozen reserve rate / 100)");
        row.setExpectedReserveReleaseDate(command.expectedReserveReleaseDate());
        row.setRecordStatus(ACTIVE);
        row.setTransactionDateTime(operation.transactionDateTime());
        row.setTransactionUtcTime(operation.transactionUtcTime());
        row.setTransactionTimeZone(operation.transactionTimeZone());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 构造引用原 HOLD 的标签币种 RETURN 原子事实。 */
    private ClearingReserveDetailDO reserveReturnDetail(CompletionCommand command,
                                                        String financeStateId,
                                                        int revision,
                                                        ReserveCalculationResult reserve,
                                                        ClearingReserveStateDO state,
                                                        String detailNo,
                                                        LocalDateTime now) {
        ClearingOperationFacts operation = command.claim().operation();
        FeeVersionSnapshot sourceSnapshot = command.source().feeSnapshot();
        ClearingReserveDetailDO row = new ClearingReserveDetailDO();
        row.setReserveClearingDetailNo(detailNo);
        row.setFinanceStateId(financeStateId);
        row.setTransactionId(operation.transactionId());
        row.setOperationId(operation.operationId());
        row.setOriginalTransactionId(state.getOriginalTransactionId());
        row.setOriginalTransactionDateTime(state.getTransactionDateTime());
        row.setSourceReserveDetailNo(state.getOriginalHoldDetailNo());
        row.setMerchantId(operation.merchantId());
        row.setPaymentType(command.paymentType());
        row.setPaymentMethod(command.paymentMethod());
        row.setTransactionType(operation.transactionType());
        row.setClearingRevision(revision);
        row.setLineNo(1);
        row.setReserveActionType("RETURN");
        row.setItemCode("RESERVE:RETURN:" + state.getOriginalHoldDetailNo());
        row.setItemName(ClearingItemNameResolver.reserve("RETURN"));
        row.setDirection(EntryDirection.CREDIT.name());
        row.setReserveCurrency(reserve.amount().currency());
        row.setReserveCurrencyExponent(reserve.amount().exponent());
        row.setBasisAmount(reserve.basisAmount().amount());
        row.setReserveRate(reserve.reserveRate());
        row.setRetainedAmount(BigDecimal.ZERO);
        row.setReturnedAmount(reserve.amount().amount());
        row.setReleasedAmount(BigDecimal.ZERO);
        row.setAdjustmentAmount(BigDecimal.ZERO);
        row.setRemainingAmount(reserve.remainingAmount().amount());
        applyReserveSnapshot(row, sourceSnapshot);
        row.setReserveSnapshotHash(state.getOriginalReserveSnapshotHash());
        row.setFormulaSnapshot("return = min(round(refund label amount * original reserve rate / 100), "
                + "original hold remaining amount)");
        row.setExpectedReserveReleaseDate(state.getExpectedReserveReleaseDate());
        row.setRecordStatus(ACTIVE);
        row.setTransactionDateTime(operation.transactionDateTime());
        row.setTransactionUtcTime(operation.transactionUtcTime());
        row.setTransactionTimeZone(operation.transactionTimeZone());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 从 HOLD 结果构造原支付保证金并发状态，初始资金恒等式必须成立。 */
    private ClearingReserveStateDO reserveState(CompletionCommand command,
                                                String financeStateId,
                                                ReserveCalculationResult reserve,
                                                String detailNo,
                                                LocalDateTime now) {
        ClearingOperationFacts operation = command.claim().operation();
        ClearingReserveStateDO row = new ClearingReserveStateDO();
        row.setReserveStateId("RS" + idGenerator.nextId());
        row.setOriginalTransactionId(operation.transactionId());
        row.setOperationId(operation.operationId());
        row.setOriginalFinanceStateId(financeStateId);
        row.setOriginalHoldDetailNo(detailNo);
        row.setOriginalFeePlanVersionId(command.feeSnapshot().feePlanVersionId());
        row.setOriginalReserveSnapshotHash(reserveSnapshotHash(command.feeSnapshot()));
        row.setMerchantId(operation.merchantId());
        row.setReserveCurrency(reserve.amount().currency());
        row.setReserveCurrencyExponent(reserve.amount().exponent());
        row.setOriginalBasisAmount(reserve.basisAmount().amount());
        row.setOriginalReserveRate(reserve.reserveRate());
        row.setOriginalRoundingMode(command.feeSnapshot().roundingMode().name());
        row.setRetainedAmount(reserve.amount().amount());
        row.setReturnedAmount(BigDecimal.ZERO);
        row.setReleasedAmount(BigDecimal.ZERO);
        row.setDebitAdjustmentAmount(BigDecimal.ZERO);
        row.setCreditAdjustmentAmount(BigDecimal.ZERO);
        row.setRemainingAmount(reserve.remainingAmount().amount());
        row.setExpectedReserveReleaseDate(command.expectedReserveReleaseDate());
        row.setReserveStatus("OPEN");
        row.setTransactionDateTime(operation.transactionDateTime());
        row.setOriginalTransactionUtcTime(operation.transactionUtcTime());
        row.setTransactionTimeZone(operation.transactionTimeZone());
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 将冻结保证金配置、舍入模式和哈希写入独立明细。 */
    private void applyReserveSnapshot(ClearingReserveDetailDO row, FeeVersionSnapshot snapshot) {
        row.setFeePlanId(snapshot.feePlanId());
        row.setFeePlanVersionId(snapshot.feePlanVersionId());
        row.setFeePlanVersionNo(snapshot.feePlanVersionNo());
        row.setReserveSnapshotHash(reserveSnapshotHash(snapshot));
        row.setReserveBasis(snapshot.reserve().reserveBasis().name());
        row.setReserveDelayUnit(snapshot.reserve().delayUnit());
        row.setReserveDelayDays(snapshot.reserve().delayDays());
        row.setRoundingMode(snapshot.roundingMode().name());
    }

    private FinanceSummary financeSummary(CompletionCommand command,
                                           int revision,
                                           String targetStatus,
                                           ClearingCalculationResult result) {
        String labelCurrency = command.claim().operation().labelCurrency();
        Set<String> feeCurrencies = new LinkedHashSet<>();
        result.fees().forEach(fee -> fee.result().components()
                .forEach(component -> feeCurrencies.add(component.amount().currency())));
        if (result.feeRefund() != null) {
            result.feeRefund().components()
                    .forEach(component -> feeCurrencies.add(component.amount().currency()));
        }
        boolean allFeesInLabel = feeCurrencies.stream().allMatch(labelCurrency::equals);
        boolean principalInLabel = result.principal() == null || labelCurrency.equals(result.principal().currency());
        boolean singleCurrencyProjection = allFeesInLabel && principalInLabel;

        BigDecimal principalSigned = signed(result.principal(), result.principalDirection());
        BigDecimal debitFees = feeAmount(result, labelCurrency, EntryDirection.DEBIT);
        BigDecimal creditFees = feeAmount(result, labelCurrency, EntryDirection.CREDIT);
        BigDecimal platformFees = debitFees.subtract(creditFees, CALCULATION_CONTEXT);
        BigDecimal feeReversal = feeReversalAmount(result, labelCurrency);
        BigDecimal merchantReceivable = singleCurrencyProjection
                ? principalSigned.subtract(platformFees, CALCULATION_CONTEXT)
                    .add(feeReversal, CALCULATION_CONTEXT) : null;
        BigDecimal reserveHold = reserveAmount(result.reserve(), ReserveActionType.HOLD);
        BigDecimal reserveReturn = reserveAmount(result.reserve(), ReserveActionType.RETURN);
        BigDecimal net = merchantReceivable == null ? null : merchantReceivable
                .subtract(value(reserveHold), CALCULATION_CONTEXT)
                .add(value(reserveReturn), CALCULATION_CONTEXT);
        boolean hasChargedFeeComponents = result.fees().stream()
                .anyMatch(fee -> !fee.result().components().isEmpty());
        boolean hasFeeFacts = hasChargedFeeComponents
                || result.feeRefund() != null && !result.feeRefund().components().isEmpty();
        String feeStatus = !hasFeeFacts ? null : result.fees().stream()
                    .anyMatch(fee -> fee.result().feeEvaluationStatus()
                            == FeeEvaluationStatus.PENDING_SETTLEMENT_RATE)
                    ? FeeEvaluationStatus.PENDING_SETTLEMENT_RATE.name()
                    : FeeEvaluationStatus.FINAL_AT_CLEARING.name();
        BigDecimal gross = signed(labelMoney(command.claim().operation()), result.principalDirection());
        return new FinanceSummary(targetStatus, revision, command.feeSnapshot().feePlanId(),
                command.feeSnapshot().feePlanVersionId(), command.feeSnapshot().feePlanVersionNo(),
                command.feeSnapshot().snapshotHash(), gross, feeCurrencies.size(), feeStatus,
                labelCurrency, command.feeSnapshot().settlementCurrency(),
                singleCurrencyProjection && hasChargedFeeComponents ? platformFees : null,
                singleCurrencyProjection && result.feeRefund() != null
                        && !result.feeRefund().components().isEmpty() ? feeReversal : null,
                merchantReceivable, reserveHold, reserveReturn, net,
                command.settlementEligibleDate(), command.expectedReserveReleaseDate());
    }

    /**
     * 将正数 Money 按财务方向转换为有符号金额，CREDIT 为正、DEBIT 为负。
     *
     * @param amount 保持原币种精度的正数金额
     * @param direction 财务借贷方向
     * @return 同币种语义的有符号主单位金额；任一输入为空时返回零
     */
    private BigDecimal signed(Money amount, EntryDirection direction) {
        if (amount == null || direction == null) {
            return BigDecimal.ZERO;
        }
        return direction == EntryDirection.CREDIT ? amount.amount() : amount.amount().negate();
    }

    /** 按指定币种汇总原子费用组件；不同币种不得直接相加。 */
    private BigDecimal feeAmount(ClearingCalculationResult result,
                                 String currency,
                                 EntryDirection direction) {
        return result.fees().stream()
                .flatMap(fee -> fee.result().components().stream())
                .filter(component -> component.direction() == direction)
                .filter(component -> currency.equals(component.amount().currency()))
                .map(component -> component.amount().amount())
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, CALCULATION_CONTEXT));
    }

    /** 按原收费币种汇总返费组件；不存在时返回零而非跨币种折算。 */
    private BigDecimal feeReversalAmount(ClearingCalculationResult result, String currency) {
        if (result.feeRefund() == null) {
            return BigDecimal.ZERO;
        }
        return result.feeRefund().components().stream()
                .filter(component -> currency.equals(component.amount().currency()))
                .map(component -> component.amount().amount())
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, CALCULATION_CONTEXT));
    }

    /** 按保证金动作类型读取标签币种金额。 */
    private BigDecimal reserveAmount(ReserveCalculationResult reserve, ReserveActionType actionType) {
        return reserve != null && reserve.actionType() == actionType ? reserve.amount().amount() : null;
    }

    /** 与全部清分事实同事务写入成功消费幂等，数据库唯一键是最终防线。 */
    private void persistSuccessIdempotency(CompletionCommand command,
                                           String targetStatus,
                                           int revision,
                                           LocalDateTime now) {
        ClearingOperationFacts operation = command.claim().operation();
        String resultSnapshot = JsonUtils.toJsonString(Map.of(
                "financeStateId", command.claim().financeStateId(),
                "clearingStatus", targetStatus,
                "clearingRevision", revision));
        requireOne(idempotencyMapper.insertSuccessfulConsumption(
                IDEMPOTENCY_KEY_PREFIX + command.message().getMessageId(), operation.merchantId(),
                operation.merchantOrderNo(), operation.transactionType(), operation.transactionId(),
                operation.operationId(), operation.transactionDateTime(), operation.transactionUtcTime(),
                operation.transactionTimeZone(), resultSnapshot, null, now), "clearing success idempotency insert");
    }

    /** 与清分状态、明细和幂等同事务写入完成 Outbox，事务外发布顺序消息。 */
    private void persistCompletionOutbox(CompletionCommand command,
                                         String targetStatus,
                                         int revision,
                                         LocalDateTime now) {
        ClearingOperationFacts operation = command.claim().operation();
        String eventNo = "CE" + idGenerator.nextId();
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId(eventNo);
        message.setCreatedAt(now);
        message.setTraceId(command.message().getTraceId());
        message.setRetryCount(0);
        message.setTransactionId(operation.transactionId());
        message.setOperationId(operation.operationId());
        message.setMerchantId(operation.merchantId());
        message.setMerchantOrderNo(operation.merchantOrderNo());
        message.setTransactionType(operation.transactionType());
        message.setTransactionStatus(operation.transactionStatus());
        message.setEventType(MqTag.TRANSACTION_CLEARING_COMPLETED);
        message.setTransactionDateTime(operation.transactionDateTime());

        ClearingTransactionEventOutboxDO outbox = new ClearingTransactionEventOutboxDO();
        outbox.setEventNo(eventNo);
        outbox.setAggregateType("TRANSACTION_CLEARING");
        outbox.setAggregateNo(command.claim().financeStateId() + ":" + revision);
        outbox.setTransactionId(operation.transactionId());
        outbox.setOperationId(operation.operationId());
        outbox.setMerchantId(operation.merchantId());
        outbox.setMerchantOrderNo(operation.merchantOrderNo());
        outbox.setTransactionType(operation.transactionType());
        outbox.setEventType(MqTag.TRANSACTION_CLEARING_COMPLETED);
        outbox.setEventStatus(EVENT_STATUS_INIT);
        outbox.setTopic(MqTopic.PAYMENT_TRANSACTION_FIFO);
        outbox.setTag(MqTag.TRANSACTION_CLEARING_COMPLETED);
        outbox.setMessageKey(eventNo);
        outbox.setMessageGroup(operation.operationId());
        outbox.setDeliveryMode("ORDERLY");
        outbox.setPayloadJson(JsonUtils.toJsonString(message));
        outbox.setRetryCount(0);
        outbox.setMaxRetryCount(10);
        outbox.setEventTime(now);
        outbox.setTransactionDateTime(operation.transactionDateTime());
        outbox.setTransactionUtcTime(operation.transactionUtcTime());
        outbox.setTransactionTimeZone(operation.transactionTimeZone());
        outbox.setVersion(0);
        outbox.setDeleted(0);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        requireOne(outboxMapper.insertLogical(outbox), "clearing completion outbox insert");
    }

    private int requiredVersion(Integer version) {
        if (version == null || version < 0) {
            throw failure(ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT,
                    "transaction operation version is unavailable");
        }
        return version;
    }

    /** 提取 Money 十进制主单位金额，空值用于表示该资金组件不存在。 */
    private BigDecimal amount(Money value) {
        return value == null ? null : value.amount();
    }

    private BigDecimal value(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String formula(FeeComponent component) {
        return switch (component.componentType()) {
            case PERCENTAGE -> "fee = round(label amount * frozen percentage rate / 100)";
            case FIXED -> "fee = round(frozen USD fixed amount)";
            case LIMIT_ADJUSTMENT -> "fee adjustment = frozen same-currency minimum or maximum boundary";
        };
    }

    /**
     * 对冻结的保证金配置快照计算 SHA-256 摘要，用于校验清分事实未被替换。
     * @param snapshot 动作受理时冻结的不可变配置快照，用于计算复现和完整性校验
     * @return 当前方法生成或规范化后的文本值
     */
    private String reserveSnapshotHash(FeeVersionSnapshot snapshot) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(JsonUtils.toJsonString(snapshot.reserve()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest unavailable", exception);
        }
    }

    private void requireOne(int affectedRows, String operation) {
        requireRows(affectedRows, 1, operation);
    }

    private void requireRows(int affectedRows, int expectedRows, String operation) {
        if (affectedRows != expectedRows) {
            throw failure(ClearingFailureCodeEnum.CLEARING_CAS_CONFLICT,
                    operation + " did not affect the expected rows");
        }
    }

    private ClearingProcessingException failure(ClearingFailureCodeEnum code, String message) {
        return new ClearingProcessingException(code, message);
    }

    private record LockedTier(ClearingFeeTierAccumulatorDO row,
                              TierContext context,
                              String periodKey) {
    }

    private record SourceFeeGroup(ClearingTransactionDetailDO representative,
                                  Money originalChargedAmount,
                                  Money refundedAmountBefore) {

        private RefundableFeeComponent refundableComponent() {
            return new RefundableFeeComponent(representative.getClearingDetailNo(),
                    originalChargedAmount, refundedAmountBefore);
        }
    }

    private record RefundedFeeFact(String currency, Integer exponent, BigDecimal amount) {
    }

    private record RefundStageContext(FeeRefundCommand feeRefundCommand,
                                      ReserveReturnCommand reserveReturnCommand,
                                      Map<String, SourceFeeGroup> sourceFeeGroups,
                                      ClearingReserveStateDO reserveState) {

        private RefundStageContext {
            sourceFeeGroups = sourceFeeGroups == null ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(sourceFeeGroups));
        }

        private static RefundStageContext empty() {
            return new RefundStageContext(null, null, Map.of(), null);
        }
    }
}
