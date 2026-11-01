package com.scott.payment.component.core.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Global ID Validator Tests 自动化测试类，位于 公共组件库，验证当前模块的正常路径、异常边界和回归场景。
 */
class GlobalIdValidatorTests {

    @Test
    void isValidShouldAcceptValidId() {
        assertThat(GlobalIdValidator.isValid("2606241530181230000458")).isTrue();
    }

    @Test
    void isValidShouldRejectInvalidCheckDigit() {
        assertThat(GlobalIdValidator.isValid("2606241530181230000457")).isFalse();
    }

    @Test
    void isValidShouldRejectBlankAndNull() {
        assertThat(GlobalIdValidator.isValid(null)).isFalse();
        assertThat(GlobalIdValidator.isValid("")).isFalse();
    }
}
