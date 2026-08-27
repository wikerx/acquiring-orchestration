package com.scott.payment.job.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证 Job 清分内部客户端在启动前拒绝不可信地址、身份和签名配置。 */
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
                .hasMessageContaining("development");

        properties.setInternalSecret("unit-test-clearing-internal-secret-32-bytes");
        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void applicationConfigurationShouldRequireTheSharedInjectedSecret() throws IOException {
        Path direct = Path.of("service-job/src/main/resources/application.yml");
        Path configuration = Files.exists(direct)
                ? direct : Path.of("src/main/resources/application.yml");

        assertThat(Files.readString(configuration))
                .contains("internal-secret: ${INTERNAL_SERVICE_AUTH_SECRET}")
                .doesNotContain("INTERNAL_SERVICE_AUTH_SECRET:dev-internal-service-secret");
    }

    @Test
    void shouldRejectBlankSecretAndUnexpectedCaller() {
        ClearingInternalClientProperties blankSecret = new ClearingInternalClientProperties();
        blankSecret.setInternalSecret(" ");
        assertThatThrownBy(blankSecret::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");

        ClearingInternalClientProperties unexpectedCaller = new ClearingInternalClientProperties();
        unexpectedCaller.setInternalCaller("service-admin");
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
        invalidTimeout.setConnectTimeoutMillis(0);
        assertThatThrownBy(invalidTimeout::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeouts");
    }
}
