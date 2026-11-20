package com.scott.payment.risk.api.internal;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.risk.domain.MerchantLimitReservationReconciliationSummary;
import com.scott.payment.risk.service.MerchantLimitReservationReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RiskReservationReconciliationInternalControllerTest {

    @Test
    void refusesRequestsWhileTheLegacyRiskSchedulerStillOwnsTheScope() {
        var service = mock(MerchantLimitReservationReconciliationService.class);
        var controller = new RiskReservationReconciliationInternalController(service, true, 100);
        assertThatThrownBy(() -> controller.reconcile(request("service-job"))).isInstanceOf(ServiceException.class);
        verifyNoInteractions(service);
    }

    @Test
    void requiresTheJobCallerAndBoundsTheReconciliationBatch() {
        var service = mock(MerchantLimitReservationReconciliationService.class);
        var controller = new RiskReservationReconciliationInternalController(service, false, 2000);
        assertThatThrownBy(() -> controller.reconcile(request("service-admin"))).isInstanceOf(ServiceException.class);
        verifyNoInteractions(service);
        var summary = new MerchantLimitReservationReconciliationSummary(1, 0, 0, 1);
        when(service.reconcile(any(), eq(500))).thenReturn(summary);
        assertThat(controller.reconcile(request("service-job")).getData()).isEqualTo(summary);
    }

    private static MockHttpServletRequest request(String caller) {
        var request = new MockHttpServletRequest();
        request.addHeader(InternalServiceSignature.HEADER_CALLER, caller);
        return request;
    }
}
