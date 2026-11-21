package com.scott.payment.settlement.service;

import com.scott.payment.settlement.domain.model.SettlementFailureStage;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementClearingLocator;
import com.scott.payment.settlement.dto.SettlementReserveSourceBinding;
import com.scott.payment.settlement.entity.MerchantReserveItemDO;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReserveClearingDetailDO;
import com.scott.payment.settlement.entity.SettlementReserveHoldMigrationDO;
import com.scott.payment.settlement.entity.SettlementReserveHoldSnapshotDO;
import com.scott.payment.settlement.entity.SettlementReserveHoldStateDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.SettlementReserveSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 按不可变修复审计解析 RELEASE 的实际责任；不修改清分事实、候选或释放幂等身份。
 */
@Service
public class SettlementReserveSourceResolver {

    private final SettlementReserveSourceMapper mapper;

    public SettlementReserveSourceResolver(SettlementReserveSourceMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 批量验证原来源或唯一的一步 HOLD 迁移证明。事实加载和入账事务各自复核一次，禁止任意 ACTIVE 回退。
     * @param batch 当前批次或预审的资金维度
     * @param facts 原始不可变清分输入
     * @param stage 当前失败阶段
     * @return 仅包含经审计迁移的 RELEASE 业务号到实际责任的绑定
     */
    public Map<String, SettlementReserveSourceBinding> resolve(SettlementBatchDO batch,
                                                              SettlementBatchFacts facts,
                                                              SettlementFailureStage stage) {
        List<SettlementReserveClearingDetailDO> releases = facts.reserveDetails().stream()
                .filter(row -> "RELEASE".equals(row.getReserveActionType())).toList();
        if (releases.isEmpty()) {
            return Map.of();
        }
        List<String> sources = releases.stream().map(SettlementReserveClearingDetailDO::getSourceReserveDetailNo)
                .distinct().toList();
        require(sources.stream().allMatch(StringUtils::hasText), stage, "reserve release source is missing");
        Map<String, SettlementReserveHoldMigrationDO> migrations = unique(
                mapper.selectMigrations(sources), SettlementReserveHoldMigrationDO::getOldHoldDetailNo, stage);
        List<SettlementReserveHoldMigrationDO> proofs = new ArrayList<>(migrations.values());
        // 先校验审计的完整性，避免将不完整原支付路由送入分片查询。
        for (SettlementReserveHoldMigrationDO proof : proofs) {
            validateMigrationHeader(proof, stage);
        }
        Map<String, SettlementReserveHoldSnapshotDO> holds = proofs.isEmpty() ? Map.of() : unique(
                mapper.selectHoldSnapshots(proofs), SettlementReserveHoldSnapshotDO::getReserveClearingDetailNo, stage);
        Map<String, SettlementReserveHoldStateDO> states = proofs.isEmpty() ? Map.of() : unique(
                mapper.selectStates(proofs), SettlementReserveHoldStateDO::getReserveStateId, stage);
        LinkedHashSet<String> itemSources = new LinkedHashSet<>(sources);
        proofs.forEach(proof -> itemSources.add(proof.getNewHoldDetailNo()));
        Map<String, MerchantReserveItemDO> items = unique(mapper.selectFundedItems(
                batch.getMerchantId(), List.copyOf(itemSources)), MerchantReserveItemDO::getSourceBusinessNo, stage);
        Map<SettlementClearingLocator, SettlementCandidateDO> candidates = new HashMap<>();
        facts.candidates().forEach(candidate -> candidates.put(new SettlementClearingLocator(
                candidate.getSourceTransactionId(), candidate.getSourceTransactionDateTime(),
                candidate.getSourceRevision()), candidate));
        Map<String, SettlementReserveSourceBinding> bindings = new HashMap<>();
        for (SettlementReserveClearingDetailDO release : releases) {
            SettlementReserveHoldMigrationDO proof = migrations.get(release.getSourceReserveDetailNo());
            if (proof == null) {
                validateItem(batch, release, items.get(release.getSourceReserveDetailNo()), stage);
                continue;
            }
            SettlementCandidateDO candidate = candidates.get(new SettlementClearingLocator(
                    release.getTransactionId(), release.getTransactionDateTime(), release.getClearingRevision()));
            validateDependency(batch, candidate, release, proof, stage);
            SettlementReserveHoldSnapshotDO oldHold = holds.get(proof.getOldHoldDetailNo());
            SettlementReserveHoldSnapshotDO newHold = holds.get(proof.getNewHoldDetailNo());
            validateHolds(release, proof, oldHold, newHold, stage);
            validateState(release, proof, states.get(proof.getReserveStateId()), stage);
            require(!items.containsKey(proof.getOldHoldDetailNo()), stage,
                    "superseded hold already has a funded liability");
            MerchantReserveItemDO item = items.get(proof.getNewHoldDetailNo());
            validateItem(batch, release, item, stage);
            require("HELD".equals(item.getReserveStatus())
                            && Objects.equals(item.getSourceTransactionId(), proof.getTransactionId())
                            && same(item.getRetainedAmount(), newHold.getRetainedAmount())
                            && zero(item.getReturnedAmount(), item.getReleasedAmount(), item.getDebitAdjustmentAmount(),
                            item.getCreditAdjustmentAmount(), item.getReversedAmount())
                            && same(remaining(item), release.getReleasedAmount()), stage,
                    "migrated hold funded liability differs from the frozen release");
            require(bindings.putIfAbsent(release.getReserveClearingDetailNo(), new SettlementReserveSourceBinding(
                    proof.getNewHoldDetailNo(), item.getId(), item.getVersion(), proof.getRepairNo())) == null,
                    stage, "reserve release binding is duplicated");
        }
        return Map.copyOf(bindings);
    }

    private void validateMigrationHeader(SettlementReserveHoldMigrationDO p, SettlementFailureStage stage) {
        require(p != null && StringUtils.hasText(p.getRepairNo()) && "EXECUTED".equals(p.getRepairStatus())
                        && StringUtils.hasText(p.getOldHoldDetailNo()) && StringUtils.hasText(p.getNewHoldDetailNo())
                        && !p.getOldHoldDetailNo().equals(p.getNewHoldDetailNo())
                        && StringUtils.hasText(p.getReserveStateId()) && StringUtils.hasText(p.getFinanceStateId())
                        && StringUtils.hasText(p.getMerchantId()) && StringUtils.hasText(p.getTransactionId())
                        && p.getTransactionDateTime() != null
                        && p.getOldRevision() != null && p.getOldRevision() > 0
                        && p.getOldRevision() < Integer.MAX_VALUE
                        && Objects.equals(p.getNewRevision(), p.getOldRevision() + 1)
                        && p.getOldStateVersion() != null && p.getOldStateVersion() >= 0
                        && p.getOldStateVersion() < Long.MAX_VALUE
                        && Objects.equals(p.getNewStateVersion(), p.getOldStateVersion() + 1)
                        && Integer.valueOf(1).equals(p.getReserveFactCount())
                        && Integer.valueOf(1).equals(p.getReserveStateCount())
                        && Integer.valueOf(1).equals(p.getDependencyCount()), stage,
                "reserve hold migration audit is incomplete or unsupported");
    }

    private void validateDependency(SettlementBatchDO batch, SettlementCandidateDO c,
                                    SettlementReserveClearingDetailDO release,
                                    SettlementReserveHoldMigrationDO p, SettlementFailureStage stage) {
        require(c != null && c.getId() != null && "RESERVE_RELEASE".equals(c.getSourceType())
                        && Objects.equals(c.getSourceBusinessId(), p.getReserveStateId())
                        && Objects.equals(release.getFinanceStateId(), p.getReserveStateId())
                        && Objects.equals(release.getOriginalTransactionId(), p.getTransactionId())
                        && Objects.equals(release.getOriginalTransactionDateTime(), p.getTransactionDateTime())
                        && Objects.equals(batch.getMerchantId(), p.getMerchantId())
                        && Objects.equals(release.getMerchantId(), p.getMerchantId())
                        && Objects.equals(p.getDependentCandidateId(), c.getId())
                        && Objects.equals(p.getCurrentDependentCandidateId(), c.getId())
                        && p.getOldCandidateId() != null && p.getNewCandidateId() != null
                        && !p.getOldCandidateId().equals(p.getNewCandidateId())
                        && Objects.equals(p.getOldDependsOnCandidateId(), p.getOldCandidateId())
                        && Objects.equals(p.getNewDependsOnCandidateId(), p.getNewCandidateId())
                        && Objects.equals(p.getCurrentDependsOnCandidateId(), p.getNewCandidateId())
                        && "RESERVE_HOLD_POSTED".equals(p.getDependencyType())
                        && "RESERVE_HOLD_POSTED".equals(p.getCurrentDependencyType())
                        && "POSTED".equals(p.getNewCandidateStatus())
                        && "CLEARING_REVISION".equals(p.getNewCandidateSourceType())
                        && Objects.equals(p.getNewCandidateSourceBusinessId(), p.getFinanceStateId())
                        && Objects.equals(p.getNewCandidateTransactionId(), p.getTransactionId())
                        && Objects.equals(p.getNewCandidateTransactionDateTime(), p.getTransactionDateTime())
                        && Objects.equals(p.getNewCandidateRevision(), p.getNewRevision())
                        && Objects.equals(p.getNewCandidateMerchantId(), batch.getMerchantId())
                        && Objects.equals(p.getNewCandidateProfileId(), batch.getSettlementProfileId())
                        && Objects.equals(p.getNewCandidateTargetCurrency(), batch.getTargetCurrency())
                        && Objects.equals(p.getNewCandidateTargetCurrencyExponent(), batch.getTargetCurrencyExponent()),
                stage, "reserve hold migration dependency or payment identity is inconsistent");
    }

    private void validateHolds(SettlementReserveClearingDetailDO release, SettlementReserveHoldMigrationDO p,
                               SettlementReserveHoldSnapshotDO oldHold, SettlementReserveHoldSnapshotDO newHold,
                               SettlementFailureStage stage) {
        require(oldHold != null && newHold != null
                        && "SUPERSEDED".equals(oldHold.getRecordStatus()) && "ACTIVE".equals(newHold.getRecordStatus())
                        && "HOLD".equals(oldHold.getReserveActionType()) && "HOLD".equals(newHold.getReserveActionType())
                        && "DEBIT".equals(oldHold.getDirection())
                        && Objects.equals(oldHold.getClearingRevision(), p.getOldRevision())
                        && Objects.equals(newHold.getClearingRevision(), p.getNewRevision())
                        && Objects.equals(oldHold.getTransactionId(), p.getTransactionId())
                        && Objects.equals(oldHold.getTransactionDateTime(), p.getTransactionDateTime())
                        && Objects.equals(oldHold.getFinanceStateId(), p.getFinanceStateId())
                        && Objects.equals(oldHold.getMerchantId(), p.getMerchantId())
                        && oldHold.getSourceReserveDetailNo() == null
                        && StringUtils.hasText(oldHold.getReserveSnapshotHash())
                        && oldHold.getFeePlanVersionId() != null
                        && oldHold.getRetainedAmount() != null && oldHold.getRetainedAmount().signum() > 0
                        && zero(oldHold.getReturnedAmount(), oldHold.getReleasedAmount(), oldHold.getAdjustmentAmount())
                        && same(oldHold.getRemainingAmount(), oldHold.getRetainedAmount())
                        && Objects.equals(oldHold.getReserveCurrency(), release.getReserveCurrency())
                        && Objects.equals(oldHold.getReserveCurrencyExponent(), release.getReserveCurrencyExponent())
                        && snapshot(oldHold).equals(snapshot(newHold)), stage,
                "replacement hold differs from the immutable original hold snapshot");
    }

    /**
     * 校验释放来源事实的有效状态，禁止候选从失效事实入账。
     */
    private void validateState(SettlementReserveClearingDetailDO release, SettlementReserveHoldMigrationDO p,
                               SettlementReserveHoldStateDO state, SettlementFailureStage stage) {
        require(state != null && Objects.equals(state.getOriginalHoldDetailNo(), p.getNewHoldDetailNo())
                        && Objects.equals(state.getOriginalTransactionId(), p.getTransactionId())
                        && Objects.equals(state.getTransactionDateTime(), p.getTransactionDateTime())
                        && Objects.equals(state.getOriginalFinanceStateId(), p.getFinanceStateId())
                        && Objects.equals(state.getMerchantId(), p.getMerchantId())
                        && Objects.equals(state.getReserveCurrency(), release.getReserveCurrency())
                        && Objects.equals(state.getReserveCurrencyExponent(), release.getReserveCurrencyExponent())
                        && Objects.equals(state.getVersion(), p.getNewStateVersion())
                        && "FULLY_RELEASED".equals(state.getReserveStatus())
                        && same(state.getReleasedAmount(), release.getReleasedAmount())
                        && same(state.getRetainedAmount(), release.getReleasedAmount())
                        && zero(state.getRemainingAmount(), state.getReturnedAmount(),
                        state.getDebitAdjustmentAmount(), state.getCreditAdjustmentAmount()), stage,
                "migrated clearing reserve state changed after the audited release");
    }

    private void validateItem(SettlementBatchDO batch, SettlementReserveClearingDetailDO release,
                              MerchantReserveItemDO item, SettlementFailureStage stage) {
        require(item != null && item.getId() != null && item.getVersion() != null && item.getVersion() >= 0
                        && Objects.equals(item.getMerchantId(), batch.getMerchantId())
                        && Objects.equals(item.getAccountId(), batch.getSettlementAccountId())
                        && Objects.equals(item.getCurrency(), release.getReserveCurrency())
                        && release.getReleasedAmount() != null && release.getReleasedAmount().signum() > 0
                        && remaining(item) != null && remaining(item).compareTo(release.getReleasedAmount()) >= 0,
                stage, "reserve release funded source is missing, mismatched or insufficient");
    }

    /** 只比较业务身份、原币金额和规则快照，允许审计号、修订及审计时间变化。 */
    private List<Object> snapshot(SettlementReserveHoldSnapshotDO h) {
        return Arrays.asList(h.getFinanceStateId(), h.getTransactionId(), h.getTransactionDateTime(),
                h.getOperationId(), h.getOriginalTransactionId(), h.getOriginalTransactionDateTime(),
                h.getSourceReserveDetailNo(), h.getMerchantId(), h.getPaymentType(), h.getPaymentMethod(),
                h.getTransactionType(), h.getReserveActionType(), h.getDirection(), h.getReserveCurrency(),
                h.getReserveCurrencyExponent(), normalized(h.getBasisAmount()), normalized(h.getReserveRate()),
                normalized(h.getRetainedAmount()), normalized(h.getReturnedAmount()), normalized(h.getReleasedAmount()),
                normalized(h.getAdjustmentAmount()), normalized(h.getRemainingAmount()), h.getFeePlanId(),
                h.getFeePlanVersionId(), h.getFeePlanVersionNo(), h.getReserveSnapshotHash(), h.getReserveBasis(),
                h.getReserveDelayUnit(), h.getReserveDelayDays(), h.getRoundingMode(), h.getFormulaSnapshot(),
                h.getExpectedReserveReleaseDate());
    }

    private BigDecimal remaining(MerchantReserveItemDO item) {
        if (item.getRetainedAmount() == null || item.getDebitAdjustmentAmount() == null
                || item.getReturnedAmount() == null || item.getReleasedAmount() == null
                || item.getCreditAdjustmentAmount() == null || item.getReversedAmount() == null) {
            return null;
        }
        return item.getRetainedAmount().add(item.getDebitAdjustmentAmount()).subtract(item.getReturnedAmount())
                .subtract(item.getReleasedAmount()).subtract(item.getCreditAdjustmentAmount()).subtract(item.getReversedAmount());
    }

    private boolean zero(BigDecimal... amounts) {
        return Arrays.stream(amounts).allMatch(value -> value != null && value.signum() == 0);
    }

    private boolean same(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private BigDecimal normalized(BigDecimal amount) {
        return amount == null ? null : amount.stripTrailingZeros();
    }

    private <T> Map<String, T> unique(List<T> rows, Function<T, String> key, SettlementFailureStage stage) {
        require(rows != null, stage, "reserve source query did not return a complete result");
        Map<String, T> result = new HashMap<>();
        for (T row : rows) {
            require(row != null && StringUtils.hasText(key.apply(row))
                            && result.putIfAbsent(key.apply(row), row) == null,
                    stage, "reserve source proof is ambiguous");
        }
        return result;
    }

    private void require(boolean condition, SettlementFailureStage stage, String message) {
        if (!condition) {
            throw new SettlementProcessingException(stage, "SETTLEMENT_RESERVE_SOURCE_INVALID", false, message);
        }
    }
}
