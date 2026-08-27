package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.entity.ClearingSettlementCandidateDO;
import com.scott.payment.clearing.mapper.ClearingSettlementCandidateMapper;
import com.scott.payment.clearing.service.ClearingSettlementCandidateService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.HexFormat;
import java.util.Objects;

/** 默认只读结算候选输出；当前候选固定带 shadow_mode=1，真实结算必须排除。 */
@Service
public class DefaultClearingSettlementCandidateService implements ClearingSettlementCandidateService {

    private final ClearingSettlementCandidateMapper candidateMapper;
    public DefaultClearingSettlementCandidateService(ClearingSettlementCandidateMapper candidateMapper) {
        this.candidateMapper = candidateMapper;
    }

    @Override
    public void create(String financeStateId, int revision, ClearingOperationFacts operation,
                       String settlementCurrency, LocalDate eligibleDate, LocalDateTime now) {
        ClearingSettlementCandidateDO expected = clearingRevisionCandidate(
                financeStateId, revision, operation, settlementCurrency, eligibleDate, now);
        candidateMapper.insertIdempotent(expected);
        ClearingSettlementCandidateDO existing = candidateMapper.selectForUpdate(financeStateId, revision);
        if (!sameIdentity(existing, expected)) {
            throw new IllegalStateException("settlement candidate source unique key contains mismatched data");
        }
    }

    @Override
    public void replace(String financeStateId, int oldRevision, int newRevision,
                        ClearingOperationFacts operation, String settlementCurrency,
                        LocalDate eligibleDate, LocalDateTime now) {
        ClearingSettlementCandidateDO old = candidateMapper.selectForUpdate(financeStateId, oldRevision);
        if (old == null || !"READY".equals(old.getCandidateStatus())
                || old.getSettlementBatchNo() != null || old.getVersion() == null) {
            throw new IllegalStateException("clearing revision is already claimed or has no replaceable candidate");
        }
        if (candidateMapper.supersedeReady(financeStateId, oldRevision, old.getVersion(), now) != 1) {
            throw new IllegalStateException("old settlement candidate supersede CAS failed");
        }
        create(financeStateId, newRevision, operation, settlementCurrency, eligibleDate, now);
    }

    /** {@inheritDoc} */
    @Override
    public void replaceReplayHeld(String financeStateId, int oldRevision, int newRevision,
                                  ClearingOperationFacts operation, String settlementCurrency,
                                  LocalDate eligibleDate, LocalDateTime now) {
        ClearingSettlementCandidateDO old = candidateMapper.selectForUpdate(financeStateId, oldRevision);
        if (old == null || !"REPLAY_HOLD".equals(old.getCandidateStatus())
                || old.getSettlementBatchNo() != null || old.getVersion() == null) {
            throw new IllegalStateException("clearing revision has no replay-held replaceable candidate");
        }
        if (candidateMapper.supersedeReplayHeld(financeStateId, oldRevision, old.getVersion(), now) != 1) {
            throw new IllegalStateException("replay-held settlement candidate supersede CAS failed");
        }
        create(financeStateId, newRevision, operation, settlementCurrency, eligibleDate, now);
    }

    @Override
    public void createReserveRelease(String reserveStateId,
                                     int sourceRevision,
                                     String releaseTransactionId,
                                     LocalDateTime releaseTransactionDateTime,
                                     String merchantId,
                                     String settlementCurrency,
                                     LocalDate eligibleDate,
                                     LocalDateTime now) {
        ClearingSettlementCandidateDO expected = candidate(
                "RESERVE_RELEASE", reserveStateId, sourceRevision, releaseTransactionId,
                releaseTransactionDateTime, merchantId, settlementCurrency, eligibleDate, now);
        candidateMapper.insertIdempotent(expected);
        ClearingSettlementCandidateDO existing = candidateMapper.selectSourceForUpdate(
                "RESERVE_RELEASE", reserveStateId, sourceRevision);
        if (!sameIdentity(existing, expected)) {
            throw new IllegalStateException("settlement candidate source unique key contains mismatched data");
        }
    }

    @Override
    public void createAdjustment(String adjustmentNo,
                                 int sourceRevision,
                                 String adjustmentTransactionId,
                                 LocalDateTime adjustmentTransactionDateTime,
                                 String merchantId,
                                 String settlementCurrency,
                                 LocalDate eligibleDate,
                                 LocalDateTime now) {
        ClearingSettlementCandidateDO expected = candidate(
                "ADJUSTMENT", adjustmentNo, sourceRevision, adjustmentTransactionId,
                adjustmentTransactionDateTime, merchantId, settlementCurrency, eligibleDate, now);
        candidateMapper.insertIdempotent(expected);
        ClearingSettlementCandidateDO existing = candidateMapper.selectSourceForUpdate(
                "ADJUSTMENT", adjustmentNo, sourceRevision);
        if (!sameIdentity(existing, expected)) {
            throw new IllegalStateException("settlement candidate source unique key contains mismatched data");
        }
    }

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
        row.setSourceTransactionDateTime(sourceTransactionDateTime);
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
