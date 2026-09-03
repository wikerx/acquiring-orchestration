package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.entity.ClearingMerchantSettlementProfileDO;
import com.scott.payment.clearing.entity.ClearingReserveAdjustmentDO;
import com.scott.payment.clearing.entity.ClearingReserveDetailDO;
import com.scott.payment.clearing.entity.ClearingReserveStateDO;
import com.scott.payment.clearing.mapper.ClearingMerchantSettlementProfileMapper;
import com.scott.payment.clearing.mapper.ClearingReserveAdjustmentMapper;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService;
import com.scott.payment.clearing.service.ReserveAdjustmentService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.clearing.support.ClearingItemNameResolver;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.reserve.core.ReserveCalculator;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentCommand;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentDirection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultReserveAdjustmentService
 * @date : 2026-08-26 19:05
 * @email : scott_x@163.com
 * @description : 以申请唯一键和双人复核驱动标签币种保证金差额事实、状态CAS和独立结算候选。
 * @status : create
 */
@Service
public class DefaultReserveAdjustmentService implements ReserveAdjustmentService {

    /**
     * {@code BUSINESS_ZONE}常量，统一 {@code DefaultReserveAdjustmentService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID);
    /**
     * 等待审核常量，统一 {@code DefaultReserveAdjustmentService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String PENDING_REVIEW = "PENDING_REVIEW";
    /**
     * {@code EXECUTED}常量，统一 {@code DefaultReserveAdjustmentService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String EXECUTED = "EXECUTED";
    /**
     * {@code REJECTED}常量，统一 {@code DefaultReserveAdjustmentService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String REJECTED = "REJECTED";

    private final ClearingReserveAdjustmentMapper adjustmentMapper;
    private final ClearingReserveMapper reserveMapper;
    private final ClearingMerchantSettlementProfileMapper profileMapper;
    private final ClearingSettlementCandidateService candidateService;
    private final ReserveCalculator reserveCalculator;
    private final ClearingOperationalMetrics metrics;

    public DefaultReserveAdjustmentService(ClearingReserveAdjustmentMapper adjustmentMapper,
                                           ClearingReserveMapper reserveMapper,
                                           ClearingMerchantSettlementProfileMapper profileMapper,
                                           ClearingSettlementCandidateService candidateService,
                                           ReserveCalculator reserveCalculator,
                                           ClearingOperationalMetrics metrics) {
        this.adjustmentMapper = adjustmentMapper;
        this.reserveMapper = reserveMapper;
        this.profileMapper = profileMapper;
        this.candidateService = candidateService;
        this.reserveCalculator = reserveCalculator;
        this.metrics = metrics;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReserveAdjustmentResult submit(SubmitCommand command) {
        try {
            ReserveAdjustmentResult result = submitInternal(command);
            recordAfterCommit("SUBMITTED");
            return result;
        } catch (RuntimeException exception) {
            metrics.recordReserveAdjustment("FAILED");
            throw exception;
        }
    }

    /**
     * 锁定保证金状态并创建待复核调整申请，申请阶段不改变余额也不创建结算候选。
     * <p>
     * requestKey 同时约束调整编号和全部冻结参数；命中重放时必须核对业务身份，禁止把同一幂等键用于不同金额或方向。
     */
    private ReserveAdjustmentResult submitInternal(SubmitCommand command) {
        validateSubmit(command);
        String adjustmentNo = stableId("RA", command.requestKey());
        ClearingReserveAdjustmentDO duplicate = adjustmentMapper.selectByRequestKeyForUpdate(command.requestKey());
        if (duplicate != null) {
            validateDuplicate(duplicate, adjustmentNo, command);
            return result(duplicate);
        }

        ClearingReserveStateDO state = reserveMapper.selectStateForUpdate(
                command.originalTransactionId(), command.originalTransactionDateTime());
        validateStateIdentity(state, command.reserveStateId(), command.originalTransactionId(),
                command.originalTransactionDateTime(), command.expectedReserveStateVersion());
        validateAdjustment(state, command.direction(), command.adjustmentAmount());
        LocalDate businessDate = LocalDateTime.ofInstant(command.requestedInstant(), BUSINESS_ZONE).toLocalDate();
        if (command.direction() == ReserveAdjustmentDirection.DEBIT
                && (command.requestedReleaseDate() == null
                || command.requestedReleaseDate().isBefore(businessDate))) {
            throw new IllegalArgumentException("debit reserve adjustment requires a non-past release date");
        }

        LocalDateTime now = LocalDateTime.ofInstant(command.requestedInstant(), ZoneOffset.UTC);
        ClearingReserveAdjustmentDO row = new ClearingReserveAdjustmentDO();
        row.setAdjustmentNo(adjustmentNo);
        row.setRequestKey(command.requestKey());
        row.setReserveStateId(state.getReserveStateId());
        row.setOriginalTransactionId(state.getOriginalTransactionId());
        row.setOriginalTransactionDateTime(state.getTransactionDateTime());
        row.setMerchantId(state.getMerchantId());
        row.setReserveCurrency(state.getReserveCurrency());
        row.setReserveCurrencyExponent(state.getReserveCurrencyExponent());
        row.setDirection(command.direction().name());
        row.setAdjustmentAmount(command.adjustmentAmount());
        row.setRequestedReleaseDate(command.direction() == ReserveAdjustmentDirection.DEBIT
                ? command.requestedReleaseDate() : null);
        row.setExpectedReserveStateVersion(state.getVersion());
        row.setReason(normalized(command.reason(), "reason", 400));
        row.setSubmitOperator(normalized(command.submitOperator(), "submit operator", 128));
        row.setAdjustmentStatus(PENDING_REVIEW);
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        adjustmentMapper.insertIdempotent(row);
        ClearingReserveAdjustmentDO persisted = adjustmentMapper.selectByRequestKeyForUpdate(command.requestKey());
        validateDuplicate(persisted, adjustmentNo, command);
        return result(persisted);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReserveAdjustmentResult review(ReviewCommand command) {
        try {
            ReserveAdjustmentResult result = reviewInternal(command);
            recordAfterCommit(REJECTED.equals(result.status()) ? "REJECTED" : "APPROVED");
            return result;
        } catch (RuntimeException exception) {
            metrics.recordReserveAdjustment("FAILED");
            throw exception;
        }
    }

    /**
     * 以申请版本 CAS 执行 Maker-Checker 复核，并拒绝提交人与复核人为同一可信主体。
     * <p>
     * 拒绝只终结申请；批准则在同一新事务中完成调整事实、保证金状态 CAS、结算候选和申请终态，任一步失败全部回滚。
     */
    private ReserveAdjustmentResult reviewInternal(ReviewCommand command) {
        validateReview(command);
        String reviewer = normalized(command.reviewOperator(), "review operator", 128);
        String comment = normalized(command.reviewComment(), "review comment", 400);
        ClearingReserveAdjustmentDO request = adjustmentMapper.selectForUpdate(command.adjustmentNo());
        if (request == null) {
            throw new IllegalStateException("reserve adjustment request does not exist");
        }
        if (terminalReplay(request, command, reviewer)) {
            return result(request);
        }
        if (!PENDING_REVIEW.equals(request.getAdjustmentStatus())
                || request.getVersion() == null
                || request.getVersion() != command.expectedRequestVersion()) {
            throw new IllegalStateException("reserve adjustment request is stale or not reviewable");
        }
        if (Objects.equals(request.getSubmitOperator(), reviewer)) {
            throw new IllegalStateException("reserve adjustment submitter and reviewer must be different");
        }
        LocalDateTime auditTime = LocalDateTime.ofInstant(command.reviewInstant(), ZoneOffset.UTC);
        if (command.decision() == ReviewDecision.REJECT) {
            requireOne(adjustmentMapper.markRejected(
                    request.getAdjustmentNo(), request.getVersion(), reviewer, comment, auditTime),
                    "reserve adjustment reject CAS");
            return new ReserveAdjustmentResult(request.getAdjustmentNo(), REJECTED, null, 0,
                    request.getVersion() + 1);
        }
        return approve(request, reviewer, comment, command.reviewInstant(), auditTime);
    }

    /** 成功指标在数据库提交后写入；无事务的纯单元测试调用则立即记录。 */
    private void recordAfterCommit(String outcome) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            metrics.recordReserveAdjustment(outcome);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /** 事务真正提交后才发布成功指标，避免回滚操作被统计为已资金化。 */
            @Override
            public void afterCommit() {
                metrics.recordReserveAdjustment(outcome);
            }

            /** @param status Spring 事务最终状态；非提交状态统一记录失败指标 */
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    metrics.recordReserveAdjustment("FAILED");
                }
            }
        });
    }

    /** 批准时在同一事务追加调整事实、CAS 保证金状态、创建候选并终结申请。 */
    private ReserveAdjustmentResult approve(ClearingReserveAdjustmentDO request,
                                            String reviewer,
                                            String comment,
                                            Instant reviewInstant,
                                            LocalDateTime auditTime) {
        LocalDateTime transactionTime = LocalDateTime.ofInstant(reviewInstant, BUSINESS_ZONE);
        LocalDate businessDate = transactionTime.toLocalDate();
        ReserveAdjustmentDirection direction = ReserveAdjustmentDirection.valueOf(request.getDirection());
        if (direction == ReserveAdjustmentDirection.DEBIT
                && (request.getRequestedReleaseDate() == null
                || request.getRequestedReleaseDate().isBefore(businessDate))) {
            throw new IllegalStateException("reviewed debit reserve adjustment release date is already past");
        }

        ClearingReserveStateDO state = reserveMapper.selectStateForUpdate(
                request.getOriginalTransactionId(), request.getOriginalTransactionDateTime());
        validateStateIdentity(state, request.getReserveStateId(), request.getOriginalTransactionId(),
                request.getOriginalTransactionDateTime(), request.getExpectedReserveStateVersion());
        com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentResult calculation =
                validateAdjustment(state, direction, request.getAdjustmentAmount());
        ClearingReserveDetailDO hold = reserveMapper.selectHoldDetail(
                state.getOriginalHoldDetailNo(), state.getTransactionDateTime());
        validateHold(state, hold);
        ClearingMerchantSettlementProfileDO profile = profileMapper.selectActiveProfile(
                state.getMerchantId(), businessDate);
        validateProfile(state, profile);

        int sourceRevision = Math.toIntExact(Math.addExact(state.getVersion(), 1L));
        String transactionId = stableId("RAD", request.getAdjustmentNo() + "|" + sourceRevision);
        ClearingReserveDetailDO detail = adjustmentDetail(
                request, state, hold, calculation, sourceRevision, transactionId,
                transactionTime, auditTime);
        requireOne(reserveMapper.insertDetail(detail), "reserve adjustment detail insert");
        requireOne(reserveMapper.applyAdjustment(
                state.getOriginalTransactionId(), state.getTransactionDateTime(), state.getVersion(),
                direction.name(), calculation.amount().amount(), calculation.remainingAmount().amount(),
                direction == ReserveAdjustmentDirection.DEBIT
                        ? request.getRequestedReleaseDate() : state.getExpectedReserveReleaseDate(), auditTime),
                "reserve adjustment state CAS");
        candidateService.createAdjustment(
                request.getAdjustmentNo(), sourceRevision, transactionId, transactionTime,
                state.getMerchantId(), profile.getTargetCurrency(), businessDate, auditTime);
        requireOne(adjustmentMapper.markExecuted(
                request.getAdjustmentNo(), request.getVersion(), reviewer, comment, auditTime,
                transactionId, sourceRevision, auditTime), "reserve adjustment execute CAS");
        return new ReserveAdjustmentResult(
                request.getAdjustmentNo(), EXECUTED, transactionId, sourceRevision, request.getVersion() + 1);
    }

    /**
     * 将数据库保证金状态转换为强类型 Money 后计算调整结果。
     * <p>
     * CREDIT 表示减少既有保证金责任，必须以 OPEN 状态和当前剩余金额为上限；DEBIT 表示新增责任，币种及币种精度沿用原标签口径。
     */
    private com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentResult
            validateAdjustment(ClearingReserveStateDO state,
                               ReserveAdjustmentDirection direction,
                               BigDecimal amount) {
        if (state.getRemainingAmount() == null || state.getRemainingAmount().signum() < 0
                || state.getReserveCurrencyExponent() == null || !StringUtils.hasText(state.getReserveCurrency())
                || state.getOriginalReserveRate() == null) {
            throw new IllegalStateException("reserve state amount identity is invalid for adjustment");
        }
        if (direction == ReserveAdjustmentDirection.CREDIT && !"OPEN".equals(state.getReserveStatus())) {
            throw new IllegalStateException("credit reserve adjustment requires an open reserve state");
        }
        return reserveCalculator.adjust(new ReserveAdjustmentCommand(
                new Money(state.getRemainingAmount(), state.getReserveCurrency(),
                        state.getReserveCurrencyExponent()),
                new Money(amount, state.getReserveCurrency(), state.getReserveCurrencyExponent()),
                direction, state.getOriginalReserveRate()));
    }

    /**
     * 生成纯保证金 ADJUSTMENT 清分事实，保留原 HOLD 的费率、币种和配置快照身份。
     * <p>
     * 该事实只用于保证金资金化和结算候选，不代表真实交易动作，因此后续不得生成伪交易状态投影。
     */
    private ClearingReserveDetailDO adjustmentDetail(
            ClearingReserveAdjustmentDO request,
            ClearingReserveStateDO state,
            ClearingReserveDetailDO hold,
            com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentResult calculation,
            int sourceRevision,
            String transactionId,
            LocalDateTime transactionTime,
            LocalDateTime auditTime) {
        ClearingReserveDetailDO row = new ClearingReserveDetailDO();
        row.setReserveClearingDetailNo(stableId("RD", request.getAdjustmentNo() + "|" + sourceRevision));
        row.setFinanceStateId(request.getAdjustmentNo());
        row.setTransactionId(transactionId);
        row.setOperationId(state.getOperationId());
        row.setOriginalTransactionId(state.getOriginalTransactionId());
        row.setOriginalTransactionDateTime(state.getTransactionDateTime());
        row.setSourceReserveDetailNo(state.getOriginalHoldDetailNo());
        row.setMerchantId(state.getMerchantId());
        row.setPaymentType(hold.getPaymentType());
        row.setPaymentMethod(hold.getPaymentMethod());
        row.setTransactionType("ADJUSTMENT");
        row.setClearingRevision(sourceRevision);
        row.setLineNo(1);
        row.setReserveActionType("ADJUSTMENT");
        row.setItemCode("RESERVE:ADJUSTMENT:" + request.getAdjustmentNo());
        row.setItemName(ClearingItemNameResolver.reserve("ADJUSTMENT"));
        row.setDirection(calculation.direction().name());
        row.setReserveCurrency(calculation.amount().currency());
        row.setReserveCurrencyExponent(calculation.amount().exponent());
        row.setBasisAmount(calculation.basisAmount().amount());
        row.setReserveRate(calculation.reserveRate());
        row.setRetainedAmount(BigDecimal.ZERO);
        row.setReturnedAmount(BigDecimal.ZERO);
        row.setReleasedAmount(BigDecimal.ZERO);
        row.setAdjustmentAmount(calculation.amount().amount());
        row.setRemainingAmount(calculation.remainingAmount().amount());
        row.setFeePlanId(hold.getFeePlanId());
        row.setFeePlanVersionId(hold.getFeePlanVersionId());
        row.setFeePlanVersionNo(hold.getFeePlanVersionNo());
        row.setReserveSnapshotHash(hold.getReserveSnapshotHash());
        row.setReserveBasis(hold.getReserveBasis());
        row.setReserveDelayUnit(hold.getReserveDelayUnit());
        row.setReserveDelayDays(hold.getReserveDelayDays());
        row.setRoundingMode(hold.getRoundingMode());
        row.setFormulaSnapshot("reviewed adjustment = absolute label-currency amount; DEBIT adds and CREDIT subtracts");
        row.setExpectedReserveReleaseDate(calculation.direction() == ReserveAdjustmentDirection.DEBIT
                ? request.getRequestedReleaseDate() : state.getExpectedReserveReleaseDate());
        row.setRecordStatus("ACTIVE");
        row.setTransactionDateTime(transactionTime);
        row.setTransactionUtcTime(auditTime);
        row.setTransactionTimeZone(TransactionShardingProperties.REQUIRED_ZONE_ID);
        row.setCreateTime(auditTime);
        row.setUpdateTime(auditTime);
        return row;
    }

    /** 锁定状态必须匹配提交时冻结的业务身份、分片时间和版本。 */
    private void validateStateIdentity(ClearingReserveStateDO state,
                                       String reserveStateId,
                                       String transactionId,
                                       LocalDateTime transactionTime,
                                       long expectedVersion) {
        if (state == null || !Objects.equals(reserveStateId, state.getReserveStateId())
                || !Objects.equals(transactionId, state.getOriginalTransactionId())
                || !Objects.equals(transactionTime, state.getTransactionDateTime())
                || state.getVersion() == null || state.getVersion() != expectedVersion) {
            throw new IllegalStateException("reserve adjustment state is missing, stale or mismatched");
        }
    }

    /** 原 HOLD 必须与状态中的快照、币种和商户身份一致。 */
    private void validateHold(ClearingReserveStateDO state, ClearingReserveDetailDO hold) {
        if (hold == null || !"HOLD".equals(hold.getReserveActionType())
                || !Objects.equals(state.getOriginalHoldDetailNo(), hold.getReserveClearingDetailNo())
                || !Objects.equals(state.getOriginalFeePlanVersionId(), hold.getFeePlanVersionId())
                || !Objects.equals(state.getOriginalReserveSnapshotHash(), hold.getReserveSnapshotHash())
                || !Objects.equals(state.getReserveCurrency(), hold.getReserveCurrency())
                || !Objects.equals(state.getReserveCurrencyExponent(), hold.getReserveCurrencyExponent())) {
            throw new IllegalStateException("reserve adjustment source hold snapshot is inconsistent");
        }
    }

    /** 活动结算档案必须属于同一商户并提供合法目标币种。 */
    private void validateProfile(ClearingReserveStateDO state,
                                 ClearingMerchantSettlementProfileDO profile) {
        if (profile == null || !Objects.equals(state.getMerchantId(), profile.getMerchantId())
                || !StringUtils.hasText(profile.getTargetCurrency())
                || profile.getTargetCurrencyExponent() == null) {
            throw new IllegalStateException("active settlement profile is unavailable for reserve adjustment");
        }
    }

    /** 重复复核只有在决定、复核人和终态一致时返回稳定结果。 */
    private boolean terminalReplay(ClearingReserveAdjustmentDO request,
                                   ReviewCommand command,
                                   String reviewer) {
        if (EXECUTED.equals(request.getAdjustmentStatus())) {
            if (command.decision() != ReviewDecision.APPROVE
                    || !Objects.equals(request.getReviewOperator(), reviewer)) {
                throw new IllegalStateException("reserve adjustment already has a different terminal decision");
            }
            return true;
        }
        if (REJECTED.equals(request.getAdjustmentStatus())) {
            if (command.decision() != ReviewDecision.REJECT
                    || !Objects.equals(request.getReviewOperator(), reviewer)) {
                throw new IllegalStateException("reserve adjustment already has a different terminal decision");
            }
            return true;
        }
        return false;
    }

    /** 幂等请求键冲突后核对全部冻结参数，禁止不同申请复用旧结果。 */
    private void validateDuplicate(ClearingReserveAdjustmentDO row,
                                   String adjustmentNo,
                                   SubmitCommand command) {
        if (row == null || !Objects.equals(row.getAdjustmentNo(), adjustmentNo)
                || !Objects.equals(row.getRequestKey(), command.requestKey())
                || !Objects.equals(row.getReserveStateId(), command.reserveStateId())
                || !Objects.equals(row.getOriginalTransactionId(), command.originalTransactionId())
                || !Objects.equals(row.getOriginalTransactionDateTime(), command.originalTransactionDateTime())
                || !Objects.equals(row.getExpectedReserveStateVersion(), command.expectedReserveStateVersion())
                || !Objects.equals(row.getDirection(), command.direction().name())
                || row.getAdjustmentAmount() == null
                || row.getAdjustmentAmount().compareTo(command.adjustmentAmount()) != 0
                || !Objects.equals(row.getRequestedReleaseDate(), command.direction() == ReserveAdjustmentDirection.DEBIT
                        ? command.requestedReleaseDate() : null)
                || !Objects.equals(row.getReason(), normalized(command.reason(), "reason", 400))
                || !Objects.equals(row.getSubmitOperator(), normalized(
                        command.submitOperator(), "submit operator", 128))) {
            throw new IllegalStateException("reserve adjustment request key contains mismatched data");
        }
    }

    /** 提交命令要求正金额、合法方向、冻结版本和可信提交审计。 */
    private void validateSubmit(SubmitCommand command) {
        if (command == null || !StringUtils.hasText(command.requestKey())
                || command.requestKey().length() > 128 || !StringUtils.hasText(command.reserveStateId())
                || !StringUtils.hasText(command.originalTransactionId())
                || command.originalTransactionDateTime() == null
                || command.expectedReserveStateVersion() < 0 || command.direction() == null
                || command.adjustmentAmount() == null || command.adjustmentAmount().signum() <= 0
                || command.requestedInstant() == null) {
            throw new IllegalArgumentException("reserve adjustment submit identity and amount are required");
        }
        normalized(command.reason(), "reason", 400);
        normalized(command.submitOperator(), "submit operator", 128);
    }

    /** 复核命令要求申请版本、固定决定、可信复核人和审计时点。 */
    private void validateReview(ReviewCommand command) {
        if (command == null || !StringUtils.hasText(command.adjustmentNo())
                || command.expectedRequestVersion() < 0 || command.decision() == null
                || command.reviewInstant() == null) {
            throw new IllegalArgumentException("reserve adjustment review identity and decision are required");
        }
        normalized(command.reviewComment(), "review comment", 400);
        normalized(command.reviewOperator(), "review operator", 128);
    }

    private String normalized(String value, String field, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        String result = value.trim().replace('\r', ' ').replace('\n', ' ');
        if (result.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return result;
    }

    /** 以业务身份派生稳定编号，使申请重放得到相同的调整、明细和候选关联键。 */
    private String stableId(String prefix, String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ReserveAdjustmentResult result(ClearingReserveAdjustmentDO row) {
        return new ReserveAdjustmentResult(
                row.getAdjustmentNo(), row.getAdjustmentStatus(), row.getExecutionTransactionId(),
                row.getSourceRevision() == null ? 0 : row.getSourceRevision(),
                row.getVersion() == null ? 0L : row.getVersion());
    }

    private void requireOne(int affected, String operation) {
        if (affected != 1) {
            throw new IllegalStateException(operation + " did not affect the expected row");
        }
    }
}
