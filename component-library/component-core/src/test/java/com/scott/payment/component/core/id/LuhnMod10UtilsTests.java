package com.scott.payment.component.core.id;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LuhnMod10UtilsTests {

    @Test
    void calculateCheckDigitShouldMatchSample() {
        int checkDigit = LuhnMod10Utils.calculateCheckDigit("260624153018123000045");

        assertThat(checkDigit).isEqualTo(8);
    }

    @Test
    void validateShouldAcceptSampleFullId() {
        assertThat(LuhnMod10Utils.validate("2606241530181230000458")).isTrue();
    }

    @Test
    void validateShouldRejectNonDigitValue() {
        assertThat(LuhnMod10Utils.validate("26062415301812300004A8")).isFalse();
    }

    @Test
    void validateShouldRejectInvalidLength() {
        assertThat(LuhnMod10Utils.validate("260624153018123000045")).isFalse();
    }

    @Test
    void calculateCheckDigitShouldRejectInvalidBody() {
        assertThatThrownBy(() -> LuhnMod10Utils.calculateCheckDigit("26062415301812300004A"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("全局唯一标识正文格式非法");
    }
}
