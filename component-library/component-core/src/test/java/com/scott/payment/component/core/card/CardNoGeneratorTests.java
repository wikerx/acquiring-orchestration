package com.scott.payment.component.core.card;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证通用测试卡号生成器的品牌前缀、长度、Luhn 校验和参数保护。
 */
class CardNoGeneratorTests {

    @Test
    void shouldGenerateValidMastercardNumber() {
        String cardNumber = CardNoGenerator.generateMasterCardNumber();

        assertCardNumber(cardNumber, 16);
        assertThat(cardNumber).matches("^(?:5[1-5]\\d{14}|(?:2221|2230|2300|2400|2500|2600|2700|2720)\\d{12})$");
    }

    @Test
    void shouldGenerateValidCardNumberByBrand() {
        assertCardNumberWithPrefix(CardNoGenerator.generateVisaCardNumber(), 16, "4");
        assertCardNumberWithPrefix(CardNoGenerator.generateAmexCardNumber(), 15, "34", "37");
        assertCardNumberWithPrefix(CardNoGenerator.generateDiscoverCardNumber(), 16,
                "6011", "65", "644", "645", "646", "647", "648", "649", "622126", "622925");
        assertCardNumberWithPrefix(CardNoGenerator.generateDinersCardNumber(), 14,
                "300", "301", "302", "303", "304", "305", "36", "38", "39");
        assertCardNumberWithPrefix(CardNoGenerator.generateJcbCardNumber(), 16,
                "3528", "3530", "3540", "3550", "3560", "3570", "3580", "3589");
        assertCardNumberWithPrefix(CardNoGenerator.generateEnrouteCardNumber(), 15, "2014", "2149");
        assertCardNumberWithPrefix(CardNoGenerator.generateVoyagerCardNumber(), 15, "8699");
    }

    @Test
    void shouldGenerateBatchMastercardNumbers() {
        List<String> cardNumbers = CardNoGenerator.generateMasterCardNumbers(20);

        assertThat(cardNumbers).hasSize(20).allSatisfy(cardNumber -> assertCardNumber(cardNumber, 16));
        assertThat(CardNoGenerator.generateMasterCardNumbers(0)).isEmpty();
    }

    @Test
    void shouldGenerateCardNumberWithCustomPrefixAtSupportedLengthBoundaries() {
        String shortestCardNumber = CardNoGenerator.generateCardNumber(new String[]{"9"}, 12);
        String longestCardNumber = CardNoGenerator.generateCardNumber(new String[]{"622202"}, 19);

        assertCardNumberWithPrefix(shortestCardNumber, 12, "9");
        assertCardNumberWithPrefix(longestCardNumber, 19, "622202");
    }

    @Test
    void shouldValidateOnlySupportedAsciiCardNumberFormat() {
        assertThat(CardNoGenerator.isValidCreditCardNumber("4111 1111 1111 1111")).isTrue();
        assertThat(CardNoGenerator.isValidCreditCardNumber(null)).isFalse();
        assertThat(CardNoGenerator.isValidCreditCardNumber("   ")).isFalse();
        assertThat(CardNoGenerator.isValidCreditCardNumber("4111-1111-1111-1111")).isFalse();
        assertThat(CardNoGenerator.isValidCreditCardNumber("4111111111111112")).isFalse();
        assertThat(CardNoGenerator.isValidCreditCardNumber("４１１１１１１１１１１１１１１１")).isFalse();
        assertThat(CardNoGenerator.isValidCreditCardNumber("12345678901")).isFalse();
    }

    @Test
    void shouldRejectInvalidGenerateArguments() {
        assertThatThrownBy(() -> CardNoGenerator.generateCardNumbers(null, 16, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prefixList cannot be empty");
        assertThatThrownBy(() -> CardNoGenerator.generateCardNumbers(new String[]{"51"}, 11, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("length must be between 12 and 19");
        assertThatThrownBy(() -> CardNoGenerator.generateCardNumbers(new String[]{"51"}, 20, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("length must be between 12 and 19");
        assertThatThrownBy(() -> CardNoGenerator.generateCardNumbers(new String[]{"51"}, 16, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("count cannot be negative");
        assertThatThrownBy(() -> CardNoGenerator.generateCardNumber(new String[]{"51", "abc"}, 16))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prefix must contain ASCII digits only");
        assertThatThrownBy(() -> CardNoGenerator.generateCardNumber(new String[]{"５１"}, 16))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prefix must contain ASCII digits only");
        assertThatThrownBy(() -> CardNoGenerator.generateCardNumber(new String[]{"123456789012"}, 12))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prefix length must be less than card number length");
    }

    private static void assertCardNumberWithPrefix(String cardNumber, int expectedLength, String... prefixes) {
        assertCardNumber(cardNumber, expectedLength);
        assertThat(prefixes).anySatisfy(prefix -> assertThat(cardNumber).startsWith(prefix));
    }

    private static void assertCardNumber(String cardNumber, int expectedLength) {
        assertThat(cardNumber).hasSize(expectedLength).containsOnlyDigits();
        assertThat(CardNoGenerator.isValidCreditCardNumber(cardNumber)).isTrue();
    }
}
