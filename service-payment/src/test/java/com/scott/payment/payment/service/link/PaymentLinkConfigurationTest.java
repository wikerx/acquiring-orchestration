package com.scott.payment.payment.service.link;

import com.scott.payment.payment.config.PaymentLinkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;

/**
 * 新入口缺省兼容旧环境；开启时缺少迁移、域名或专用密钥必须启动失败。
 */
class PaymentLinkConfigurationTest {
    private final ApplicationContextRunner context=new ApplicationContextRunner().withUserConfiguration(Config.class);
    @Configuration(proxyBeanMethods=false)
    @EnableConfigurationProperties(PaymentLinkProperties.class)
    static class Config { }
    @Test void disabledFeatureStartsWithoutNewSecretsOrSchemaFlag() {
        context.run(c->{assertThat(c).hasNotFailed();assertThat(c.getBean(PaymentLinkProperties.class).isEnabled()).isFalse();});
    }
    @Test void enabledFeatureRejectsMissingStorageReadiness() {
        context.withPropertyValues("payment.link.enabled=true").run(c->assertThat(c).hasFailed());
    }
    @Test void enabledFeatureAcceptsDedicatedValidConfigurationAndHidesKeysInToString() {
        valid().run(c->{assertThat(c).hasNotFailed();assertThat(c.getBean(PaymentLinkProperties.class).toString()).doesNotContain("secret-unit-test-only");});
    }
    @Test void enabledFeatureRejectsExternalHttpOrigin() {
        valid().withPropertyValues("payment.link.frontend-base-url=http://pay.example.invalid").run(c->assertThat(c).hasFailed());
    }
    @Test void enabledFeatureRejectsWeakOrMissingActiveKeys() {
        valid().withPropertyValues("payment.link.signing-keys.v1=short").run(c->assertThat(c).hasFailed());
        valid().withPropertyValues("payment.link.active-key-version=v2").run(c->assertThat(c).hasFailed());
    }
    @Test void featureContainmentStartsWithoutTurningOffExistingStorageGuards() {
        context.withPropertyValues("payment.link.enabled=false","payment.link.storage-ready=true").run(c->{assertThat(c).hasNotFailed();assertThat(c.getBean(PaymentLinkProperties.class).isStorageReady()).isTrue();});
    }
    /** 发布配置必须能够实际解析，两层限流窗口不得因缩进错误合并或脱离所属配置。 */
    @Test void shippingServiceConfigsParseWithIndependentMerchantAndLinkRateWindows() {
        java.util.Properties openapi = null;
        for (String service : java.util.List.of("admin", "merchant", "payment", "openapi")) {
            var factory = new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
            factory.setResources(new org.springframework.core.io.FileSystemResource(
                    "../docs/deployment/nacos/service-" + service + "-dev.yaml"));
            java.util.Properties properties = factory.getObject();
            assertThat(properties).isNotNull();
            if ("openapi".equals(service)) openapi = properties;
        }
        assertThat(openapi).isNotNull();
        assertThat(openapi.getProperty("payment.openapi.rate-limit.window"))
                .contains("OPENAPI_MERCHANT_RATE_LIMIT_WINDOW");
        assertThat(openapi.getProperty("payment-link.browser.window"))
                .contains("PAYMENT_LINK_BROWSER_RATE_WINDOW");
        assertThat(openapi).doesNotContainKey("payment.openapi.window");
    }
    private ApplicationContextRunner valid() {
        return context.withPropertyValues("payment.link.enabled=true","payment.link.storage-ready=true","payment.link.frontend-base-url=https://pay.example.invalid","payment.link.active-key-version=v1","payment.link.signing-keys.v1=secret-unit-test-only-0123456789abcdef");
    }
}
