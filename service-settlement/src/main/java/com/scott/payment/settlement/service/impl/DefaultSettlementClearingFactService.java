package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.settlement.domain.model.SettlementFailureStage;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementClearingLocator;
import com.scott.payment.settlement.dto.SettlementCurrency;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReserveClearingDetailDO;
import com.scott.payment.settlement.entity.SettlementTransactionClearingDetailDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.SettlementClearingFactMapper;
import com.scott.payment.settlement.service.SettlementClearingFactService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementClearingFactService
 * @date : 2026-08-26 23:10
 * @email : scott_x@163.com
 * @description : 一次读取批次候选、一次读取交易明细、一次读取保证金明细，并阻断路由、版本、商户和币种冲突。
 * @status : create
 */
@Service
public class DefaultSettlementClearingFactService implements SettlementClearingFactService {

    private static final String ACTIVE = "ACTIVE";
    private static final String CLEARING_REVISION = "CLEARING_REVISION";
    private static final String RESERVE_RELEASE = "RESERVE_RELEASE";
    private static final String ADJUSTMENT = "ADJUSTMENT";
    private static final Set<String> SOURCE_TYPES = Set.of(
            CLEARING_REVISION, RESERVE_RELEASE, ADJUSTMENT);
    private static final Set<String> DIRECTIONS = Set.of("CREDIT", "DEBIT");

    private final SettlementClearingFactMapper factMapper;

    public DefaultSettlementClearingFactService(SettlementClearingFactMapper factMapper) {
        this.factMapper = factMapper;
    }

    /**
     * 所有季度路由键来自已认领候选，任何候选缺少清分事实都会阻断整批计算。
     *
     * @param batch 已取得数据库处理租约的批次
     * @return 完整批次事实
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    public SettlementBatchFacts load(SettlementBatchDO batch) {
        validateBatch(batch);
        List<SettlementCandidateDO> candidates = safe(factMapper.selectClaimedCandidates(
                batch.getSettlementBatchNo()));
        if (candidates.size() != batch.getCandidateCount()) {
            throw failure("SETTLEMENT_CANDIDATE_COUNT_MISMATCH",
                    "settlement batch candidate relation count is inconsistent");
        }

        Map<FactKey, SettlementCandidateDO> candidateByKey = new LinkedHashMap<>();
        List<SettlementClearingLocator> locators = new ArrayList<>(candidates.size());
        for (SettlementCandidateDO candidate : candidates) {
            validateCandidate(batch, candidate);
            FactKey key = new FactKey(candidate.getSourceTransactionId(),
                    candidate.getSourceTransactionDateTime(), candidate.getSourceRevision());
            if (candidateByKey.putIfAbsent(key, candidate) != null) {
                throw failure("SETTLEMENT_CANDIDATE_ROUTE_DUPLICATED",
                        "settlement batch contains duplicated clearing locators");
            }
            locators.add(new SettlementClearingLocator(
                    key.transactionId(), key.transactionDateTime(), key.clearingRevision()));
        }

        List<SettlementTransactionClearingDetailDO> transactionDetails = safe(
                factMapper.selectTransactionDetails(locators));
        List<SettlementReserveClearingDetailDO> reserveDetails = safe(
                factMapper.selectReserveDetails(locators));
        Map<String, Integer> exponents = new HashMap<>();
        Set<FactKey> factsByCandidate = new HashSet<>();
        Set<String> detailNumbers = new HashSet<>();
        for (SettlementTransactionClearingDetailDO row : transactionDetails) {
            FactKey key = validateTransactionDetail(batch, candidateByKey, row);
            factsByCandidate.add(key);
            requireUnique(detailNumbers, row.getClearingDetailNo());
            addCurrency(exponents, row.getCurrency(), row.getCurrencyExponent());
            if (row.getMinimumAmountUsd() != null || row.getMaximumAmountUsd() != null) {
                addCurrency(exponents, "USD", 2);
            }
        }
        for (SettlementReserveClearingDetailDO row : reserveDetails) {
            FactKey key = validateReserveDetail(batch, candidateByKey, row);
            factsByCandidate.add(key);
            requireUnique(detailNumbers, row.getReserveClearingDetailNo());
            addCurrency(exponents, row.getReserveCurrency(), row.getReserveCurrencyExponent());
        }
        if (!factsByCandidate.equals(candidateByKey.keySet())) {
            throw failure("SETTLEMENT_CLEARING_FACT_MISSING",
                    "one or more settlement candidates have no active clearing facts");
        }
        addCurrency(exponents, batch.getTargetCurrency(), batch.getTargetCurrencyExponent());
        Set<SettlementCurrency> currencies = new HashSet<>();
        exponents.forEach((currency, exponent) -> currencies.add(new SettlementCurrency(currency, exponent)));
        return new SettlementBatchFacts(candidates, transactionDetails, reserveDetails, currencies);
    }

    private void validateBatch(SettlementBatchDO batch) {
        if (batch == null || !StringUtils.hasText(batch.getSettlementBatchNo())
                || !StringUtils.hasText(batch.getMerchantId()) || batch.getCandidateCount() == null
                || batch.getCandidateCount() <= 0 || !StringUtils.hasText(batch.getTargetCurrency())
                || batch.getTargetCurrencyExponent() == null || batch.getSettlementProfileId() == null
                || !StringUtils.hasText(batch.getBatchType())) {
            throw failure("SETTLEMENT_BATCH_INCOMPLETE", "settlement batch identity is incomplete");
        }
    }

    private void validateCandidate(SettlementBatchDO batch, SettlementCandidateDO candidate) {
        if (candidate == null || candidate.getId() == null || candidate.getSourceRevision() == null
                || candidate.getSourceRevision() < 1 || !SOURCE_TYPES.contains(candidate.getSourceType())
                || !sourceMatchesBatch(candidate.getSourceType(), batch.getBatchType())
                || !StringUtils.hasText(candidate.getSourceBusinessId())
                || !StringUtils.hasText(candidate.getSourceTransactionId())
                || candidate.getSourceTransactionDateTime() == null
                || !Objects.equals(candidate.getMerchantId(), batch.getMerchantId())
                || !Objects.equals(candidate.getSettlementProfileId(), batch.getSettlementProfileId())
                || !Objects.equals(candidate.getTargetCurrency(), batch.getTargetCurrency())
                || !Objects.equals(candidate.getTargetCurrencyExponent(), batch.getTargetCurrencyExponent())
                || !Objects.equals(candidate.getSettlementBatchNo(), batch.getSettlementBatchNo())
                || !"CLAIMED".equals(candidate.getCandidateStatus())
                || !Integer.valueOf(0).equals(candidate.getShadowMode())) {
            throw failure("SETTLEMENT_CANDIDATE_INCONSISTENT",
                    "settlement candidate identity or source type is inconsistent");
        }
    }

    private FactKey validateTransactionDetail(
            SettlementBatchDO batch,
            Map<FactKey, SettlementCandidateDO> candidateByKey,
            SettlementTransactionClearingDetailDO row) {
        FactKey key = transactionKey(row == null ? null : row.getTransactionId(),
                row == null ? null : row.getTransactionDateTime(),
                row == null ? null : row.getClearingRevision());
        SettlementCandidateDO candidate = candidateByKey.get(key);
        if (row == null || candidate == null || !ACTIVE.equals(row.getRecordStatus())
                || !CLEARING_REVISION.equals(candidate.getSourceType())
                || !Objects.equals(row.getFinanceStateId(), candidate.getSourceBusinessId())
                || !Objects.equals(row.getMerchantId(), batch.getMerchantId())
                || !StringUtils.hasText(row.getClearingDetailNo()) || row.getLineNo() == null
                || row.getLineNo() < 1 || !StringUtils.hasText(row.getItemType())
                || !StringUtils.hasText(row.getTransactionType())
                || !StringUtils.hasText(row.getPaymentType())
                || !StringUtils.hasText(row.getPaymentMethod())
                || !DIRECTIONS.contains(row.getDirection()) || row.getAmount() == null
                || row.getAmount().signum() < 0 || !StringUtils.hasText(row.getCurrency())
                || row.getCurrencyExponent() == null || !StringUtils.hasText(row.getRoundingMode())) {
            throw failure("SETTLEMENT_TRANSACTION_FACT_INCONSISTENT",
                    "transaction clearing fact is incomplete or inconsistent");
        }
        return key;
    }

    private FactKey validateReserveDetail(
            SettlementBatchDO batch,
            Map<FactKey, SettlementCandidateDO> candidateByKey,
            SettlementReserveClearingDetailDO row) {
        FactKey key = transactionKey(row == null ? null : row.getTransactionId(),
                row == null ? null : row.getTransactionDateTime(),
                row == null ? null : row.getClearingRevision());
        SettlementCandidateDO candidate = candidateByKey.get(key);
        BigDecimal amount = reserveAmount(row);
        if (row == null || candidate == null || !ACTIVE.equals(row.getRecordStatus())
                || !reserveActionMatchesSource(row.getReserveActionType(), candidate.getSourceType())
                || !Objects.equals(row.getFinanceStateId(), candidate.getSourceBusinessId())
                || !Objects.equals(row.getMerchantId(), batch.getMerchantId())
                || !StringUtils.hasText(row.getReserveClearingDetailNo()) || row.getLineNo() == null
                || row.getLineNo() < 1 || !StringUtils.hasText(row.getReserveActionType())
                || !StringUtils.hasText(row.getTransactionType())
                || !StringUtils.hasText(row.getPaymentType())
                || !StringUtils.hasText(row.getPaymentMethod())
                || !DIRECTIONS.contains(row.getDirection()) || amount == null || amount.signum() < 0
                || !StringUtils.hasText(row.getReserveCurrency())
                || row.getReserveCurrencyExponent() == null || !StringUtils.hasText(row.getRoundingMode())) {
            throw failure("SETTLEMENT_RESERVE_FACT_INCONSISTENT",
                    "reserve clearing fact is incomplete or inconsistent");
        }
        return key;
    }

    private boolean sourceMatchesBatch(String sourceType, String batchType) {
        return switch (batchType) {
            case "REGULAR" -> CLEARING_REVISION.equals(sourceType) || ADJUSTMENT.equals(sourceType);
            case "RESERVE_RELEASE" -> RESERVE_RELEASE.equals(sourceType);
            case "ADJUSTMENT" -> ADJUSTMENT.equals(sourceType);
            default -> false;
        };
    }

    private boolean reserveActionMatchesSource(String actionType, String sourceType) {
        if (CLEARING_REVISION.equals(sourceType)) {
            return "HOLD".equals(actionType) || "RETURN".equals(actionType);
        }
        if (RESERVE_RELEASE.equals(sourceType)) {
            return "RELEASE".equals(actionType);
        }
        return ADJUSTMENT.equals(sourceType) && ADJUSTMENT.equals(actionType);
    }

    private BigDecimal reserveAmount(SettlementReserveClearingDetailDO row) {
        if (row == null || row.getReserveActionType() == null) {
            return null;
        }
        return switch (row.getReserveActionType()) {
            case "HOLD" -> row.getRetainedAmount();
            case "RETURN" -> row.getReturnedAmount();
            case "RELEASE" -> row.getReleasedAmount();
            case "ADJUSTMENT" -> row.getAdjustmentAmount();
            default -> null;
        };
    }

    private FactKey transactionKey(String transactionId, LocalDateTime time, Integer revision) {
        if (!StringUtils.hasText(transactionId) || time == null || revision == null || revision < 1) {
            throw failure("SETTLEMENT_CLEARING_ROUTE_INCOMPLETE",
                    "settlement clearing fact route is incomplete");
        }
        return new FactKey(transactionId, time, revision);
    }

    private void addCurrency(Map<String, Integer> exponents, String currency, Integer exponent) {
        SettlementCurrency value;
        try {
            value = new SettlementCurrency(currency, exponent == null ? -1 : exponent);
            int isoExponent = Currency.getInstance(value.currency()).getDefaultFractionDigits();
            if (isoExponent >= 0 && isoExponent != value.exponent()) {
                throw new IllegalArgumentException("currency exponent differs from ISO definition");
            }
        } catch (RuntimeException exception) {
            throw failure("SETTLEMENT_CURRENCY_INVALID", "settlement clearing currency is invalid");
        }
        Integer previous = exponents.putIfAbsent(value.currency(), value.exponent());
        if (previous != null && previous != value.exponent()) {
            throw failure("SETTLEMENT_CURRENCY_EXPONENT_CONFLICT",
                    "one settlement currency uses multiple exponents");
        }
    }

    private void requireUnique(Set<String> detailNumbers, String value) {
        if (!StringUtils.hasText(value) || !detailNumbers.add(value)) {
            throw failure("SETTLEMENT_CLEARING_DETAIL_DUPLICATED",
                    "settlement clearing detail number is missing or duplicated");
        }
    }

    private SettlementProcessingException failure(String code, String message) {
        return new SettlementProcessingException(
                SettlementFailureStage.FACT_LOADING, code, false, message);
    }

    private <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : List.copyOf(rows);
    }

    private record FactKey(String transactionId,
                           LocalDateTime transactionDateTime,
                           int clearingRevision) {
    }
}
