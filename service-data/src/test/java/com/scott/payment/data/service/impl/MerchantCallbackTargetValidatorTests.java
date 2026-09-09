package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
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
 * @description : 验证商户回调出站地址不会访问平台私网。
 * @status : create
 */
class MerchantCallbackTargetValidatorTests {

    @Test
    void shouldRejectHostResolvingToPrivateAddress() throws Exception {
        MerchantCallbackTargetValidator validator = new MerchantCallbackTargetValidator(
                new DataMerchantNotificationProperties(),
                host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{10, 1, 2, 3})});

        assertThatThrownBy(() -> validator.validate("https://merchant.example/callback"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("private or reserved");
    }

    @Test
    void shouldRejectPlainHttpByDefault() {
        MerchantCallbackTargetValidator validator = new MerchantCallbackTargetValidator(
                new DataMerchantNotificationProperties(), host -> new InetAddress[0]);

        assertThatThrownBy(() -> validator.validate("http://merchant.example/callback"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void shouldAcceptPublicHttpsTarget() throws Exception {
        MerchantCallbackTargetValidator validator = new MerchantCallbackTargetValidator(
                new DataMerchantNotificationProperties(),
                host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})});

        assertThatCode(() -> validator.validate("https://merchant.example/callback"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowPrivateHttpOnlyWhenBothDevOverridesAreEnabled() {
        DataMerchantNotificationProperties properties = new DataMerchantNotificationProperties();
        properties.setAllowHttp(true);
        properties.setAllowPrivateNetwork(true);
        MerchantCallbackTargetValidator validator = new MerchantCallbackTargetValidator(
                properties, host -> new InetAddress[0]);

        assertThatCode(() -> validator.validate("http://127.0.0.1:18080/callback"))
                .doesNotThrowAnyException();
    }
}
