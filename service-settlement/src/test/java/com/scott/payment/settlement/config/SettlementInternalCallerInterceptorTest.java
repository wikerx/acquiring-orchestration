package com.scott.payment.settlement.config;

import com.scott.payment.component.web.internal.InternalServiceSignature;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementInternalCallerInterceptorTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证结算内部管理接口只接受已签名的 service-admin 调用身份。
 * @status : create
 */
class SettlementInternalCallerInterceptorTest {

    @Test
    void shouldAllowOnlyServiceAdminWithoutDisclosingAllowedCaller() throws Exception {
        SettlementInternalCallerInterceptor interceptor = new SettlementInternalCallerInterceptor();
        MockHttpServletRequest allowed = new MockHttpServletRequest();
        allowed.addHeader(InternalServiceSignature.HEADER_CALLER, "service-admin");
        assertThat(interceptor.preHandle(
                allowed, new MockHttpServletResponse(), new Object())).isTrue();

        MockHttpServletRequest denied = new MockHttpServletRequest();
        denied.addHeader(InternalServiceSignature.HEADER_CALLER, "service-job");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(denied, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("internal settlement caller is not allowed")
                .doesNotContain("service-admin");
    }
}
