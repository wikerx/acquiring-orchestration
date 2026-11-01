package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeTierSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierMetric;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.payment.entity.MerchantFeeVersionPointerDO;
import com.scott.payment.payment.entity.MerchantFeeVersionSnapshotRowDO;
import com.scott.payment.payment.mapper.MerchantFeeVersionSnapshotMapper;
import com.scott.payment.payment.service.MerchantFeeVersionQueryService;
import com.scott.payment.payment.service.dto.MerchantFeeVersionConfigurationDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 商户费用版本的只读查询与组装服务。
 *
 * <p>查询使用独立只读事务，避免交易分片事务的数据源上下文污染费用库。ACTIVE 指针从主库读取，
 * 明确版本可先查从库再由上层按同一版本回主库。JOIN 行只负责组装规则和阶梯，不在此处计算费用、
 * 汇率或舍入结果。</p>
 */
@Service
public class DefaultMerchantFeeVersionQueryService implements MerchantFeeVersionQueryService {

    /**
     * 固定费及上下限字段当前按 USD 金额建模。
     */
    private static final String USD = "USD";
    /**
     * USD 法定辅币位数，用于构造 Money 值对象，不用于提前舍入数据库费率。
     */
    private static final int USD_EXPONENT = 2;

    private final MerchantFeeVersionSnapshotMapper snapshotMapper;

    /**
     * 创建费用版本只读实现。
     *
     * @param snapshotMapper 现有费用表只读聚合 Mapper
     */
    public DefaultMerchantFeeVersionQueryService(MerchantFeeVersionSnapshotMapper snapshotMapper) {
        this.snapshotMapper = snapshotMapper;
    }

    /**
     * 从主库读取商户当前生效的费用方案版本指针。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public MerchantFeeVersionPointerDO findActivePointerFromMaster(String merchantId) {
        return snapshotMapper.selectActivePointer(merchantId);
    }

    /**
     * 从从库读取指定费用方案版本的不可变配置明细。
     */
    @Override
    @DS(DataSourceName.SLAVE)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public MerchantFeeVersionConfigurationDTO findVersionFromSlave(String merchantId,
                                                                   Long feePlanId,
                                                                   Long feePlanVersionId) {
        return toConfiguration(snapshotMapper.selectVersionRows(merchantId, feePlanId, feePlanVersionId));
    }

    /**
     * 从主库读取指定费用方案版本的不可变配置明细，用于复制延迟回源。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public MerchantFeeVersionConfigurationDTO findVersionFromMaster(String merchantId,
                                                                    Long feePlanId,
                                                                    Long feePlanVersionId) {
        return toConfiguration(snapshotMapper.selectVersionRows(merchantId, feePlanId, feePlanVersionId));
    }

    /**
     * 将一次 JOIN 的稳定行序组装为不可变费用版本，不进行金额计算或汇率读取。
     */
    private MerchantFeeVersionConfigurationDTO toConfiguration(List<MerchantFeeVersionSnapshotRowDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        try {
            MerchantFeeVersionSnapshotRowDO first = rows.get(0);
            Map<Long, RuleAccumulator> rules = new LinkedHashMap<>();
            for (MerchantFeeVersionSnapshotRowDO row : rows) {
                // 一次 JOIN 的每一行必须属于同一商户和版本，防止错误关联产生跨版本费用快照。
                validateVersionIdentity(first, row);
                RuleAccumulator accumulator = rules.computeIfAbsent(row.getFeeRuleId(),
                        ignored -> new RuleAccumulator(row));
                accumulator.addTier(row);
            }
            List<FeeRuleConfigurationSnapshot> snapshots = rules.values().stream()
                    .map(RuleAccumulator::toSnapshot)
                    .toList();
            if (snapshots.isEmpty()) {
                throw new IllegalArgumentException("fee rules are required");
            }
            return new MerchantFeeVersionConfigurationDTO(
                    first.getMerchantId(),
                    first.getFeePlanId(),
                    first.getFeePlanVersionId(),
                    requirePositive(first.getFeePlanVersionNo(), "fee version number"),
                    first.getSettlementCurrency(),
                    requiredEnum(first.getInitialDelayUnit(), "settlement delay unit"),
                    requirePositive(first.getInitialDelayDays(), "initial settlement delay days"),
                    requirePositive(first.getRegularDelayDays(), "regular settlement delay days"),
                    requiredEnum(first.getSettlementFrequency(), "settlement frequency"),
                    first.getFrequencyDay(),
                    java.util.Objects.requireNonNull(first.getEffectiveTime(),
                            "fee version effective time is required").toLocalDate(),
                    first.getReserveRate(),
                    first.getReserveDelayUnit(),
                    requirePositive(first.getReserveDelayDays(), "reserve delay days"),
                    snapshots);
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND.getCode(),
                    "Merchant active fee configuration is incomplete", exception);
        }
    }

    /**
     * 防止异常 JOIN 行把其他商户或版本内容混入同一快照。
     */
    private void validateVersionIdentity(MerchantFeeVersionSnapshotRowDO expected,
                                         MerchantFeeVersionSnapshotRowDO actual) {
        if (!java.util.Objects.equals(expected.getMerchantId(), actual.getMerchantId())
                || !java.util.Objects.equals(expected.getFeePlanId(), actual.getFeePlanId())
                || !java.util.Objects.equals(expected.getFeePlanVersionId(), actual.getFeePlanVersionId())
                || !java.util.Objects.equals(expected.getFeePlanVersionNo(), actual.getFeePlanVersionNo())) {
            throw new IllegalArgumentException("fee version rows contain mixed identities");
        }
    }

    /**
     * 校验并获取正值。
     */
    private static int requirePositive(Integer value, String fieldName) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    /**
     * 解析商户费用计算模式。
     */
    private static FeeMode feeMode(String value) {
        return FeeMode.valueOf(requiredEnum(value, "fee mode"));
    }

    /**
     * 解析阶梯计费指标。
     */
    private static TierMetric tierMetric(String value) {
        return value == null || value.isBlank() ? null
                : TierMetric.valueOf(requiredEnum(value, "tier metric"));
    }

    /**
     * 读取并校验必填枚举配置。
     */
    private static String requiredEnum(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 创建美元金额值。
     */
    private static Money usd(BigDecimal amount) {
        return amount == null ? null : new Money(amount, USD, USD_EXPONENT);
    }

    /**
     * 单条费用规则及其有序阶梯的组装状态。
     */
    private static final class RuleAccumulator {

        /**
         * 当前规则的公共字段，取自该规则第一条 JOIN 行。
         */
        private final MerchantFeeVersionSnapshotRowDO rule;
        /**
         * SQL 稳定顺序下收集的阶梯，顺序直接参与后续费用规则匹配。
         */
        private final List<FeeTierSnapshot> tiers = new ArrayList<>();

        /**
         * 创建单条费用规则的组装器并校验规则主键。
         *
         * @param rule 当前费用规则第一条有序 JOIN 行
         */
        private RuleAccumulator(MerchantFeeVersionSnapshotRowDO rule) {
            if (rule.getFeeRuleId() == null || rule.getFeeRuleId() < 1) {
                throw new IllegalArgumentException("fee rule id must be positive");
            }
            this.rule = rule;
        }

        /**
         * 向费用配置中添加阶梯规则。
         */
        private void addTier(MerchantFeeVersionSnapshotRowDO row) {
            if (row.getFeeTierId() == null) {
                return;
            }
            tiers.add(new FeeTierSnapshot(
                    row.getFeeTierId(),
                    row.getTierLowerBound(),
                    row.getTierUpperBound(),
                    row.getTierPercentageRate(),
                    usd(row.getTierFixedAmountUsd()),
                    usd(row.getTierMinimumAmountUsd()),
                    usd(row.getTierMaximumAmountUsd())));
        }

        /**
         * 转换为商户交易快照。
         */
        private FeeRuleConfigurationSnapshot toSnapshot() {
            FeeMode mode = feeMode(rule.getFeeMode());
            // STANDARD 与 TIER 的结构必须互斥，配置不完整时 Fail Closed，禁止猜测计费方式。
            if (mode == FeeMode.STANDARD && !tiers.isEmpty()) {
                throw new IllegalArgumentException("standard fee rule must not contain tiers");
            }
            if (mode == FeeMode.TIER && tiers.isEmpty()) {
                throw new IllegalArgumentException("tier fee rule requires tiers");
            }
            FeeRuleSnapshot calculationRule = new FeeRuleSnapshot(
                    rule.getFeeRuleId(),
                    mode,
                    rule.getPercentageRate(),
                    usd(rule.getFixedAmountUsd()),
                    usd(rule.getMinimumAmountUsd()),
                    usd(rule.getMaximumAmountUsd()),
                    tierMetric(rule.getTierMetric()));
            return new FeeRuleConfigurationSnapshot(
                    rule.getFeeRuleId(),
                    rule.getFeeCategory(),
                    rule.getTransactionType(),
                    rule.getPaymentType(),
                    rule.getPaymentMethod(),
                    rule.getRiskServiceType(),
                    rule.getChargeTrigger(),
                    calculationRule,
                    tiers);
        }
    }
}
