package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import com.scott.payment.component.core.model.RefundContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 控制台只读操作投影，不能代替支付核心持锁后的校验，也不修改交易状态。 */
public final class ConsoleTransactionPolicy {
    private static final Set<String> REFUND_SOURCES = Set.of("PAYMENT", "CAPTURE", "PRE_AUTH_COMPLETION");
    private static final Set<String> CONFLICTS = Set.of("CAPTURE", "PRE_AUTH_COMPLETION", "VOID", "INCREMENTAL_AUTHORIZATION");
    private static final Set<String> PENDING = Set.of("INIT", "PENDING", "PROCESSING");

    private ConsoleTransactionPolicy() {
    }

    /** 按每笔成功资金来源计算 S+P；待审批已对应 PENDING 动作，不能再从审批表重复计数。 */
    public static RefundContext refundContext(TransactionOperationResponse source,
                                              List<TransactionOperationResponse> operations) {
        String currency = source.getLabelCurrency() == null ? source.getTransactionCurrency() : source.getLabelCurrency();
        int digits = IsoCurrencyResolver.resolve(currency).map(info -> info.defaultFractionDigits())
                .filter(value -> value >= 0).orElseThrow(() -> new ApiException(ApiResultEnum.PARAM_INVALID));
        if (currency.equalsIgnoreCase(source.getTransactionCurrency()) && source.getCurrencyExponent() != null) {
            digits = source.getCurrencyExponent();
        }
        boolean payment = "PAYMENT".equals(source.getTransactionType());
        BigDecimal success = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        for (TransactionOperationResponse operation : operations) {
            if (!"REFUND".equals(operation.getTransactionType())
                    || !Objects.equals(source.getOperationId(), operation.getOperationId())
                    || !payment && !Objects.equals(source.getTransactionId(), operation.getSourceTransactionId())) {
                continue;
            }
            if (!payment && "SUCCESS".equals(operation.getTransactionStatus())) {
                success = success.add(labelAmount(operation, currency));
            } else if (operation.getTransactionStatus() != null && PENDING.contains(operation.getTransactionStatus())) {
                pending = pending.add(labelAmount(operation, currency));
            }
        }
        BigDecimal available;
        if (payment) {
            // PAYMENT 的历史主单累计投影保留兼容，授权链的每笔请款/完成独立计算。
            BigDecimal recorded = source.getLabelRefundedAmount();
            if (recorded == null && currency.equalsIgnoreCase(source.getTransactionCurrency())) {
                recorded = source.getRefundedAmount();
            }
            success = recorded == null ? success : recorded;
            available = source.getLabelAvailableRefundAmount();
            if (available == null && currency.equalsIgnoreCase(source.getTransactionCurrency())) {
                available = source.getAvailableRefundAmount();
            }
            if (available == null) {
                available = labelAmount(source, currency).subtract(success);
            }
        } else {
            available = labelAmount(source, currency).subtract(success);
        }
        return new RefundContext(currency, digits, success.setScale(digits, RoundingMode.HALF_UP),
                pending.setScale(digits, RoundingMode.HALF_UP),
                available.subtract(pending).max(BigDecimal.ZERO).setScale(digits, RoundingMode.DOWN));
    }

    /** 完整生命周期批量载入后再计算，不能只看当前页或原授权历史 SUCCESS。 */
    public static void enrich(List<TransactionOperationResponse> rows, List<TransactionOperationResponse> facts) {
        for (TransactionOperationResponse row : rows) {
            if (row.getOperationId() == null || row.getTransactionType() == null) {
                row.setAvailableActions(List.of());
                continue;
            }
            List<TransactionOperationResponse> lifecycle = facts.stream()
                    .filter(item -> item.getTransactionType() != null && item.getTransactionStatus() != null)
                    .filter(item -> Objects.equals(row.getOperationId(), item.getOperationId())).toList();
            List<String> actions = new ArrayList<>();
            boolean success = "SUCCESS".equals(row.getTransactionStatus());
            boolean closed = lifecycle.stream().anyMatch(item -> "SUCCESS".equals(item.getTransactionStatus())
                    && Set.of("VOID", "PRE_AUTH_COMPLETION").contains(item.getTransactionType()));
            boolean busy = lifecycle.stream().anyMatch(item -> PENDING.contains(item.getTransactionStatus())
                    && CONFLICTS.contains(item.getTransactionType()));
            if (success && !closed && !busy && Set.of("AUTHORIZATION", "PRE_AUTHORIZATION").contains(row.getTransactionType())) {
                BigDecimal available = row.getLabelAvailableCaptureAmount();
                if (available != null && available.signum() > 0) {
                    actions.add("PRE_AUTHORIZATION".equals(row.getTransactionType()) ? "PRE_AUTH_COMPLETION" : "CAPTURE");
                }
                BigDecimal captured = row.getLabelCapturedAmount();
                if (captured != null && captured.signum() == 0) {
                    actions.add("VOID");
                }
            }
            if (success && REFUND_SOURCES.contains(row.getTransactionType())) {
                RefundContext context = refundContext(row, lifecycle);
                row.setRefundContext(context);
                if (context.availableRefundAmount().signum() > 0 && !lifecycle.stream().anyMatch(item ->
                        "VOID".equals(item.getTransactionType()) && PENDING.contains(item.getTransactionStatus()))) {
                    actions.add("REFUND");
                }
            }
            row.setAvailableActions(List.copyOf(actions));
        }
    }

    private static BigDecimal labelAmount(TransactionOperationResponse operation, String currency) {
        if (operation.getLabelAmount() != null) {
            return operation.getLabelAmount();
        }
        if (currency.equalsIgnoreCase(operation.getTransactionCurrency()) && operation.getTransactionAmount() != null) {
            return operation.getTransactionAmount();
        }
        throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR, "source label amount is missing");
    }
}
