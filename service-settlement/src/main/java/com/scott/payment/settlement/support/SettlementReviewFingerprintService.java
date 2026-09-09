package com.scott.payment.settlement.support;

import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementCalculationPreview;
import com.scott.payment.settlement.dto.SettlementReviewCreateCommand;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementReserveClearingDetailDO;
import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.entity.SettlementResultSummaryDO;
import com.scott.payment.settlement.entity.SettlementReviewRateDO;
import com.scott.payment.settlement.entity.SettlementTransactionClearingDetailDO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewFingerprintService
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 为预审候选选择、清分事实、统一汇率矩阵和财务结果生成稳定 SHA-256 指纹；金额使用 plain string，集合排序后再编码。
 * @status : create
 */
@Component
public final class SettlementReviewFingerprintService {

    /**
     * 对按候选 ID 排序后的候选及期望版本生成选择指纹。
     *
     * @param candidates 用户提交的候选引用集合
     * @return 带版本前缀的稳定 SHA-256 十六进制摘要
     */
    public String selection(List<SettlementReviewCreateCommand.CandidateReference> candidates) {
        Canonical value = new Canonical("selection-v1");
        candidates.stream().sorted(Comparator.comparing(
                        SettlementReviewCreateCommand.CandidateReference::candidateId))
                .forEach(row -> value.add(row.candidateId()).add(row.expectedVersion()));
        return value.digest();
    }

    /**
     * 对完整候选、交易清分事实和保证金清分事实生成来源指纹。
     *
     * @param facts 预审冻结的完整清分事实
     * @return 稳定 SHA-256 十六进制摘要
     */
    public String source(SettlementBatchFacts facts) {
        Canonical value = new Canonical("source-v1");
        appendCandidates(value, facts.candidates());
        appendTransactionFacts(value, facts.transactionDetails(), null);
        appendReserveFacts(value, facts.reserveDetails(), null);
        return value.digest();
    }

    /**
     * 对单个候选及其精确路由到的交易/保证金事实生成关系指纹。
     *
     * @param facts 预审冻结的完整清分事实
     * @param candidate 需要生成关系指纹的候选
     * @return 稳定 SHA-256 十六进制摘要
     */
    public String candidateSource(SettlementBatchFacts facts, SettlementCandidateDO candidate) {
        Canonical value = new Canonical("candidate-source-v1");
        appendCandidate(value, candidate);
        Route route = new Route(candidate.getSourceTransactionId(),
                candidate.getSourceTransactionDateTime(), candidate.getSourceRevision());
        appendTransactionFacts(value, facts.transactionDetails(), route);
        appendReserveFacts(value, facts.reserveDetails(), route);
        return value.digest();
    }

    /**
     * 按来源币种排序后对归一直接汇率、精度、来源和生效时间生成汇率指纹。
     *
     * @param rates 预审冻结汇率矩阵行
     * @return 稳定 SHA-256 十六进制摘要
     */
    public String rates(List<SettlementReviewRateDO> rates) {
        Canonical value = new Canonical("rate-v1");
        rates.stream().sorted(Comparator.comparing(SettlementReviewRateDO::getSourceCurrency))
                .forEach(row -> value.add(row.getSourceCurrency()).add(row.getTargetCurrency())
                        .add(row.getDirectRate()).add(row.getSourceCurrencyExponent())
                        .add(row.getTargetCurrencyExponent()).add(row.getRateSource())
                        .add(row.getQuoteId()).add(row.getSourceQuoteDirection())
                        .add(row.getEffectiveTime()));
        return value.digest();
    }

    /**
     * 对排序后的结果明细、汇总和净额生成财务结果指纹。
     *
     * @param preview 预审计算快照
     * @return 稳定 SHA-256 十六进制摘要
     */
    public String result(SettlementCalculationPreview preview) {
        Canonical value = new Canonical("result-v1");
        preview.items().stream().sorted(Comparator
                        .comparing(SettlementResultItemDO::getCandidateId,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SettlementResultItemDO::getResultLineNo))
                .forEach(row -> appendResult(value, row));
        preview.summaries().stream().sorted(Comparator
                        .comparing(SettlementResultSummaryDO::getPaymentType)
                        .thenComparing(SettlementResultSummaryDO::getPaymentMethod)
                        .thenComparing(SettlementResultSummaryDO::getTransactionType)
                        .thenComparing(SettlementResultSummaryDO::getResultItemType)
                        .thenComparing(SettlementResultSummaryDO::getFeeCategory)
                        .thenComparing(SettlementResultSummaryDO::getDirection)
                        .thenComparing(SettlementResultSummaryDO::getSourceCurrency))
                .forEach(row -> value.add(row.getMerchantId()).add(row.getPaymentType())
                        .add(row.getPaymentMethod()).add(row.getTransactionType())
                        .add(row.getResultItemType()).add(row.getFeeCategory()).add(row.getDirection())
                        .add(row.getSourceCurrency()).add(row.getTargetCurrency())
                        .add(row.getTransactionCount()).add(row.getSourceAmount()).add(row.getTargetAmount()));
        value.add(preview.netDirection()).add(preview.netAmount());
        return value.digest();
    }

    /** 按候选主键稳定排序后写入规范化指纹，隔离数据库返回顺序差异。 */
    private void appendCandidates(Canonical value, List<SettlementCandidateDO> candidates) {
        candidates.stream().sorted(Comparator.comparing(SettlementCandidateDO::getId))
                .forEach(row -> appendCandidate(value, row));
    }

    /** 将影响候选身份、归属、目标币种和可结算日的字段完整写入指纹。 */
    private void appendCandidate(Canonical value, SettlementCandidateDO row) {
        value.add(row.getId()).add(row.getCandidateNo()).add(row.getSourceType())
                .add(row.getSourceBusinessId()).add(row.getSourceRevision())
                .add(row.getSourceTransactionId()).add(row.getSourceTransactionDateTime())
                .add(row.getMerchantId()).add(row.getSettlementProfileId())
                .add(row.getTargetCurrency()).add(row.getTargetCurrencyExponent())
                .add(row.getSettlementEligibleDate()).add(row.getShadowMode());
    }

    /**
     * 按交易时间、交易号、修订、行号和明细号稳定排序后写入交易清分金额与费用约束。
     *
     * <p>不包含展示字段，任何影响金额、币种、限额、舍入或公式的事实都必须进入指纹。</p>
     */
    private void appendTransactionFacts(Canonical value,
                                        List<SettlementTransactionClearingDetailDO> rows,
                                        Route onlyRoute) {
        rows.stream().filter(row -> onlyRoute == null || onlyRoute.matches(row))
                .sorted(Comparator.comparing(SettlementTransactionClearingDetailDO::getTransactionDateTime)
                        .thenComparing(SettlementTransactionClearingDetailDO::getTransactionId)
                        .thenComparing(SettlementTransactionClearingDetailDO::getClearingRevision)
                        .thenComparing(SettlementTransactionClearingDetailDO::getLineNo)
                        .thenComparing(SettlementTransactionClearingDetailDO::getClearingDetailNo))
                .forEach(row -> value.add(row.getClearingDetailNo()).add(row.getFinanceStateId())
                        .add(row.getTransactionId()).add(row.getOperationId()).add(row.getSourceTransactionId())
                        .add(row.getMerchantId()).add(row.getPaymentType()).add(row.getPaymentMethod())
                        .add(row.getTransactionType()).add(row.getClearingRevision()).add(row.getLineNo())
                        .add(row.getItemType()).add(row.getFeeCategory()).add(row.getDirection())
                        .add(row.getLabelCurrency()).add(row.getLabelAmount()).add(row.getLabelCurrencyExponent())
                        .add(row.getFeeGroupNo()).add(row.getComponentNo()).add(row.getComponentType())
                        .add(row.getAmount()).add(row.getCurrency()).add(row.getCurrencyExponent())
                        .add(row.getMinimumAmountUsd()).add(row.getMaximumAmountUsd())
                        .add(row.getLimitEvaluationStatus()).add(row.getAppliedLimit())
                        .add(row.getRoundingMode()).add(row.getFormulaSnapshot())
                        .add(row.getRecordStatus()).add(row.getTransactionDateTime()));
    }

    /**
     * 按交易分片和明细身份稳定排序后写入保证金原标签币种责任事实。
     *
     * <p>RESERVE_RELEASE 和 ADJUSTMENT 只参与资金指纹，不因此生成伪交易投影。</p>
     */
    private void appendReserveFacts(Canonical value,
                                    List<SettlementReserveClearingDetailDO> rows,
                                    Route onlyRoute) {
        rows.stream().filter(row -> onlyRoute == null || onlyRoute.matches(row))
                .sorted(Comparator.comparing(SettlementReserveClearingDetailDO::getTransactionDateTime)
                        .thenComparing(SettlementReserveClearingDetailDO::getTransactionId)
                        .thenComparing(SettlementReserveClearingDetailDO::getClearingRevision)
                        .thenComparing(SettlementReserveClearingDetailDO::getLineNo)
                        .thenComparing(SettlementReserveClearingDetailDO::getReserveClearingDetailNo))
                .forEach(row -> value.add(row.getReserveClearingDetailNo()).add(row.getFinanceStateId())
                        .add(row.getTransactionId()).add(row.getOperationId()).add(row.getOriginalTransactionId())
                        .add(row.getOriginalTransactionDateTime()).add(row.getSourceReserveDetailNo())
                        .add(row.getMerchantId()).add(row.getPaymentType()).add(row.getPaymentMethod())
                        .add(row.getTransactionType()).add(row.getClearingRevision()).add(row.getLineNo())
                        .add(row.getReserveActionType()).add(row.getDirection()).add(row.getReserveCurrency())
                        .add(row.getReserveCurrencyExponent()).add(row.getRetainedAmount())
                        .add(row.getReturnedAmount()).add(row.getReleasedAmount()).add(row.getAdjustmentAmount())
                        .add(row.getRoundingMode()).add(row.getFormulaSnapshot())
                        .add(row.getExpectedReserveReleaseDate()).add(row.getRecordStatus())
                        .add(row.getTransactionDateTime()));
    }

    /** 将逐笔结果的来源、方向、币种、直接汇率、舍入和最终金额写入审批指纹。 */
    private void appendResult(Canonical value, SettlementResultItemDO row) {
        value.add(row.getCandidateId()).add(row.getResultLineNo()).add(row.getMerchantId())
                .add(row.getSettlementAccountId()).add(row.getSourceDetailType()).add(row.getSourceDetailNo())
                .add(row.getSourceTransactionId()).add(row.getSourceTransactionDateTime())
                .add(row.getFeeGroupNo()).add(row.getResultItemType()).add(row.getResultRole())
                .add(row.getPaymentType()).add(row.getPaymentMethod()).add(row.getTransactionType())
                .add(row.getFeeCategory()).add(row.getDirection()).add(row.getSourceAmount())
                .add(row.getSourceCurrency()).add(row.getSourceCurrencyExponent())
                .add(row.getUnroundedTargetAmount()).add(row.getTargetAmount())
                .add(row.getTargetCurrency()).add(row.getTargetCurrencyExponent())
                .add(row.getAppliedLimit()).add(row.getMinimumTargetAmount())
                .add(row.getMaximumTargetAmount()).add(row.getRoundingMode()).add(row.getFormulaSnapshot());
    }

    private record Route(String transactionId,
                         java.time.LocalDateTime transactionDateTime,
                         Integer revision) {
        private boolean matches(SettlementTransactionClearingDetailDO row) {
            return transactionId.equals(row.getTransactionId())
                    && transactionDateTime.equals(row.getTransactionDateTime())
                    && revision.equals(row.getClearingRevision());
        }

        private boolean matches(SettlementReserveClearingDetailDO row) {
            return transactionId.equals(row.getTransactionId())
                    && transactionDateTime.equals(row.getTransactionDateTime())
                    && revision.equals(row.getClearingRevision());
        }
    }

    private static final class Canonical {
        /**
         * 值字段，保存 规范化 当前处理所需的业务取值。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
         * </p>
         */
        private final StringBuilder value = new StringBuilder();

        private Canonical(String version) {
            add(version);
        }

        private Canonical add(Object field) {
            String normalized;
            if (field == null) {
                normalized = "<null>";
            } else if (field instanceof BigDecimal decimal) {
                normalized = decimal.signum() == 0 ? "0" : decimal.stripTrailingZeros().toPlainString();
            } else {
                normalized = field.toString();
            }
            value.append(normalized.length()).append(':').append(normalized).append('|');
            return this;
        }

        private String digest() {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
        }
    }
}
