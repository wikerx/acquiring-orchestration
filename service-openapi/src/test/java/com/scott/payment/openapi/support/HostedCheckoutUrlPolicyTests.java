package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.security.OutboundHttpUrlPolicy;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HostedCheckoutUrlPolicyTests
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : Hosted Checkout 商户 URL 和平台前端 URL 结构策略测试。
 * @status : create
 */
class HostedCheckoutUrlPolicyTests {

    @Test
    void shouldAllowHttpAndHttpsForPublicPrivateAndLoopbackHosts() {
        HostedCheckoutUrlPolicy policy = developmentPolicy();

        assertThat(policy.validateMerchantUrl(
                "https://merchant.example/result", "transactionInfo.redirectUrl"))
                .isEqualTo("https://merchant.example/result");
        assertThat(policy.validateMerchantUrl(
                "http://192.168.1.10/notify", "transactionInfo.callbackUrl"))
                .isEqualTo("http://192.168.1.10/notify");
        assertThat(policy.resolvePlatformOrigin("http://[::1]:5175/checkout"))
                .isEqualTo("http://[::1]:5175");
        assertThat(policy.normalizePlatformBaseUrl("http://pay.example.com/checkout/"))
                .isEqualTo("http://pay.example.com/checkout");
    }

    @Test
    void shouldRejectUnsafePlatformConfigurationAsInternalError() {
        HostedCheckoutUrlPolicy policy = developmentPolicy();

        assertThatThrownBy(() -> policy.normalizePlatformBaseUrl("https://user:secret@pay.example.com/"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode());
    }

    @Test
    void shouldRejectPrivateCallbackWithStableExternalCodeInStrictMode() throws Exception {
        HostedCheckoutUrlPolicy policy = new HostedCheckoutUrlPolicy(new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY,
                host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")}));

        assertThatThrownBy(() -> policy.validateMerchantUrl(
                "https://merchant.example/callback", "transactionInfo.callbackUrl"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.CALLBACK_URL_NOT_ALLOWED.getCode());
    }

    @Test
    void shouldAcceptPublicHttpsCallbackInStrictMode() throws Exception {
        HostedCheckoutUrlPolicy policy = new HostedCheckoutUrlPolicy(new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY,
                host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")}));

        assertThat(policy.validateMerchantUrl(
                "https://merchant.example/callback", "transactionInfo.callbackUrl"))
                .isEqualTo("https://merchant.example/callback");
    }

    @Test
    void shouldFailClosedForPrivateCallbackWhenConstructedWithoutEnvironment() {
        HostedCheckoutUrlPolicy policy = new HostedCheckoutUrlPolicy();

        assertThatThrownBy(() -> policy.validateMerchantUrl(
                "https://127.0.0.1/callback", "transactionInfo.callbackUrl"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.CALLBACK_URL_NOT_ALLOWED.getCode());
    }

    private HostedCheckoutUrlPolicy developmentPolicy() {
        return new HostedCheckoutUrlPolicy(
                new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.Mode.DEVELOPMENT));
    }
}
