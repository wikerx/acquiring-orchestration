package com.scott.payment.risk.service;

import com.scott.payment.component.core.iso.IsoCountryResolver;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.risk.domain.RiskRuntimeLookupValue;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskRuntimeValueNormalizer
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 交易链路风控值归一化组件。
 * @status : create
 */
@Component
public class RiskRuntimeValueNormalizer {

    /** 管理端 BIN 区间统一采用的最大位数，短 BIN 右补零后参与数值比较。 */
    private static final int CARD_BIN_MAX_LENGTH = 11;

    /**
     * 去除商户号首尾空白并生成稳定查询哈希。
     *
     * @param merchantId 平台商户号，不允许为空
     * @return 商户号查询值；输入为空时返回 {@code null}
     */
    public RiskRuntimeLookupValue merchant(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        String value = merchantId.trim();
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(value);
        lookupValue.setMatchValueHash(sha256(value));
        lookupValue.setMatchValueMasked(value);
        return lookupValue;
    }

    /**
     * 提取卡 BIN 数字并转换为固定长度区间比较值，不保留完整卡号。
     *
     * @param cardBin 卡 BIN 或以卡号形式传入的数字文本
     * @return 仅保留前六位展示值和固定长度数值的查询对象；有效数字少于六位时返回 {@code null}
     */
    public RiskRuntimeLookupValue cardBin(String cardBin) {
        if (!StringUtils.hasText(cardBin)) {
            return null;
        }
        String digits = cardBin.replaceAll("\\D", "");
        if (digits.length() < 6) {
            return null;
        }
        String normalized = rightPad(digits.substring(0, Math.min(digits.length(), CARD_BIN_MAX_LENGTH)), CARD_BIN_MAX_LENGTH, '0');
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(digits);
        lookupValue.setMatchValueMasked(digits.substring(0, 6));
        lookupValue.setNumericValue(new BigDecimal(normalized));
        return lookupValue;
    }

    /**
     * 规范化卡号并生成只包含哈希或脱敏片段的风控查询值。
     * @param cardNo 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 当前方法生成的 {@code RiskRuntimeLookupValue} 结果
     */
    public RiskRuntimeLookupValue cardNo(String cardNo) {
        if (!StringUtils.hasText(cardNo)) {
            return null;
        }
        String digits = cardNo.replaceAll("\\D", "");
        if (digits.length() < 12 || digits.length() > 19) {
            return null;
        }
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(digits);
        lookupValue.setMatchValueMasked(SensitiveDataMaskUtils.maskPan(digits));
        lookupValue.setMatchValueHash(sha256(digits));
        return lookupValue;
    }

    /**
     * 从完整 PAN 生成稳定卡指纹，再按管理端卡指纹名单的文本规则计算查询哈希。
     */
    public RiskRuntimeLookupValue cardFingerprint(String cardNo) {
        RiskRuntimeLookupValue cardLookup = cardNo(cardNo);
        if (cardLookup == null) {
            return null;
        }
        return text(sha256(cardLookup.getRawValue()), true);
    }

    /**
     * 规范化 IP 地址并生成数值区间或摘要形式的风控查询值。
     * @param ip 待规范化的可识别信息，仅允许以脱敏、哈希或数值区间形式参与匹配
     * @return 当前方法生成的 {@code RiskRuntimeLookupValue} 结果
     */
    public RiskRuntimeLookupValue ip(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        ParsedIp parsedIp = parseIp(ip.trim());
        if (parsedIp == null) {
            return null;
        }
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(parsedIp.original());
        lookupValue.setMatchValueMasked(parsedIp.original());
        lookupValue.setMatchValueHash(sha256(parsedIp.original()));
        lookupValue.setNumericValue(new BigDecimal(parsedIp.number()));
        lookupValue.setIpVersion(parsedIp.version());
        return lookupValue;
    }

    /**
     * 将国家名称、二位码或三位码解析为大写 ISO alpha-3 代码。
     *
     * @param country 国家或地区输入
     * @return 含 ISO alpha-3 代码的查询值；无法得到三位码时返回 {@code null}
     */
    public RiskRuntimeLookupValue country(String country) {
        if (!StringUtils.hasText(country)) {
            return null;
        }
        String alpha3 = IsoCountryResolver.resolve(country.trim())
                .map(value -> value.alpha3().toUpperCase(Locale.ROOT))
                .orElseGet(() -> country.trim().toUpperCase(Locale.ROOT));
        if (alpha3.length() != 3) {
            return null;
        }
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(country.trim());
        lookupValue.setCountryAlpha3(alpha3);
        lookupValue.setMatchValueMasked(alpha3);
        lookupValue.setMatchValueHash(sha256(alpha3));
        return lookupValue;
    }

    /**
     * 归一化账单区域层级。国家是区域规则的必需维度，州省和城市按规则层级逐级参与匹配。
     */
    public RiskRuntimeLookupValue region(String country, String stateProvince, String city) {
        RiskRuntimeLookupValue countryLookup = country(country);
        if (countryLookup == null) {
            return null;
        }
        String normalizedState = trimToNull(stateProvince);
        String normalizedCity = trimToNull(city);
        String masked = String.join("/",
                countryLookup.getCountryAlpha3(),
                normalizedState == null ? "" : normalizedState,
                normalizedCity == null ? "" : normalizedCity);
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(masked);
        lookupValue.setCountryAlpha3(countryLookup.getCountryAlpha3());
        lookupValue.setStateProvinceName(normalizedState);
        lookupValue.setCityName(normalizedCity);
        lookupValue.setMatchValueMasked(masked);
        lookupValue.setMatchValueHash(sha256(masked.toLowerCase(Locale.ROOT)));
        return lookupValue;
    }

    /**
     * 规范化邮箱后生成不可逆摘要形式的风控查询值。
     * @param email 待规范化的可识别信息，仅允许以脱敏、哈希或数值区间形式参与匹配
     * @return 当前方法生成的 {@code RiskRuntimeLookupValue} 结果
     */
    public RiskRuntimeLookupValue email(String email) {
        String normalizedEmail = normalizedEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return null;
        }
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(normalizedEmail);
        lookupValue.setMatchValueMasked(SensitiveDataMaskUtils.maskEmail(normalizedEmail));
        lookupValue.setMatchValueHash(sha256(normalizedEmail));
        return lookupValue;
    }

    /**
     * 从规范邮箱中提取域名并生成域名匹配哈希。
     *
     * @param email 邮箱地址
     * @return 域名查询值；邮箱无效时返回 {@code null}
     */
    public RiskRuntimeLookupValue emailDomain(String email) {
        String normalizedEmail = normalizedEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return null;
        }
        String domain = normalizedEmail.substring(normalizedEmail.indexOf('@') + 1);
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(domain);
        lookupValue.setMatchValueMasked(domain);
        lookupValue.setMatchValueHash(sha256(domain));
        return lookupValue;
    }

    /**
     * 从规范邮箱中提取用户名并生成脱敏展示值与匹配哈希。
     *
     * @param email 邮箱地址
     * @return 邮箱用户名查询值；邮箱无效时返回 {@code null}
     */
    public RiskRuntimeLookupValue emailUsername(String email) {
        String normalizedEmail = normalizedEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return null;
        }
        String username = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(username);
        lookupValue.setMatchValueMasked(maskText(username));
        lookupValue.setMatchValueHash(sha256(username));
        return lookupValue;
    }

    /**
     * 去除电话号码首尾和内部空白，并生成脱敏展示值与不可逆哈希。
     *
     * @param phone 电话号码
     * @return 电话查询值；输入为空时返回 {@code null}
     */
    public RiskRuntimeLookupValue phone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String value = phone.trim().replaceAll("\\s+", "");
        if (!StringUtils.hasText(value)) {
            return null;
        }
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(value);
        lookupValue.setMatchValueMasked(SensitiveDataMaskUtils.maskMobile(value));
        lookupValue.setMatchValueHash(sha256(value));
        return lookupValue;
    }

    /**
     * 按管理端普通名单值规则归一化文本。
     */
    public RiskRuntimeLookupValue text(String value, boolean sensitive) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(normalized);
        lookupValue.setMatchValueMasked(sensitive ? maskText(normalized) : normalized);
        lookupValue.setMatchValueHash(sha256(normalized.toLowerCase(Locale.ROOT)));
        return lookupValue;
    }

    /**
     * 按管理端邮编规则归一化，展示值保留格式，查询值忽略空格和短横线。
     */
    public RiskRuntimeLookupValue postalCode(String postalCode) {
        if (!StringUtils.hasText(postalCode)) {
            return null;
        }
        String displayValue = postalCode.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        String lookupValueText = displayValue.replaceAll("[\\s-]", "");
        if (!StringUtils.hasText(lookupValueText)) {
            return null;
        }
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(displayValue);
        lookupValue.setMatchValueMasked(maskText(displayValue));
        lookupValue.setMatchValueHash(sha256(lookupValueText));
        return lookupValue;
    }

    /**
     * 规范化来源 URL 主机名，生成只用于白名单匹配的风控查询值。
     * @param sourceUrl 来源或回跳地址，必须经过协议、主机和长度校验
     * @return 当前方法生成的 {@code RiskRuntimeLookupValue} 结果
     */
    public RiskRuntimeLookupValue sourceHost(String sourceUrl) {
        String host = normalizeHost(sourceUrl);
        if (!StringUtils.hasText(host)) {
            return null;
        }
        RiskRuntimeLookupValue lookupValue = new RiskRuntimeLookupValue();
        lookupValue.setRawValue(sourceUrl == null ? null : sourceUrl.trim());
        lookupValue.setSourceHost(host);
        lookupValue.setMatchValueMasked(host);
        lookupValue.setMatchValueHash(sha256(host));
        return lookupValue;
    }

    /**
     * 校验邮箱仅含一个分隔符，并将域名转换为 ASCII 小写形式。
     *
     * @param email 原始邮箱地址
     * @return 规范邮箱；结构或域名无效时返回 {@code null}
     */
    private String normalizedEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        String value = email.trim().toLowerCase(Locale.ROOT);
        int atIndex = value.indexOf('@');
        if (atIndex <= 0 || atIndex != value.lastIndexOf('@') || atIndex == value.length() - 1) {
            return null;
        }
        String host = normalizeDomain(value.substring(atIndex + 1));
        if (!StringUtils.hasText(host)) {
            return null;
        }
        return value.substring(0, atIndex) + "@" + host;
    }

    /**
     * 去除文本首尾空白，将空白输入统一转换为 {@code null}。
     *
     * @param value 原始文本
     * @return 规范文本或 {@code null}
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 解析绝对 URL 并提取规范化主机名，不接受缺少主机部分的相对地址。
     *
     * @param sourceUrl 原始来源 URL
     * @return ASCII 小写主机名；解析失败时返回 {@code null}
     */
    private String normalizeHost(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(sourceUrl.trim());
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return null;
            }
            return normalizeDomain(host);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 清理域名前导分隔符并按 IDN 规则转换为 ASCII 小写形式。
     *
     * @param rawDomain 原始域名
     * @return 规范域名；IDN 转换失败时返回 {@code null}
     */
    private String normalizeDomain(String rawDomain) {
        if (!StringUtils.hasText(rawDomain)) {
            return null;
        }
        String domain = rawDomain.trim().toLowerCase(Locale.ROOT);
        while (domain.startsWith("@")) {
            domain = domain.substring(1);
        }
        try {
            return IDN.toASCII(domain).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 严格解析 IPv4 或 IPv6，并计算用于数据库区间比较的无符号整数。
     *
     * @param ip 已去除首尾空白的地址文本
     * @return 规范地址、版本和数值；地址无效时返回 {@code null}
     */
    private ParsedIp parseIp(String ip) {
        try {
            if (ip.contains(":")) {
                byte[] bytes = InetAddress.getByName(ip).getAddress();
                if (bytes.length != 16) {
                    return null;
                }
                return new ParsedIp(InetAddress.getByName(ip).getHostAddress().toLowerCase(Locale.ROOT), "IPV6", new BigInteger(1, bytes));
            }
            String[] parts = ip.split("\\.", -1);
            if (parts.length != 4) {
                return null;
            }
            BigInteger number = BigInteger.ZERO;
            StringBuilder normalizedIpv4 = new StringBuilder();
            for (String part : parts) {
                if (!part.matches("\\d{1,3}")) {
                    return null;
                }
                int segment = Integer.parseInt(part);
                if (segment < 0 || segment > 255) {
                    return null;
                }
                if (!normalizedIpv4.isEmpty()) {
                    normalizedIpv4.append('.');
                }
                normalizedIpv4.append(segment);
                number = number.shiftLeft(8).add(BigInteger.valueOf(segment));
            }
            return new ParsedIp(normalizedIpv4.toString(), "IPV4", number);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    /**
     * 将卡 BIN 数字串右侧补齐到固定比较长度。
     *
     * @param value 原始数字串
     * @param length 目标长度
     * @param ch 补位字符
     * @return 长度不小于目标值的比较文本
     */
    private String rightPad(String value, int length, char ch) {
        if (value.length() >= length) {
            return value;
        }
        return value + String.valueOf(ch).repeat(length - value.length());
    }

    /**
     * 对通用敏感文本仅保留首尾字符，短文本完全隐藏。
     *
     * @param value 待脱敏文本
     * @return 脱敏文本；输入为空时返回 {@code null}
     */
    private String maskText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= 4 ? "***" : text.charAt(0) + "***" + text.charAt(text.length() - 1);
    }

    /**
     * 计算风控匹配使用的稳定 SHA-256 十六进制摘要。
     *
     * @param value 已按对应规则规范化的明文，仅在内存中参与摘要计算
     * @return 小写十六进制摘要
     */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("risk lookup hash failed", exception);
        }
    }

    private record ParsedIp(String original, String version, BigInteger number) {
    }
}
