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
 * @description : LuhnMod10UtilsTests 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
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
