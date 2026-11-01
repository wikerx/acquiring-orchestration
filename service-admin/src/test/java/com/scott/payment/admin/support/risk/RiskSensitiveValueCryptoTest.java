package com.scott.payment.admin.support.risk;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 风控敏感值主密钥失败关闭和轮换兼容测试。
 */
class RiskSensitiveValueCryptoTest {

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
