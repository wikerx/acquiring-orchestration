package com.scott.payment.component.core.card;

import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardNoGenerator
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Card No Generator，位于 component-library/component-core 的业务组件层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
@Slf4j
public final class CardNoGenerator {

    /**
     * 支付卡号最小长度。当前工具仅面向通用 PAN 测试场景，低于该长度不生成。
     */
    private static final int MIN_CARD_NUMBER_LENGTH = 12;

    /**
     * 支付卡号最大长度。ISO/IEC 7812 账号长度常见上限为 19 位。
     */
    private static final int MAX_CARD_NUMBER_LENGTH = 19;

    /**
     * Luhn 校验算法模数。
     */
    private static final int LUHN_MODULUS = 10;

    /**
     * 数字字符的随机上界，`nextInt(10)` 会生成 0-9。
     */
    private static final int DIGIT_BOUND = 10;

    /**
     * 日志中用于标识自定义前缀生成的卡品牌。
     */
    private static final String CUSTOM_CARD_BRAND = "CUSTOM";

    /**
     * Mastercard 卡品牌日志名称。
     */
    private static final String MASTERCARD_CARD_BRAND = "Mastercard";

    /**
     * Visa 卡品牌日志名称。
     */
    private static final String VISA_CARD_BRAND = "Visa";

    /**
     * American Express 卡品牌日志名称。
     */
    private static final String AMEX_CARD_BRAND = "American Express";

    /**
     * Discover 卡品牌日志名称。
     */
    private static final String DISCOVER_CARD_BRAND = "Discover";

    /**
     * Diners Club 卡品牌日志名称。
     */
    private static final String DINERS_CARD_BRAND = "Diners Club";

    /**
     * JCB 卡品牌日志名称。
     */
    private static final String JCB_CARD_BRAND = "JCB";

    /**
     * EnRoute 卡品牌日志名称。
     */
    private static final String ENROUTE_CARD_BRAND = "EnRoute";

    /**
     * Voyager 卡品牌日志名称。
     */
    private static final String VOYAGER_CARD_BRAND = "Voyager";

    /**
     * 安全随机数生成器，用于生成测试卡号的随机中间位。
     */
    private static final RandomGenerator RANDOM = new SecureRandom();

    /**
     * Visa 测试卡号前缀列表。
     */
    private static final String[] VISA_PREFIX_LIST = {
            "4539", "4556", "4916", "4532", "4929", "40240071", "4485", "4716", "4"
    };

    /**
     * Mastercard 测试卡号前缀列表。
     */
    private static final String[] MASTERCARD_PREFIX_LIST = {
            "51", "52", "53", "54", "55"
    };

    /**
     * American Express 测试卡号前缀列表。
     */
    private static final String[] AMEX_PREFIX_LIST = {
            "34", "37"
    };

    /**
     * Discover 测试卡号前缀列表。
     */
    private static final String[] DISCOVER_PREFIX_LIST = {
            "6011"
    };

    /**
     * Diners Club 测试卡号前缀列表。
     */
    private static final String[] DINERS_PREFIX_LIST = {
            "300", "301", "302", "303", "36", "38"
    };

    /**
     * EnRoute 测试卡号前缀列表。
     */
    private static final String[] ENROUTE_PREFIX_LIST = {
            "2014", "2149"
    };

    /**
     * JCB 测试卡号前缀列表。
     */
    private static final String[] JCB_PREFIX_LIST = {
            "35"
    };

    /**
     * Voyager 测试卡号前缀列表。
     */
    private static final String[] VOYAGER_PREFIX_LIST = {
            "8699"
    };

    /**
     * 工具类不允许实例化。
     */
    private CardNoGenerator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 生成 Mastercard 测试卡号列表
     *
     * @param count 生成数量
     * @return 测试卡号列表
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param count 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static List<String> generateMasterCardNumbers(int count) {
        return generateCardNumbers(MASTERCARD_CARD_BRAND, MASTERCARD_PREFIX_LIST, 16, count);
    }

    /**
     * 生成单个 Mastercard 测试卡号
     *
     * @return Mastercard 测试卡号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String generateMasterCardNumber() {
        return generateCardNumber(MASTERCARD_CARD_BRAND, MASTERCARD_PREFIX_LIST, 16);
    }

    /**
     * 生成单个 Visa 测试卡号
     *
     * @return Visa 测试卡号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String generateVisaCardNumber() {
        return generateCardNumber(VISA_CARD_BRAND, VISA_PREFIX_LIST, 16);
    }

    /**
     * 生成单个 American Express 测试卡号
     *
     * <p>注意：Amex 常见长度为 15 位。</p>
     *
     * @return American Express 测试卡号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String generateAmexCardNumber() {
        return generateCardNumber(AMEX_CARD_BRAND, AMEX_PREFIX_LIST, 15);
    }

    /**
     * 生成单个 Discover 测试卡号
     *
     * <p>Discover 常见长度为 16 位。</p>
     *
     * @return Discover 测试卡号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String generateDiscoverCardNumber() {
        return generateCardNumber(DISCOVER_CARD_BRAND, DISCOVER_PREFIX_LIST, 16);
    }

    /**
     * 生成单个 Diners 测试卡号
     *
     * @return Diners 测试卡号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String generateDinersCardNumber() {
        return generateCardNumber(DINERS_CARD_BRAND, DINERS_PREFIX_LIST, 14);
    }

    /**
     * 生成单个 JCB 测试卡号
     *
     * <p>JCB 常见长度为 16-19 位，这里默认生成 16 位。</p>
     *
     * @return JCB 测试卡号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String generateJcbCardNumber() {
        return generateCardNumber(JCB_CARD_BRAND, JCB_PREFIX_LIST, 16);
    }

    /**
     * 生成单个 EnRoute 测试卡号
     *
     * @return EnRoute 测试卡号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String generateEnrouteCardNumber() {
        return generateCardNumber(ENROUTE_CARD_BRAND, ENROUTE_PREFIX_LIST, 15);
    }

    /**
     * 生成单个 Voyager 测试卡号
     *
     * @return Voyager 测试卡号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String generateVoyagerCardNumber() {
        return generateCardNumber(VOYAGER_CARD_BRAND, VOYAGER_PREFIX_LIST, 15);
    }

    /**
     * 批量生成测试卡号
     *
     * @param prefixList 卡号前缀列表
     * @param length     卡号长度
     * @param count      生成数量
     * @return 测试卡号列表
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param prefixList 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param length 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param count 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static List<String> generateCardNumbers(String[] prefixList, int length, int count) {
        return generateCardNumbers(CUSTOM_CARD_BRAND, prefixList, length, count);
    }

    /**
     * 批量生成指定卡品牌的测试卡号。
     *
     * @param cardBrand  卡品牌名称，仅用于日志排查
     * @param prefixList 卡号前缀列表
     * @param length     卡号长度
     * @param count      生成数量
     * @return 测试卡号列表
     */
    private static List<String> generateCardNumbers(String cardBrand, String[] prefixList, int length, int count) {
        if (prefixList == null || prefixList.length == 0) {
            throw new IllegalArgumentException("prefixList cannot be empty");
        }
        validateCardLength(length);
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }

        log.info("批量生成测试卡号开始，cardBrand={}，length={}，count={}，prefixCount={}",
                cardBrand, length, count, prefixList.length);
        List<String> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(generateCardNumber(cardBrand, prefixList, length, false));
        }
        log.info("批量生成测试卡号完成，cardBrand={}，length={}，count={}，firstCard={}，lastCard={}",
                cardBrand,
                length,
                result.size(),
                result.isEmpty() ? "-" : SensitiveDataMaskUtils.maskPan(result.get(0)),
                result.isEmpty() ? "-" : SensitiveDataMaskUtils.maskPan(result.get(result.size() - 1)));
        return result;
    }

    /**
     * 生成单个测试卡号
     *
     * @param prefixList 卡号前缀列表
     * @param length     卡号长度
     * @return 测试卡号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param prefixList 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param length 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String generateCardNumber(String[] prefixList, int length) {
        return generateCardNumber(CUSTOM_CARD_BRAND, prefixList, length);
    }

    /**
     * 生成指定卡品牌的单个测试卡号。
     *
     * @param cardBrand  卡品牌名称，仅用于日志排查
     * @param prefixList 卡号前缀列表
     * @param length     卡号长度
     * @return 测试卡号
     */
    private static String generateCardNumber(String cardBrand, String[] prefixList, int length) {
        return generateCardNumber(cardBrand, prefixList, length, true);
    }

    /**
     * 生成单个测试卡号，并按需打印单条生成日志。
     *
     * @param cardBrand     卡品牌名称，仅用于日志排查
     * @param prefixList    卡号前缀列表
     * @param length        卡号长度
     * @param logCardResult true：打印单条生成日志；false：由批量方法统一打印摘要
     * @return 测试卡号
     */
    private static String generateCardNumber(String cardBrand, String[] prefixList, int length, boolean logCardResult) {
        if (prefixList == null || prefixList.length == 0) {
            throw new IllegalArgumentException("prefixList cannot be empty");
        }
        validateCardLength(length);

        String prefix = prefixList[RANDOM.nextInt(prefixList.length)];
        String cardNumber = completeCardNumber(prefix, length);
        if (logCardResult) {
            log.info("生成测试卡号成功，cardBrand={}，prefix={}，length={}，maskedCardNo={}，luhnValid={}",
                    cardBrand,
                    prefix,
                    length,
                    SensitiveDataMaskUtils.maskPan(cardNumber),
                    isValidCreditCardNumber(cardNumber));
        }
        return cardNumber;
    }

    /**
     * 根据前缀和指定长度补全卡号，并生成 Luhn 校验位
     *
     * @param prefix 卡号前缀
     * @param length 卡号总长度
     * @return 完整卡号
     */
    private static String completeCardNumber(String prefix, int length) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix cannot be blank");
        }
        if (!prefix.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("prefix must contain digits only");
        }
        if (prefix.length() >= length) {
            throw new IllegalArgumentException("prefix length must be less than card number length");
        }
        validateCardLength(length);

        StringBuilder cardNumber = new StringBuilder(prefix);

        while (cardNumber.length() < length - 1) {
            cardNumber.append(RANDOM.nextInt(DIGIT_BOUND));
        }

        int checkDigit = calculateLuhnCheckDigit(cardNumber.toString());
        cardNumber.append(checkDigit);

        return cardNumber.toString();
    }

    /**
     * 计算 Luhn 校验位
     *
     * @param numberWithoutCheckDigit 不含校验位的卡号
     * @return 校验位
     */
    private static int calculateLuhnCheckDigit(String numberWithoutCheckDigit) {
        int sum = 0;
        boolean doubleDigit = true;

        for (int i = numberWithoutCheckDigit.length() - 1; i >= 0; i--) {
            int digit = Character.digit(numberWithoutCheckDigit.charAt(i), 10);

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
     * 判断是否为合法卡号
     *
     * @param cardNumber 卡号
     * @return true：通过 Luhn 校验；false：未通过
     */
    /**
     * 判断收单支付条件是否满足，供业务分支或权限控制使用。
     * @param cardNumber 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static boolean isValidCreditCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            log.warn("卡号Luhn校验失败，原因=卡号为空");
            return false;
        }

        String normalizedCardNumber = cardNumber.replaceAll("\\s+", "");

        if (!normalizedCardNumber.chars().allMatch(Character::isDigit)) {
            log.warn("卡号Luhn校验失败，原因=存在非数字字符，maskedCardNo={}",
                    SensitiveDataMaskUtils.maskPan(normalizedCardNumber));
            return false;
        }
        if (normalizedCardNumber.length() < MIN_CARD_NUMBER_LENGTH
                || normalizedCardNumber.length() > MAX_CARD_NUMBER_LENGTH) {
            log.warn("卡号Luhn校验失败，原因=长度不合法，length={}，maskedCardNo={}",
                    normalizedCardNumber.length(),
                    SensitiveDataMaskUtils.maskPan(normalizedCardNumber));
            return false;
        }

        int sum = 0;
        boolean doubleDigit = false;

        for (int i = normalizedCardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.digit(normalizedCardNumber.charAt(i), 10);

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
        log.debug("卡号Luhn校验完成，maskedCardNo={}，length={}，valid={}",
                SensitiveDataMaskUtils.maskPan(normalizedCardNumber),
                normalizedCardNumber.length(),
                valid);
        return valid;
    }

    /**
     * 校验卡号长度是否处于工具支持范围内。
     *
     * @param length 卡号长度
     */
    private static void validateCardLength(int length) {
        if (length < MIN_CARD_NUMBER_LENGTH || length > MAX_CARD_NUMBER_LENGTH) {
            throw new IllegalArgumentException("length must be between 10 and 19");
        }
    }

    /**
     * 本地演示入口，仅输出脱敏后的测试卡号。
     *
     * @param args 命令行参数，当前未使用
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param args 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public static void main(String[] args) {
        log.info("Mastercard: {}", SensitiveDataMaskUtils.maskPan(generateMasterCardNumber()));
        log.info("Visa      : {}", SensitiveDataMaskUtils.maskPan(generateVisaCardNumber()));
        log.info("Amex      : {}", SensitiveDataMaskUtils.maskPan(generateAmexCardNumber()));
        log.info("Discover  : {}", SensitiveDataMaskUtils.maskPan(generateDiscoverCardNumber()));
        log.info("Diners    : {}", SensitiveDataMaskUtils.maskPan(generateDinersCardNumber()));
        log.info("JCB       : {}", SensitiveDataMaskUtils.maskPan(generateJcbCardNumber()));
        log.info("EnRoute   : {}", SensitiveDataMaskUtils.maskPan(generateEnrouteCardNumber()));
        log.info("Voyager   : {}", SensitiveDataMaskUtils.maskPan(generateVoyagerCardNumber()));

        List<String> masterCards = generateMasterCardNumbers(5);
        for (String cardNumber : masterCards) {
            log.info("批量生成结果，maskedCardNo={}，valid={}",
                    SensitiveDataMaskUtils.maskPan(cardNumber),
                    isValidCreditCardNumber(cardNumber)
            );
        }
    }
}
