package com.scott.payment.component.db.auth.support;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MfaSecretConfigurationTests
 * @date : 2026-10-25 00:00
 * @email : scott_x@163.com
 * @description : MFA 主密钥门禁回归测试，验证服务身份、环境限制、密钥强度以及未初始化时失败关闭。
 * @status : create
 */
class MfaSecretConfigurationTests {

    private static final String STRONG_SECRET = "0123456789abcdef0123456789abcdef";

    @AfterEach
    void tearDown() {
        MfaSecretCrypto.resetForTests();
    }

    @Test
    void shouldInitializeWithExplicitStrongSecret() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "service-admin")
                .withProperty("payment.mfa.secret", STRONG_SECRET);
        environment.setActiveProfiles("prod");

        new MfaSecretConfiguration(environment).afterPropertiesSet();
        String cipherText = MfaSecretCrypto.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(cipherText).startsWith("v1:");
        assertThat(MfaSecretCrypto.decrypt(cipherText)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void shouldAllowEphemeralSecretOnlyForIsolatedTestProfile() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "service-merchant")
                .withProperty("payment.mfa.allow-ephemeral-test-secret", "true");
        environment.setActiveProfiles("test");

        new MfaSecretConfiguration(environment).afterPropertiesSet();

        assertThat(MfaSecretCrypto.decrypt(MfaSecretCrypto.encrypt("TEST-ONLY")))
                .isEqualTo("TEST-ONLY");
        assertThat(MfaSecretKeyPolicy.resolve(null, true, new String[]{"test"}))
                .isNotEqualTo(MfaSecretKeyPolicy.resolve(null, true, new String[]{"test"}));
    }

    @Test
    void shouldRejectMissingSecretInRestrictedAndUnknownEnvironments() {
        MockEnvironment prod = new MockEnvironment()
                .withProperty("spring.application.name", "service-admin")
                .withProperty("payment.mfa.allow-ephemeral-test-secret", "true");
        prod.setActiveProfiles("prod");
        MockEnvironment unknown = new MockEnvironment()
                .withProperty("spring.application.name", "service-merchant");

        assertThatThrownBy(() -> new MfaSecretConfiguration(prod).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payment.mfa.secret");
        assertThatThrownBy(() -> new MfaSecretConfiguration(unknown).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payment.mfa.secret");
    }

    @Test
    void shouldRejectEphemeralSecretOutsideIsolatedTestProfile() {
        MockEnvironment dev = new MockEnvironment()
                .withProperty("spring.application.name", "service-admin")
                .withProperty("payment.mfa.allow-ephemeral-test-secret", "true");
        dev.setActiveProfiles("dev");
        MockEnvironment mixed = new MockEnvironment()
                .withProperty("spring.application.name", "service-merchant")
                .withProperty("payment.mfa.allow-ephemeral-test-secret", "true");
        mixed.setActiveProfiles("test", "dev");

        assertThatThrownBy(() -> new MfaSecretConfiguration(dev).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payment.mfa.secret");
        assertThatThrownBy(() -> new MfaSecretConfiguration(mixed).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payment.mfa.secret");
    }

    @Test
    void shouldRejectWeakSecretWithoutEchoingIt() {
        MockEnvironment weak = new MockEnvironment()
                .withProperty("spring.application.name", "service-admin")
                .withProperty("payment.mfa.secret", "short-secret");
        weak.setActiveProfiles("prod");

        assertThatThrownBy(() -> new MfaSecretConfiguration(weak).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("short-secret");
    }

    @Test
    void shouldDecryptOldCipherDuringBoundedKeyRotation() {
        String previousSecret = "previous-mfa-secret-0123456789abcdef";
        String currentSecret = "current-mfa-secret-0123456789abcdef0";
        MfaSecretCrypto.configure(previousSecret);
        String oldCipherText = MfaSecretCrypto.encrypt("JBSWY3DPEHPK3PXP");

        MfaSecretCrypto.configure(currentSecret, List.of(previousSecret));
        String newCipherText = MfaSecretCrypto.encrypt("NEW-MFA-SECRET");

        assertThat(MfaSecretCrypto.decrypt(oldCipherText)).isEqualTo("JBSWY3DPEHPK3PXP");
        MfaSecretCrypto.configure(previousSecret);
        assertThatThrownBy(() -> MfaSecretCrypto.decrypt(newCipherText))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void shouldRejectInvalidPreviousKeyConfiguration() {
        assertThatThrownBy(() -> MfaSecretCrypto.configure(STRONG_SECRET, List.of(STRONG_SECRET)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate");
        assertThatThrownBy(() -> MfaSecretCrypto.configure(STRONG_SECRET,
                List.of("previous-mfa-secret-00000000000001", "previous-mfa-secret-00000000000002",
                        "previous-mfa-secret-00000000000003")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at most 2");
        assertThatThrownBy(() -> MfaSecretCrypto.configure(STRONG_SECRET,
                List.of("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("too-short");
    }

    @Test
    void shouldLoadPreviousKeyFromServiceConfiguration() throws Exception {
        String previousSecret = "previous-mfa-secret-0123456789abcdef";
        MfaSecretCrypto.configure(previousSecret);
        String oldCipherText = MfaSecretCrypto.encrypt("ROTATING-MFA");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "service-admin")
                .withProperty("payment.mfa.secret", STRONG_SECRET)
                .withProperty("payment.mfa.previous-secrets", previousSecret);
        environment.setActiveProfiles("prod");

        new MfaSecretConfiguration(environment).afterPropertiesSet();

        assertThat(MfaSecretCrypto.decrypt(oldCipherText)).isEqualTo("ROTATING-MFA");
    }

    @Test
    void shouldRejectCipherTextAfterKeyChanges() {
        MfaSecretCrypto.configure(STRONG_SECRET);
        String cipherText = MfaSecretCrypto.encrypt("JBSWY3DPEHPK3PXP");
        MfaSecretCrypto.configure("fedcba9876543210fedcba9876543210");

        assertThatThrownBy(() -> MfaSecretCrypto.decrypt(cipherText))
                .isInstanceOf(ServiceException.class)
                .hasMessageNotContaining(STRONG_SECRET);
    }

    @Test
    void shouldFailClosedBeforeStartupInitialization() {
        assertThatThrownBy(() -> MfaSecretCrypto.encrypt("JBSWY3DPEHPK3PXP"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void shouldNotRequireMfaKeyForServicesThatDoNotOwnMfaData() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "service-payment");
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new MfaSecretConfiguration(environment).afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> MfaSecretCrypto.encrypt("JBSWY3DPEHPK3PXP"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("not initialized");
    }
}
