package com.scott.payment.openapi.api.rest.checkout.v1;

import com.scott.payment.openapi.application.checkout.OpenApiHostedCheckoutApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Hosted Checkout 浏览器桥接页测试，验证 3DS 返回数据不被误判为支付成功且敏感报文不回显。
 */
class HostedCheckoutBrowserControllerTests {

    @Test
    void shouldRenderThreeDsBridgeWithoutTreatingReturnAsSuccess() {
        HostedCheckoutBrowserController controller =
                new HostedCheckoutBrowserController(mock(OpenApiHostedCheckoutApplicationService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("checkoutSessionId", "CS-001");
        request.setParameter("checkoutAttemptId", "CA-001");
        request.setParameter("threeDsReturnToken", "return-token-value");
        request.setParameter("cres", "sensitive-cres-value");
        request.setParameter("threeDSSessionData", "session-secret-value");

        String html = controller.threeDsBridgePost(request);

        assertThat(html).contains("HOSTED_CHECKOUT_3DS_RETURN", "CS-001", "CA-001", "return-token-value");
        assertThat(html).contains("postMessage");
        assertThat(html).doesNotContain("SUCCEEDED", "SUCCESSFUL", "Payment successful");
        assertThat(html).doesNotContain("sensitive-cres-value", "session-secret-value");
    }
}
