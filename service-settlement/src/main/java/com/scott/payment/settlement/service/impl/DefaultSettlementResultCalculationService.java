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
import com.scott.payment.settlement.dto.SettlementCalculationPreview;
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
import com.scott.payment.settlement.support.SettlementReviewFingerprintService;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 财务计算统一 MathContext，约束中间计算精度并避免过早舍入。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    /**
     * {@code ITEM_INSERT_PAGE_SIZE}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int ITEM_INSERT_PAGE_SIZE = 500;
    /**
     * {@code SUMMARY_INSERT_PAGE_SIZE}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int SUMMARY_INSERT_PAGE_SIZE = 200;
    /**
     * {@code TRACE}常量，统一 {@code DefaultSettlementResultCalculationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与日志 MDC 和 X-Trace-Id 请求头共同串联一次链路。
     * </p>
     */
    private static final String TRACE = "TRACE";
    /**
     * {@code FINANCIAL_COMPONENT}常量，统一 {@code DefaultSettlementResultCalculationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String FINANCIAL_COMPONENT = "FINANCIAL_COMPONENT";
    /**
     * {@code NONE}常量，统一 {@code DefaultSettlementResultCalculationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String NONE = "NONE";

    private final SettlementBatchMapper batchMapper;
    private final SettlementResultMapper resultMapper;
    private final SettlementReviewFingerprintService fingerprintService;
    private final SettlementAmountCalculator amountCalculator = new SettlementAmountCalculator();
    private final SettlementFeeGroupCalculator feeGroupCalculator = new SettlementFeeGroupCalculator();

    public DefaultSettlementResultCalculationService(SettlementBatchMapper batchMapper,
                                                     SettlementResultMapper resultMapper) {
        this(batchMapper, resultMapper, new SettlementReviewFingerprintService());
    }

    @Autowired
    public DefaultSettlementResultCalculationService(SettlementBatchMapper batchMapper,
                                                     SettlementResultMapper resultMapper,
                                                     SettlementReviewFingerprintService fingerprintService) {
        this.batchMapper = batchMapper;
        this.resultMapper = resultMapper;
        this.fingerprintService = fingerprintService;
    }

    /**
     * 使用冻结清分事实和统一汇率执行纯预览计算，不写正式结果表或修改批次状态。
     *
     * @param batch 预审或正式批次的商户、账户、币种和类型维度
     * @param facts 完整交易及保证金清分事实
     * @param rates 已锁定统一直接汇率及持久化行映射
     * @param now 结果快照时间
     * @return 不可变结果明细、汇总和目标币种净额
     * @throws SettlementProcessingException 金额、币种、费用组、汇率或舍入事实不完整时抛出
     */
    @Override
    public SettlementCalculationPreview preview(SettlementBatchDO batch,
                                                SettlementBatchFacts facts,
                                                SettlementLockedRateMatrix rates,
                                                LocalDateTime now) {
        Objects.requireNonNull(batch, "settlement preview batch is required");
        Objects.requireNonNull(facts, "settlement preview facts are required");
        Objects.requireNonNull(rates, "settlement preview rates are required");
        Objects.requireNonNull(now, "settlement preview time is required");
        List<SettlementResultItemDO> items = calculate(batch, facts, rates, now);
        if (items.isEmpty()) {
            throw failure("SETTLEMENT_RESULT_EMPTY", false,
                    "settlement batch produced no financial or audit results");
        }
        List<SettlementResultSummaryDO> resultSummaries = summaries(items, now);
        return preview(batch, items, resultSummaries);
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
        if (StringUtils.hasText(batch.getResultFingerprint())) {
            String actualFingerprint = fingerprintService.result(
                    preview(batch, expectedItems, expectedSummaries));
            if (!batch.getResultFingerprint().equals(actualFingerprint)) {
                throw failure("SETTLEMENT_REVIEW_RESULT_FINGERPRINT_MISMATCH", false,
                        "formal settlement result differs from the approved review snapshot");
            }
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

    /** 从财务组件目标金额计算 CREDIT/DEBIT 净额，批次预审结果以该值做指纹和审批一致性校验。 */
    private SettlementCalculationPreview preview(SettlementBatchDO batch,
                                                 List<SettlementResultItemDO> items,
                                                 List<SettlementResultSummaryDO> resultSummaries) {
        BigDecimal signed = items.stream()
                .filter(item -> FINANCIAL_COMPONENT.equals(item.getResultRole()))
                .map(item -> "CREDIT".equals(item.getDirection())
                        ? item.getTargetAmount() : item.getTargetAmount().negate())
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, CALCULATION_CONTEXT));
        String direction = signed.signum() < 0 ? "DEBIT" : "CREDIT";
        BigDecimal amount = signed.abs().setScale(batch.getTargetCurrencyExponent());
        return new SettlementCalculationPreview(items, resultSummaries, direction, amount);
    }

    /** 按候选和清分明细稳定顺序生成结果；全程保留未舍入目标金额，最终才按目标 exponent 舍入。 */
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

    /** 对单个候选生成本金、费用 TRACE/最终值和保证金结果，禁止跨候选或跨币种隐式合并。 */
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

    /** 将清分本金按批次统一直接汇率换算为参与净额的 FINANCIAL_COMPONENT。 */
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

    /** 保存单个费用组件换算 TRACE；仅供审计，不重复参与批次净额。 */
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

    /** 保存费用组应用 USD 最低/最高限额后的唯一最终财务组件，避免组件和最终值重复扣费。 */
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

    /**
     * 对待结算评估费用组执行跨币种归并：百分比组件保留标签币种，固定费及上下限继续使用 USD，
     * 统一换算到目标币种后才应用限额和最终舍入。
     */
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

    /** 对清分阶段已最终确定的同币种费用组复核方向、金额和限额身份，不二次应用模板规则。 */
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

    /** 将 HOLD、RETURN、RELEASE 或 ADJUSTMENT 原标签币种金额换算为结算结果，不改变保证金聚合原币种。 */
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

    /** 构造不可变结果项，同时冻结来源/目标币种精度、汇率行、舍入模式和公式快照。 */
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

    /** 使用批次归一直接汇率和 DECIMAL128 计算未舍入值，再按目标币种 exponent 执行指定舍入。 */
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

    /** 仅聚合 FINANCIAL_COMPONENT，并按支付/交易/费用/方向及来源和目标币种完整分组。 */
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

    /** 从单一完整分组累加器生成不可变结果汇总，交易数按候选主键去重。 */
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

    /** 分页追加不可变结果明细，数据库唯一键承担最终幂等。 */
    private void insertItems(List<SettlementResultItemDO> rows) {
        for (int start = 0; start < rows.size(); start += ITEM_INSERT_PAGE_SIZE) {
            resultMapper.insertItemsIdempotent(rows.subList(start,
                    Math.min(start + ITEM_INSERT_PAGE_SIZE, rows.size())));
        }
    }

    /** 分页追加不可变结果汇总，数据库业务维度唯一键承担最终幂等。 */
    private void insertSummaries(List<SettlementResultSummaryDO> rows) {
        for (int start = 0; start < rows.size(); start += SUMMARY_INSERT_PAGE_SIZE) {
            resultMapper.insertSummariesIdempotent(rows.subList(start,
                    Math.min(start + SUMMARY_INSERT_PAGE_SIZE, rows.size())));
        }
    }

    /** 对幂等回读的结果明细逐业务身份和金额核对，拒绝唯一键碰撞或部分写入。 */
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

    /** 比较结果项全部资金身份、未舍入/舍入金额、精度、汇率和公式快照。 */
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

    /** 对幂等回读的汇总逐完整分组和金额核对，拒绝跨币种或部分写入。 */
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

    /** 构造包含来源/目标币种在内的完整汇总键，禁止多币种使用同一累加器。 */
    private SummaryKey summaryKey(SettlementResultSummaryDO row) {
        return new SummaryKey(row.getSettlementBatchNo(), row.getMerchantId(), row.getPaymentType(),
                row.getPaymentMethod(), row.getTransactionType(), row.getResultItemType(),
                row.getFeeCategory(), row.getDirection(), row.getSourceCurrency(), row.getTargetCurrency());
    }

    /** 校验费用组内商户、支付维度、方向、标签币种、USD 限额和舍入模式一致。 */
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

    /** 从费用组读取可空公共金额字段；不同组件出现不一致非空值时阻断结算。 */
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

    /** 将固定单笔费或最低/最高限额的 USD 金额使用同一批次 USD 锁定汇率换算为目标币种。 */
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

    /** 从保证金清分行提取与动作类型唯一对应的非负原标签币种金额。 */
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

    /** 按交易号、分片时间和清分修订精确定位候选，防止不同修订串单。 */
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

    /** 将清分冻结舍入模式转换为枚举，缺失或未知值时拒绝结算。 */
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

    /** 将清分限额结果转换为领域枚举，缺失时使用 NONE，未知值时拒绝结算。 */
    private AppliedLimit appliedLimit(String value) {
        try {
            return AppliedLimit.valueOf(value);
        } catch (RuntimeException exception) {
            throw failure("SETTLEMENT_APPLIED_LIMIT_INVALID", false,
                    "clearing fee applied limit is unsupported");
        }
    }

    /** 要求批次处于可计算状态且当前处理租约仍归 owner 所有并未过期。 */
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

    /**
     * 按金额数值而非 BigDecimal scale 比较结算结果，避免 1.0 与 1.00 被误判为资金差异。
     *
     * @param left 左侧金额，可为空
     * @param right 右侧金额，可为空
     * @return 两侧同时为空或数值相等时返回 true
     */
    private boolean amountEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private String valueOrNone(String value) {
        return StringUtils.hasText(value) ? value : NONE;
    }

    /** 以批次号、候选和结果行号生成稳定结果项号，确保事务重放身份一致。 */
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

        /**
         * 按完整业务分组维度提供确定性排序，保证汇总和指纹在重放时稳定。
         *
         * @param other 另一完整汇总维度键
         * @return 按所有维度串联后的稳定字典序比较结果
         */
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
        /** 当前完整分组已计入的候选主键，用于防止交易笔数重复。 */
        private final Set<Long> candidateIds = new HashSet<>();
        /** 来源币种未提前舍入的累计金额，使用 DECIMAL128 中间精度。 */
        private BigDecimal sourceAmount = BigDecimal.ZERO;
        /** 目标结算币种未提前舍入的累计金额，使用 DECIMAL128 中间精度。 */
        private BigDecimal targetAmount = BigDecimal.ZERO;

        /** 累加同一完整分组的来源/目标金额，并按候选主键去重统计交易数。 */
        private void add(Long candidateId, BigDecimal source, BigDecimal target) {
            candidateIds.add(candidateId);
            sourceAmount = sourceAmount.add(source, CALCULATION_CONTEXT);
            targetAmount = targetAmount.add(target, CALCULATION_CONTEXT);
        }
    }
}
