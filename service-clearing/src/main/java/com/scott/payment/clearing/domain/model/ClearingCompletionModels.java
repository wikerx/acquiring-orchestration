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
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCompletionModels
 * @date : 2026-08-26 10:30
 * @email : scott_x@163.com
 * @description : 定义清分事务外准备与阶段B原子提交之间的内部契约，不携带持卡人数据、汇率或余额对象。
 * @status : create
 */
public final class ClearingCompletionModels {

    private ClearingCompletionModels() {
    }

    /** 非分表 locator 提供的当前动作及生命周期根分片定位事实。 */
    public record LocatorFacts(String transactionId,
                               String operationId,
                               String rootTransactionId,
                               String merchantId,
                               String merchantOrderNo,
                               String transactionType,
                               LocalDateTime transactionDateTime,
                               LocalDateTime rootTransactionDateTime) {

        public LocatorFacts {
            requireText(transactionId, "locator transaction id");
            requireText(operationId, "locator operation id");
            requireText(rootTransactionId, "locator root transaction id");
            requireText(merchantId, "locator merchant id");
            Objects.requireNonNull(transactionDateTime, "locator transaction time is required");
            Objects.requireNonNull(rootTransactionDateTime, "locator root transaction time is required");
        }
    }

    /** 退款、冲正等动作依赖的源动作及其不可变费用版本。 */
    public record SourceContext(ClearingOperationFacts operation,
                                LocatorFacts locator,
                                FeeVersionSnapshot feeSnapshot) {

        public SourceContext {
            Objects.requireNonNull(operation, "source clearing operation is required");
            Objects.requireNonNull(locator, "source clearing locator is required");
            Objects.requireNonNull(feeSnapshot, "source fee snapshot is required");
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

    /** 阶段B可观察结果。 */
    public record CompletionResult(String clearingStatus,
                                   int clearingRevision,
                                   int transactionDetailCount,
                                   int reserveDetailCount) {
    }

    /** transaction_finance_state 的单币种查询摘要；权威结算输入仍是不可变清分明细。 */
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

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
