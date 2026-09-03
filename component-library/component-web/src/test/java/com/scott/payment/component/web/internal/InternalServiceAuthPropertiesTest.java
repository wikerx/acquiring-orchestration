package com.scott.payment.component.web.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 调用方隔离的内部服务鉴权配置启动门禁测试。 */
class InternalServiceAuthPropertiesTest {

    @Test
    void shouldRequireCallerScopedCredentials() {
        InternalServiceAuthProperties properties = new InternalServiceAuthProperties();

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("caller credentials are required");
    }

    @Test
    void shouldRejectWeakOrUnresolvedSecrets() {
        InternalServiceAuthProperties.CallerCredential credential = credential("${UNRESOLVED_SECRET}");
        InternalServiceAuthProperties properties = properties(credential);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active secret is required");

        credential.setActiveSecret("short-secret");
        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("too weak");
    }

    @Test
    void shouldAcceptDistinctActiveAndPreviousSecretsWithInternalPaths() {
        InternalServiceAuthProperties.CallerCredential credential =
                credential("active-internal-secret-at-least-32-bytes");
        credential.setPreviousSecret("previous-internal-secret-at-least-32-bytes");

        assertThatCode(properties(credential)::validate).doesNotThrowAnyException();
    }

    private InternalServiceAuthProperties properties(InternalServiceAuthProperties.CallerCredential credential) {
        InternalServiceAuthProperties properties = new InternalServiceAuthProperties();
        properties.setCallers(Map.of("service-admin", credential));
        return properties;
    }

    private InternalServiceAuthProperties.CallerCredential credential(String activeSecret) {
        InternalServiceAuthProperties.CallerCredential credential = new InternalServiceAuthProperties.CallerCredential();
        credential.setActiveSecret(activeSecret);
        credential.setAllowedPaths(List.of("/internal/settlement/**"));
        return credential;
    }
}
