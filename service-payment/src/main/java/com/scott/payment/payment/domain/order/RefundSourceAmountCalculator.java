package com.scott.payment.payment.domain.order;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 按具体成功请款/完成动作隔离退款额度；调用方必须持有同一生命周期主单锁。 */
public final class RefundSourceAmountCalculator {
    private static final Set<String> SOURCES = Set.of("CAPTURE", "PRE_AUTH_COMPLETION");

    private RefundSourceAmountCalculator() {
    }

    /** 构造仅用于额度计算的来源快照，不改写主单、动作或历史累计事实。 */
    public static TransactionOrderDO sourceBudget(TransactionOrderDO root, TransactionOperationDO source,
                                                 List<TransactionOperationDO> operations) {
        if (source == null || !SOURCES.contains(source.getTransactionType())) {
            return root;
        }
        if (!"SUCCESS".equals(source.getTransactionStatus())
                || !Objects.equals(root.getOperationId(), source.getOperationId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "refund source must be a successful action in this lifecycle");
        }
        BigDecimal labelPrincipal = labelAmount(root, source);
        BigDecimal principal = requireAmount(source.getTransactionAmount());
        BigDecimal labelRefunded = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        for (TransactionOperationDO operation : operations) {
            if ("REFUND".equals(operation.getTransactionType())
                    && "SUCCESS".equals(operation.getTransactionStatus())
                    && Objects.equals(source.getTransactionId(), operation.getSourceTransactionId())) {
                labelRefunded = labelRefunded.add(labelAmount(root, operation));
                refunded = refunded.add(requireAmount(operation.getTransactionAmount()));
            }
        }
        TransactionOrderDO budget = new TransactionOrderDO();
        BeanUtils.copyProperties(root, budget);
        budget.setLabelAmount(labelPrincipal);
        budget.setLabelRefundedAmount(labelRefunded);
        budget.setRefundedAmount(refunded);
        budget.setLabelAvailableRefundAmount(labelPrincipal.subtract(labelRefunded));
        budget.setAvailableRefundAmount(principal.subtract(refunded));
        return budget;
    }

    /** 同一来源的未终态退款参与预占，其他请款来源独立计算。 */
    public static List<TransactionOperationDO> pendingForSource(TransactionOperationDO source,
                                                               List<TransactionOperationDO> pending) {
        if (source == null || !SOURCES.contains(source.getTransactionType())) {
            return pending;
        }
        return pending.stream().filter(operation -> Objects.equals(
                source.getTransactionId(), operation.getSourceTransactionId())).toList();
    }

    /** 根来源旧请求与逐笔请款来源请求可并存，任何组合都不能突破整个生命周期本金。 */
    public static void validateRootCapacity(TransactionOrderDO root, List<TransactionOperationDO> pending,
                                           FrozenRateFollowUpAmountCalculator.AmountPlan plan) {
        BigDecimal remaining = requireAmount(root.getAvailableRefundAmount());
        BigDecimal labelRemaining = root.getLabelAvailableRefundAmount();
        if (labelRemaining == null && (!org.springframework.util.StringUtils.hasText(root.getLabelCurrency())
                || root.getLabelCurrency().equalsIgnoreCase(root.getTransactionCurrency()))) {
            labelRemaining = remaining;
        }
        if (labelRemaining == null) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "root refund label balance is missing");
        }
        for (TransactionOperationDO reservation : pending) {
            remaining = remaining.subtract(requireAmount(reservation.getTransactionAmount()));
            labelRemaining = labelRemaining.subtract(labelAmount(root, reservation));
        }
        if (plan.transactionAmount().compareTo(remaining) > 0 || plan.labelAmount().compareTo(labelRemaining) > 0) {
            throw new ServiceException(ApiResultEnum.REFUND_AMOUNT_EXCEEDS_AVAILABLE);
        }
    }

    /** 标签金额缺失时只允许同币种兼容回退，跨币种缺失必须拒绝以免扩大可退额度。 */
    private static BigDecimal labelAmount(TransactionOrderDO root, TransactionOperationDO operation) {
        if (operation.getLabelAmount() != null) {
            return operation.getLabelAmount();
        }
        if ((!org.springframework.util.StringUtils.hasText(root.getLabelCurrency())
                || root.getLabelCurrency().equalsIgnoreCase(root.getTransactionCurrency()))) {
            return requireAmount(operation.getTransactionAmount());
        }
        throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "refund source label amount is missing");
    }

    private static BigDecimal requireAmount(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "refund source amount is invalid");
        }
        return value;
    }
}
