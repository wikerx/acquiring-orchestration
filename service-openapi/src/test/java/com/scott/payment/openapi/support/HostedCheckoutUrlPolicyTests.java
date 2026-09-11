package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.openapi.config.HostedCheckoutProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Hosted Checkout URL 环境策略测试。 */
class HostedCheckoutUrlPolicyTests {

    @Test
    void shouldAllowHttpsByDefault() {
        HostedCheckoutUrlPolicy policy = policy(false);

        assertThat(policy.validateMerchantUrl(
                "https://merchant.example/result", "transactionInfo.redirectUrl"))
                .isEqualTo("https://merchant.example/result");
        assertThat(policy.normalizePlatformBaseUrl("https://pay.example.com/checkout/"))
                .isEqualTo("https://pay.example.com/checkout");
    }

    @Test
    void shouldRejectLoopbackHttpByDefault() {
        HostedCheckoutUrlPolicy policy = policy(false);

        assertThatThrownBy(() -> policy.validateMerchantUrl(
                "http://localhost:5175/result", "transactionInfo.redirectUrl"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.PARAM_INVALID.getCode());
    }

    @Test
    void shouldAllowOnlyLoopbackHttpWhenEnabled() {
        HostedCheckoutUrlPolicy policy = policy(true);

        assertThat(policy.resolvePlatformOrigin("http://[::1]:5175/checkout"))
                .isEqualTo("http://[::1]:5175");
        assertThatThrownBy(() -> policy.validateMerchantUrl(
                "http://192.168.1.10/result", "transactionInfo.redirectUrl"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void shouldRejectUnsafePlatformConfigurationAsInternalError() {
        HostedCheckoutUrlPolicy policy = policy(true);

        assertThatThrownBy(() -> policy.normalizePlatformBaseUrl("https://user:secret@pay.example.com/"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode());
    }

    private HostedCheckoutUrlPolicy policy(boolean allowLoopbackHttp) {
        HostedCheckoutProperties properties = new HostedCheckoutProperties();
        properties.setAllowLoopbackHttp(allowLoopbackHttp);
        return new HostedCheckoutUrlPolicy(properties);
    }
}
