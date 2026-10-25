package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.security.OutboundHttpUrlPolicy;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantCallbackTargetValidatorTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 dev/test 可使用本地回调目标，严格环境只允许解析到公网地址的 HTTPS URL。
 * @status : create
 */
class MerchantCallbackTargetValidatorTests {

    @Test
    void shouldAllowPrivateHttpsTarget() {
        MerchantCallbackTargetValidator validator = developmentValidator();

        assertThatCode(() -> validator.validate("https://10.1.2.3:8443/callback"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowLoopbackHttpTarget() {
        MerchantCallbackTargetValidator validator = developmentValidator();

        assertThatCode(() -> validator.validate("http://127.0.0.1:9000/merchant/callback/payment"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptPublicHttpsTarget() {
        MerchantCallbackTargetValidator validator = developmentValidator();

        assertThatCode(() -> validator.validate("https://merchant.example/callback"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnsupportedScheme() {
        MerchantCallbackTargetValidator validator = developmentValidator();

        assertThatThrownBy(() -> validator.validate("ftp://127.0.0.1/callback"))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.CALLBACK_URL_NOT_ALLOWED.getCode());
    }

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

    @Test
    void shouldFailClosedForPrivateTargetWhenConstructedWithoutEnvironment() {
        MerchantCallbackTargetValidator validator = new MerchantCallbackTargetValidator();

        assertThatThrownBy(() -> validator.validate("https://127.0.0.1/callback"))
                .isInstanceOf(MerchantCallbackTargetRejectedException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.CALLBACK_URL_NOT_ALLOWED.getCode());
    }

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

    private MerchantCallbackTargetValidator developmentValidator() {
        return new MerchantCallbackTargetValidator(
                new OutboundHttpUrlPolicy(OutboundHttpUrlPolicy.Mode.DEVELOPMENT));
    }
}
