package com.scott.payment.clearing.config;

import com.scott.payment.component.web.internal.InternalServiceSignature;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClearingInternalCallerInterceptorTest {

    @Test
    void shouldAllowOnlyConfiguredInternalCaller() throws Exception {
        ClearingProperties properties = new ClearingProperties();
        properties.setInternalAllowedCallers(List.of("service-admin", "service-job"));
        ClearingInternalCallerInterceptor interceptor = new ClearingInternalCallerInterceptor(properties);

        MockHttpServletRequest allowed = new MockHttpServletRequest();
        allowed.setRequestURI("/internal/clearing/v1/transactions/search");
        allowed.addHeader(InternalServiceSignature.HEADER_CALLER, "service-admin");

        assertThat(interceptor.preHandle(allowed, new MockHttpServletResponse(), new Object())).isTrue();

        MockHttpServletRequest denied = new MockHttpServletRequest();
        denied.setRequestURI("/internal/clearing/v1/transactions/search");
        denied.addHeader(InternalServiceSignature.HEADER_CALLER, "service-payment");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(denied, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("internal clearing caller is not allowed")
                .doesNotContain("service-admin", "service-job");
    }

    /** Job 只能调用补偿扫描，不能复用其 HMAC 身份执行人工清分命令。 */
    @Test
    void shouldRestrictCallerByClearingInternalPath() throws Exception {
        ClearingProperties properties = new ClearingProperties();
        properties.setInternalAllowedCallers(List.of("service-admin", "service-job"));
        ClearingInternalCallerInterceptor interceptor = new ClearingInternalCallerInterceptor(properties);

        MockHttpServletRequest jobCompensation = new MockHttpServletRequest();
        jobCompensation.setRequestURI("/internal/clearing/v1/compensations/scan");
        jobCompensation.addHeader(InternalServiceSignature.HEADER_CALLER, "service-job");
        assertThat(interceptor.preHandle(
                jobCompensation, new MockHttpServletResponse(), new Object())).isTrue();

        MockHttpServletRequest jobManualRetry = new MockHttpServletRequest();
        jobManualRetry.setRequestURI("/internal/clearing/v1/transactions/TX-1/retry");
        jobManualRetry.addHeader(InternalServiceSignature.HEADER_CALLER, "service-job");
        MockHttpServletResponse jobDenied = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(jobManualRetry, jobDenied, new Object())).isFalse();
        assertThat(jobDenied.getStatus()).isEqualTo(401);

        MockHttpServletRequest adminCompensation = new MockHttpServletRequest();
        adminCompensation.setRequestURI("/internal/clearing/v1/compensations/scan");
        adminCompensation.addHeader(InternalServiceSignature.HEADER_CALLER, "service-admin");
        assertThat(interceptor.preHandle(
                adminCompensation, new MockHttpServletResponse(), new Object())).isFalse();
    }
}
