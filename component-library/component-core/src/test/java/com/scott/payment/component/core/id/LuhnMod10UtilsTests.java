package com.scott.payment.component.core.id;

import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LuhnMod10UtilsTests
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : Luhn Mod 10 Utils Tests 通用函数集合，位于 公共组件库，封装格式化、校验、脱敏、加密、编码或标准化逻辑，调用方以静态方法获取本地计算结果。
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
