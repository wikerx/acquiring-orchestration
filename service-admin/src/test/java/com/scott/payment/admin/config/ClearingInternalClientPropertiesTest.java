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
 * @classname : ClearingInternalClientPropertiesTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Admin 清分内部客户端在启动前拒绝不可信地址、身份和签名配置。
 * @status : create
 */
class ClearingInternalClientPropertiesTest {

    @Test
    void shouldFailClosedUntilAnInjectedNonDevelopmentSecretIsProvided() {
        ClearingInternalClientProperties properties = new ClearingInternalClientProperties();
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");

        properties.setInternalSecret("dev-internal-service-secret");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too weak");

        properties.setInternalSecret("unit-test-clearing-internal-secret-32-bytes");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void nacosServiceConfigurationShouldRequireTheSharedInjectedSecret() throws IOException {
        Path direct = Path.of("docs/deployment/nacos/service-admin-dev.yaml");
        Path configuration = Files.exists(direct)
                ? direct : Path.of("../docs/deployment/nacos/service-admin-dev.yaml");

        String content = Files.readString(configuration);
        String clearingSection = content.substring(
                content.indexOf("  clearing-client:"), content.indexOf("  settlement-client:"));
        assertThat(clearingSection)
                .contains("internal-secret: ${acquiring.internal-auth.edges.admin-clearing.active-secret}")
                .doesNotContain("INTERNAL_SERVICE_AUTH_SECRET");
    }

    @Test
    void shouldRejectBlankSecretAndUnexpectedCaller() {
        ClearingInternalClientProperties blankSecret = new ClearingInternalClientProperties();
        blankSecret.setInternalSecret(" ");
        assertThatThrownBy(blankSecret::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");

        ClearingInternalClientProperties unexpectedCaller = new ClearingInternalClientProperties();
        unexpectedCaller.setInternalCaller("service-job");
        assertThatThrownBy(unexpectedCaller::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("caller");
    }

    @Test
    void shouldRejectUnsafeBaseUrlAndInvalidTimeout() {
        ClearingInternalClientProperties unsafeUrl = new ClearingInternalClientProperties();
        unsafeUrl.setInternalSecret("unit-test-clearing-internal-secret-32-bytes");
        unsafeUrl.setBaseUrl("file:///tmp/service-clearing");
        assertThatThrownBy(unsafeUrl::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base-url");

        ClearingInternalClientProperties invalidTimeout = new ClearingInternalClientProperties();
        invalidTimeout.setInternalSecret("unit-test-clearing-internal-secret-32-bytes");
        invalidTimeout.setReadTimeoutMillis(0);
        assertThatThrownBy(invalidTimeout::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeouts");
    }
}
