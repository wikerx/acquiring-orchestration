package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.security.OutboundHttpUrlPolicy;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 dev/test 可使用本地回调目标，严格环境只允许解析到公网地址的 HTTPS URL。
 */
class MerchantCallbackTargetValidatorTests {

    /**
     * 验证开发环境允许私网 HTTPS 回调目标。
     */
    @Test
    void shouldAllowPrivateHttpsTarget() {
        MerchantCallbackTargetValidator validator = developmentValidator();

        assertThatCode(() -> validator.validate("https://10.1.2.3:8443/callback"))
                .doesNotThrowAnyException();
    }

    /**
     * 验证开发环境允许回环 HTTP 回调目标。
     */
    @Test
    void shouldAllowLoopbackHttpTarget() {
        MerchantCallbackTargetValidator validator = developmentValidator();

        assertThatCode(() -> validator.validate("http://127.0.0.1:9000/merchant/callback/payment"))
                .doesNotThrowAnyException();
    }

    /**
     * 验证严格模式允许只解析到公网地址的 HTTPS 目标。
     */
    @Test
    void shouldAcceptPublicHttpsTarget() {
        MerchantCallbackTargetValidator validator = developmentValidator();

        assertThatCode(() -> validator.validate("https://merchant.example/callback"))
                .doesNotThrowAnyException();
    }

    /**
     * 验证回调 URL 使用非 HTTP 协议时拒绝出站。
     */
    @Test
    void shouldRejectUnsupportedScheme() {
        MerchantCallbackTargetValidator validator = developmentValidator();

        assertThatThrownBy(() -> validator.validate("ftp://127.0.0.1/callback"))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.CALLBACK_URL_NOT_ALLOWED.getCode());
    }

    /**
     * 验证严格模式拒绝私网地址和混合公网私网解析结果。
     */
    @Test
    void shouldRejectPrivateAndMixedDnsAnswersInStrictMode() throws Exception {
        MerchantCallbackTargetValidator privateTarget = new MerchantCallbackTargetValidator(
                new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY,
                        host -> new InetAddress[]{InetAddress.getByName("10.0.0.8")}));
        MerchantCallbackTargetValidator mixedTarget = new MerchantCallbackTargetValidator(
                new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY,
                        host -> new InetAddress[]{
                                InetAddress.getByName("8.8.8.8"),
                                InetAddress.getByName("127.0.0.1")
                        }));

        assertThatThrownBy(() -> privateTarget.validate("https://merchant.example/callback"))
                .isInstanceOf(MerchantCallbackTargetRejectedException.class);
        assertThatThrownBy(() -> mixedTarget.validate("https://merchant.example/callback"))
                .isInstanceOf(MerchantCallbackTargetRejectedException.class);
    }

    /**
     * 验证缺少运行环境时默认严格拒绝私网目标。
     */
    @Test
    void shouldFailClosedForPrivateTargetWhenConstructedWithoutEnvironment() {
        MerchantCallbackTargetValidator validator = new MerchantCallbackTargetValidator();

        assertThatThrownBy(() -> validator.validate("https://127.0.0.1/callback"))
                .isInstanceOf(MerchantCallbackTargetRejectedException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.CALLBACK_URL_NOT_ALLOWED.getCode());
    }

    /**
     * 验证连接阶段 DNS 解析会阻断重绑定后的私网地址。
     */
    @Test
    void connectionDnsResolverShouldRejectAReboundPrivateAddress() throws Exception {
        MerchantCallbackTargetValidator validator = new MerchantCallbackTargetValidator(
                new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.Mode.PUBLIC_HTTPS_ONLY,
                        host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")}));
        MerchantCallbackDnsResolver resolver = new MerchantCallbackDnsResolver(validator);

        assertThatThrownBy(() -> resolver.resolve("merchant.example"))
                .isInstanceOf(MerchantCallbackTargetRejectedException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.CALLBACK_URL_NOT_ALLOWED.getCode());
    }

    /**
     * 构造允许开发环境私网目标的回调校验器。
     */
    private MerchantCallbackTargetValidator developmentValidator() {
        return new MerchantCallbackTargetValidator(
                new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.Mode.DEVELOPMENT));
    }
}
