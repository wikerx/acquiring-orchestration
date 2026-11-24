package com.scott.payment.openapi.api.rest.checkout.v1;

import com.scott.payment.openapi.application.checkout.OpenApiHostedCheckoutApplicationService;
import com.scott.payment.openapi.service.OpenApiSystemConfigService;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.openapi.support.HostedCheckoutUrlPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hosted Checkout 浏览器桥接页测试，验证 3DS 返回数据不被误判为支付成功且敏感报文不回显。
 */
class HostedCheckoutBrowserControllerTests {

    /**
     * 验证 3DS bridge 只回传协议参数摘要，并保持既有字段大小写。
     */
    @Test
    void shouldRenderThreeDsBridgeWithoutTreatingReturnAsSuccess() {
        OpenApiSystemConfigService systemConfigService = mock(OpenApiSystemConfigService.class);
        when(systemConfigService.requiredEnabledValue("platform.checkout.frontend-base-url"))
                .thenReturn("https://pay.example.com/checkout");
        HostedCheckoutBrowserController controller =
                controller(systemConfigService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("checkoutSessionId", "CS-001");
        request.setParameter("checkoutAttemptId", "CA-001");
        request.setParameter("threeDsReturnToken", "return-token-value");
        request.setParameter("cres", "sensitive-cres-value");
        request.setParameter("PaRes", "sensitive-pares-value");
        request.setParameter("threeDSSessionData", "session-secret-value");

        String html = controller.threeDsBridgePost(request);

        assertThat(html).contains("HOSTED_CHECKOUT_3DS_RETURN", "CS-001", "CA-001", "return-token-value");
        assertThat(html).contains("postMessage");
        assertThat(html).contains("window.top.postMessage(payload, \"https://pay.example.com\")");
        assertThat(html).doesNotContain("window.parent.postMessage");
        assertThat(html).doesNotContain("postMessage(payload, '*')");
        assertThat(html).doesNotContain("SUCCEEDED", "SUCCESSFUL", "Payment successful");
        assertThat(html).contains("\"PaRes\":").doesNotContain("\"paRes\":");
        assertThat(html).doesNotContain(
                "sensitive-cres-value", "sensitive-pares-value", "session-secret-value");
    }

    @Test
    void shouldFailClosedWhenCheckoutFrontendOriginIsMissing() {
        OpenApiSystemConfigService systemConfigService = mock(OpenApiSystemConfigService.class);
        HostedCheckoutBrowserController controller =
                controller(systemConfigService);

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
                controller(systemConfigService);

        assertThatThrownBy(() -> controller.threeDsBridgePost(new MockHttpServletRequest()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not a valid checkout frontend origin");
    }

    @Test
    void shouldAllowExternalHttpCheckoutFrontendOrigin() {
        OpenApiSystemConfigService systemConfigService = mock(OpenApiSystemConfigService.class);
        when(systemConfigService.requiredEnabledValue("platform.checkout.frontend-base-url"))
                .thenReturn("http://pay.example.com/checkout");

        String html = controller(systemConfigService).threeDsBridgePost(new MockHttpServletRequest());

        assertThat(html).contains("window.top.postMessage(payload, \"http://pay.example.com\")");
    }

    @Test
    void shouldAllowLoopbackHttpCheckoutFrontendOrigin() {
        OpenApiSystemConfigService systemConfigService = mock(OpenApiSystemConfigService.class);
        when(systemConfigService.requiredEnabledValue("platform.checkout.frontend-base-url"))
                .thenReturn("http://127.0.0.1:5175/checkout");

        String html = controller(systemConfigService)
                .threeDsBridgePost(new MockHttpServletRequest());

        assertThat(html).contains("window.top.postMessage(payload, \"http://127.0.0.1:5175\")");
    }

    /**
     * 钱包渠道回跳必须先由网关收敛支付状态，再以新的普通收银台令牌返回同一前端。
     */
    @Test
    void shouldRedirectWalletReturnToNormalHostedCheckoutResult() {
        OpenApiHostedCheckoutApplicationService applicationService =
                mock(OpenApiHostedCheckoutApplicationService.class);
        OpenApiSystemConfigService systemConfigService = mock(OpenApiSystemConfigService.class);
        when(systemConfigService.requiredEnabledValue("platform.checkout.frontend-base-url"))
                .thenReturn("http://127.0.0.1:5175");
        when(applicationService.handleWalletReturn(org.mockito.ArgumentMatchers.any()))
                .thenReturn("http://127.0.0.1:5175/checkout/new-token/new-cover");

        ResponseEntity<Void> response = controller(applicationService, systemConfigService)
                .walletReturn("1234567890123456." + "a".repeat(64), "CHANNEL_TX_001");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SEE_OTHER);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("http://127.0.0.1:5175/checkout/new-token/new-cover"));
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    }

    /**
     * 支付核心返回的跳转地址即使可访问，也不能把付款人送离平台收银台。
     */
    @Test
    void shouldRejectWalletReturnTargetOutsidePlatformCheckout() {
        OpenApiHostedCheckoutApplicationService applicationService =
                mock(OpenApiHostedCheckoutApplicationService.class);
        OpenApiSystemConfigService systemConfigService = mock(OpenApiSystemConfigService.class);
        when(systemConfigService.requiredEnabledValue("platform.checkout.frontend-base-url"))
                .thenReturn("http://127.0.0.1:5175");
        when(applicationService.handleWalletReturn(org.mockito.ArgumentMatchers.any()))
                .thenReturn("http://127.0.0.1:58090/simulate/result");

        assertThatThrownBy(() -> controller(applicationService, systemConfigService)
                .walletReturn("1234567890123456." + "a".repeat(64), "CHANNEL_TX_001"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not a platform checkout URL");
    }

    private HostedCheckoutBrowserController controller(OpenApiSystemConfigService systemConfigService) {
        return controller(mock(OpenApiHostedCheckoutApplicationService.class), systemConfigService);
    }

    private HostedCheckoutBrowserController controller(
            OpenApiHostedCheckoutApplicationService applicationService,
            OpenApiSystemConfigService systemConfigService) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        return new HostedCheckoutBrowserController(
                applicationService,
                systemConfigService,
                new HostedCheckoutUrlPolicy(environment));
    }
}
