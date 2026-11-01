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
 * 清分费用版本只读实现，通过独立主从事务聚合动作锁定版本，禁止更新费用模板或改查当前活动版本。
 */
@Service
public class DefaultFeeVersionQueryService implements FeeVersionQueryService {

    /**
     * 固定费、最低费和最高费原子组件使用的配置币种。
     */
    private static final String USD = "USD";
    /**
     * USD 费用原子组件使用的 ISO 4217 小数指数。
     */
    private static final int USD_EXPONENT = 2;
    /**
     * 不可变费用版本允许声明的三位大写 ISO 结算币种格式。
     */
    private static final Pattern ISO_CURRENCY = Pattern.compile("[A-Z]{3}");

    private final ClearingFeeVersionSnapshotMapper snapshotMapper;

    /**
     * 创建费用版本查询服务，并注入不可变版本的只读聚合访问对象。
     *
     * @param snapshotMapper 确切费用版本只读聚合 Mapper
     */
    public DefaultFeeVersionQueryService(ClearingFeeVersionSnapshotMapper snapshotMapper) {
        this.snapshotMapper = snapshotMapper;
    }

    /**
     * 从只读数据源加载指定商户的不可变费用版本配置。
     */
    @Override
    @DS(DataSourceName.SLAVE)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public FeeVersionConfigurationDTO findVersionFromSlave(String merchantId,
                                                           Long feePlanId,
                                                           Long feePlanVersionId) {
        return toConfiguration(snapshotMapper.selectVersionRows(merchantId, feePlanId, feePlanVersionId));
    }

    /**
     * 从主数据源加载指定商户的不可变费用版本配置。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public FeeVersionConfigurationDTO findVersionFromMaster(String merchantId,
                                                            Long feePlanId,
                                                            Long feePlanVersionId) {
        return toConfiguration(snapshotMapper.selectVersionRows(merchantId, feePlanId, feePlanVersionId));
    }

    /**
     * 将 JOIN 展开的费用版本行聚合为单一配置，并拒绝混合身份或重复收费维度。
     */
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
                first.getSettlementCurrency(), first.getInitialDelayUnit(),
                first.getInitialDelayDays(), first.getRegularDelayDays(),
                first.getSettlementFrequency(), first.getFrequencyDay(),
                first.getEffectiveTime().toLocalDate(), first.getReserveRate(), first.getReserveDelayUnit(),
                first.getReserveDelayDays(), snapshots);
    }

    /**
     * 费用版本头必须完整且仍处于可追溯的 ACTIVE 或 SUPERSEDED 状态。
     */
    private void validateVersionHeader(ClearingFeeVersionSnapshotRowDO row) {
        if (row == null || row.getMerchantId() == null || row.getMerchantId().isBlank()
                || row.getFeePlanId() == null || row.getFeePlanId() < 1
                || row.getFeePlanVersionId() == null || row.getFeePlanVersionId() < 1
                || row.getFeePlanVersionNo() == null || row.getFeePlanVersionNo() < 1
                || row.getSettlementCurrency() == null
                || !ISO_CURRENCY.matcher(row.getSettlementCurrency()).matches()
                || !validDelayUnit(row.getInitialDelayUnit())
                || row.getInitialDelayDays() == null || row.getInitialDelayDays() < 1
                || row.getRegularDelayDays() == null || row.getRegularDelayDays() < 1
                || !validSettlementFrequency(row.getSettlementFrequency())
                || !validFrequencyDay(row.getSettlementFrequency(), row.getFrequencyDay())
                || row.getEffectiveTime() == null
                || row.getReserveRate() == null || row.getReserveRate().signum() < 0
                || row.getReserveRate().compareTo(new BigDecimal("100")) > 0
                || !validDelayUnit(row.getReserveDelayUnit())
                || row.getReserveDelayDays() == null || row.getReserveDelayDays() < 1) {
            throw failure(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE,
                    "immutable fee version header is malformed");
        }
    }

    /**
     * 判断结算延迟单位是否为自然日或已确认工作日。
     */
    private boolean validDelayUnit(String delayUnit) {
        return "D".equals(delayUnit) || "T".equals(delayUnit);
    }

    /**
     * 判断结算频率是否属于平台支持的固定周期。
     */
    private boolean validSettlementFrequency(String frequency) {
        return "DAILY".equals(frequency) || "WEEKLY".equals(frequency)
                || "BIWEEKLY".equals(frequency) || "MONTHLY".equals(frequency);
    }

    /**
     * 校验结算频率与频率日参数的组合约束。
     */
    private boolean validFrequencyDay(String frequency, Integer day) {
        if (frequency == null) {
            return false;
        }
        return switch (frequency) {
            case "DAILY" -> day == null;
            case "WEEKLY", "BIWEEKLY" -> day != null && day >= 1 && day <= 7;
            case "MONTHLY" -> day != null && day >= 1 && day <= 28;
            default -> false;
        };
    }

    /**
     * 同一不可变版本内禁止出现重复规则维度，避免一笔动作重复收费。
     */
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

    /**
     * 规范化可选规则维度，便于比较同一不可变版本内的配置行。
     */
    private static String normalized(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * JOIN 展开的每行必须属于同一商户、方案和不可变版本。
     */
    private void validateIdentity(ClearingFeeVersionSnapshotRowDO expected,
                                  ClearingFeeVersionSnapshotRowDO actual) {
        if (!Objects.equals(expected.getMerchantId(), actual.getMerchantId())
                || !Objects.equals(expected.getFeePlanId(), actual.getFeePlanId())
                || !Objects.equals(expected.getFeePlanVersionId(), actual.getFeePlanVersionId())
                || !Objects.equals(expected.getFeePlanVersionNo(), actual.getFeePlanVersionNo())
                || !Objects.equals(expected.getSettlementCurrency(), actual.getSettlementCurrency())
                || !Objects.equals(expected.getInitialDelayUnit(), actual.getInitialDelayUnit())
                || !Objects.equals(expected.getInitialDelayDays(), actual.getInitialDelayDays())
                || !Objects.equals(expected.getRegularDelayDays(), actual.getRegularDelayDays())
                || !Objects.equals(expected.getSettlementFrequency(), actual.getSettlementFrequency())
                || !Objects.equals(expected.getFrequencyDay(), actual.getFrequencyDay())
                || !Objects.equals(expected.getEffectiveTime(), actual.getEffectiveTime())
                || !decimalEquals(expected.getReserveRate(), actual.getReserveRate())
                || !Objects.equals(expected.getReserveDelayUnit(), actual.getReserveDelayUnit())
                || !Objects.equals(expected.getReserveDelayDays(), actual.getReserveDelayDays())) {
            throw failure(ClearingFailureCodeEnum.FEE_VERSION_NOT_IMMUTABLE,
                    "immutable fee version rows contain mixed identities or terms");
        }
    }

    /**
     * 按数值而非小数位表示比较费用金额和费率。
     */
    private static boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    /**
     * 构造带稳定失败码的费用版本配置异常。
     */
    private static ClearingProcessingException failure(ClearingFailureCodeEnum code, String message) {
        return new ClearingProcessingException(code, message);
    }

    /**
     * 将可空 USD 原子费用转换为带固定指数的精确金额。
     */
    private static Money usd(BigDecimal amount) {
        return amount == null ? null : new Money(amount, USD, USD_EXPONENT);
    }

    /**
     * 规范化必填枚举文本，缺失值按费用版本损坏处理。
     */
    private static String requiredEnum(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 聚合同一费用规则及其按顺序展开的阶梯配置。
     */
    private static final class RuleAccumulator {

        /**
         * 代表当前费用规则身份和非阶梯条款的首条聚合行。
         */
        private final ClearingFeeVersionSnapshotRowDO rule;
        /**
         * JOIN 行展开出的阶梯条款，最终与首行规则一起冻结为计算快照。
         */
        private final List<FeeTierSnapshot> tiers = new ArrayList<>();

        /**
         * 以首条有效规则行创建聚合器，缺失规则主键时立即失败。
         */
        private RuleAccumulator(ClearingFeeVersionSnapshotRowDO rule) {
            if (rule.getFeeRuleId() == null || rule.getFeeRuleId() < 1) {
                throw failure(ClearingFailureCodeEnum.FEE_RULE_NOT_CONFIGURED,
                        "immutable fee version contains no usable rule");
            }
            this.rule = rule;
        }

        /**
         * 阶梯行必须连续归属于当前规则，且金额和边界口径由规则类型决定。
         */
        private void addTier(ClearingFeeVersionSnapshotRowDO row) {
            validateRuleTerms(row);
            if (row.getFeeTierId() != null) {
                tiers.add(new FeeTierSnapshot(
                        row.getFeeTierId(), row.getTierLowerBound(), row.getTierUpperBound(),
                        row.getTierPercentageRate(), usd(row.getTierFixedAmountUsd()),
                        usd(row.getTierMinimumAmountUsd()), usd(row.getTierMaximumAmountUsd())));
            }
        }

        /**
         * 百分比、USD 固定费和上下限必须满足非负及组合约束。
         */
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

        /**
         * 将已验证的规则和阶梯聚合为不可变费用计算快照。
         */
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

    /**
     * 用于识别同一费用版本内重复收费规则的业务维度组合。
     */
    private record RuleDimension(String feeCategory,
                                 String riskServiceType,
                                 String transactionType,
                                 String paymentType,
                                 String paymentMethod) {
    }
}
