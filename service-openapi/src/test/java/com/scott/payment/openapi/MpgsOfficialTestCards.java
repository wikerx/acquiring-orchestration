package com.scott.payment.openapi;

import com.scott.payment.component.core.util.SensitiveDataMaskUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * MPGS live 验收允许使用的 Mastercard Gateway 官方测试卡目录。
 *
 * <p>来源：{@value #DOCUMENTATION_URL}。该目录只服务真实 MPGS 沙箱测试，禁止接受运行时注入的
 * 任意 PAN；其他渠道的测试卡策略不受此目录限制。</p>
 */
final class MpgsOfficialTestCards {

    static final String DOCUMENTATION_URL =
            "https://test-gateway.mastercard.com/api/documentation/integrationGuidelines/"
                    + "supportedFeatures/testAndGoLive.html?locale=zh_CN";

    private static final List<TestCard> CARDS = List.of(
            new TestCard("MASTERCARD", "5123450000000008", "Y"),
            new TestCard("MASTERCARD", "2223000000000007", "Y"),
            new TestCard("MASTERCARD", "5111111111111118", "N"),
            new TestCard("MASTERCARD", "2223000000000023", "N"),
            new TestCard("VISA", "4508750015741019", "Y"),
            new TestCard("VISA", "4012000033330026", "N"),
            new TestCard("JCB", "3528000000000007", "Y"),
            new TestCard("JCB", "3528111100000001", "N")
    );

    private MpgsOfficialTestCards() {
    }

    static TestCard defaultCard() {
        return CARDS.get(0);
    }

    static List<TestCard> all() {
        return CARDS;
    }

    static TestCard requireOfficial(String cardBrand, String cardNo) {
        String normalizedBrand = cardBrand == null ? "" : cardBrand.trim().toUpperCase(Locale.ROOT);
        return CARDS.stream()
                .filter(card -> card.cardBrand().equals(normalizedBrand) && card.cardNo().equals(cardNo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "MPGS live tests only accept cards published in the official test-card catalog"));
    }

    /**
     * Mastercard Gateway 官方测试卡。完整 PAN 和安全码只能进入真实沙箱请求，不得写日志。
     */
    record TestCard(String cardBrand, String cardNo, String threeDsEnrolled) {

        private static final String EXPIRATION_MONTH = "01";
        private static final String EXPIRATION_YEAR = "2039";
        private static final String SECURITY_CODE = "100";

        String expirationMonth() {
            return EXPIRATION_MONTH;
        }

        String expirationYear() {
            return EXPIRATION_YEAR;
        }

        String securityCode() {
            return SECURITY_CODE;
        }

        String maskedCardNo() {
            return SensitiveDataMaskUtils.maskPan(cardNo);
        }

        Map<String, String> safeSummary() {
            return Map.of(
                    "cardBrand", cardBrand,
                    "maskedCardNo", maskedCardNo(),
                    "threeDsEnrolled", threeDsEnrolled
            );
        }

        @Override
        public String toString() {
            return safeSummary().toString();
        }
    }
}
