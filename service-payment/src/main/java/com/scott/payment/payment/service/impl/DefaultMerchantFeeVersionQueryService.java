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
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantFeeVersionQueryService
 * @date : 2026-08-25 22:40
 * @email : scott_x@163.com
 * @description : Payment 费用版本只读实现，通过 REQUIRES_NEW 暂停交易分片事务并用一次 JOIN 聚合现有商户费用规则，禁止更新费用配置。
 * @status : create
 */
@Service
public class DefaultMerchantFeeVersionQueryService implements MerchantFeeVersionQueryService {

    private static final String USD = "USD";
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

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public MerchantFeeVersionPointerDO findActivePointerFromMaster(String merchantId) {
        return snapshotMapper.selectActivePointer(merchantId);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public MerchantFeeVersionConfigurationDTO findVersionFromSlave(String merchantId,
                                                                   Long feePlanId,
                                                                   Long feePlanVersionId) {
        return toConfiguration(snapshotMapper.selectVersionRows(merchantId, feePlanId, feePlanVersionId));
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public MerchantFeeVersionConfigurationDTO findVersionFromMaster(String merchantId,
                                                                    Long feePlanId,
                                                                    Long feePlanVersionId) {
        return toConfiguration(snapshotMapper.selectVersionRows(merchantId, feePlanId, feePlanVersionId));
    }

    /** 将一次 JOIN 的稳定行序组装为不可变费用版本，不进行金额计算或汇率读取。 */
    private MerchantFeeVersionConfigurationDTO toConfiguration(List<MerchantFeeVersionSnapshotRowDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        try {
            MerchantFeeVersionSnapshotRowDO first = rows.get(0);
            Map<Long, RuleAccumulator> rules = new LinkedHashMap<>();
            for (MerchantFeeVersionSnapshotRowDO row : rows) {
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
                    first.getReserveRate(),
                    first.getReserveDelayUnit(),
                    requirePositive(first.getReserveDelayDays(), "reserve delay days"),
                    snapshots);
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND.getCode(),
                    "Merchant active fee configuration is incomplete", exception);
        }
    }

    /** 防止异常 JOIN 行把其他商户或版本内容混入同一快照。 */
    private void validateVersionIdentity(MerchantFeeVersionSnapshotRowDO expected,
                                         MerchantFeeVersionSnapshotRowDO actual) {
        if (!java.util.Objects.equals(expected.getMerchantId(), actual.getMerchantId())
                || !java.util.Objects.equals(expected.getFeePlanId(), actual.getFeePlanId())
                || !java.util.Objects.equals(expected.getFeePlanVersionId(), actual.getFeePlanVersionId())
                || !java.util.Objects.equals(expected.getFeePlanVersionNo(), actual.getFeePlanVersionNo())) {
            throw new IllegalArgumentException("fee version rows contain mixed identities");
        }
    }

    private static int requirePositive(Integer value, String fieldName) {
        if (value == null || value < 1) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static FeeMode feeMode(String value) {
        return FeeMode.valueOf(requiredEnum(value, "fee mode"));
    }

    private static TierMetric tierMetric(String value) {
        return value == null || value.isBlank() ? null
                : TierMetric.valueOf(requiredEnum(value, "tier metric"));
    }

    private static String requiredEnum(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static Money usd(BigDecimal amount) {
        return amount == null ? null : new Money(amount, USD, USD_EXPONENT);
    }

    /** 单条费用规则及其有序阶梯的组装状态。 */
    private static final class RuleAccumulator {

        private final MerchantFeeVersionSnapshotRowDO rule;
        private final List<FeeTierSnapshot> tiers = new ArrayList<>();

        private RuleAccumulator(MerchantFeeVersionSnapshotRowDO rule) {
            if (rule.getFeeRuleId() == null || rule.getFeeRuleId() < 1) {
                throw new IllegalArgumentException("fee rule id must be positive");
            }
            this.rule = rule;
        }

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

        private FeeRuleConfigurationSnapshot toSnapshot() {
            FeeMode mode = feeMode(rule.getFeeMode());
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
