package com.scott.payment.settlement.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.domain.model.SettlementFailureStage;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementCurrency;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementBatchRateDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementBatchRateMapper;
import com.scott.payment.settlement.service.SettlementBatchRateLockService;
import com.scott.payment.settlement.service.SettlementRateResolutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementBatchRateLockService
 * @date : 2026-08-26 23:20
 * @email : scott_x@163.com
 * @description : 在批次行锁事务内完整写入或复用汇率矩阵，逐项回读后才以租约和 version CAS 标记 RATE_LOCKED。
 * @status : create
 */
@Service
public class DefaultSettlementBatchRateLockService implements SettlementBatchRateLockService {

    /**
     * 汇率类型，用于区分 {@code DefaultSettlementBatchRateLockService} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RATE_TYPE = "SETTLEMENT";
    /**
     * 汇率状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String RATE_STATUS = "LOCKED";

    private final SettlementBatchMapper batchMapper;
    private final SettlementBatchRateMapper rateMapper;
    private final SettlementRateResolutionService resolutionService;

    public DefaultSettlementBatchRateLockService(SettlementBatchMapper batchMapper,
                                                 SettlementBatchRateMapper rateMapper,
                                                 SettlementRateResolutionService resolutionService) {
        this.batchMapper = batchMapper;
        this.rateMapper = rateMapper;
        this.resolutionService = resolutionService;
    }

    /**
     * 已存在任意汇率行时不再查询新报价；只有完整矩阵才允许继续，避免一次批次混用多个估值时点。
     *
     * @param leasedBatch 已取得处理租约的批次
     * @param facts 本批全部清分事实
     * @param owner 当前租约所有者
     * @param now 统一锁定时间
     * @return 已锁定矩阵
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(rollbackFor = Exception.class)
    public SettlementLockedRateMatrix lockOrLoad(SettlementBatchDO leasedBatch,
                                                 SettlementBatchFacts facts,
                                                 String owner,
                                                 LocalDateTime now) {
        Objects.requireNonNull(leasedBatch, "leased settlement batch is required");
        Objects.requireNonNull(facts, "settlement batch facts are required");
        Objects.requireNonNull(now, "settlement rate lock time is required");
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("settlement processing owner is required");
        }
        SettlementBatchDO batch = batchMapper.selectByBatchNoForUpdate(leasedBatch.getSettlementBatchNo());
        validateLease(batch, owner.trim(), now);

        List<SettlementBatchRateDO> stored = safe(rateMapper.selectByBatchNo(batch.getSettlementBatchNo()));
        if (stored.isEmpty()) {
            RateMatrix resolved = resolve(facts.currencies(), batch, now);
            rateMapper.insertBatchIdempotent(rows(batch, resolved, owner.trim(), now));
            stored = safe(rateMapper.selectByBatchNo(batch.getSettlementBatchNo()));
        }
        SettlementLockedRateMatrix locked = validateAndConvert(batch, facts.currencies(), stored);
        SettlementBatchStatus status = status(batch.getBatchStatus());
        if (status == SettlementBatchStatus.CLAIMED || status == SettlementBatchStatus.FAILED_RETRYABLE) {
            if (batchMapper.markRateLocked(batch.getSettlementBatchNo(), owner.trim(),
                    requireVersion(batch), now) != 1) {
                throw failure("SETTLEMENT_RATE_STATE_CAS_FAILED", true,
                        "settlement batch rate lock state CAS failed");
            }
            batch.setBatchStatus(SettlementBatchStatus.RATE_LOCKED.name());
            batch.setRateLockedTime(now);
            batch.setVersion(batch.getVersion() + 1);
        } else if (status != SettlementBatchStatus.RATE_LOCKED
                && status != SettlementBatchStatus.CALCULATING) {
            throw failure("SETTLEMENT_RATE_STATE_INVALID", false,
                    "settlement batch status does not allow rate reuse");
        }
        copyMutableState(batch, leasedBatch);
        return locked;
    }

    /** 调用统一报价解析服务生成完整来源币种到目标币种的归一直接汇率矩阵。 */
    private RateMatrix resolve(Set<SettlementCurrency> currencies,
                               SettlementBatchDO batch,
                               LocalDateTime now) {
        try {
            return resolutionService.resolve(currencies, batch.getTargetCurrency(),
                    batch.getTargetCurrencyExponent(), now);
        } catch (IllegalStateException exception) {
            throw failure("SETTLEMENT_RATE_MISSING", true,
                    "one or more settlement currency pairs have no effective rate");
        } catch (IllegalArgumentException exception) {
            throw failure("SETTLEMENT_RATE_QUOTE_INVALID", false,
                    "settlement rate quote or currency exponent is invalid");
        }
    }

    /** 将纯计算汇率矩阵映射为批次不可变行，冻结精度、来源、报价方向和锁定时间。 */
    private List<SettlementBatchRateDO> rows(SettlementBatchDO batch,
                                             RateMatrix matrix,
                                             String owner,
                                             LocalDateTime now) {
        List<SettlementBatchRateDO> rows = new ArrayList<>(matrix.rates().size());
        for (LockedRate rate : matrix.rates()) {
            SettlementBatchRateDO row = new SettlementBatchRateDO();
            row.setSettlementBatchNo(batch.getSettlementBatchNo());
            row.setSourceCurrency(rate.pair().sourceCurrency());
            row.setTargetCurrency(rate.pair().targetCurrency());
            row.setRateType(RATE_TYPE);
            row.setDirectRate(rate.directRate());
            row.setSourceCurrencyExponent(rate.sourceCurrencyExponent());
            row.setTargetCurrencyExponent(rate.targetCurrencyExponent());
            row.setRateSource(rate.rateSource());
            row.setQuoteId(rate.quoteId());
            row.setSourceQuoteDirection(rate.sourceQuoteDirection().name());
            row.setEffectiveTime(rate.effectiveTime());
            row.setLockedTime(now);
            row.setLockedBy(owner);
            row.setRateStatus(RATE_STATUS);
            row.setCreateTime(now);
            rows.add(row);
        }
        return rows;
    }

    /** 校验持久化汇率矩阵覆盖全部币种且方向/精度/目标一致，并建立来源币种到行 ID 映射。 */
    private SettlementLockedRateMatrix validateAndConvert(SettlementBatchDO batch,
                                                           Set<SettlementCurrency> currencies,
                                                           List<SettlementBatchRateDO> stored) {
        Map<String, SettlementCurrency> expected = currencies.stream().collect(Collectors.toMap(
                SettlementCurrency::currency, Function.identity(), (left, right) -> {
                    if (left.exponent() != right.exponent()) {
                        throw failure("SETTLEMENT_RATE_EXPONENT_CONFLICT", false,
                                "settlement currency exponent is inconsistent");
                    }
                    return left;
                }));
        if (stored.size() != expected.size()) {
            throw failure("SETTLEMENT_RATE_MATRIX_PARTIAL", false,
                    "existing settlement rate matrix is incomplete and cannot be supplemented");
        }
        List<LockedRate> rates = new ArrayList<>(stored.size());
        Map<String, Long> ids = new LinkedHashMap<>();
        for (SettlementBatchRateDO row : stored) {
            SettlementCurrency source = row == null ? null : expected.get(row.getSourceCurrency());
            if (source == null || row.getId() == null || row.getId() <= 0
                    || !Objects.equals(row.getSettlementBatchNo(), batch.getSettlementBatchNo())
                    || !Objects.equals(row.getTargetCurrency(), batch.getTargetCurrency())
                    || !Objects.equals(row.getSourceCurrencyExponent(), source.exponent())
                    || !Objects.equals(row.getTargetCurrencyExponent(), batch.getTargetCurrencyExponent())
                    || !RATE_TYPE.equals(row.getRateType()) || !RATE_STATUS.equals(row.getRateStatus())
                    || row.getDirectRate() == null || row.getDirectRate().signum() <= 0
                    || row.getEffectiveTime() == null || row.getRateSource() == null
                    || row.getSourceQuoteDirection() == null || ids.putIfAbsent(
                            row.getSourceCurrency(), row.getId()) != null) {
                throw failure("SETTLEMENT_RATE_MATRIX_CONFLICT", false,
                        "stored settlement rate matrix identity is inconsistent");
            }
            try {
                rates.add(new LockedRate(
                        new CurrencyPair(row.getSourceCurrency(), row.getTargetCurrency()),
                        row.getDirectRate(), row.getSourceCurrencyExponent(),
                        row.getTargetCurrencyExponent(), row.getRateSource(), row.getQuoteId(),
                        QuoteDirection.valueOf(row.getSourceQuoteDirection()), row.getEffectiveTime()));
            } catch (RuntimeException exception) {
                throw failure("SETTLEMENT_RATE_MATRIX_CONFLICT", false,
                        "stored settlement rate matrix value is invalid");
            }
        }
        if (!ids.keySet().equals(expected.keySet())) {
            throw failure("SETTLEMENT_RATE_MATRIX_PARTIAL", false,
                    "existing settlement rate matrix does not cover all source currencies");
        }
        return new SettlementLockedRateMatrix(RateMatrix.of(rates), ids);
    }

    /** 要求批次处于可锁汇率状态且处理租约归 owner 所有并未过期。 */
    private void validateLease(SettlementBatchDO batch, String owner, LocalDateTime now) {
        if (batch == null || batch.getVersion() == null
                || !owner.equals(batch.getProcessingOwner())
                || batch.getProcessingDeadline() == null
                || !batch.getProcessingDeadline().isAfter(now)) {
            throw failure("SETTLEMENT_PROCESSING_LEASE_LOST", true,
                    "settlement batch processing lease is unavailable or expired");
        }
    }

    /** 从锁读批次取得非空 version，作为汇率状态 CAS 前置条件。 */
    private long requireVersion(SettlementBatchDO batch) {
        if (batch.getVersion() == null) {
            throw failure("SETTLEMENT_BATCH_VERSION_MISSING", false,
                    "settlement batch version is missing");
        }
        return batch.getVersion();
    }

    /** 将数据库批次状态解析为枚举，拒绝未知状态值。 */
    private SettlementBatchStatus status(String value) {
        try {
            return SettlementBatchStatus.valueOf(value);
        } catch (RuntimeException exception) {
            throw failure("SETTLEMENT_BATCH_STATUS_INVALID", false,
                    "settlement batch status is unsupported");
        }
    }

    /** 将数据库 CAS 后的状态、版本和锁定时间同步回调用方批次快照。 */
    private void copyMutableState(SettlementBatchDO source, SettlementBatchDO target) {
        target.setBatchStatus(source.getBatchStatus());
        target.setRateLockedTime(source.getRateLockedTime());
        target.setVersion(source.getVersion());
        target.setProcessingOwner(source.getProcessingOwner());
        target.setProcessingDeadline(source.getProcessingDeadline());
    }

    private List<SettlementBatchRateDO> safe(List<SettlementBatchRateDO> rows) {
        return rows == null ? List.of() : List.copyOf(rows);
    }

    private SettlementProcessingException failure(String code, boolean retryable, String message) {
        return new SettlementProcessingException(
                SettlementFailureStage.RATE_LOCKING, code, retryable, message);
    }
}
