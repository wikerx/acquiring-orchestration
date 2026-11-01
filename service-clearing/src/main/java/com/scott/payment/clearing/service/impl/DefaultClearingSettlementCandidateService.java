package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.model.SettlementCandidateDependencyPlan;
import com.scott.payment.clearing.domain.state.SettlementCandidateDependencyType;
import com.scott.payment.clearing.entity.ClearingSettlementCandidateDO;
import com.scott.payment.clearing.entity.ClearingSettlementCandidateDependencyDO;
import com.scott.payment.clearing.mapper.ClearingSettlementCandidateMapper;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 默认只读结算候选输出；当前候选固定带 shadow_mode=1，真实结算必须排除。
 */
@Service
public class DefaultClearingSettlementCandidateService implements ClearingSettlementCandidateService {

    private final ClearingSettlementCandidateMapper candidateMapper;

    /**
     * 创建结算候选服务，并注入候选及其依赖关系的持久化访问对象。
     */
    public DefaultClearingSettlementCandidateService(ClearingSettlementCandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }

    /**
     * 为首次清分修订创建结算候选，并在同一事务中持久化源候选依赖。
     */
    @Override
    public void create(String financeStateId, int revision, ClearingOperationFacts operation,
                       String settlementCurrency, LocalDate eligibleDate,
                       SettlementCandidateDependencyPlan dependencyPlan, LocalDateTime now) {
        requireTransactionDependencySource(operation, dependencyPlan);
        ClearingSettlementCandidateDO expected = clearingRevisionCandidate(
                financeStateId, revision, operation, settlementCurrency, eligibleDate, now);
        persist(expected, dependencyPlan, now);
    }

    /**
     * 使用版本 CAS 作废未认领的旧候选，再创建新清分修订候选。
     */
    @Override
    public void replace(String financeStateId, int oldRevision, int newRevision,
                        ClearingOperationFacts operation, String settlementCurrency,
                        LocalDate eligibleDate, SettlementCandidateDependencyPlan dependencyPlan,
                        LocalDateTime now) {
        ClearingSettlementCandidateDO old = candidateMapper.selectForUpdate(financeStateId, oldRevision);
        if (old == null || !"READY".equals(old.getCandidateStatus())
                || old.getSettlementBatchNo() != null || old.getVersion() == null) {
            throw new IllegalStateException("clearing revision is already claimed or has no replaceable candidate");
        }
        if (candidateMapper.supersedeReady(financeStateId, oldRevision, old.getVersion(), now) != 1) {
            throw new IllegalStateException("old settlement candidate supersede CAS failed");
        }
        create(financeStateId, newRevision, operation, settlementCurrency, eligibleDate, dependencyPlan, now);
    }

    /**
     * 作废重放冻结中的旧候选，并为重放产生的新修订创建候选。
     */
    @Override
    public void replaceReplayHeld(String financeStateId, int oldRevision, int newRevision,
                                  ClearingOperationFacts operation, String settlementCurrency,
                                  LocalDate eligibleDate, SettlementCandidateDependencyPlan dependencyPlan,
                                  LocalDateTime now) {
        ClearingSettlementCandidateDO old = candidateMapper.selectForUpdate(financeStateId, oldRevision);
        if (old == null || !"REPLAY_HOLD".equals(old.getCandidateStatus())
                || old.getSettlementBatchNo() != null || old.getVersion() == null) {
            throw new IllegalStateException("clearing revision has no replay-held replaceable candidate");
        }
        if (candidateMapper.supersedeReplayHeld(financeStateId, oldRevision, old.getVersion(), now) != 1) {
            throw new IllegalStateException("replay-held settlement candidate supersede CAS failed");
        }
        create(financeStateId, newRevision, operation, settlementCurrency, eligibleDate, dependencyPlan, now);
    }

    /**
     * 为到期保证金释放动作创建独立候选及其原支付候选依赖。
     */
    @Override
    public void createReserveRelease(String reserveStateId,
                                     int sourceRevision,
                                     String releaseTransactionId,
                                     LocalDateTime releaseTransactionDateTime,
                                     String merchantId,
                                     String settlementCurrency,
                                     LocalDate eligibleDate,
                                     SettlementCandidateDependencyPlan dependencyPlan,
                                     LocalDateTime now) {
        ClearingSettlementCandidateDO expected = candidate(
                "RESERVE_RELEASE", reserveStateId, sourceRevision, releaseTransactionId,
                releaseTransactionDateTime, merchantId, settlementCurrency, eligibleDate, now);
        persist(expected, dependencyPlan, now);
    }

    /**
     * 为已批准的保证金调整动作创建独立候选及其原支付候选依赖。
     */
    @Override
    public void createAdjustment(String adjustmentNo,
                                 int sourceRevision,
                                 String adjustmentTransactionId,
                                 LocalDateTime adjustmentTransactionDateTime,
                                 String merchantId,
                                 String settlementCurrency,
                                 LocalDate eligibleDate,
                                 SettlementCandidateDependencyPlan dependencyPlan,
                                 LocalDateTime now) {
        ClearingSettlementCandidateDO expected = candidate(
                "ADJUSTMENT", adjustmentNo, sourceRevision, adjustmentTransactionId,
                adjustmentTransactionDateTime, merchantId, settlementCurrency, eligibleDate, now);
        persist(expected, dependencyPlan, now);
    }

    /**
     * 在调用方资金事务内幂等写入候选及其全部不可变依赖。
     *
     * <p>存在依赖时通过一条有序锁查询同时锁定当前和源候选，任一候选缺失或身份不一致都会抛出异常，
     * 使候选、依赖以及调用方清分事实整体回滚。</p>
     *
     * @param expected 当前调用预期的候选快照
     * @param dependencyPlan 源候选依赖计划
     * @param now UTC 审计时间
     */
    private void persist(ClearingSettlementCandidateDO expected,
                         SettlementCandidateDependencyPlan dependencyPlan,
                         LocalDateTime now) {
        Objects.requireNonNull(dependencyPlan, "settlement candidate dependency plan is required");
        candidateMapper.insertIdempotent(expected);
        if (!dependencyPlan.required()) {
            ClearingSettlementCandidateDO existing = "CLEARING_REVISION".equals(expected.getSourceType())
                    ? candidateMapper.selectForUpdate(
                            expected.getSourceBusinessId(), expected.getSourceRevision())
                    : candidateMapper.selectSourceForUpdate(
                            expected.getSourceType(), expected.getSourceBusinessId(), expected.getSourceRevision());
            requireSameCandidate(existing, expected);
            return;
        }

        List<ClearingSettlementCandidateDO> locked = candidateMapper.selectDependencyPairForUpdate(
                expected.getSourceType(), expected.getSourceBusinessId(), expected.getSourceRevision(),
                dependencyPlan.sourceFinanceStateId(), dependencyPlan.sourceRevision());
        ClearingSettlementCandidateDO current = findCandidate(
                locked, expected.getSourceType(), expected.getSourceBusinessId(), expected.getSourceRevision());
        ClearingSettlementCandidateDO source = findCandidate(
                locked, "CLEARING_REVISION", dependencyPlan.sourceFinanceStateId(),
                dependencyPlan.sourceRevision());
        requireSameCandidate(current, expected);
        requireDependencySource(source, current, dependencyPlan);

        dependencyPlan.dependencyTypes().stream()
                .sorted()
                .forEach(type -> persistDependency(current.getId(), source.getId(), type, now));
    }

    /**
     * 从已锁定集合中按完整来源唯一键定位候选。
     *
     * @param candidates 已按主键加锁的候选集合
     * @param sourceType 来源类型
     * @param sourceBusinessId 来源业务号
     * @param sourceRevision 来源修订
     * @return 唯一键匹配的候选；不存在时返回 null
     */
    private ClearingSettlementCandidateDO findCandidate(List<ClearingSettlementCandidateDO> candidates,
                                                         String sourceType,
                                                         String sourceBusinessId,
                                                         int sourceRevision) {
        if (candidates == null) {
            return null;
        }
        return candidates.stream()
                .filter(candidate -> Objects.equals(candidate.getSourceType(), sourceType)
                        && Objects.equals(candidate.getSourceBusinessId(), sourceBusinessId)
                        && Objects.equals(candidate.getSourceRevision(), sourceRevision))
                .findFirst()
                .orElse(null);
    }

    /**
     * 校验当前候选与调用方构造的不可变身份完全一致。
     *
     * @param actual 数据库已锁定候选
     * @param expected 调用方预期候选
     */
    private void requireSameCandidate(ClearingSettlementCandidateDO actual,
                                      ClearingSettlementCandidateDO expected) {
        if (!sameIdentity(actual, expected)) {
            throw new IllegalStateException("settlement candidate source unique key contains mismatched data");
        }
    }

    /**
     * 校验源候选真实存在、主键不同并属于计划中的同一商户和源动作。
     *
     * @param source 数据库已锁定的源候选
     * @param current 数据库已锁定的当前候选
     * @param plan 调用方冻结的依赖计划
     */
    private void requireDependencySource(ClearingSettlementCandidateDO source,
                                         ClearingSettlementCandidateDO current,
                                         SettlementCandidateDependencyPlan plan) {
        if (source == null) {
            throw new IllegalStateException("settlement dependency source candidate does not exist");
        }
        if (source.getId() == null || current.getId() == null
                || Objects.equals(source.getId(), current.getId())
                || !Objects.equals(source.getSourceTransactionId(), plan.sourceTransactionId())
                || !Objects.equals(source.getMerchantId(), plan.merchantId())
                || !Objects.equals(current.getMerchantId(), plan.merchantId())) {
            throw new IllegalStateException("settlement dependency source candidate identity is inconsistent");
        }
    }

    /**
     * 幂等写入单个依赖并回读核对唯一键，禁止静默丢失依赖。
     *
     * @param candidateId 当前候选主键
     * @param sourceCandidateId 源候选主键
     * @param type 依赖类型
     * @param now UTC 审计时间
     */
    private void persistDependency(Long candidateId,
                                   Long sourceCandidateId,
                                   SettlementCandidateDependencyType type,
                                   LocalDateTime now) {
        ClearingSettlementCandidateDependencyDO expected = new ClearingSettlementCandidateDependencyDO();
        expected.setCandidateId(candidateId);
        expected.setDependsOnCandidateId(sourceCandidateId);
        expected.setDependencyType(type.name());
        expected.setCreateTime(now);
        candidateMapper.insertDependencyIdempotent(expected);
        ClearingSettlementCandidateDependencyDO existing = candidateMapper.selectDependencyForUpdate(
                candidateId, sourceCandidateId, type.name());
        if (existing == null
                || !Objects.equals(existing.getCandidateId(), candidateId)
                || !Objects.equals(existing.getDependsOnCandidateId(), sourceCandidateId)
                || !Objects.equals(existing.getDependencyType(), type.name())) {
            throw new IllegalStateException("settlement candidate dependency was not persisted consistently");
        }
    }

    /**
     * 交易候选依赖必须引用动作声明的同一源交易，避免调用方把退款关联到无关候选。
     *
     * @param operation 当前交易动作
     * @param dependencyPlan 源候选依赖计划
     */
    private void requireTransactionDependencySource(ClearingOperationFacts operation,
                                                    SettlementCandidateDependencyPlan dependencyPlan) {
        Objects.requireNonNull(dependencyPlan, "settlement candidate dependency plan is required");
        if (dependencyPlan.required()
                && (operation == null
                || !Objects.equals(operation.sourceTransactionId(), dependencyPlan.sourceTransactionId())
                || !Objects.equals(operation.merchantId(), dependencyPlan.merchantId()))) {
            throw new IllegalArgumentException("settlement dependency does not match transaction source identity");
        }
    }

    /**
     * 将真实交易清分修订转换为 CLEARING_REVISION 候选。
     */
    private ClearingSettlementCandidateDO clearingRevisionCandidate(String financeStateId,
                                                                     int revision,
                                                                     ClearingOperationFacts operation,
                                                                     String settlementCurrency,
                                                                     LocalDate eligibleDate,
                                                                     LocalDateTime now) {
        if (!StringUtils.hasText(financeStateId) || revision < 1 || operation == null
                || !StringUtils.hasText(settlementCurrency) || eligibleDate == null || now == null) {
            throw new IllegalArgumentException("settlement candidate identity, currency and date are required");
        }
        return candidate("CLEARING_REVISION", financeStateId, revision, operation.transactionId(),
                operation.transactionDateTime(), operation.merchantId(), settlementCurrency, eligibleDate, now);
    }

    /**
     * 统一构造三类候选并校验 ISO 币种及来源身份，不在此计算汇率或金额。
     */
    private ClearingSettlementCandidateDO candidate(String sourceType,
                                                     String sourceBusinessId,
                                                     int revision,
                                                     String sourceTransactionId,
                                                     LocalDateTime sourceTransactionDateTime,
                                                     String merchantId,
                                                     String settlementCurrency,
                                                     LocalDate eligibleDate,
                                                     LocalDateTime now) {
        if (!StringUtils.hasText(sourceType) || !StringUtils.hasText(sourceBusinessId)
                || revision < 1 || !StringUtils.hasText(sourceTransactionId)
                || sourceTransactionDateTime == null || !StringUtils.hasText(merchantId)
                || !StringUtils.hasText(settlementCurrency) || eligibleDate == null || now == null) {
            throw new IllegalArgumentException("settlement candidate identity, currency and date are required");
        }
        ClearingSettlementCandidateDO row = new ClearingSettlementCandidateDO();
        row.setCandidateNo(candidateNo(sourceType, sourceBusinessId, revision));
        row.setSourceType(sourceType);
        row.setSourceBusinessId(sourceBusinessId);
        row.setSourceRevision(revision);
        row.setSourceTransactionId(sourceTransactionId);
        row.setSourceTransactionDateTime(sourceTransactionDateTime.truncatedTo(ChronoUnit.MILLIS));
        row.setMerchantId(merchantId);
        row.setSettlementProfileId(null);
        row.setTargetCurrency(settlementCurrency);
        int exponent = Currency.getInstance(settlementCurrency).getDefaultFractionDigits();
        if (exponent < 0 || exponent > 8) {
            throw new IllegalArgumentException("settlement currency exponent is unsupported");
        }
        row.setTargetCurrencyExponent(exponent);
        row.setSettlementEligibleDate(eligibleDate);
        row.setCandidateStatus("READY");
        row.setShadowMode(1);
        row.setVersion(0L);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /**
     * 幂等唯一键命中后必须核对全部来源和路由身份。
     */
    private boolean sameIdentity(ClearingSettlementCandidateDO actual,
                                 ClearingSettlementCandidateDO expected) {
        return actual != null
                && Objects.equals(actual.getCandidateNo(), expected.getCandidateNo())
                && Objects.equals(actual.getSourceType(), expected.getSourceType())
                && Objects.equals(actual.getSourceBusinessId(), expected.getSourceBusinessId())
                && Objects.equals(actual.getSourceRevision(), expected.getSourceRevision())
                && Objects.equals(actual.getSourceTransactionId(), expected.getSourceTransactionId())
                && Objects.equals(actual.getSourceTransactionDateTime(), expected.getSourceTransactionDateTime())
                && Objects.equals(actual.getMerchantId(), expected.getMerchantId())
                && Objects.equals(actual.getSettlementProfileId(), expected.getSettlementProfileId())
                && Objects.equals(actual.getTargetCurrency(), expected.getTargetCurrency())
                && Objects.equals(actual.getTargetCurrencyExponent(), expected.getTargetCurrencyExponent())
                && Objects.equals(actual.getSettlementEligibleDate(), expected.getSettlementEligibleDate())
                && Objects.equals(actual.getShadowMode(), expected.getShadowMode());
    }

    /**
     * 以真实来源类型、业务标识和修订号生成稳定候选号，作为重放时复用的数据库幂等身份。
     *
     * @param sourceType CLEARING_REVISION、RESERVE_RELEASE 或 ADJUSTMENT 来源类型
     * @param sourceBusinessId 来源交易、释放项或调整项标识
     * @param revision 来源修订号
     * @return 带 SC 前缀的稳定 SHA-256 截断指纹
     */
    private String candidateNo(String sourceType, String sourceBusinessId, int revision) {
        try {
            String identity = "CLEARING_REVISION".equals(sourceType)
                    ? sourceBusinessId + "|" + revision
                    : sourceType + "|" + sourceBusinessId + "|" + revision;
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return "SC" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
