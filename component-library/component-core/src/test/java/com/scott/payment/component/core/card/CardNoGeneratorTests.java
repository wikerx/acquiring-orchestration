package com.scott.payment.component.core.card;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardNoGeneratorTests
 * @date : 2026-06-03 15:45
 * @email : scott_x@163.com
 * @description : 测试卡号生成工具单元测试，验证长度、前缀、Luhn 校验和异常保护
 * @status : create
 */
class CardNoGeneratorTests {

    /**
     * 验证 Mastercard 测试卡号生成结果符合 16 位长度、51-55 前缀和 Luhn 校验规则。
     */
    @Test
    void shouldGenerateValidMastercardNumber() {
        String cardNumber = CardNoGenerator.generateMasterCardNumber();

        assertThat(cardNumber).hasSize(16);
        assertThat(cardNumber).matches("^5[1-5]\\d{14}$");
        assertThat(CardNoGenerator.isValidCreditCardNumber(cardNumber)).isTrue();
    }

    /**
     * 验证各卡品牌默认生成长度符合当前工具声明的测试场景。
     */
    @Test
    void shouldGenerateValidCardNumberByBrand() {
        assertCardNumber(CardNoGenerator.generateVisaCardNumber(), 16);
        assertCardNumber(CardNoGenerator.generateAmexCardNumber(), 15);
        assertCardNumber(CardNoGenerator.generateDiscoverCardNumber(), 16);
        assertCardNumber(CardNoGenerator.generateDinersCardNumber(), 14);
        assertCardNumber(CardNoGenerator.generateJcbCardNumber(), 16);
        assertCardNumber(CardNoGenerator.generateEnrouteCardNumber(), 15);
        assertCardNumber(CardNoGenerator.generateVoyagerCardNumber(), 15);
    }

    /**
     * 验证批量生成数量正确，并且每一条卡号都满足 Luhn 校验规则。
     */
    @Test
    void shouldGenerateBatchMastercardNumbers() {
        List<String> cardNumbers = CardNoGenerator.generateMasterCardNumbers(5);

        assertThat(cardNumbers).hasSize(5);
        assertThat(cardNumbers).allSatisfy(cardNumber -> {
            assertThat(cardNumber).hasSize(16);
            assertThat(CardNoGenerator.isValidCreditCardNumber(cardNumber)).isTrue();
        });
    }

    /**
     * 验证自定义前缀生成时，结果保留指定前缀并通过 Luhn 校验。
     */
    @Test
    void shouldGenerateCardNumberWithCustomPrefix() {
        String cardNumber = CardNoGenerator.generateCardNumber(new String[]{"622202"}, 19);

        assertThat(cardNumber).startsWith("622202");
        assertThat(cardNumber).hasSize(19);
        assertThat(CardNoGenerator.isValidCreditCardNumber(cardNumber)).isTrue();
    }

    /**
     * 验证空白、非数字、长度异常、校验位错误等非法卡号会被拒绝。
     */
    @Test
    void shouldRejectInvalidCardNumber() {
        assertThat(CardNoGenerator.isValidCreditCardNumber(null)).isFalse();
        assertThat(CardNoGenerator.isValidCreditCardNumber("")).isFalse();
        assertThat(CardNoGenerator.isValidCreditCardNumber("4111 1111 1111 1112")).isFalse();
        assertThat(CardNoGenerator.isValidCreditCardNumber("411111111111abcd")).isFalse();
        assertThat(CardNoGenerator.isValidCreditCardNumber("123456789")).isFalse();
    }

    /**
     * 验证生成参数异常时可以快速失败，避免生成不符合支付场景的测试 PAN。
     */
    @Test
    void shouldRejectInvalidGenerateArguments() {
        assertThatThrownBy(() -> CardNoGenerator.generateCardNumbers(null, 16, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prefixList cannot be empty");
        assertThatThrownBy(() -> CardNoGenerator.generateCardNumbers(new String[]{"51"}, 9, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("length must be between 10 and 19");
        assertThatThrownBy(() -> CardNoGenerator.generateCardNumbers(new String[]{"51"}, 16, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("count cannot be negative");
        assertThatThrownBy(() -> CardNoGenerator.generateCardNumber(new String[]{"abc"}, 16))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prefix must contain digits only");
    }

    /**
     * 校验生成卡号的长度和 Luhn 规则。
     *
     * @param cardNumber 生成后的测试卡号
     * @param length     预期卡号长度
     */
    private void assertCardNumber(String cardNumber, int length) {
        assertThat(cardNumber).hasSize(length);
        assertThat(CardNoGenerator.isValidCreditCardNumber(cardNumber)).isTrue();
    }
}
