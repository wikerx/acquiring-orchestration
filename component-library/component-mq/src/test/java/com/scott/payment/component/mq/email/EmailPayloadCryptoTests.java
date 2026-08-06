package com.scott.payment.component.mq.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailPayloadCryptoTests
 * @date : 2026-08-02 23:40
 * @email : scott_x@163.com
 * @description : 验证异步邮件敏感正文只以随机化 AES-GCM 密文保存且缺少密钥时拒绝处理
 * @status : create
 */
class EmailPayloadCryptoTests {

    /** 相同正文每次生成不同密文，且都能恢复原文。 */
    @Test
    void shouldEncryptWithRandomIvAndDecrypt() {
        EmailPayloadCrypto crypto = new EmailPayloadCrypto("unit-test-email-secret");

        String first = crypto.encrypt("one-time-code-content");
        String second = crypto.encrypt("one-time-code-content");

        assertThat(first).isNotEqualTo(second).doesNotContain("one-time-code-content");
        assertThat(crypto.decrypt(first)).isEqualTo("one-time-code-content");
        assertThat(crypto.decrypt(second)).isEqualTo("one-time-code-content");
    }

    /** 禁止使用源码内置默认密钥降级处理敏感邮件正文。 */
    @Test
    void shouldRejectEncryptionWhenSecretIsMissing() {
        EmailPayloadCrypto crypto = new EmailPayloadCrypto(" ");

        assertThatThrownBy(() -> crypto.encrypt("sensitive-content"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("email encryption secret is not configured");
    }
}
