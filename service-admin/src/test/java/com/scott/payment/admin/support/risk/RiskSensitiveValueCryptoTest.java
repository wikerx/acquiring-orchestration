package com.scott.payment.admin.support.risk;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 风控敏感值主密钥失败关闭和轮换兼容测试。
 */
class RiskSensitiveValueCryptoTest {

    /**
     * 历史开发环境固定密钥，仅用于构造兼容性回归密文。
     */
    private static final String LEGACY_DEV_SECRET = "local-risk-secret-change-me";

    /**
     * 验证缺少进程外主密钥时初始化失败。
     */
    @Test
    void shouldFailClosedWhenSecretIsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new RiskSensitiveValueCrypto(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("payment.risk.secret");
    }

    /**
     * 验证 dev 缺少密钥时错误信息提供本地引导脚本，而不是只报告抽象门禁失败。
     */
    @Test
    void shouldExplainLocalBootstrapWhenDevSecretIsMissing() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertThatThrownBy(() -> new RiskSensitiveValueCrypto(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("service-admin-dev.yaml")
                .hasMessageContaining("Nacos");
    }

    /**
     * 验证当前主密钥能够完成敏感值加解密。
     */
    @Test
    void shouldEncryptAndDecryptWithConfiguredSecret() {
        RiskSensitiveValueCrypto crypto = crypto("current-risk-secret-0123456789abcdef");

        String cipherText = crypto.encrypt("4111111111111111");

        assertThat(cipherText).doesNotContain("4111111111111111");
        assertThat(crypto.decrypt(cipherText)).isEqualTo("4111111111111111");
    }

    /**
     * 验证轮换后的实例仍能读取历史主密钥生成的存量密文。
     */
    @Test
    void shouldDecryptPreviousKeyDuringRotationWindow() {
        String previousSecret = "previous-risk-secret-0123456789abcdef";
        String cipherText = crypto(previousSecret).encrypt("sensitive-value");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("payment.risk.secret", "current-risk-secret-0123456789abcdef")
                .withProperty("payment.risk.previous-secrets", previousSecret);
        environment.setActiveProfiles("prod");

        RiskSensitiveValueCrypto rotatedCrypto = new RiskSensitiveValueCrypto(environment);

        assertThat(rotatedCrypto.decrypt(cipherText)).isEqualTo("sensitive-value");
    }

    /**
     * 验证纯 dev 环境显式开启兼容后，可以读取历史开发默认密钥生成的存量密文。
     */
    @Test
    void shouldDecryptLegacyDevCipherOnlyWhenCompatibilityIsExplicitlyEnabled() throws Exception {
        String legacyCipherText = encryptWithSecret(LEGACY_DEV_SECRET, "legacy-sensitive-value");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("payment.risk.secret", "current-risk-secret-0123456789abcdef")
                .withProperty("payment.risk.allow-legacy-dev-key-decryption", "true");
        environment.setActiveProfiles("dev");

        RiskSensitiveValueCrypto crypto = new RiskSensitiveValueCrypto(environment);

        assertThat(crypto.decrypt(legacyCipherText)).isEqualTo("legacy-sensitive-value");
        String currentCipherText = crypto.encrypt("new-sensitive-value");
        assertThatThrownBy(() -> decryptWithSecret(LEGACY_DEV_SECRET, currentCipherText))
                .isInstanceOf(AEADBadTagException.class);
        assertThatThrownBy(() -> crypto(LEGACY_DEV_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    /**
     * 验证生产、UAT 及混合 profile 均拒绝启用历史开发密钥兼容。
     */
    @Test
    void shouldRejectLegacyDevCompatibilityOutsideIsolatedDevProfile() {
        for (String[] profiles : new String[][]{{"prod"}, {"uat"}, {"dev", "prod"}}) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("payment.risk.secret", "current-risk-secret-0123456789abcdef")
                    .withProperty("payment.risk.allow-legacy-dev-key-decryption", "true");
            environment.setActiveProfiles(profiles);

            assertThatThrownBy(() -> new RiskSensitiveValueCrypto(environment))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("only allowed for the isolated dev profile")
                    .hasMessageNotContaining(LEGACY_DEV_SECRET);
        }
    }

    /**
     * 使用与历史实现相同的 AES-GCM 派生规则构造回归密文。
     *
     * @param secret 历史主密钥
     * @param plainText 待加密文本
     * @return 历史格式密文
     */
    private String encryptWithSecret(String secret, String plainText) throws Exception {
        byte[] iv = new byte[12];
        for (int index = 0; index < iv.length; index++) {
            iv[index] = (byte) (index + 1);
        }
        byte[] key = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * 使用指定历史密钥尝试解密，用于证明新密文不会回退到旧密钥加密。
     *
     * @param secret 历史主密钥
     * @param cipherText AES-GCM 密文
     * @return 解密文本
     */
    private String decryptWithSecret(String secret, String cipherText) throws Exception {
        String[] parts = cipherText.split("\\.", 2);
        byte[] key = MessageDigest.getInstance("SHA-256")
                .digest(secret.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
        return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
    }

    /**
     * 使用指定强密钥创建生产模式加密组件。
     *
     * @param secret 当前主密钥
     * @return 风控敏感值加密组件
     */
    private RiskSensitiveValueCrypto crypto(String secret) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("payment.risk.secret", secret);
        environment.setActiveProfiles("prod");
        return new RiskSensitiveValueCrypto(environment);
    }
}
