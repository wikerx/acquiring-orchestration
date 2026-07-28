package com.scott.payment.component.core.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalIdValidatorTests
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : Global ID Validator Tests 自动化测试类，位于 公共组件库，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
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
