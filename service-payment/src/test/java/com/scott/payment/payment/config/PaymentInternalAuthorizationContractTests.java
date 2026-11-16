package com.scott.payment.payment.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payment 内部接口最小路径授权配置契约。
 */
class PaymentInternalAuthorizationContractTests {

    private static final String TRADE_BLACK_RESOLVE_PATH =
            "/internal/payment/risk/trade-black/transactions/resolve";

    /**
     * 系统交易黑名单只能通过精确路径授权给 Admin，禁止扩大为 risk 或 payment 通配权限。
     */
    @Test
    void serviceAdminShouldAllowTradeBlackResolutionByExactPath() throws IOException {
        String configuration = readRepositoryFile("docs/deployment/nacos/service-payment-dev.yaml");
        String serviceAdminPermissions = between(configuration, "      service-admin:\n", "      service-job:\n");

        assertThat(serviceAdminPermissions)
                .contains("          - " + TRADE_BLACK_RESOLVE_PATH)
                .doesNotContain(
                        "          - /internal/payment/risk/**",
                        "          - /internal/payment/**"
                );
    }

    private String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertThat(start).as("service-admin internal authorization block start").isGreaterThanOrEqualTo(0);
        assertThat(end).as("service-admin internal authorization block end").isGreaterThan(start);
        return source.substring(start, end);
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct);
        }
        return Files.readString(Path.of("..").resolve(relativePath).normalize());
    }
}
