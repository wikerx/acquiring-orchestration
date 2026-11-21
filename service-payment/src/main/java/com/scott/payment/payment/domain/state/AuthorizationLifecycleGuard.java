package com.scott.payment.payment.domain.state;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;

import java.util.List;
import java.util.Set;

/** 授权主单锁内的生命周期门禁；历史起点成功状态不代表后续动作仍开放。 */
public final class AuthorizationLifecycleGuard {

    private static final Set<String> AUTHORIZATION_TYPES = Set.of("AUTHORIZATION", "PRE_AUTHORIZATION");
    private static final Set<String> CLOSING_TYPES = Set.of("VOID", "PRE_AUTH_COMPLETION");
    private static final Set<String> CONFLICTING_TYPES = Set.of("VOID", "CAPTURE", "PRE_AUTH_COMPLETION", "INCREMENTAL_AUTHORIZATION");

    private AuthorizationLifecycleGuard() {
    }

    /** 在整条生命周期上核对关闭及未决冲突；明确失败不关闭授权，退款不参与该互斥组。 */
    public static void validateOpen(TransactionOrderDO source, List<TransactionOperationDO> operations) {
        if (source == null || !AUTHORIZATION_TYPES.contains(source.getTransactionType())) {
            return;
        }
        boolean closed = operations != null && operations.stream().anyMatch(operation ->
                "SUCCESS".equals(operation.getTransactionStatus())
                        && CLOSING_TYPES.contains(operation.getTransactionType()));
        if (closed) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "authorization lifecycle is closed");
        }
        boolean pending = operations != null && operations.stream().anyMatch(operation ->
                CONFLICTING_TYPES.contains(operation.getTransactionType())
                        && Set.of("INIT", "PROCESSING", "PENDING").contains(operation.getTransactionStatus()));
        if (pending) {
            throw new ServiceException(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(),
                    "authorization lifecycle has an unresolved action");
        }
    }
}
