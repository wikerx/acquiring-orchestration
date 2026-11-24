package com.scott.payment.job.handler.operation;

import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.client.admin.AdminInternalClient;
import com.scott.payment.job.client.admin.dto.PaymentCapabilityOperationsClientDTOs.RecoveryResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentCapabilityDeliveryRecoveryJobTests {

    private final AdminInternalClient admin = mock(AdminInternalClient.class);
    private final PaymentCapabilityDeliveryRecoveryJob job = new PaymentCapabilityDeliveryRecoveryJob(admin);

    @Test
    void reportsPartialResultsInTheRunMessage() {
        when(admin.recoverPaymentCapabilityDelivery(any()))
                .thenReturn(new RecoveryResponse(2, 1, 3, 1, 1, true));

        var result = job.execute(context(30));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo(
                "requeued=2, selected=3, relayed=1, failed=1, deferred=1, budgetExhausted=true");
    }

    @Test
    void distinguishesAnUnknownResponseFromAnEmptyRun() {
        when(admin.recoverPaymentCapabilityDelivery(any()))
                .thenThrow(new IllegalStateException("owner unavailable"))
                .thenReturn(new RecoveryResponse(0, 0, 0, 0, 0, false));

        var unavailable = job.execute(context(30));
        var empty = job.execute(context(30));

        assertThat(unavailable.isSuccess()).isFalse();
        assertThat(unavailable.getErrorMessage()).isEqualTo("result=unknown, errorType=IllegalStateException");
        assertThat(empty.isSuccess()).isTrue();
        assertThat(empty.getMessage()).contains("selected=0", "failed=0", "deferred=0");
    }

    @Test
    void doesNotCallAdminWhenTheRunBudgetHasExpired() {
        var result = job.execute(context(-1));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("budgetExhausted=true");
        verifyNoInteractions(admin);
    }

    private static JobExecuteContext context(long seconds) {
        JobExecuteContext context = new JobExecuteContext();
        context.setExecutionDeadline(Instant.now().plusSeconds(seconds));
        return context;
    }
}
