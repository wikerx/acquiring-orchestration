package com.scott.payment.clearing.domain.model;

import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;

/**
 * 定义清分事务外准备与阶段B原子提交之间的内部契约，不携带持卡人数据、汇率或余额对象。
 */
public final class ClearingCompletionModels {

    /**
     * 领域模型容器不允许实例化。
     */
    private ClearingCompletionModels() {
    }

    /**
     * 非分表 locator 提供的当前动作及生命周期根分片定位事实。
     */
    public record LocatorFacts(String transactionId,
                               String operationId,
                               String rootTransactionId,
                               String merchantId,
                               String merchantOrderNo,
                               String transactionType,
                               LocalDateTime transactionDateTime,
                               LocalDateTime rootTransactionDateTime) {

        /**
         * 校验动作与生命周期根交易的精确分片定位事实。
         */
        public LocatorFacts {
            requireText(transactionId, "locator transaction id");
            requireText(operationId, "locator operation id");
            requireText(rootTransactionId, "locator root transaction id");
            requireText(merchantId, "locator merchant id");
            Objects.requireNonNull(transactionDateTime, "locator transaction time is required");
            Objects.requireNonNull(rootTransactionDateTime, "locator root transaction time is required");
        }
    }

    /**
     * 退款、冲正等动作依赖的源动作、有效清分修订及不可变费用版本。
     *
     * @param operation 源交易动作事实
     * @param locator 源交易精确分片定位
     * @param feeSnapshot 源动作受理时冻结的费用版本
     * @param financeStateId 源动作财务状态号
     * @param clearingRevision 源动作当前有效清分修订
     */
    public record SourceContext(ClearingOperationFacts operation,
                                LocatorFacts locator,
                                FeeVersionSnapshot feeSnapshot,
                                String financeStateId,
                                int clearingRevision) {

        /**
         * 校验源交易、费用快照和结算候选定位身份完整。
         */
        public SourceContext {
            Objects.requireNonNull(operation, "source clearing operation is required");
            Objects.requireNonNull(locator, "source clearing locator is required");
            Objects.requireNonNull(feeSnapshot, "source fee snapshot is required");
            requireText(financeStateId, "source finance state id");
            if (clearingRevision < 1) {
                throw new IllegalArgumentException("source clearing revision must be positive");
            }
        }
    }

    /**
     * 阶段B提交命令。费用快照可能来自 Redis/Slave/Master，但进入该命令前已经完成确切版本和 hash 校验。
     */
    public record CompletionCommand(PaymentTransactionEventMessage message,
                                    ClearingClaimResult claim,
                                    String processingOwner,
                                    FeeVersionSnapshot feeSnapshot,
                                    LocatorFacts currentLocator,
                                    String paymentType,
                                    String paymentMethod,
                                    Set<String> occurredRiskServices,
                                    SourceContext source,
                                    LocalDate settlementEligibleDate,
                                    LocalDate expectedReserveReleaseDate) {

        /**
         * 校验阶段 B 提交所需事实，并冻结已发生风险服务集合。
         */
        public CompletionCommand {
            Objects.requireNonNull(message, "clearing message is required");
            Objects.requireNonNull(claim, "clearing claim is required");
            requireText(processingOwner, "processing owner");
            Objects.requireNonNull(feeSnapshot, "fee snapshot is required");
            Objects.requireNonNull(currentLocator, "current locator is required");
            requireText(paymentType, "clearing payment type");
            requireText(paymentMethod, "clearing payment method");
            occurredRiskServices = occurredRiskServices == null ? Set.of() : Set.copyOf(occurredRiskServices);
            Objects.requireNonNull(settlementEligibleDate, "settlement eligible date is required");
        }
    }

    /**
     * 阶段B可观察结果。
     */
    public record CompletionResult(String clearingStatus,
                                   int clearingRevision,
                                   int transactionDetailCount,
                                   int reserveDetailCount) {
    }

    /**
     * transaction_finance_state 的单币种查询摘要；权威结算输入仍是不可变清分明细。
     */
    public record FinanceSummary(String clearingStatus,
                                 int clearingRevision,
                                 Long feePlanId,
                                 Long feePlanVersionId,
                                 int feePlanVersionNo,
                                 String feeSnapshotHash,
                                 BigDecimal grossLabelAmount,
                                 int feeComponentCurrencyCount,
                                 String feeEvaluationStatus,
                                 String labelCurrency,
                                 String settlementCurrency,
                                 BigDecimal platformFeeAmount,
                                 BigDecimal feeReversalAmount,
                                 BigDecimal merchantReceivableAmount,
                                 BigDecimal reserveAmount,
                                 BigDecimal reserveReversalAmount,
                                 BigDecimal netSettlementAmount,
                                 LocalDate settlementEligibleDate,
                                 LocalDate expectedReserveReleaseDate) {
    }

    /**
     * 校验领域模型中的必填文本字段。
     */
    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
