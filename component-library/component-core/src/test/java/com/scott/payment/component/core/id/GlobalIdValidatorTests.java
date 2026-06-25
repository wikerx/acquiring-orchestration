package com.scott.payment.component.core.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
