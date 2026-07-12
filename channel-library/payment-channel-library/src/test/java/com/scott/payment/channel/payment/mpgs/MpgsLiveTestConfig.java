package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.component.core.json.JsonUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsLiveTestConfig
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 真实联网测试配置加载器，位于 payment-channel-library 测试支撑层，只从环境变量或本机临时文件读取测试商户配置，避免真实密钥写入仓库源码。
 * @status : create
 */
final class MpgsLiveTestConfig {

    static final String ENABLED_ENV = "MPGS_LIVE_TEST_ENABLED";

    private static final String DEFAULT_CONFIG_FILE = "/Users/scott/Desktop/未命名 2.txt";

    private static final Pattern CONFIG_LINE_PATTERN = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9_-]*)\\s*[:：]\\s*(.*?)\\s*$");

    private static final String DEFAULT_CARD_NO = "5123450000000008";

    private static final String DEFAULT_EXPIRY_MONTH = "01";

    private static final String DEFAULT_EXPIRY_YEAR = "2039";

    private static final String DEFAULT_CSC = "100";

    private static final String DEFAULT_AMOUNT = "1.00";

    private static final String DEFAULT_CURRENCY = "USD";

    private final String baseUrl;

    private final String version;

    private final String merchantId;

    private final String username;

    private final String password;

    private final String cardNo;

    private final String expiryMonth;

    private final String expiryYear;

    private final String csc;

    private final String amount;

    private final String currency;

    private MpgsLiveTestConfig(Map<String, String> fileConfig) {
        this.baseUrl = required("MPGS_BASE_URL", fileConfig.get("baseUrl"));
        this.version = value("MPGS_VERSION", "100");
        this.merchantId = required("MPGS_MERCHANT_ID", fileConfig.get("merchantId"));
        this.username = username(fileConfig);
        this.password = required("MPGS_PASSWORD", fileConfig.get("password"));
        this.cardNo = value("MPGS_TEST_CARD_NO", DEFAULT_CARD_NO);
        this.expiryMonth = value("MPGS_TEST_EXPIRY_MONTH", DEFAULT_EXPIRY_MONTH);
        this.expiryYear = value("MPGS_TEST_EXPIRY_YEAR", DEFAULT_EXPIRY_YEAR);
        this.csc = value("MPGS_TEST_CSC", DEFAULT_CSC);
        this.amount = value("MPGS_TEST_AMOUNT", DEFAULT_AMOUNT);
        this.currency = value("MPGS_TEST_CURRENCY", DEFAULT_CURRENCY);
    }

    /**
     * 加载 MPGS 真实联网测试配置。
     *
     * @return 测试配置
     */
    static MpgsLiveTestConfig load() {
        Map<String, String> fileConfig = readFileConfig();
        return new MpgsLiveTestConfig(fileConfig);
    }

    /**
     * 构造 MPGS 渠道属性对象。
     *
     * @return MPGS 渠道属性
     */
    MpgsChannelProperties toProperties() {
        MpgsChannelProperties properties = new MpgsChannelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(baseUrl);
        properties.setVersion(version);
        properties.setMerchantId(merchantId);
        properties.setApiUsername(username);
        properties.setApiPassword(password);
        properties.setReadTimeoutMillis(45000);
        return properties;
    }

    String merchantId() {
        return merchantId;
    }

    String cardNo() {
        return cardNo;
    }

    String expiryMonth() {
        return expiryMonth;
    }

    String expiryYear() {
        return expiryYear;
    }

    String csc() {
        return csc;
    }

    String amount() {
        return amount;
    }

    String currency() {
        return currency;
    }

    /**
     * 构造真实联网配置摘要。
     * <p>
     * 该摘要用于测试日志，必须隐藏密码和卡号敏感位；返回 JSON 字符串，便于直接从日志中提取分析。
     *
     * @return 脱敏后的配置摘要
     */
    String maskedSummary() {
        return JsonUtils.toJsonString(new MaskedConfig(baseUrl, version, merchantId, maskUsername(username),
                "***", MpgsApiClient.maskMpgsJson("{\"number\":\"" + cardNo + "\"}")));
    }

    /**
     * 解析 MPGS 用户名。
     * <p>
     * 用户提供的参考文件中可能把用户名写成商户号并带括号说明，这里会清理说明文字并补齐
     * MPGS Basic Auth 要求的 merchant. 前缀，避免说明文字进入认证头导致 401。
     *
     * @param fileConfig 本机参考配置
     * @return MPGS Basic Auth 用户名
     */
    private String username(Map<String, String> fileConfig) {
        String configured = value("MPGS_USERNAME", usernameValue(fileConfig.get("username")));
        if (!StringUtils.hasText(configured)) {
            return "merchant." + merchantId;
        }
        String trimmed = configured.trim();
        return trimmed.startsWith("merchant.") ? trimmed : "merchant." + trimmed;
    }

    /**
     * 读取本机 MPGS 真实联网测试配置文件。
     * <p>
     * 只用于测试；生产配置必须来自服务配置中心或安全配置，不允许读取桌面临时文件。
     *
     * @return 配置键值对，文件不存在时返回空 Map
     */
    private static Map<String, String> readFileConfig() {
        String configPath = value("MPGS_CONFIG_FILE", DEFAULT_CONFIG_FILE);
        Path path = Path.of(configPath);
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        try {
            Map<String, String> result = new LinkedHashMap<>();
            for (String line : Files.readAllLines(path)) {
                Matcher matcher = CONFIG_LINE_PATTERN.matcher(line);
                if (matcher.matches()) {
                    result.put(matcher.group(1), fileValue(matcher.group(2)));
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("读取MPGS测试配置文件失败：" + path, e);
        }
    }

    /**
     * 获取必填配置值，缺失时直接让测试失败，避免以空凭据访问真实渠道。
     *
     * @param envName  环境变量名
     * @param fallback 文件配置兜底值
     * @return 配置值
     */
    private static String required(String envName, String fallback) {
        String value = value(envName, fallback);
        assertThat(value).as(envName).isNotBlank();
        return value;
    }

    /**
     * 获取配置值，环境变量优先于文件配置。
     *
     * @param envName  环境变量名
     * @param fallback 文件配置兜底值
     * @return 配置值或 null
     */
    private static String value(String envName, String fallback) {
        String envValue = System.getenv(envName);
        if (StringUtils.hasText(envValue)) {
            return envValue.trim();
        }
        return fallback == null ? null : fallback.trim();
    }

    /**
     * 清洗文件配置值，去掉最外层引号，保留密码等真实值本身。
     *
     * @param value 文件中的原始值
     * @return 清洗后的值
     */
    private static String fileValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * 清洗用户名配置中的行内说明。
     *
     * @param value 文件中的用户名值
     * @return 去掉括号说明和空格说明后的用户名
     */
    private static String usernameValue(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.replaceFirst("[（(].*$", "")
                .replaceFirst("\\s+.*$", "")
                .trim();
    }

    /**
     * 脱敏 MPGS Basic Auth 用户名。
     *
     * @param value 用户名
     * @return 脱敏用户名
     */
    private String maskUsername(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 8) + "***";
    }

    private record MaskedConfig(String baseUrl,
                                String version,
                                String merchantId,
                                String username,
                                String password,
                                String card) {
    }
}
