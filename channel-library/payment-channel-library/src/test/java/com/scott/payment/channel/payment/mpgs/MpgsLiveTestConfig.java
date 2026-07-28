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

    /**
     * ENABLED ENV，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；不允许为空；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    static final String ENABLED_ENV = "MPGS_LIVE_TEST_ENABLED";

    /**
     * DEFAULT CONFIG FILE，用于保存 Mpgs Live Test Config 中与 default配置file 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_CONFIG_FILE = "/Users/scott/Desktop/未命名 2.txt";

    /**
     * CONFIG LINE PATTERN，用于保存 Mpgs Live Test Config 中与 配置linepattern 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final Pattern CONFIG_LINE_PATTERN = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9_-]*)\\s*[:：]\\s*(.*?)\\s*$");

    /**
     * DEFAULT CARD NO，用于保存 Mpgs Live Test Config 中与 defaultcardno 相关的业务属性。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_CARD_NO = "5123450000000008";

    /**
     * DEFAULT EXPIRY MONTH，用于保存 Mpgs Live Test Config 中与 defaultexpirymonth 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_EXPIRY_MONTH = "01";

    /**
     * DEFAULT EXPIRY YEAR，用于保存 Mpgs Live Test Config 中与 defaultexpiryyear 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_EXPIRY_YEAR = "2039";

    /**
     * DEFAULT CSC，用于保存 Mpgs Live Test Config 中与 defaultcsc 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DEFAULT_CSC = "100";

    /**
     * DEFAULT AMOUNT，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；不允许为空；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private static final String DEFAULT_AMOUNT = "1.00";

    /**
     * DEFAULT CURRENCY，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；不允许为空；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private static final String DEFAULT_CURRENCY = "USD";

    /**
     * base URL，表示回调、通知、来源站点或远程接口地址。
     * <p>
     * 单位：无；格式：HTTP/HTTPS URL 或服务路径；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度和协议由调用方校验；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String baseUrl;

    /**
     * version，用于保存 Mpgs Live Test Config 中与 version 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String version;

    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
     * </p>
     */
    private final String merchantId;

    /**
     * username，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String username;

    /**
     * password，用于保存 Mpgs Live Test Config 中与 password 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；高敏感字段，禁止明文打印日志，禁止写入异常消息。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String password;

    /**
     * card No，表示银行卡号或脱敏卡号字段。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；银行卡敏感字段，只允许脱敏或摘要化使用。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String cardNo;

    /**
     * expiry Month，用于保存 Mpgs Live Test Config 中与 expirymonth 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String expiryMonth;

    /**
     * expiry Year，用于保存 Mpgs Live Test Config 中与 expiryyear 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String expiryYear;

    /**
     * csc，用于保存 Mpgs Live Test Config 中与 csc 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String csc;

    /**
     * amount，表示当前交易、费用、限额或统计口径下的金额值。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：必须与 currency 或同名币种字段一起解释。
     * </p>
     */
    private final String amount;

    /**
     * currency，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
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
