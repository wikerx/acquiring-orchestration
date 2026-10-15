package com.scott.payment.component.core.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** PCI 管理密码规则的边界测试。 */
class PasswordPolicyTest {

    @Test
    void shouldAcceptPasswordWithRequiredLengthAndCharacterClasses() {
        assertThat(PasswordPolicy.isValid("AdminAccess@2026")).isTrue();
        assertThat(PasswordPolicy.isValid("Ab1!56789012")).isTrue();
    }

    @Test
    void shouldRejectPasswordOutsideLengthBoundary() {
        assertThat(PasswordPolicy.isValid("Ab1!5678901")).isFalse();
        assertThat(PasswordPolicy.isValid("Aa1!" + "x".repeat(61))).isFalse();
    }

    @Test
    void shouldRejectPasswordMissingAnyRequiredCharacterClassOrContainingWhitespace() {
        assertThat(PasswordPolicy.isValid("adminaccess@2026")).isFalse();
        assertThat(PasswordPolicy.isValid("ADMINACCESS@2026")).isFalse();
        assertThat(PasswordPolicy.isValid("AdminAccessOnly!")).isFalse();
        assertThat(PasswordPolicy.isValid("AdminAccess2026")).isFalse();
        assertThat(PasswordPolicy.isValid("Admin Access@2026")).isFalse();
    }
}
