package com.scott.payment.admin.support.risk;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.security.crypto.SensitiveFieldCipher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisputeAlertCredentialCryptoTest {

    private static final String MASTER = "fixture-master-key-ae4e9b17cb209100f05c";
    private static final String REFERENCE = "e57c5867-ba65-4510-99be-c19a903e2e94";

    @Test
    void ciphertextIsRandomizedAndBoundToAccountReferenceAndField() {
        var crypto = new DisputeAlertCredentialCrypto(new MockEnvironment().withProperty("payment.risk.secret", MASTER));
        String cipher = crypto.encrypt(29, REFERENCE, "private", "private-fixture");
        String aad = "dispute-alert-credential-v1:29:" + REFERENCE + ":private";
        String key = "dispute-alert-credential-v1:" + MASTER;

        assertThat(cipher).doesNotContain("private-fixture");
        assertThat(crypto.encrypt(29, REFERENCE, "private", "private-fixture")).isNotEqualTo(cipher);
        assertThat(SensitiveFieldCipher.decrypt(cipher, key, aad)).isEqualTo("private-fixture");
        assertThatThrownBy(() -> SensitiveFieldCipher.decrypt(cipher, key, aad.replace(":29:", ":30:")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SensitiveFieldCipher.decrypt(cipher, key, aad.replace(":private", ":public")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void persistedCredentialsRequireAnExistingStableMasterKey() {
        var environment = new MockEnvironment().withProperty("payment.risk.allow-ephemeral-test-secret", "true");
        environment.setActiveProfiles("test");
        var crypto = new DisputeAlertCredentialCrypto(environment);

        assertThatThrownBy(() -> crypto.encrypt(29, REFERENCE, "private", "private-fixture"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("预警凭据加密失败").hasMessageNotContaining("private-fixture");
    }
}
