package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.settlement.core.SettlementAmountCalculator;
import com.scott.payment.finance.settlement.core.SettlementFeeGroupCalculator;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.AmountDirection;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.AmountLine;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.AppliedLimit;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.ConversionCommand;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.ConversionResult;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.FeeComponentInput;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.FeeComponentKind;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.FeeGroupCommand;
import com.scott.payment.finance.settlement.model.SettlementCalculationModels.FeeGroupResult;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementFailureStage;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReserveClearingDetailDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.entity.SettlementResultSummaryDO;
import com.scott.payment.settlement.entity.SettlementTransactionClearingDetailDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import com.scott.payment.settlement.service.SettlementResultCalculationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementResultCalculationService
 * @date : 2026-08-26 23:30
 * @email : scott_x@163.com
 * @description : 将本金、费用组件、费用组最终值和独立保证金事实转换为不可变结果，汇总后原子进入 CALCULATED 等待入账。
 * @status : create
 */
@Service
public class DefaultSettlementResultCalculationService implements SettlementResultCalculationService {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final int ITEM_INSERT_PAGE_SIZE = 500;
    private static final int SUMMARY_INSERT_PAGE_SIZE = 200;
    private static final String TRACE = "TRACE";
    private static final String FINANCIAL_COMPONENT = "FINANCIAL_COMPONENT";
    private static final String NONE = "NONE";

    private final SettlementBatchMapper batchMapper;
    private final SettlementResultMapper resultMapper;
    private final SettlementAmountCalculator amountCalculator = new SettlementAmountCalculator();
    private final SettlementFeeGroupCalculator feeGroupCalculator = new SettlementFeeGroupCalculator();

    public DefaultSettlementResultCalculationService(SettlementBatchMapper batchMapper,
                                                     SettlementResultMapper resultMapper) {
        this.batchMapper = batchMapper;
        this.resultMapper = resultMapper;
    }

    /**
     * 状态 CAS、结果追加、幂等回读、汇总追加和 CALCULATED CAS 位于一个 transaction 主库事务。
     *
     * @param leasedBatch 已锁定汇率且持有租约的批次
     * @param facts 完整清分事实
     * @param rates 完整批次汇率矩阵
     * @param owner 当前租约所有者
     * @param now 计算时间
     * @return 结果明细数
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public int calculateAndPersist(SettlementBatchDO leasedBatch,
                                   SettlementBatchFacts facts,
                                   SettlementLockedRateMatrix rates,
                                   String owner,
                                   LocalDateTime now) {
        Objects.requireNonNull(leasedBatch, "leased settlement batch is required");
        Objects.requireNonNull(facts, "settlement batch facts are required");
        Objects.requireNonNull(rates, "locked settlement rates are required");
        Objects.requireNonNull(now, "settlement calculation time is required");
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("settlement processing owner is required");
        }

        SettlementBatchDO batch = batchMapper.selectByBatchNoForUpdate(leasedBatch.getSettlementBatchNo());
        validateLease(batch, owner.trim(), now);
        SettlementBatchStatus status = status(batch.getBatchStatus());
        if (status == SettlementBatchStatus.RATE_LOCKED) {
            if (batchMapper.beginCalculating(batch.getSettlementBatchNo(), owner.trim(),
                    batch.getVersion(), now) != 1) {
                throw failure("SETTLEMENT_CALCULATION_STATE_CAS_FAILED", true,
                        "settlement batch calculation state CAS failed");
            }
            batch.setBatchStatus(SettlementBatchStatus.CALCULATING.name());
            batch.setVersion(batch.getVersion() + 1);
        } else if (status != SettlementBatchStatus.CALCULATING) {
            throw failure("SETTLEMENT_CALCULATION_STATE_INVALID", false,
                    "settlement batch status does not allow result calculation");
        }

        List<SettlementResultItemDO> expectedItems = calculate(batch, facts, rates, now);
        if (expectedItems.isEmpty()) {
            throw failure("SETTLEMENT_RESULT_EMPTY", false,
                    "settlement batch produced no financial or audit results");
        }
        insertItems(expectedItems);
        List<SettlementResultItemDO> storedItems = safe(resultMapper.selectItemsByBatch(
                batch.getSettlementBatchNo()));
        validateStoredItems(expectedItems, storedItems);

        List<SettlementResultSummaryDO> expectedSummaries = summaries(expectedItems, now);
        if (expectedSummaries.isEmpty()) {
            throw failure("SETTLEMENT_SUMMARY_EMPTY", false,
                    "settlement batch produced no financial summaries");
        }
        insertSummaries(expectedSummaries);
        List<SettlementResultSummaryDO> storedSummaries = safe(resultMapper.selectSummariesByBatch(
                batch.getSettlementBatchNo()));
        validateStoredSummaries(expectedSummaries, storedSummaries);

        if (batchMapper.markCalculated(batch.getSettlementBatchNo(), owner.trim(),
                batch.getVersion(), now) != 1) {
            throw failure("SETTLEMENT_CALCULATED_STATE_CAS_FAILED", true,
                    "settlement batch calculated state CAS failed");
        }
        leasedBatch.setBatchStatus(SettlementBatchStatus.CALCULATED.name());
        leasedBatch.setCalculatedTime(now);
        leasedBatch.setProcessingOwner(null);
        leasedBatch.setProcessingDeadline(null);
        leasedBatch.setVersion(batch.getVersion() + 1);
        return expectedItems.size();
    }

    private List<SettlementResultItemDO> calculate(SettlementBatchDO batch,
                                                   SettlementBatchFacts facts,
                                                   SettlementLockedRateMatrix rates,
                                                   LocalDateTime now) {
        Map<FactKey, SettlementCandidateDO> candidates = new HashMap<>();
        facts.candidates().forEach(candidate -> candidates.put(new FactKey(
                candidate.getSourceTransactionId(), candidate.getSourceTransactionDateTime(),
                candidate.getSourceRevision()), candidate));
        Map<Long, List<SettlementTransactionClearingDetailDO>> transactionByCandidate = new HashMap<>();
        for (SettlementTransactionClearingDetailDO row : facts.transactionDetails()) {
            SettlementCandidateDO candidate = requireCandidate(candidates, row.getTransactionId(),
                    row.getTransactionDateTime(), row.getClearingRevision());
            transactionByCandidate.computeIfAbsent(candidate.getId(), ignored -> new ArrayList<>()).add(row);
        }
        Map<Long, List<SettlementReserveClearingDetailDO>> reserveByCandidate = new HashMap<>();
        for (SettlementReserveClearingDetailDO row : facts.reserveDetails()) {
            SettlementCandidateDO candidate = requireCandidate(candidates, row.getTransactionId(),
                    row.getTransactionDateTime(), row.getClearingRevision());
            reserveByCandidate.computeIfAbsent(candidate.getId(), ignored -> new ArrayList<>()).add(row);
        }

        List<SettlementResultItemDO> results = new ArrayList<>();
        facts.candidates().stream().sorted(Comparator.comparing(SettlementCandidateDO::getId))
                .forEach(candidate -> calculateCandidate(batch, candidate,
                        transactionByCandidate.getOrDefault(candidate.getId(), List.of()),
                        reserveByCandidate.getOrDefault(candidate.getId(), List.of()), rates, now, results));
        return List.copyOf(results);
    }

    private void calculateCandidate(SettlementBatchDO batch,
                                    SettlementCandidateDO candidate,
                                    List<SettlementTransactionClearingDetailDO> transactionRows,
                                    List<SettlementReserveClearingDetailDO> reserveRows,
                                    SettlementLockedRateMatrix rates,
                                    LocalDateTime now,
                                    List<SettlementResultItemDO> results) {
        List<SettlementTransactionClearingDetailDO> sortedTransactions = transactionRows.stream()
                .sorted(Comparator.comparing(SettlementTransactionClearingDetailDO::getLineNo)
                        .thenComparing(SettlementTransactionClearingDetailDO::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int lineNo = 0;
        for (SettlementTransactionClearingDetailDO row : sortedTransactions) {
            if ("PRINCIPAL".equals(row.getItemType())) {
                lineNo++;
                results.add(principal(batch, candidate, row, rates, lineNo, now));
            }
        }

        Map<String, List<SettlementTransactionClearingDetailDO>> feeGroups = new LinkedHashMap<>();
        for (SettlementTransactionClearingDetailDO row : sortedTransactions) {
            if ("PRINCIPAL".equals(row.getItemType())) {
                continue;
            }
            if (!StringUtils.hasText(row.getFeeGroupNo())) {
                throw failure("SETTLEMENT_FEE_GROUP_MISSING", false,
                        "non-principal clearing detail has no fee group");
            }
            feeGroups.computeIfAbsent(row.getFeeGroupNo(), ignored -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<String, List<SettlementTransactionClearingDetailDO>> entry : feeGroups.entrySet()) {
            List<SettlementTransactionClearingDetailDO> group = entry.getValue();
            for (SettlementTransactionClearingDetailDO component : group) {
                lineNo++;
                results.add(feeTrace(batch, candidate, component, rates, lineNo, now));
            }
            lineNo++;
            results.add(feeFinal(batch, candidate, entry.getKey(), group, rates, lineNo, now));
        }

        for (SettlementReserveClearingDetailDO row : reserveRows.stream()
                .sorted(Comparator.comparing(SettlementReserveClearingDetailDO::getLineNo)
                        .thenComparing(SettlementReserveClearingDetailDO::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList()) {
            lineNo++;
            results.add(reserve(batch, candidate, row, rates, lineNo, now));
        }
    }

    private SettlementResultItemDO principal(SettlementBatchDO batch,
                                             SettlementCandidateDO candidate,
                                             SettlementTransactionClearingDetailDO row,
                                             SettlementLockedRateMatrix rates,
                                             int lineNo,
                                             LocalDateTime now) {
        Converted converted = convert(row.getClearingDetailNo(), row.getAmount(), row.getCurrency(),
                row.getCurrencyExponent(), row.getDirection(), rounding(row.getRoundingMode()), batch, rates);
        return item(batch, candidate, lineNo, "TRANSACTION_CLEARING", row.getClearingDetailNo(),
                null, "PRINCIPAL", FINANCIAL_COMPONENT, row.getPaymentType(), row.getPaymentMethod(),
                row.getTransactionType(), null, row.getDirection(), row.getAmount(), row.getCurrency(),
                row.getCurrencyExponent(), rates.requireRateId(row.getCurrency()), converted.unrounded(),
                converted.rounded(), NONE, null, null, row.getRoundingMode(),
                "principal converted once with the immutable batch rate", now);
    }

    private SettlementResultItemDO feeTrace(SettlementBatchDO batch,
                                            SettlementCandidateDO candidate,
                                            SettlementTransactionClearingDetailDO row,
                                            SettlementLockedRateMatrix rates,
                                            int lineNo,
                                            LocalDateTime now) {
        Converted converted = convert(row.getClearingDetailNo(), row.getAmount(), row.getCurrency(),
                row.getCurrencyExponent(), row.getDirection(), rounding(row.getRoundingMode()), batch, rates);
        return item(batch, candidate, lineNo, "TRANSACTION_CLEARING", row.getClearingDetailNo(),
                row.getFeeGroupNo(), "FEE_COMPONENT", TRACE, row.getPaymentType(), row.getPaymentMethod(),
                row.getTransactionType(), row.getFeeCategory(), row.getDirection(), row.getAmount(),
                row.getCurrency(), row.getCurrencyExponent(), rates.requireRateId(row.getCurrency()),
                converted.unrounded(), converted.rounded(), NONE, null, null, row.getRoundingMode(),
                "clearing fee component converted for audit; excluded from financial summary", now);
    }

    private SettlementResultItemDO feeFinal(SettlementBatchDO batch,
                                            SettlementCandidateDO candidate,
                                            String feeGroupNo,
                                            List<SettlementTransactionClearingDetailDO> rows,
                                            SettlementLockedRateMatrix rates,
                                            int lineNo,
                                            LocalDateTime now) {
        SettlementTransactionClearingDetailDO representative = rows.get(0);
        validateFeeGroupIdentity(rows, representative);
        RoundingMode roundingMode = rounding(representative.getRoundingMode());
        FeeFinal finalValue = rows.stream().anyMatch(row ->
                "PENDING_SETTLEMENT_RATE".equals(row.getLimitEvaluationStatus()))
                ? pendingFeeGroup(rows, feeGroupNo, batch, rates, roundingMode)
                : finalizedFeeGroup(rows, batch, rates, roundingMode);
        long identityRateId = rates.requireRateId(batch.getTargetCurrency());
        return item(batch, candidate, lineNo, "TRANSACTION_CLEARING",
                representative.getClearingDetailNo(), feeGroupNo, "FEE_GROUP_FINAL",
                FINANCIAL_COMPONENT, representative.getPaymentType(), representative.getPaymentMethod(),
                representative.getTransactionType(), representative.getFeeCategory(), finalValue.direction(),
                finalValue.rounded(), batch.getTargetCurrency(), batch.getTargetCurrencyExponent(),
                identityRateId, finalValue.unrounded(), finalValue.rounded(),
                finalValue.appliedLimit().name(), finalValue.minimum(), finalValue.maximum(),
                representative.getRoundingMode(), finalValue.formula(), now);
    }

    private FeeFinal pendingFeeGroup(List<SettlementTransactionClearingDetailDO> rows,
                                     String feeGroupNo,
                                     SettlementBatchDO batch,
                                     SettlementLockedRateMatrix rates,
                                     RoundingMode roundingMode) {
        List<FeeComponentInput> components = new ArrayList<>();
        for (SettlementTransactionClearingDetailDO row : rows) {
            if (!"DEBIT".equals(row.getDirection())) {
                throw failure("SETTLEMENT_PENDING_FEE_DIRECTION_INVALID", false,
                        "pending settlement-rate fee components must be merchant debits");
            }
            FeeComponentKind kind = switch (row.getComponentType()) {
                case "PERCENTAGE" -> FeeComponentKind.PERCENTAGE;
                case "FIXED" -> FeeComponentKind.FIXED;
                default -> throw failure("SETTLEMENT_PENDING_FEE_COMPONENT_INVALID", false,
                        "pending settlement-rate fee group contains an unsupported component");
            };
            components.add(new FeeComponentInput(row.getClearingDetailNo(), kind,
                    new Money(row.getAmount(), row.getCurrency(), row.getCurrencyExponent())));
        }
        BigDecimal minimum = consistentOptional(rows, true);
        BigDecimal maximum = consistentOptional(rows, false);
        FeeGroupResult calculated;
        try {
            calculated = feeGroupCalculator.calculate(new FeeGroupCommand(
                    feeGroupNo, components, usd(minimum), usd(maximum), batch.getTargetCurrency(),
                    batch.getTargetCurrencyExponent(), roundingMode), rates.matrix());
        } catch (IllegalArgumentException exception) {
            throw failure("SETTLEMENT_FEE_GROUP_CALCULATION_INVALID", false,
                    "fee group cannot be evaluated with the locked batch rates");
        }
        BigDecimal selected = calculated.unroundedCalculatedAmount()
                .add(calculated.limitAdjustmentAmount(), CALCULATION_CONTEXT);
        return new FeeFinal("DEBIT", selected, calculated.finalFee().amount(), calculated.appliedLimit(),
                calculated.unroundedMinimumAmount(), calculated.unroundedMaximumAmount(),
                "fee group = percentage(label currency) + fixed(USD), then apply USD min/max with batch rates");
    }

    private FeeFinal finalizedFeeGroup(List<SettlementTransactionClearingDetailDO> rows,
                                       SettlementBatchDO batch,
                                       SettlementLockedRateMatrix rates,
                                       RoundingMode roundingMode) {
        BigDecimal signed = BigDecimal.ZERO;
        for (SettlementTransactionClearingDetailDO row : rows) {
            Converted converted = convert(row.getClearingDetailNo(), row.getAmount(), row.getCurrency(),
                    row.getCurrencyExponent(), row.getDirection(), roundingMode, batch, rates);
            signed = "CREDIT".equals(row.getDirection())
                    ? signed.subtract(converted.unrounded(), CALCULATION_CONTEXT)
                    : signed.add(converted.unrounded(), CALCULATION_CONTEXT);
        }
        String direction = signed.signum() < 0 ? "CREDIT" : "DEBIT";
        BigDecimal unrounded = signed.abs();
        BigDecimal rounded = unrounded.setScale(batch.getTargetCurrencyExponent(), roundingMode);
        BigDecimal minimum = convertUsd(consistentOptional(rows, true), batch, rates);
        BigDecimal maximum = convertUsd(consistentOptional(rows, false), batch, rates);
        AppliedLimit applied = rows.stream().map(SettlementTransactionClearingDetailDO::getAppliedLimit)
                .filter(StringUtils::hasText).filter(value -> !NONE.equals(value))
                .map(this::appliedLimit).findFirst().orElse(AppliedLimit.NONE);
        return new FeeFinal(direction, unrounded, rounded, applied, minimum, maximum,
                "fee group final preserves clearing-final components and converts their signed net with batch rates");
    }

    private SettlementResultItemDO reserve(SettlementBatchDO batch,
                                           SettlementCandidateDO candidate,
                                           SettlementReserveClearingDetailDO row,
                                           SettlementLockedRateMatrix rates,
                                           int lineNo,
                                           LocalDateTime now) {
        BigDecimal sourceAmount = reserveAmount(row);
        Converted converted = convert(row.getReserveClearingDetailNo(), sourceAmount,
                row.getReserveCurrency(), row.getReserveCurrencyExponent(), row.getDirection(),
                rounding(row.getRoundingMode()), batch, rates);
        String resultType = switch (row.getReserveActionType()) {
            case "HOLD" -> "RESERVE_HOLD";
            case "RETURN" -> "RESERVE_RETURN";
            case "RELEASE" -> "RESERVE_RELEASE";
            case "ADJUSTMENT" -> "ADJUSTMENT";
            default -> throw failure("SETTLEMENT_RESERVE_ACTION_INVALID", false,
                    "reserve clearing action is unsupported");
        };
        return item(batch, candidate, lineNo, "RESERVE_CLEARING", row.getReserveClearingDetailNo(),
                null, resultType, FINANCIAL_COMPONENT, row.getPaymentType(), row.getPaymentMethod(),
                row.getTransactionType(), null, row.getDirection(), sourceAmount,
                row.getReserveCurrency(), row.getReserveCurrencyExponent(),
                rates.requireRateId(row.getReserveCurrency()), converted.unrounded(), converted.rounded(),
                NONE, null, null, row.getRoundingMode(),
                "reserve clearing fact converted only at settlement with the immutable batch rate", now);
    }

    private SettlementResultItemDO item(SettlementBatchDO batch,
                                        SettlementCandidateDO candidate,
                                        int lineNo,
                                        String sourceDetailType,
                                        String sourceDetailNo,
                                        String feeGroupNo,
                                        String resultItemType,
                                        String resultRole,
                                        String paymentType,
                                        String paymentMethod,
                                        String transactionType,
                                        String feeCategory,
                                        String direction,
                                        BigDecimal sourceAmount,
                                        String sourceCurrency,
                                        int sourceExponent,
                                        long rateId,
                                        BigDecimal unroundedTarget,
                                        BigDecimal targetAmount,
                                        String appliedLimit,
                                        BigDecimal minimumTarget,
                                        BigDecimal maximumTarget,
                                        String roundingMode,
                                        String formula,
                                        LocalDateTime now) {
        SettlementResultItemDO row = new SettlementResultItemDO();
        row.setSettlementResultItemNo(resultItemNo(batch.getSettlementBatchNo(), candidate.getId(), lineNo));
        row.setSettlementBatchNo(batch.getSettlementBatchNo());
        row.setCandidateId(candidate.getId());
        row.setResultLineNo(lineNo);
        row.setMerchantId(batch.getMerchantId());
        row.setSettlementAccountId(batch.getSettlementAccountId());
        row.setSourceDetailType(sourceDetailType);
        row.setSourceDetailNo(sourceDetailNo);
        row.setSourceTransactionId(candidate.getSourceTransactionId());
        row.setSourceTransactionDateTime(candidate.getSourceTransactionDateTime());
        row.setFeeGroupNo(feeGroupNo);
        row.setResultItemType(resultItemType);
        row.setResultRole(resultRole);
        row.setPaymentType(paymentType);
        row.setPaymentMethod(paymentMethod);
        row.setTransactionType(transactionType);
        row.setFeeCategory(feeCategory);
        row.setDirection(direction);
        row.setSourceAmount(sourceAmount);
        row.setSourceCurrency(sourceCurrency);
        row.setSourceCurrencyExponent(sourceExponent);
        row.setSettlementBatchRateId(rateId);
        row.setUnroundedTargetAmount(unroundedTarget);
        row.setTargetAmount(targetAmount);
        row.setTargetCurrency(batch.getTargetCurrency());
        row.setTargetCurrencyExponent(batch.getTargetCurrencyExponent());
        row.setAppliedLimit(appliedLimit);
        row.setMinimumTargetAmount(minimumTarget);
        row.setMaximumTargetAmount(maximumTarget);
        row.setRoundingMode(roundingMode);
        row.setFormulaSnapshot(formula);
        row.setLedgerIdempotencyKey(null);
        row.setCreateTime(now);
        return row;
    }

    private Converted convert(String lineNo,
                              BigDecimal amount,
                              String currency,
                              int exponent,
                              String direction,
                              RoundingMode roundingMode,
                              SettlementBatchDO batch,
                              SettlementLockedRateMatrix rates) {
        try {
            ConversionResult result = amountCalculator.calculate(new ConversionCommand(
                    List.of(new AmountLine(lineNo, new Money(amount, currency, exponent),
                            AmountDirection.valueOf(direction))), batch.getTargetCurrency(),
                    batch.getTargetCurrencyExponent(), roundingMode), rates.matrix());
            BigDecimal unrounded = result.convertedLines().get(0).unroundedTargetAmount();
            return new Converted(unrounded,
                    unrounded.setScale(batch.getTargetCurrencyExponent(), roundingMode));
        } catch (IllegalArgumentException exception) {
            throw failure("SETTLEMENT_AMOUNT_CONVERSION_INVALID", false,
                    "clearing amount cannot be converted with the locked batch rate");
        }
    }

    private List<SettlementResultSummaryDO> summaries(List<SettlementResultItemDO> items,
                                                      LocalDateTime now) {
        Map<SummaryKey, SummaryAccumulator> grouped = new LinkedHashMap<>();
        items.stream().filter(item -> FINANCIAL_COMPONENT.equals(item.getResultRole())).forEach(item -> {
            SummaryKey key = new SummaryKey(item.getSettlementBatchNo(), item.getMerchantId(),
                    valueOrNone(item.getPaymentType()), valueOrNone(item.getPaymentMethod()),
                    valueOrNone(item.getTransactionType()), item.getResultItemType(),
                    valueOrNone(item.getFeeCategory()), item.getDirection(), item.getSourceCurrency(),
                    item.getTargetCurrency());
            grouped.computeIfAbsent(key, ignored -> new SummaryAccumulator())
                    .add(item.getCandidateId(), item.getSourceAmount(), item.getTargetAmount());
        });
        return grouped.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> summary(entry.getKey(), entry.getValue(), now)).toList();
    }

    private SettlementResultSummaryDO summary(SummaryKey key,
                                               SummaryAccumulator value,
                                               LocalDateTime now) {
        SettlementResultSummaryDO row = new SettlementResultSummaryDO();
        row.setSettlementBatchNo(key.batchNo());
        row.setMerchantId(key.merchantId());
        row.setPaymentType(key.paymentType());
        row.setPaymentMethod(key.paymentMethod());
        row.setTransactionType(key.transactionType());
        row.setResultItemType(key.resultItemType());
        row.setFeeCategory(key.feeCategory());
        row.setDirection(key.direction());
        row.setSourceCurrency(key.sourceCurrency());
        row.setTargetCurrency(key.targetCurrency());
        row.setTransactionCount((long) value.candidateIds.size());
        row.setSourceAmount(value.sourceAmount);
        row.setTargetAmount(value.targetAmount);
        row.setCreateTime(now);
        return row;
    }

    private void insertItems(List<SettlementResultItemDO> rows) {
        for (int start = 0; start < rows.size(); start += ITEM_INSERT_PAGE_SIZE) {
            resultMapper.insertItemsIdempotent(rows.subList(start,
                    Math.min(start + ITEM_INSERT_PAGE_SIZE, rows.size())));
        }
    }

    private void insertSummaries(List<SettlementResultSummaryDO> rows) {
        for (int start = 0; start < rows.size(); start += SUMMARY_INSERT_PAGE_SIZE) {
            resultMapper.insertSummariesIdempotent(rows.subList(start,
                    Math.min(start + SUMMARY_INSERT_PAGE_SIZE, rows.size())));
        }
    }

    private void validateStoredItems(List<SettlementResultItemDO> expected,
                                     List<SettlementResultItemDO> stored) {
        if (expected.size() != stored.size()) {
            throw failure("SETTLEMENT_RESULT_IDEMPOTENCY_CONFLICT", false,
                    "stored settlement result item count differs from calculation");
        }
        Map<String, SettlementResultItemDO> actual = new HashMap<>();
        stored.forEach(row -> actual.put(row.getSettlementResultItemNo(), row));
        for (SettlementResultItemDO row : expected) {
            SettlementResultItemDO value = actual.get(row.getSettlementResultItemNo());
            if (!sameItem(row, value)) {
                throw failure("SETTLEMENT_RESULT_IDEMPOTENCY_CONFLICT", false,
                        "stored settlement result identity differs from calculation");
            }
        }
    }

    private boolean sameItem(SettlementResultItemDO left, SettlementResultItemDO right) {
        return right != null
                && Objects.equals(left.getSettlementBatchNo(), right.getSettlementBatchNo())
                && Objects.equals(left.getCandidateId(), right.getCandidateId())
                && Objects.equals(left.getResultLineNo(), right.getResultLineNo())
                && Objects.equals(left.getSourceDetailType(), right.getSourceDetailType())
                && Objects.equals(left.getSourceDetailNo(), right.getSourceDetailNo())
                && Objects.equals(left.getFeeGroupNo(), right.getFeeGroupNo())
                && Objects.equals(left.getResultItemType(), right.getResultItemType())
                && Objects.equals(left.getResultRole(), right.getResultRole())
                && Objects.equals(left.getPaymentType(), right.getPaymentType())
                && Objects.equals(left.getPaymentMethod(), right.getPaymentMethod())
                && Objects.equals(left.getTransactionType(), right.getTransactionType())
                && Objects.equals(left.getFeeCategory(), right.getFeeCategory())
                && Objects.equals(left.getDirection(), right.getDirection())
                && amountEquals(left.getSourceAmount(), right.getSourceAmount())
                && Objects.equals(left.getSourceCurrency(), right.getSourceCurrency())
                && Objects.equals(left.getSourceCurrencyExponent(), right.getSourceCurrencyExponent())
                && Objects.equals(left.getSettlementBatchRateId(), right.getSettlementBatchRateId())
                && amountEquals(left.getUnroundedTargetAmount(), right.getUnroundedTargetAmount())
                && amountEquals(left.getTargetAmount(), right.getTargetAmount())
                && Objects.equals(left.getTargetCurrency(), right.getTargetCurrency())
                && Objects.equals(left.getTargetCurrencyExponent(), right.getTargetCurrencyExponent())
                && Objects.equals(left.getAppliedLimit(), right.getAppliedLimit())
                && amountEquals(left.getMinimumTargetAmount(), right.getMinimumTargetAmount())
                && amountEquals(left.getMaximumTargetAmount(), right.getMaximumTargetAmount())
                && Objects.equals(left.getRoundingMode(), right.getRoundingMode())
                && Objects.equals(left.getFormulaSnapshot(), right.getFormulaSnapshot())
                && right.getLedgerIdempotencyKey() == null;
    }

    private void validateStoredSummaries(List<SettlementResultSummaryDO> expected,
                                         List<SettlementResultSummaryDO> stored) {
        if (expected.size() != stored.size()) {
            throw failure("SETTLEMENT_SUMMARY_IDEMPOTENCY_CONFLICT", false,
                    "stored settlement summary count differs from calculation");
        }
        Map<SummaryKey, SettlementResultSummaryDO> actual = new HashMap<>();
        stored.forEach(row -> actual.put(summaryKey(row), row));
        for (SettlementResultSummaryDO row : expected) {
            SettlementResultSummaryDO value = actual.get(summaryKey(row));
            if (value == null || !Objects.equals(row.getTransactionCount(), value.getTransactionCount())
                    || !amountEquals(row.getSourceAmount(), value.getSourceAmount())
                    || !amountEquals(row.getTargetAmount(), value.getTargetAmount())) {
                throw failure("SETTLEMENT_SUMMARY_IDEMPOTENCY_CONFLICT", false,
                        "stored settlement summary identity differs from calculation");
            }
        }
    }

    private SummaryKey summaryKey(SettlementResultSummaryDO row) {
        return new SummaryKey(row.getSettlementBatchNo(), row.getMerchantId(), row.getPaymentType(),
                row.getPaymentMethod(), row.getTransactionType(), row.getResultItemType(),
                row.getFeeCategory(), row.getDirection(), row.getSourceCurrency(), row.getTargetCurrency());
    }

    private void validateFeeGroupIdentity(List<SettlementTransactionClearingDetailDO> rows,
                                          SettlementTransactionClearingDetailDO expected) {
        for (SettlementTransactionClearingDetailDO row : rows) {
            if (!Objects.equals(row.getPaymentType(), expected.getPaymentType())
                    || !Objects.equals(row.getPaymentMethod(), expected.getPaymentMethod())
                    || !Objects.equals(row.getTransactionType(), expected.getTransactionType())
                    || !Objects.equals(row.getFeeCategory(), expected.getFeeCategory())
                    || !Objects.equals(row.getRoundingMode(), expected.getRoundingMode())) {
                throw failure("SETTLEMENT_FEE_GROUP_IDENTITY_CONFLICT", false,
                        "one clearing fee group contains inconsistent dimensions");
            }
        }
    }

    private BigDecimal consistentOptional(List<SettlementTransactionClearingDetailDO> rows,
                                          boolean minimum) {
        BigDecimal expected = null;
        for (SettlementTransactionClearingDetailDO row : rows) {
            BigDecimal value = minimum ? row.getMinimumAmountUsd() : row.getMaximumAmountUsd();
            if (value == null) {
                continue;
            }
            if (expected != null && expected.compareTo(value) != 0) {
                throw failure("SETTLEMENT_FEE_LIMIT_CONFLICT", false,
                        "one clearing fee group contains inconsistent USD limits");
            }
            expected = value;
        }
        return expected;
    }

    private BigDecimal convertUsd(BigDecimal amount,
                                  SettlementBatchDO batch,
                                  SettlementLockedRateMatrix rates) {
        if (amount == null) {
            return null;
        }
        LockedRate rate = rates.matrix().require("USD", batch.getTargetCurrency());
        return amount.multiply(rate.directRate(), CALCULATION_CONTEXT);
    }

    private Money usd(BigDecimal amount) {
        return amount == null ? null : new Money(amount, "USD", 2);
    }

    private BigDecimal reserveAmount(SettlementReserveClearingDetailDO row) {
        return switch (row.getReserveActionType()) {
            case "HOLD" -> row.getRetainedAmount();
            case "RETURN" -> row.getReturnedAmount();
            case "RELEASE" -> row.getReleasedAmount();
            case "ADJUSTMENT" -> row.getAdjustmentAmount();
            default -> throw failure("SETTLEMENT_RESERVE_ACTION_INVALID", false,
                    "reserve clearing action is unsupported");
        };
    }

    private SettlementCandidateDO requireCandidate(Map<FactKey, SettlementCandidateDO> candidates,
                                                   String transactionId,
                                                   LocalDateTime transactionTime,
                                                   Integer revision) {
        SettlementCandidateDO candidate = revision == null ? null
                : candidates.get(new FactKey(transactionId, transactionTime, revision));
        if (candidate == null) {
            throw failure("SETTLEMENT_RESULT_CANDIDATE_MISSING", false,
                    "clearing fact does not belong to a claimed settlement candidate");
        }
        return candidate;
    }

    private RoundingMode rounding(String value) {
        try {
            RoundingMode mode = RoundingMode.valueOf(value);
            if (!Set.of(RoundingMode.HALF_UP, RoundingMode.HALF_EVEN, RoundingMode.DOWN).contains(mode)) {
                throw new IllegalArgumentException("unsupported settlement rounding mode");
            }
            return mode;
        } catch (RuntimeException exception) {
            throw failure("SETTLEMENT_ROUNDING_MODE_INVALID", false,
                    "clearing fact rounding mode is unsupported");
        }
    }

    private AppliedLimit appliedLimit(String value) {
        try {
            return AppliedLimit.valueOf(value);
        } catch (RuntimeException exception) {
            throw failure("SETTLEMENT_APPLIED_LIMIT_INVALID", false,
                    "clearing fee applied limit is unsupported");
        }
    }

    private void validateLease(SettlementBatchDO batch, String owner, LocalDateTime now) {
        if (batch == null || batch.getVersion() == null || !owner.equals(batch.getProcessingOwner())
                || batch.getProcessingDeadline() == null || !batch.getProcessingDeadline().isAfter(now)) {
            throw failure("SETTLEMENT_PROCESSING_LEASE_LOST", true,
                    "settlement batch processing lease is unavailable or expired");
        }
    }

    private SettlementBatchStatus status(String value) {
        try {
            return SettlementBatchStatus.valueOf(value);
        } catch (RuntimeException exception) {
            throw failure("SETTLEMENT_BATCH_STATUS_INVALID", false,
                    "settlement batch status is unsupported");
        }
    }

    private boolean amountEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private String valueOrNone(String value) {
        return StringUtils.hasText(value) ? value : NONE;
    }

    private String resultItemNo(String batchNo, Long candidateId, int lineNo) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((batchNo + "|" + candidateId + "|" + lineNo).getBytes(StandardCharsets.UTF_8));
            return "SI" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : List.copyOf(rows);
    }

    private SettlementProcessingException failure(String code, boolean retryable, String message) {
        return new SettlementProcessingException(
                SettlementFailureStage.RESULT_CALCULATION, code, retryable, message);
    }

    private record FactKey(String transactionId,
                           LocalDateTime transactionDateTime,
                           int clearingRevision) {
    }

    private record Converted(BigDecimal unrounded, BigDecimal rounded) {
    }

    private record FeeFinal(String direction,
                            BigDecimal unrounded,
                            BigDecimal rounded,
                            AppliedLimit appliedLimit,
                            BigDecimal minimum,
                            BigDecimal maximum,
                            String formula) {
    }

    private record SummaryKey(String batchNo,
                              String merchantId,
                              String paymentType,
                              String paymentMethod,
                              String transactionType,
                              String resultItemType,
                              String feeCategory,
                              String direction,
                              String sourceCurrency,
                              String targetCurrency) implements Comparable<SummaryKey> {

        @Override
        public int compareTo(SummaryKey other) {
            return String.join("|", batchNo, merchantId, paymentType, paymentMethod, transactionType,
                    resultItemType, feeCategory, direction, sourceCurrency, targetCurrency)
                    .compareTo(String.join("|", other.batchNo, other.merchantId, other.paymentType,
                            other.paymentMethod, other.transactionType, other.resultItemType,
                            other.feeCategory, other.direction, other.sourceCurrency, other.targetCurrency));
        }
    }

    private static final class SummaryAccumulator {
        private final Set<Long> candidateIds = new HashSet<>();
        private BigDecimal sourceAmount = BigDecimal.ZERO;
        private BigDecimal targetAmount = BigDecimal.ZERO;

        private void add(Long candidateId, BigDecimal source, BigDecimal target) {
            candidateIds.add(candidateId);
            sourceAmount = sourceAmount.add(source, CALCULATION_CONTEXT);
            targetAmount = targetAmount.add(target, CALCULATION_CONTEXT);
        }
    }
}
