package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.api.internal.PaymentPreparationRecoveryInternalController.CancellationRequest;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService.CancellationDecision;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentPreparationRecoveryInternalControllerTest {
    private static final String KEY = "a".repeat(64);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 6, 30, 23, 59, 59, 850_000_000);
    private final PaymentPreparationOwnershipService ownership = mock(PaymentPreparationOwnershipService.class);
    private final PaymentPreparationRecoveryInternalController controller =
            new PaymentPreparationRecoveryInternalController(ownership);

    @Test
    void onlyVerifiedRiskCallerCanRequestCancellationEvidence() {
        var request = new CancellationRequest("FIXTURE", KEY, 2, "SYNTHETIC-CANDIDATE", TIME.toString());
        assertThatThrownBy(() -> controller.reconcile(request, null))
                .isInstanceOfSatisfying(ServiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ApiResultEnum.FORBIDDEN.getCode()));
        assertThatThrownBy(() -> controller.reconcile(request, "service-job"))
                .isInstanceOfSatisfying(ServiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ApiResultEnum.FORBIDDEN.getCode()));
        verifyNoInteractions(ownership);
    }

    @Test
    void preservesExactPreparationTimeAndReturnsOnlyTheDecision() {
        var request = new CancellationRequest("FIXTURE", KEY, 2, "SYNTHETIC-CANDIDATE", TIME.toString());
        when(ownership.reconcileCancellation("FIXTURE", KEY, 2, "SYNTHETIC-CANDIDATE", TIME))
                .thenReturn(CancellationDecision.RETAIN);
        assertThat(controller.reconcile(request, "service-risk").getData()).isEqualTo(CancellationDecision.RETAIN);
    }
}
