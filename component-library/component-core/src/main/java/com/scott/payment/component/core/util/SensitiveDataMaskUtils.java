package com.scott.payment.component.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SensitiveDataMaskUtils
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sensitive Data Mask 工具，位于 component-library/component-core 的业务组件层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class SensitiveDataMaskUtils {

    /**
     * 密钥类字段统一替换为固定星号，禁止日志中出现任何明文片段。
     */
    private static final Pattern SECRET_FIELD_PATTERN = Pattern.compile(
            "(\"(?:password|oldPassword|newPassword|Authorization|accessToken|token|apiKey|secretKey|privateKey|publicKey|merchantSecret)\"\\s*:\\s*\")([^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 卡号字段保留前 6 后 4，用于 BIN、尾号和链路排查。
     */
    private static final Pattern CARD_FIELD_PATTERN = Pattern.compile(
            "(\"(?:cardNo|pan)\"\\s*:\\s*\")([0-9]{6})([0-9]{1,19})([0-9]{4})(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 银行账号、IBAN、Swift/BIC 保留少量定位信息，避免完整账号或银行路由信息进入日志。
     */
    private static final Pattern ACCOUNT_FIELD_PATTERN = Pattern.compile(
            "(\"(?:bankAccount|accountNumber|iban|swiftCode|bic)\"\\s*:\\s*\")([A-Za-z0-9]{4})([A-Za-z0-9\\s-]*)([A-Za-z0-9]{4})(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 手机号保留前 3 后 4。
     */
    private static final Pattern MOBILE_FIELD_PATTERN = Pattern.compile(
            "(\"(?:mobile)\"\\s*:\\s*\")([^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 邮箱保留首字符和域名。
     */
    private static final Pattern EMAIL_FIELD_PATTERN = Pattern.compile(
            "(\"(?:email)\"\\s*:\\s*\")([^\"\\\\@]*)(@[^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 证件字段统一隐藏，避免身份证和护照号泄露。
     */
    private static final Pattern ID_FIELD_PATTERN = Pattern.compile(
            "(\"(?:idCard|passportNo)\"\\s*:\\s*\")([^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 安全码、CAVV 等认证敏感数据统一隐藏。
     */
    private static final Pattern SECURITY_CODE_PATTERN = Pattern.compile(
            "(\"(?:securityCode|cvv|cvc|cavv)\"\\s*:\\s*\")([^\"\\\\]*)(\")",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 工具类不允许实例化。
     */
    private SensitiveDataMaskUtils() {
    }

    /**
     * 对 JSON 文本中的敏感字段执行统一脱敏。
     *
     * @param json 原始 JSON 文本
     * @return 脱敏后的 JSON 文本
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param json 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String maskJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        String masked = SECRET_FIELD_PATTERN.matcher(json).replaceAll("$1***$3");
        masked = maskCardNo(masked);
        masked = maskAccountNumber(masked);
        masked = maskMobileField(masked);
        masked = maskEmailField(masked);
        masked = SECURITY_CODE_PATTERN.matcher(masked).replaceAll("$1***$3");
        return ID_FIELD_PATTERN.matcher(masked).replaceAll("$1***$3");
    }

    /**
     * 对卡号字段执行脱敏。
     *
     * @param value 原始文本
     * @return 脱敏后的文本
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String maskCardNo(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return CARD_FIELD_PATTERN.matcher(value).replaceAll(matchResult -> Matcher.quoteReplacement(
                matchResult.group(1)
                        + matchResult.group(2)
                        + "******"
                        + matchResult.group(4)
                        + matchResult.group(5)
        ));
    }

    /**
     * 对银行账号、IBAN、Swift/BIC 字段执行脱敏。
     *
     * @param value 原始文本
     * @return 脱敏后的文本
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param value 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String maskAccountNumber(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return ACCOUNT_FIELD_PATTERN.matcher(value).replaceAll(matchResult -> Matcher.quoteReplacement(
                matchResult.group(1)
                        + matchResult.group(2)
                        + "******"
                        + matchResult.group(4)
                        + matchResult.group(5)
        ));
    }

    /**
     * 对单个手机号执行脱敏。
     *
     * @param mobile 原始手机号
     * @return 脱敏后的手机号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param mobile 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return "***";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    /**
     * 对单个邮箱执行脱敏。
     *
     * @param email 原始邮箱
     * @return 脱敏后的邮箱
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param email 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    /**
     * 对单个 PAN 卡号执行脱敏。
     * <p>
     * 支付日志只允许保留前 6 位和后 4 位，用于排查 BIN、尾号和链路数据是否一致；中间数字全部使用星号替换。
     *
     * @param cardNo 原始 PAN 卡号
     * @return 脱敏后的 PAN，入参为空或长度不足时返回固定星号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param cardNo 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static String maskPan(String cardNo) {
        if (cardNo == null || cardNo.length() < 10) {
            return "******";
        }
        return cardNo.substring(0, 6) + "******" + cardNo.substring(cardNo.length() - 4);
    }

    private static String maskMobileField(String value) {
        return MOBILE_FIELD_PATTERN.matcher(value).replaceAll(matchResult -> Matcher.quoteReplacement(
                matchResult.group(1) + maskMobile(matchResult.group(2)) + matchResult.group(3)
        ));
    }

    private static String maskEmailField(String value) {
        return EMAIL_FIELD_PATTERN.matcher(value).replaceAll(matchResult -> Matcher.quoteReplacement(
                matchResult.group(1) + maskEmail(matchResult.group(2) + matchResult.group(3)) + matchResult.group(4)
        ));
    }
}
