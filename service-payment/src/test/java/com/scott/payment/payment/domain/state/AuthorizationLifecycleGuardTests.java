package com.scott.payment.payment.domain.state;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AuthorizationLifecycleGuardTests {
    @Test
    void completionOrVoidSuccessClosesHistoricalSuccessfulPreauthorization() {
        TransactionOrderDO root = root();
        for (String type : List.of("PRE_AUTH_COMPLETION", "VOID")) {
            TransactionOperationDO operation = operation(type, "SUCCESS");
            assertThatThrownBy(() -> AuthorizationLifecycleGuard.validateOpen(root, List.of(operation)))
                    .isInstanceOf(ServiceException.class);
            assertThat(root.getTransactionStatus()).isEqualTo("SUCCESS");
            assertThat(root.getAvailableCaptureAmount()).isEqualByComparingTo("200");
        }
    }

    @Test
    void confirmedFailureKeepsPreauthorizationValidAndOrdinaryAuthorizationCannotIncrement() {
        TransactionOrderDO root = root();
        assertThatCode(() -> AuthorizationLifecycleGuard.validateOpen(root,
                List.of(operation("PRE_AUTH_COMPLETION", "FAILED"), operation("VOID", "FAILED"))))
                .doesNotThrowAnyException();
        DefaultTransactionStateMachineService state = new DefaultTransactionStateMachineService();
        assertThatCode(() -> state.validateFollowUpAction(root,
                PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION, BigDecimal.ONE, "USD"))
                .doesNotThrowAnyException();
        root.setTransactionType("AUTHORIZATION");
        assertThatThrownBy(() -> state.validateFollowUpAction(root,
                PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION, BigDecimal.ONE, "USD"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void unresolvedActionsBlockWholeLifecycleRegardlessOfImmediateSourcePointer() {
        for (String type : List.of("CAPTURE", "PRE_AUTH_COMPLETION", "VOID", "INCREMENTAL_AUTHORIZATION")) {
            var pending = operation(type, "PROCESSING");
            pending.setSourceTransactionId("ANOTHER-ACTION-IN-SAME-LIFECYCLE");
            assertThatThrownBy(() -> AuthorizationLifecycleGuard.validateOpen(root(), List.of(pending)))
                    .isInstanceOf(ServiceException.class);
        }
        assertThatCode(() -> AuthorizationLifecycleGuard.validateOpen(root(), List.of(operation("REFUND", "PENDING"))))
                .doesNotThrowAnyException();
    }

    private TransactionOrderDO root() {
        TransactionOrderDO root = new TransactionOrderDO();
        root.setTransactionType("PRE_AUTHORIZATION");
        root.setTransactionStatus("SUCCESS");
        root.setTransactionCurrency("USD");
        root.setAvailableCaptureAmount(new BigDecimal("200"));
        return root;
    }

    private TransactionOperationDO operation(String type, String status) {
        TransactionOperationDO operation = new TransactionOperationDO();
        operation.setTransactionType(type);
        operation.setTransactionStatus(status);
        return operation;
    }
}
