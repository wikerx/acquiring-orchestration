package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import org.junit.jupiter.api.Test;

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
        HostedCheckoutUrlPolicy policy = new HostedCheckoutUrlPolicy();

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
        HostedCheckoutUrlPolicy policy = new HostedCheckoutUrlPolicy();

        assertThatThrownBy(() -> policy.normalizePlatformBaseUrl("https://user:secret@pay.example.com/"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode());
    }
}
