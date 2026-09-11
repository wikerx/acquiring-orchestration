package com.scott.payment.openapi.api.rest.checkout.v1;

import com.scott.payment.openapi.application.checkout.OpenApiHostedCheckoutApplicationService;
import com.scott.payment.openapi.service.OpenApiSystemConfigService;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.openapi.config.HostedCheckoutProperties;
import com.scott.payment.openapi.support.HostedCheckoutUrlPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HostedCheckoutBrowserControllerTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Hosted Checkout 浏览器桥接页测试，验证 3DS 返回数据不被误判为支付成功且敏感报文不回显。
 * @status : create
 */
class HostedCheckoutBrowserControllerTests {

    @Test
    void shouldRenderThreeDsBridgeWithoutTreatingReturnAsSuccess() {
        OpenApiSystemConfigService systemConfigService = mock(OpenApiSystemConfigService.class);
        when(systemConfigService.requiredEnabledValue("platform.checkout.frontend-base-url"))
                .thenReturn("https://pay.example.com/checkout");
        HostedCheckoutBrowserController controller =
                controller(systemConfigService, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("checkoutSessionId", "CS-001");
        request.setParameter("checkoutAttemptId", "CA-001");
        request.setParameter("threeDsReturnToken", "return-token-value");
        request.setParameter("cres", "sensitive-cres-value");
        request.setParameter("threeDSSessionData", "session-secret-value");

        String html = controller.threeDsBridgePost(request);

        assertThat(html).contains("HOSTED_CHECKOUT_3DS_RETURN", "CS-001", "CA-001", "return-token-value");
        assertThat(html).contains("postMessage");
        assertThat(html).contains("window.top.postMessage(payload, \"https://pay.example.com\")");
        assertThat(html).doesNotContain("window.parent.postMessage");
        assertThat(html).doesNotContain("postMessage(payload, '*')");
        assertThat(html).doesNotContain("SUCCEEDED", "SUCCESSFUL", "Payment successful");
        assertThat(html).doesNotContain("sensitive-cres-value", "session-secret-value");
    }

    @Test
    void shouldFailClosedWhenCheckoutFrontendOriginIsMissing() {
        OpenApiSystemConfigService systemConfigService = mock(OpenApiSystemConfigService.class);
        HostedCheckoutBrowserController controller =
                controller(systemConfigService, false);

        assertThatThrownBy(() -> controller.threeDsBridgeGet(new MockHttpServletRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not a valid checkout frontend origin");
    }

    @Test
    void shouldFailClosedWhenCheckoutFrontendOriginIsInvalid() {
        OpenApiSystemConfigService systemConfigService = mock(OpenApiSystemConfigService.class);
        when(systemConfigService.requiredEnabledValue("platform.checkout.frontend-base-url"))
                .thenReturn("javascript:alert(1)");
        HostedCheckoutBrowserController controller =
                controller(systemConfigService, false);

        assertThatThrownBy(() -> controller.threeDsBridgePost(new MockHttpServletRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not a valid checkout frontend origin");
    }

    @Test
    void shouldRejectExternalHttpCheckoutFrontendOrigin() {
        OpenApiSystemConfigService systemConfigService = mock(OpenApiSystemConfigService.class);
        when(systemConfigService.requiredEnabledValue("platform.checkout.frontend-base-url"))
                .thenReturn("http://pay.example.com/checkout");

        assertThatThrownBy(() -> controller(systemConfigService, true)
                .threeDsBridgePost(new MockHttpServletRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not a valid checkout frontend origin");
    }

    @Test
    void shouldAllowLoopbackHttpCheckoutFrontendOriginWhenEnabled() {
        OpenApiSystemConfigService systemConfigService = mock(OpenApiSystemConfigService.class);
        when(systemConfigService.requiredEnabledValue("platform.checkout.frontend-base-url"))
                .thenReturn("http://127.0.0.1:5175/checkout");

        String html = controller(systemConfigService, true)
                .threeDsBridgePost(new MockHttpServletRequest());

        assertThat(html).contains("window.top.postMessage(payload, \"http://127.0.0.1:5175\")");
    }

    private HostedCheckoutBrowserController controller(OpenApiSystemConfigService systemConfigService,
                                                        boolean allowLoopbackHttp) {
        HostedCheckoutProperties properties = new HostedCheckoutProperties();
        properties.setAllowLoopbackHttp(allowLoopbackHttp);
        return new HostedCheckoutBrowserController(
                mock(OpenApiHostedCheckoutApplicationService.class),
                systemConfigService,
                new HostedCheckoutUrlPolicy(properties));
    }
}
