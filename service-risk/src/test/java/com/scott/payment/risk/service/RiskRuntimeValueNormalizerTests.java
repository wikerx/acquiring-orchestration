package com.scott.payment.risk.service;

import com.scott.payment.risk.domain.RiskRuntimeLookupValue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 风控运行时值归一化契约测试。
 */
class RiskRuntimeValueNormalizerTests {

    /**
     * 被测归一化器，所有用例验证原始敏感值不会进入匹配摘要或展示值。
     */
    private final RiskRuntimeValueNormalizer normalizer = new RiskRuntimeValueNormalizer();

    /**
     * 验证卡号指纹稳定且查询值不保留原始 PAN。
     */
    @Test
    void shouldGenerateStableCardFingerprintWithoutRetainingPan() {
        String pan = "4111111111111234";
        String fingerprint = sha256(pan);

        RiskRuntimeLookupValue lookupValue = normalizer.cardFingerprint("4111 1111 1111 1234");

        assertThat(lookupValue.getRawValue()).isEqualTo(fingerprint);
        assertThat(lookupValue.getMatchValueHash()).isEqualTo(sha256(fingerprint));
        assertThat(lookupValue.getMatchValueMasked()).doesNotContain(pan);
    }

    /**
     * 验证普通文本摘要与管理端归一化规则保持一致。
     */
    @Test
    void shouldAlignSimpleTextHashWithAdminNormalizer() {
        RiskRuntimeLookupValue lookupValue = normalizer.text("  Example Trading Limited  ", true);

        assertThat(lookupValue.getRawValue()).isEqualTo("Example Trading Limited");
        assertThat(lookupValue.getMatchValueHash()).isEqualTo(sha256("example trading limited"));
        assertThat(lookupValue.getMatchValueMasked()).doesNotContain("Example Trading Limited");
    }

    /**
     * 验证邮编匹配摘要忽略空格和连字符差异。
     */
    @Test
    void shouldIgnoreSpacesAndHyphensWhenNormalizingPostalCode() {
        RiskRuntimeLookupValue lookupValue = normalizer.postalCode(" sw1a- 1aa ");

        assertThat(lookupValue.getRawValue()).isEqualTo("SW1A- 1AA");
        assertThat(lookupValue.getMatchValueHash()).isEqualTo(sha256("SW1A1AA"));
    }

    /**
     * 验证频控键使用规范化 IP 的摘要而不是原始地址。
     */
    @Test
    void shouldHashCanonicalIpForFrequencyKeys() {
        RiskRuntimeLookupValue lookupValue = normalizer.ip("010.000.000.001");

        assertThat(lookupValue.getRawValue()).isEqualTo("10.0.0.1");
        assertThat(lookupValue.getMatchValueHash()).isEqualTo(sha256("10.0.0.1"));
        assertThat(lookupValue.getMatchValueHash()).doesNotContain(lookupValue.getRawValue());
    }

    /**
     * 验证国家、省州和城市按层级地区匹配规则归一化。
     */
    @Test
    void shouldNormalizeCountryStateAndCityForHierarchicalRegionMatching() {
        RiskRuntimeLookupValue lookupValue = normalizer.region(
                "brb",
                " Saint Michael ",
                " Bridgetown ");

        assertThat(lookupValue.getCountryAlpha3()).isEqualTo("BRB");
        assertThat(lookupValue.getStateProvinceName()).isEqualTo("Saint Michael");
        assertThat(lookupValue.getCityName()).isEqualTo("Bridgetown");
        assertThat(lookupValue.getMatchValueMasked()).isEqualTo("BRB/Saint Michael/Bridgetown");
    }

    /**
     * 计算测试期望值使用的 SHA-256 十六进制摘要。
     */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
