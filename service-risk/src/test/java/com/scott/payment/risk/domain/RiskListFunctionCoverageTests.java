package com.scott.payment.risk.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理端名单定义与运行时名单枚举覆盖契约测试。
 */
class RiskListFunctionCoverageTests {

    private static final Set<String> EXPECTED_RUNTIME_TABLES = Set.of(
            "risk_aml_card",
            "risk_aml_card_bin",
            "risk_aml_ip",
            "risk_aml_country",
            "risk_aml_email",
            "risk_aml_phone",
            "risk_aml_cardholder_name",
            "risk_aml_legal_person",
            "risk_aml_enterprise",
            "risk_aml_merchant_billing_address",
            "risk_aml_source_url",
            "risk_black_card_no",
            "risk_black_card_fingerprint",
            "risk_black_card_bin",
            "risk_black_cardholder_name",
            "risk_black_phone",
            "risk_black_ip",
            "risk_black_region",
            "risk_black_email",
            "risk_black_email_username",
            "risk_black_email_domain",
            "risk_black_billing_address",
            "risk_black_billing_zip",
            "risk_black_billing_country",
            "risk_black_shipping_address",
            "risk_black_shipping_zip",
            "risk_black_shipping_country",
            "risk_black_issuer_country",
            "risk_black_device_fingerprint",
            "risk_white_merchant",
            "risk_white_card_no",
            "risk_white_card_fingerprint",
            "risk_white_card_bin",
            "risk_white_ip",
            "risk_white_trade_country",
            "risk_white_issuer_country",
            "risk_white_email",
            "risk_white_email_domain",
            "risk_white_phone",
            "risk_white_customer_id",
            "risk_white_device_fingerprint"
    );

    @Test
    void shouldCoverEveryManagedAmlBlacklistAndWhitelistTable() {
        Set<String> runtimeTables = Arrays.stream(RiskListFunction.values())
                .map(RiskListFunction::getTableName)
                .collect(Collectors.toSet());

        assertThat(runtimeTables).containsExactlyInAnyOrderElementsOf(EXPECTED_RUNTIME_TABLES);
        assertThat(RiskListFunction.values()).hasSize(EXPECTED_RUNTIME_TABLES.size());
    }
}
