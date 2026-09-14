package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantCallbackTargetValidatorTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证商户回调支持 HTTP/HTTPS 公网、内网和回环地址，同时拒绝非法 URL 结构。
 * @status : create
 */
class MerchantCallbackTargetValidatorTests {

    @Test
    void shouldAllowPrivateHttpsTarget() {
        MerchantCallbackTargetValidator validator = new MerchantCallbackTargetValidator();

        assertThatCode(() -> validator.validate("https://10.1.2.3:8443/callback"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowLoopbackHttpTarget() {
        MerchantCallbackTargetValidator validator = new MerchantCallbackTargetValidator();

        assertThatCode(() -> validator.validate("http://127.0.0.1:9000/merchant/callback/payment"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptPublicHttpsTarget() {
        MerchantCallbackTargetValidator validator = new MerchantCallbackTargetValidator();

        assertThatCode(() -> validator.validate("https://merchant.example/callback"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnsupportedScheme() {
        MerchantCallbackTargetValidator validator = new MerchantCallbackTargetValidator();

        assertThatThrownBy(() -> validator.validate("ftp://127.0.0.1/callback"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("HTTP or HTTPS");
    }
}
