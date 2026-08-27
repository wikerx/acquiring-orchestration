package com.scott.payment.clearing.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.dto.FeeVersionConfigurationDTO;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.entity.ClearingFeeVersionSnapshotRowDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingFeeVersionSnapshotMapper;
import com.scott.payment.clearing.service.FeeVersionQueryService;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeTierSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierMetric;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.money.model.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultFeeVersionQueryService
 * @date : 2026-08-26 10:05
 * @email : scott_x@163.com
 * @description : 清分费用版本只读实现，通过独立主从事务聚合动作锁定版本，禁止更新费用模板或改查当前活动版本。
 * @status : create
 */
@Service
public class DefaultFeeVersionQueryService implements FeeVersionQueryService {

    private static final String USD = "USD";
    private static final int USD_EXPONENT = 2;
    private static final Pattern ISO_CURRENCY = Pattern.compile("[A-Z]{3}");

    private final ClearingFeeVersionSnapshotMapper snapshotMapper;

    /** @param snapshotMapper 确切费用版本只读聚合 Mapper */
    public DefaultFeeVersionQueryService(ClearingFeeVersionSnapshotMapper snapshotMapper) {
        this.snapshotMapper = snapshotMapper;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public FeeVersionConfigurationDTO findVersionFromSlave(String merchantId,
                                                           Long feePlanId,
                                                           Long feePlanVersionId) {
        return toConfiguration(snapshotMapper.selectVersionRows(merchantId, feePlanId, feePlanVersionId));
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public FeeVersionConfigurationDTO findVersionFromMaster(String merchantId,
                                                            Long feePlanId,
                                                            Long feePlanVersionId) {
        return toConfiguration(snapshotMapper.selectVersionRows(merchantId, feePlanId, feePlanVersionId));
    }

    private FeeVersionConfigurationDTO toConfiguration(List<ClearingFeeVersionSnapshotRowDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        ClearingFeeVersionSnapshotRowDO first = rows.get(0);
        validateVersionHeader(first);
        Map<Long, RuleAccumulator> rules = new LinkedHashMap<>();
        for (ClearingFeeVersionSnapshotRowDO row : rows) {
            validateIdentity(first, row);
            rules.computeIfAbsent(row.getFeeRuleId(), ignored -> new RuleAccumulator(row)).addTier(row);
        }
        List<FeeRuleConfigurationSnapshot> snapshots = rules.values().stream()
                .map(RuleAccumulator::toSnapshot)
                .toList();
        if (snapshots.isEmpty()) {
            throw failure(ClearingFailureCodeEnum.FEE_RULE_NOT_CONFIGURED,
                    "immutable fee version contains no rules");
        }
        validateDistinctRuleDimensions(snapshots);
        return new FeeVersionConfigurationDTO(
                first.getMerchantId(), first.getFeePlanId(), first.getFeePlanVersionId(),
                first.getFeePlanVersionNo(),
                first.getSettlementCurrency(), first.getReserveRate(), first.getReserveDelayUnit(),
                first.getReserveDelayDays(), snapshots);
    }

    private void validateVersionHeader(ClearingFeeVersionSnapshotRowDO row) {
        if (row == null || row.getMerchantId() == null || row.getMerchantId().isBlank()
                || row.getFeePlanId() == null || row.getFeePlanId() < 1
                || row.getFeePlanVersionId() == null || row.getFeePlanVersionId() < 1
                || row.getFeePlanVersionNo() == null || row.getFeePlanVersionNo() < 1
                || row.getSettlementCurrency() == null
                || !ISO_CURRENCY.matcher(row.getSettlementCurrency()).matches()
                || row.getReserveRate() == null || row.getReserveRate().signum() < 0
                || row.getReserveRate().compareTo(new BigDecimal("100")) > 0
                || !Set.of("D", "T").contains(row.getReserveDelayUnit())
                || row.getReserveDelayDays() == null || row.getReserveDelayDays() < 1) {
            throw failure(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE,
                    "immutable fee version header is malformed");
        }
    }

    private void validateDistinctRuleDimensions(List<FeeRuleConfigurationSnapshot> rules) {
        Set<RuleDimension> dimensions = new HashSet<>();
        for (FeeRuleConfigurationSnapshot rule : rules) {
            RuleDimension dimension = new RuleDimension(
                    normalized(rule.feeCategory()), normalized(rule.riskServiceType()),
                    normalized(rule.transactionType()), normalized(rule.paymentType()),
                    normalized(rule.paymentMethod()));
            if (!dimensions.add(dimension)) {
                throw failure(ClearingFailureCodeEnum.FEE_RULE_AMBIGUOUS,
                        "immutable fee version contains ambiguous rule dimensions");
            }
        }
    }

    private static String normalized(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private void validateIdentity(ClearingFeeVersionSnapshotRowDO expected,
                                  ClearingFeeVersionSnapshotRowDO actual) {
        if (!Objects.equals(expected.getMerchantId(), actual.getMerchantId())
                || !Objects.equals(expected.getFeePlanId(), actual.getFeePlanId())
                || !Objects.equals(expected.getFeePlanVersionId(), actual.getFeePlanVersionId())
                || !Objects.equals(expected.getFeePlanVersionNo(), actual.getFeePlanVersionNo())
                || !Objects.equals(expected.getSettlementCurrency(), actual.getSettlementCurrency())
                || !decimalEquals(expected.getReserveRate(), actual.getReserveRate())
                || !Objects.equals(expected.getReserveDelayUnit(), actual.getReserveDelayUnit())
                || !Objects.equals(expected.getReserveDelayDays(), actual.getReserveDelayDays())) {
            throw failure(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE,
                    "immutable fee version rows contain mixed identities or terms");
        }
    }

    private static boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private static ClearingProcessingException failure(ClearingFailureCodeEnum code, String message) {
        return new ClearingProcessingException(code, message);
    }

    private static Money usd(BigDecimal amount) {
        return amount == null ? null : new Money(amount, USD, USD_EXPONENT);
    }

    private static String requiredEnum(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static final class RuleAccumulator {

        private final ClearingFeeVersionSnapshotRowDO rule;
        private final List<FeeTierSnapshot> tiers = new ArrayList<>();

        private RuleAccumulator(ClearingFeeVersionSnapshotRowDO rule) {
            if (rule.getFeeRuleId() == null || rule.getFeeRuleId() < 1) {
                throw failure(ClearingFailureCodeEnum.FEE_RULE_NOT_CONFIGURED,
                        "immutable fee version contains no usable rule");
            }
            this.rule = rule;
        }

        private void addTier(ClearingFeeVersionSnapshotRowDO row) {
            validateRuleTerms(row);
            if (row.getFeeTierId() != null) {
                tiers.add(new FeeTierSnapshot(
                        row.getFeeTierId(), row.getTierLowerBound(), row.getTierUpperBound(),
                        row.getTierPercentageRate(), usd(row.getTierFixedAmountUsd()),
                        usd(row.getTierMinimumAmountUsd()), usd(row.getTierMaximumAmountUsd())));
            }
        }

        private void validateRuleTerms(ClearingFeeVersionSnapshotRowDO row) {
            if (!Objects.equals(rule.getFeeRuleId(), row.getFeeRuleId())
                    || !Objects.equals(normalized(rule.getFeeCategory()), normalized(row.getFeeCategory()))
                    || !Objects.equals(normalized(rule.getTransactionType()), normalized(row.getTransactionType()))
                    || !Objects.equals(normalized(rule.getPaymentType()), normalized(row.getPaymentType()))
                    || !Objects.equals(normalized(rule.getPaymentMethod()), normalized(row.getPaymentMethod()))
                    || !Objects.equals(normalized(rule.getRiskServiceType()), normalized(row.getRiskServiceType()))
                    || !Objects.equals(normalized(rule.getChargeTrigger()), normalized(row.getChargeTrigger()))
                    || !Objects.equals(normalized(rule.getFeeMode()), normalized(row.getFeeMode()))
                    || !decimalEquals(rule.getPercentageRate(), row.getPercentageRate())
                    || !decimalEquals(rule.getFixedAmountUsd(), row.getFixedAmountUsd())
                    || !decimalEquals(rule.getMinimumAmountUsd(), row.getMinimumAmountUsd())
                    || !decimalEquals(rule.getMaximumAmountUsd(), row.getMaximumAmountUsd())
                    || !Objects.equals(normalized(rule.getTierMetric()), normalized(row.getTierMetric()))) {
                throw failure(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE,
                        "immutable fee version contains conflicting rows for one rule");
            }
        }

        private FeeRuleConfigurationSnapshot toSnapshot() {
            try {
                FeeMode feeMode = FeeMode.valueOf(requiredEnum(rule.getFeeMode(), "fee mode"));
                TierMetric tierMetric = rule.getTierMetric() == null || rule.getTierMetric().isBlank()
                        ? null : TierMetric.valueOf(requiredEnum(rule.getTierMetric(), "tier metric"));
                FeeRuleSnapshot calculationRule = new FeeRuleSnapshot(
                        rule.getFeeRuleId(), feeMode, rule.getPercentageRate(), usd(rule.getFixedAmountUsd()),
                        usd(rule.getMinimumAmountUsd()), usd(rule.getMaximumAmountUsd()), tierMetric);
                return new FeeRuleConfigurationSnapshot(
                        rule.getFeeRuleId(), rule.getFeeCategory(), rule.getTransactionType(),
                        rule.getPaymentType(), rule.getPaymentMethod(), rule.getRiskServiceType(),
                        rule.getChargeTrigger(), calculationRule, tiers);
            } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
                throw failure(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE,
                        "immutable fee version contains a malformed rule structure");
            }
        }
    }

    private record RuleDimension(String feeCategory,
                                 String riskServiceType,
                                 String transactionType,
                                 String paymentType,
                                 String paymentMethod) {
    }
}
