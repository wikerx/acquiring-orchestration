package com.scott.payment.admin.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementInternalClientPropertiesTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Admin 结算内部客户端启动时拒绝不可信地址、身份和超时配置。
 * @status : create
 */
class SettlementInternalClientPropertiesTest {

    @Test
    void shouldFailClosedUntilAnInjectedNonDevelopmentSecretIsProvided() {
        SettlementInternalClientProperties properties = new SettlementInternalClientProperties();
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");

        properties.setInternalSecret("dev-internal-service-secret");
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too weak");

        properties.setInternalSecret("unit-test-settlement-internal-secret-32-bytes");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void nacosServiceConfigurationShouldRequireTheSharedInjectedSecret() throws IOException {
        Path direct = Path.of("docs/deployment/nacos/service-admin-dev.yaml");
        Path configuration = Files.exists(direct)
                ? direct : Path.of("../docs/deployment/nacos/service-admin-dev.yaml");

        String content = Files.readString(configuration);
        String settlementSection = content.substring(content.indexOf("  settlement-client:"));
        assertThat(settlementSection)
                .contains("internal-secret: ${acquiring.internal-auth.edges.admin-settlement.active-secret}")
                .doesNotContain("SETTLEMENT_INTERNAL_AUTH_SECRET", "INTERNAL_SERVICE_AUTH_SECRET");
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
                .hasMessageContaining("secret");

        SettlementInternalClientProperties unresolvedSecret = new SettlementInternalClientProperties();
        unresolvedSecret.setInternalSecret("${INTERNAL_SERVICE_AUTH_SECRET}");
        assertThatThrownBy(unresolvedSecret::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");

        SettlementInternalClientProperties caller = new SettlementInternalClientProperties();
        caller.setInternalCaller("service-job");
        assertThatThrownBy(caller::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("caller");

        SettlementInternalClientProperties timeout = new SettlementInternalClientProperties();
        timeout.setInternalSecret("unit-test-settlement-internal-secret-32-bytes");
        timeout.setReadTimeoutMillis(0);
        assertThatThrownBy(timeout::validate).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeouts");
    }
}
