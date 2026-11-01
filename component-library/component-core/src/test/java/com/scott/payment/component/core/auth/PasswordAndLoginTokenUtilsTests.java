package com.scott.payment.component.core.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密码哈希与登录 token 工具测试
 */
class PasswordAndLoginTokenUtilsTests {

    /**
     * 验证 PBKDF2 密码哈希可以正确匹配原始密码，同时拒绝错误密码。
     */
    @Test
    void shouldHashAndVerifyPassword() {
        String salt = PasswordHashUtils.generateSalt();
        String hash = PasswordHashUtils.hashPassword("Merchant@123456", salt);

        assertThat(hash).isNotBlank();
        assertThat(PasswordHashUtils.matches("Merchant@123456", salt, hash)).isTrue();
        assertThat(PasswordHashUtils.matches("Wrong@123456", salt, hash)).isFalse();
    }

    /**
     * 验证登录 token 每次随机生成，并且 token_hash 稳定可重复计算。
     */
    @Test
    void shouldGenerateRandomTokenAndStableHash() {
        String firstToken = LoginTokenUtils.generateToken();
        String secondToken = LoginTokenUtils.generateToken();
        String firstHash = LoginTokenUtils.hashToken(firstToken);

        assertThat(firstToken).isNotBlank();
        assertThat(secondToken).isNotBlank();
        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(firstHash).hasSize(64);
        assertThat(LoginTokenUtils.hashToken(firstToken)).isEqualTo(firstHash);
    }
}
