package com.scott.payment.component.core.id;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LuhnMod10UtilsTests
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Luhn Mod10 Utils Tests，位于 component-library/component-core 的测试层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
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
