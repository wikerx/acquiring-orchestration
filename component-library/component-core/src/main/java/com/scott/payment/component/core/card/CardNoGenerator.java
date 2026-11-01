package com.scott.payment.component.core.card;

import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 生成满足长度和 Luhn 校验规则的通用测试卡号。
 *
 * <p>本工具只保证生成结果的格式和校验位正确，不保证卡号属于真实发卡范围，也不保证任何渠道受理。
 * 生成结果只能用于隔离的测试环境；渠道规定固定测试卡时，应使用渠道官方提供的测试数据。
 * MPGS 联调必须使用
 * <a href="https://test-gateway.mastercard.com/api/documentation/integrationGuidelines/supportedFeatures/testAndGoLive.html?locale=zh_CN">Mastercard Gateway Test and Go Live</a>
 * 公布的固定测试卡。</p>
 */
@Slf4j
public final class CardNoGenerator {

    /**
     * ISO/IEC 7812 卡号常用长度边界；生成器不接受边界外的自定义长度。
     */
    private static final int MIN_CARD_NUMBER_LENGTH = 12;
    /**
     * {@code MAX_CARD_NUMBER_LENGTH}常量，统一 {@code CardNoGenerator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MAX_CARD_NUMBER_LENGTH = 19;
    /**
     * Luhn 算法按十进制校验，随机补位也只生成十进制数字。
     */
    private static final int LUHN_MODULUS = 10;
    /**
     * {@code DIGIT_BOUND}常量，统一 {@code CardNoGenerator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int DIGIT_BOUND = 10;

    /**
     * 品牌名称只用于不含卡号的安全日志，不参与卡品牌识别或渠道路由。
     */
    private static final String CUSTOM_CARD_BRAND = "CUSTOM";
    /**
     * {@code MASTERCARD_CARD_BRAND}常量，统一 {@code CardNoGenerator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String MASTERCARD_CARD_BRAND = "Mastercard";
    /**
     * {@code VISA_CARD_BRAND}常量，统一 {@code CardNoGenerator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String VISA_CARD_BRAND = "Visa";
    /**
     * {@code AMEX_CARD_BRAND}常量，统一 {@code CardNoGenerator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String AMEX_CARD_BRAND = "American Express";
    /**
     * {@code DISCOVER_CARD_BRAND}常量，统一 {@code CardNoGenerator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String DISCOVER_CARD_BRAND = "Discover";
    /**
     * {@code DINERS_CARD_BRAND}常量，统一 {@code CardNoGenerator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String DINERS_CARD_BRAND = "Diners Club";
    /**
     * {@code JCB_CARD_BRAND}常量，统一 {@code CardNoGenerator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String JCB_CARD_BRAND = "JCB";
    /**
     * {@code ENROUTE_CARD_BRAND}常量，统一 {@code CardNoGenerator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String ENROUTE_CARD_BRAND = "EnRoute";
    /**
     * {@code VOYAGER_CARD_BRAND}常量，统一 {@code CardNoGenerator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String VOYAGER_CARD_BRAND = "Voyager";

    /**
     * 测试数据使用安全随机源，避免并发生成时依赖可预测的全局序列。
     */
    private static final RandomGenerator RANDOM = new SecureRandom();

    /**
     * 常见品牌前缀只用于生成外形合理的测试数据，不代表真实 IIN 分配或渠道受理范围。
     */
    private static final String[] VISA_PREFIXES = {"4"};
    private static final String[] MASTERCARD_PREFIXES = {
            "51", "52", "53", "54", "55", "2221", "2230", "2300", "2400", "2500", "2600", "2700", "2720"
    };
    private static final String[] AMEX_PREFIXES = {"34", "37"};
    private static final String[] DISCOVER_PREFIXES = {
            "6011", "65", "644", "645", "646", "647", "648", "649", "622126", "622925"
    };
    private static final String[] DINERS_PREFIXES = {"300", "301", "302", "303", "304", "305", "36", "38", "39"};
    private static final String[] JCB_PREFIXES = {"3528", "3530", "3540", "3550", "3560", "3570", "3580", "3589"};
    private static final String[] ENROUTE_PREFIXES = {"2014", "2149"};
    /**
     * {@code VOYAGER_PREFIXES}常量，统一 {@code CardNoGenerator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String[] VOYAGER_PREFIXES = {"8699"};

    private CardNoGenerator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 生成指定数量的 16 位 Mastercard 测试卡号。
     *
     * @param count 生成数量，不能为负数
     * @return 生成的测试卡号列表
     * @throws IllegalArgumentException count 为负数时抛出
     */
    public static List<String> generateMasterCardNumbers(int count) {
        return generateCardNumbers(MASTERCARD_CARD_BRAND, MASTERCARD_PREFIXES, 16, count);
    }

    /**
     * 生成一个 16 位 Mastercard 测试卡号。
     *
     * @return 生成的测试卡号
     */
    public static String generateMasterCardNumber() {
        return generateCardNumber(MASTERCARD_CARD_BRAND, MASTERCARD_PREFIXES, 16);
    }

    /**
     * 生成一个 16 位 Visa 测试卡号。
     *
     * @return 生成的测试卡号
     */
    public static String generateVisaCardNumber() {
        return generateCardNumber(VISA_CARD_BRAND, VISA_PREFIXES, 16);
    }

    /**
     * 生成一个 15 位 American Express 测试卡号。
     *
     * @return 生成的测试卡号
     */
    public static String generateAmexCardNumber() {
        return generateCardNumber(AMEX_CARD_BRAND, AMEX_PREFIXES, 15);
    }

    /**
     * 生成一个 16 位 Discover 测试卡号。
     *
     * @return 生成的测试卡号
     */
    public static String generateDiscoverCardNumber() {
        return generateCardNumber(DISCOVER_CARD_BRAND, DISCOVER_PREFIXES, 16);
    }

    /**
     * 生成一个 14 位 Diners Club 测试卡号。
     *
     * @return 生成的测试卡号
     */
    public static String generateDinersCardNumber() {
        return generateCardNumber(DINERS_CARD_BRAND, DINERS_PREFIXES, 14);
    }

    /**
     * 生成一个 16 位 JCB 测试卡号。
     *
     * @return 生成的测试卡号
     */
    public static String generateJcbCardNumber() {
        return generateCardNumber(JCB_CARD_BRAND, JCB_PREFIXES, 16);
    }

    /**
     * 生成一个 15 位 EnRoute 测试卡号。
     *
     * @return 生成的测试卡号
     */
    public static String generateEnrouteCardNumber() {
        return generateCardNumber(ENROUTE_CARD_BRAND, ENROUTE_PREFIXES, 15);
    }

    /**
     * 生成一个 15 位 Voyager 测试卡号。
     *
     * @return 生成的测试卡号
     */
    public static String generateVoyagerCardNumber() {
        return generateCardNumber(VOYAGER_CARD_BRAND, VOYAGER_PREFIXES, 15);
    }

    /**
     * 使用自定义前缀批量生成测试卡号。
     *
     * @param prefixList ASCII 数字前缀列表；每个前缀必须至少为一位并短于目标长度
     * @param length     卡号总长度，支持 12 至 19 位
     * @param count      生成数量，不能为负数
     * @return 生成的测试卡号列表
     * @throws IllegalArgumentException 前缀、长度或数量不符合约束时抛出
     */
    public static List<String> generateCardNumbers(String[] prefixList, int length, int count) {
        return generateCardNumbers(CUSTOM_CARD_BRAND, prefixList, length, count);
    }

    /**
     * 使用自定义前缀生成一个测试卡号。
     *
     * @param prefixList ASCII 数字前缀列表；每个前缀必须至少为一位并短于目标长度
     * @param length     卡号总长度，支持 12 至 19 位
     * @return 生成的测试卡号
     * @throws IllegalArgumentException 前缀或长度不符合约束时抛出
     */
    public static String generateCardNumber(String[] prefixList, int length) {
        return generateCardNumber(CUSTOM_CARD_BRAND, prefixList, length);
    }

    /**
     * 判断卡号是否处于支持的长度范围并通过 Luhn 校验。
     *
     * <p>空白字符会在校验前移除，其他分隔符不被接受。本方法不校验卡品牌、IIN 分配或渠道可用性。</p>
     *
     * @param cardNumber 待校验的卡号
     * @return 格式和 Luhn 校验均通过时返回 {@code true}
     */
    public static boolean isValidCreditCardNumber(String cardNumber) {
        String normalizedCardNumber = normalizeCardNumber(cardNumber);
        if (normalizedCardNumber == null || normalizedCardNumber.isEmpty()) {
            log.debug("测试卡号校验未通过, event: test_card_number_validation_rejected, reason: empty");
            return false;
        }
        if (normalizedCardNumber.length() < MIN_CARD_NUMBER_LENGTH
                || normalizedCardNumber.length() > MAX_CARD_NUMBER_LENGTH) {
            log.debug("测试卡号校验未通过, event: test_card_number_validation_rejected, reason: invalidLength, length: {}",
                    normalizedCardNumber.length());
            return false;
        }
        if (!isAsciiDigits(normalizedCardNumber)) {
            log.debug("测试卡号校验未通过, event: test_card_number_validation_rejected, reason: invalidCharacter, length: {}",
                    normalizedCardNumber.length());
            return false;
        }

        int sum = 0;
        boolean doubleDigit = false;
        for (int index = normalizedCardNumber.length() - 1; index >= 0; index--) {
            int digit = normalizedCardNumber.charAt(index) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }

        boolean valid = sum % LUHN_MODULUS == 0;
        log.debug("测试卡号校验完成, event: test_card_number_validation_completed, length: {}, valid: {}",
                normalizedCardNumber.length(), valid);
        return valid;
    }

    /**
     * 在完成一次输入校验后批量生成测试卡号，日志只记录品牌、长度和数量。
     *
     * @param cardBrand 安全日志使用的品牌名称
     * @param prefixList 候选数字前缀
     * @param length 目标卡号长度
     * @param count 生成数量
     * @return 新建的测试卡号列表
     */
    private static List<String> generateCardNumbers(String cardBrand, String[] prefixList, int length, int count) {
        String[] validatedPrefixes = validateAndCopyPrefixes(prefixList, length);
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }

        List<String> cardNumbers = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cardNumbers.add(generateCardNumberUnchecked(validatedPrefixes, length));
        }
        log.info("批量测试卡号生成完成, event: test_card_number_batch_generated, cardBrand: {}, length: {}, count: {}",
                cardBrand, length, cardNumbers.size());
        return cardNumbers;
    }

    /**
     * 生成单个测试卡号；返回值属于敏感测试数据，方法内部不记录卡号内容。
     *
     * @param cardBrand 安全日志使用的品牌名称
     * @param prefixList 候选数字前缀
     * @param length 目标卡号长度
     * @return 通过 Luhn 校验的测试卡号
     */
    private static String generateCardNumber(String cardBrand, String[] prefixList, int length) {
        String[] validatedPrefixes = validateAndCopyPrefixes(prefixList, length);
        String cardNumber = generateCardNumberUnchecked(validatedPrefixes, length);
        log.debug("测试卡号生成完成, event: test_card_number_generated, cardBrand: {}, length: {}",
                cardBrand, length);
        return cardNumber;
    }

    /**
     * 从已校验前缀中随机选择一个，补齐主体数字并计算最后一位 Luhn 校验位。
     *
     * @param validatedPrefixes 已通过格式和长度校验的前缀副本
     * @param length 目标卡号长度
     * @return 生成的测试卡号
     */
    private static String generateCardNumberUnchecked(String[] validatedPrefixes, int length) {
        String prefix = validatedPrefixes[RANDOM.nextInt(validatedPrefixes.length)];
        StringBuilder cardNumber = new StringBuilder(length).append(prefix);
        while (cardNumber.length() < length - 1) {
            cardNumber.append(RANDOM.nextInt(DIGIT_BOUND));
        }
        cardNumber.append(calculateLuhnCheckDigit(cardNumber));
        return cardNumber.toString();
    }

    /**
     * 校验候选前缀并返回防御性副本，避免调用方在生成过程中修改共享数组。
     *
     * @param prefixList 调用方提供的候选前缀
     * @param length 目标卡号长度
     * @return 可供生成算法安全使用的前缀副本
     */
    private static String[] validateAndCopyPrefixes(String[] prefixList, int length) {
        if (prefixList == null || prefixList.length == 0) {
            throw new IllegalArgumentException("prefixList cannot be empty");
        }
        validateCardLength(length);

        String[] prefixCopy = prefixList.clone();
        for (String prefix : prefixCopy) {
            if (prefix == null || prefix.isBlank()) {
                throw new IllegalArgumentException("prefix cannot be blank");
            }
            if (!isAsciiDigits(prefix)) {
                throw new IllegalArgumentException("prefix must contain ASCII digits only");
            }
            if (prefix.length() >= length) {
                throw new IllegalArgumentException("prefix length must be less than card number length");
            }
        }
        return prefixCopy;
    }

    /**
     * 校验目标长度是否处于生成器支持的 12 至 19 位范围。
     */
    private static void validateCardLength(int length) {
        if (length < MIN_CARD_NUMBER_LENGTH || length > MAX_CARD_NUMBER_LENGTH) {
            throw new IllegalArgumentException("length must be between 12 and 19");
        }
    }

    /**
     * 根据不含校验位的数字序列计算 Luhn 校验位。
     *
     * @param numberWithoutCheckDigit 已完成 ASCII 数字校验的卡号主体
     * @return 0 至 9 的校验位
     */
    private static int calculateLuhnCheckDigit(CharSequence numberWithoutCheckDigit) {
        int sum = 0;
        boolean doubleDigit = true;
        for (int index = numberWithoutCheckDigit.length() - 1; index >= 0; index--) {
            int digit = numberWithoutCheckDigit.charAt(index) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return (LUHN_MODULUS - (sum % LUHN_MODULUS)) % LUHN_MODULUS;
    }

    /**
     * 移除输入中的 Unicode 空白，保留其他字符供后续严格 ASCII 数字校验拒绝。
     */
    private static String normalizeCardNumber(String cardNumber) {
        if (cardNumber == null) {
            return null;
        }
        StringBuilder normalized = new StringBuilder(cardNumber.length());
        for (int index = 0; index < cardNumber.length(); index++) {
            char current = cardNumber.charAt(index);
            if (!Character.isWhitespace(current)) {
                normalized.append(current);
            }
        }
        return normalized.toString();
    }

    /**
     * 判断字符序列是否只包含 ASCII 数字，拒绝视觉相似的全角或其他 Unicode 数字。
     */
    private static boolean isAsciiDigits(CharSequence value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < '0' || current > '9') {
                return false;
            }
        }
        return true;
    }
}
