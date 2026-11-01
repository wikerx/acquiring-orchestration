package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.domain.model.SettlementCandidateDependencyPlan;
import com.scott.payment.clearing.domain.state.SettlementCandidateDependencyType;
import com.scott.payment.clearing.entity.ClearingMerchantSettlementProfileDO;
import com.scott.payment.clearing.entity.ClearingReserveDetailDO;
import com.scott.payment.clearing.entity.ClearingReserveStateDO;
import com.scott.payment.clearing.mapper.ClearingMerchantSettlementProfileMapper;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService;
import com.scott.payment.clearing.service.ReserveReleaseService;
import com.scott.payment.clearing.support.ClearingItemNameResolver;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.reserve.core.ReserveCalculator;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveCalculationResult;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveReleaseCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
import java.util.Set;

/**
 * 锁定原支付保证金状态后，按当前剩余标签币种金额原子追加到期释放事实并生成结算候选。
 */
@Service
public class DefaultReserveReleaseService implements ReserveReleaseService {

    /**
     * 原保证金仍有可释放余额时的状态。
     */
    private static final String OPEN = "OPEN";
    /**
     * 当前修订保证金明细仍参与余额计算时的有效标记。
     */
    private static final String ACTIVE = "ACTIVE";
    /**
     * 将释放时刻转换为交易分片时间和业务日期的固定平台时区。
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID);

    private final ClearingReserveMapper reserveMapper;
    private final ClearingMerchantSettlementProfileMapper profileMapper;
    private final ClearingSettlementCandidateService candidateService;
    private final ReserveCalculator reserveCalculator;

    /**
     * 创建保证金释放服务，并注入状态、结算资料、候选和精确金额计算依赖。
     */
    public DefaultReserveReleaseService(ClearingReserveMapper reserveMapper,
                                        ClearingMerchantSettlementProfileMapper profileMapper,
                                        ClearingSettlementCandidateService candidateService,
                                        ReserveCalculator reserveCalculator) {
        this.reserveMapper = reserveMapper;
        this.profileMapper = profileMapper;
        this.candidateService = candidateService;
        this.reserveCalculator = reserveCalculator;
    }

    /**
     * 锁定原支付保证金状态并一次性释放全部到期余额，重复调用返回现有结果。
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ReserveReleaseResult release(String reserveStateId,
                                        String originalTransactionId,
                                        LocalDateTime originalTransactionDateTime,
                                        Instant releaseInstant) {
        requireIdentity(reserveStateId, originalTransactionId, originalTransactionDateTime, releaseInstant);
        LocalDateTime releaseTime = LocalDateTime.ofInstant(releaseInstant, BUSINESS_ZONE);
        LocalDate releaseDate = releaseTime.toLocalDate();
        LocalDateTime auditTime = LocalDateTime.ofInstant(releaseInstant, ZoneOffset.UTC);

        ClearingReserveStateDO state = reserveMapper.selectStateForUpdate(
                originalTransactionId, originalTransactionDateTime);
        if (state == null) {
            throw new IllegalStateException("reserve release candidate state does not exist");
        }
        validateLockedIdentity(state, reserveStateId, originalTransactionId, originalTransactionDateTime);
        if (!OPEN.equals(state.getReserveStatus()) || state.getRemainingAmount() == null
                || state.getRemainingAmount().signum() == 0) {
            return new ReserveReleaseResult(ReserveReleaseOutcome.ALREADY_FINAL, null, 0);
        }
        if (state.getExpectedReserveReleaseDate() == null
                || state.getExpectedReserveReleaseDate().isAfter(releaseDate)) {
            return new ReserveReleaseResult(ReserveReleaseOutcome.NOT_DUE, null, 0);
        }
        if (state.getRemainingAmount().signum() < 0 || state.getVersion() == null
                || state.getVersion() < 0 || state.getReserveCurrencyExponent() == null) {
            throw new IllegalStateException("locked reserve state is invalid for release");
        }

        ClearingReserveDetailDO hold = reserveMapper.selectHoldDetail(
                state.getOriginalHoldDetailNo(), state.getTransactionDateTime());
        validateHold(state, hold);
        ClearingMerchantSettlementProfileDO profile = profileMapper.selectActiveProfile(
                state.getMerchantId(), releaseDate);
        validateProfile(state, profile);

        ReserveCalculationResult calculation = reserveCalculator.release(new ReserveReleaseCommand(
                new Money(state.getRemainingAmount(), state.getReserveCurrency(),
                        state.getReserveCurrencyExponent()), state.getOriginalReserveRate()));
        int sourceRevision = Math.toIntExact(Math.addExact(state.getVersion(), 1L));
        String releaseTransactionId = stableId("RRL", reserveStateId + "|" + sourceRevision);
        ClearingReserveDetailDO detail = releaseDetail(
                state, hold, calculation, sourceRevision, releaseTransactionId, releaseTime, auditTime);

        requireOne(reserveMapper.insertDetail(detail), "reserve release detail insert");
        requireOne(reserveMapper.applyRelease(
                state.getOriginalTransactionId(), state.getTransactionDateTime(), state.getVersion(),
                calculation.amount().amount(), auditTime, releaseDate), "reserve release state CAS");
        candidateService.createReserveRelease(
                state.getReserveStateId(), sourceRevision, releaseTransactionId, releaseTime,
                state.getMerchantId(), profile.getTargetCurrency(), releaseDate,
                reserveDependency(state, hold), auditTime);
        return new ReserveReleaseResult(
                ReserveReleaseOutcome.RELEASED, releaseTransactionId, sourceRevision);
    }

    /**
     * 构造保证金释放对原支付 HOLD 候选的强制依赖。
     *
     * @param state 已锁定的原支付保证金状态
     * @param hold 原始保证金扣留事实
     * @return 必须与释放候选同事务写入的依赖计划
     */
    private SettlementCandidateDependencyPlan reserveDependency(ClearingReserveStateDO state,
                                                                ClearingReserveDetailDO hold) {
        return new SettlementCandidateDependencyPlan(
                state.getOriginalFinanceStateId(), hold.getClearingRevision(),
                state.getOriginalTransactionId(), state.getMerchantId(),
                Set.of(SettlementCandidateDependencyType.RESERVE_HOLD_POSTED));
    }

    /**
     * 构造标签币种 RELEASE 事实，金额等于锁定状态的全部剩余负债。
     */
    private ClearingReserveDetailDO releaseDetail(ClearingReserveStateDO state,
                                                  ClearingReserveDetailDO hold,
                                                  ReserveCalculationResult calculation,
                                                  int sourceRevision,
                                                  String transactionId,
                                                  LocalDateTime releaseTime,
                                                  LocalDateTime auditTime) {
        ClearingReserveDetailDO row = new ClearingReserveDetailDO();
        row.setReserveClearingDetailNo(stableId("RD", state.getReserveStateId() + "|RELEASE|" + sourceRevision));
        row.setFinanceStateId(state.getReserveStateId());
        row.setTransactionId(transactionId);
        row.setOperationId(state.getOperationId());
        row.setOriginalTransactionId(state.getOriginalTransactionId());
        row.setOriginalTransactionDateTime(state.getTransactionDateTime());
        row.setSourceReserveDetailNo(state.getOriginalHoldDetailNo());
        row.setMerchantId(state.getMerchantId());
        row.setPaymentType(hold.getPaymentType());
        row.setPaymentMethod(hold.getPaymentMethod());
        row.setTransactionType("RESERVE_RELEASE");
        row.setClearingRevision(sourceRevision);
        row.setLineNo(1);
        row.setReserveActionType("RELEASE");
        row.setItemCode("RESERVE:RELEASE");
        row.setItemName(ClearingItemNameResolver.reserve("RELEASE"));
        row.setDirection("CREDIT");
        row.setReserveCurrency(calculation.amount().currency());
        row.setReserveCurrencyExponent(calculation.amount().exponent());
        row.setBasisAmount(calculation.basisAmount().amount());
        row.setReserveRate(calculation.reserveRate());
        row.setRetainedAmount(java.math.BigDecimal.ZERO);
        row.setReturnedAmount(java.math.BigDecimal.ZERO);
        row.setReleasedAmount(calculation.amount().amount());
        row.setAdjustmentAmount(java.math.BigDecimal.ZERO);
        row.setRemainingAmount(calculation.remainingAmount().amount());
        row.setFeePlanId(hold.getFeePlanId());
        row.setFeePlanVersionId(hold.getFeePlanVersionId());
        row.setFeePlanVersionNo(hold.getFeePlanVersionNo());
        row.setReserveSnapshotHash(hold.getReserveSnapshotHash());
        row.setReserveBasis(hold.getReserveBasis());
        row.setReserveDelayUnit(hold.getReserveDelayUnit());
        row.setReserveDelayDays(hold.getReserveDelayDays());
        row.setRoundingMode(hold.getRoundingMode());
        row.setFormulaSnapshot("release = current locked remaining reserve in original label currency");
        row.setExpectedReserveReleaseDate(state.getExpectedReserveReleaseDate());
        row.setRecordStatus(ACTIVE);
        row.setTransactionDateTime(releaseTime);
        row.setTransactionUtcTime(auditTime);
        row.setTransactionTimeZone(TransactionShardingProperties.REQUIRED_ZONE_ID);
        row.setCreateTime(auditTime);
        row.setUpdateTime(auditTime);
        return row;
    }

    /**
     * 扫描快照不能替代行锁，事务内必须重新核对原支付身份和分片时间。
     */
    private void validateLockedIdentity(ClearingReserveStateDO state,
                                        String reserveStateId,
                                        String originalTransactionId,
                                        LocalDateTime originalTransactionDateTime) {
        if (!Objects.equals(reserveStateId, state.getReserveStateId())
                || !Objects.equals(originalTransactionId, state.getOriginalTransactionId())
                || !Objects.equals(originalTransactionDateTime, state.getTransactionDateTime())) {
            throw new IllegalStateException("reserve release candidate identity changed after scan");
        }
    }

    /**
     * 原 HOLD 必须与状态中的快照、币种和商户身份一致。
     */
    private void validateHold(ClearingReserveStateDO state, ClearingReserveDetailDO hold) {
        if (hold == null || !"HOLD".equals(hold.getReserveActionType())
                || !Objects.equals(state.getOriginalHoldDetailNo(), hold.getReserveClearingDetailNo())
                || !StringUtils.hasText(state.getOriginalFinanceStateId())
                || hold.getClearingRevision() == null || hold.getClearingRevision() < 1
                || !Objects.equals(state.getOriginalFeePlanVersionId(), hold.getFeePlanVersionId())
                || !Objects.equals(state.getOriginalReserveSnapshotHash(), hold.getReserveSnapshotHash())
                || !Objects.equals(state.getReserveCurrency(), hold.getReserveCurrency())
                || !Objects.equals(state.getReserveCurrencyExponent(), hold.getReserveCurrencyExponent())) {
            throw new IllegalStateException("original reserve hold snapshot does not match locked state");
        }
    }

    /**
     * 释放候选使用当前活动结算档案目标币种，清分阶段不执行换汇。
     */
    private void validateProfile(ClearingReserveStateDO state,
                                 ClearingMerchantSettlementProfileDO profile) {
        if (profile == null || !Objects.equals(state.getMerchantId(), profile.getMerchantId())
                || !StringUtils.hasText(profile.getTargetCurrency())
                || profile.getTargetCurrencyExponent() == null) {
            throw new IllegalStateException("active merchant settlement profile is unavailable for reserve release");
        }
    }

    /**
     * 校验调度任务携带原保证金状态、交易分片和统一释放时刻。
     */
    private void requireIdentity(String reserveStateId,
                                 String originalTransactionId,
                                 LocalDateTime originalTransactionDateTime,
                                 Instant releaseInstant) {
        if (!StringUtils.hasText(reserveStateId) || !StringUtils.hasText(originalTransactionId)
                || originalTransactionDateTime == null || releaseInstant == null) {
            throw new IllegalArgumentException("reserve release scan identity and release time are required");
        }
    }

    /**
     * 以原保证金状态和释放修订身份派生稳定明细号，保证调度重放不重复释放。
     */
    private String stableId(String prefix, String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 要求带版本条件的保证金释放写操作精确命中一行。
     */
    private void requireOne(int affectedRows, String operation) {
        if (affectedRows != 1) {
            throw new IllegalStateException(operation + " did not affect the expected row");
        }
    }
}
