package com.scott.payment.admin.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Admin 结算内部客户端启动时拒绝不可信地址、身份和超时配置。 */
class SettlementInternalClientPropertiesTest {

    @Test
    void defaultsShouldBeValidForLocalDevelopment() {
        new SettlementInternalClientProperties().validate();
    }

    @Test
    void shouldRejectUnsafeBaseUrlBlankSecretUnexpectedCallerAndInvalidTimeout() {
        SettlementInternalClientProperties unsafeUrl = new SettlementInternalClientProperties();
        unsafeUrl.setBaseUrl("file:///tmp/service-settlement");
        assertThatThrownBy(unsafeUrl::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base-url");

        SettlementInternalClientProperties blankSecret = new SettlementInternalClientProperties();
        blankSecret.setInternalSecret(" ");
        assertThatThrownBy(blankSecret::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HMAC");

        SettlementInternalClientProperties caller = new SettlementInternalClientProperties();
        caller.setInternalCaller("service-job");
        assertThatThrownBy(caller::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HMAC");

        SettlementInternalClientProperties timeout = new SettlementInternalClientProperties();
        timeout.setReadTimeoutMillis(0);
        assertThatThrownBy(timeout::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeouts");
    }
}
