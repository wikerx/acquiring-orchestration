package com.scott.payment.capability;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantAdmissionPolicyTest {

    private static final LocalDateTime EFFECTIVE = LocalDateTime.parse("2026-11-20T11:00:00");
    private static final LocalDateTime EXPIRY = LocalDateTime.parse("2026-11-20T12:00:00");

    @Test
    void walletAuthorizationUsesTheWalletEvenWhenTheUnderlyingVisaIsKnown() {
        assertThat(MerchantAdmissionPolicy.itemKey("apple_pay", "VISA")).isEqualTo("METHOD:APPLE_PAY");
        assertThat(MerchantAdmissionPolicy.itemKey("bank_card", "VISA")).isEqualTo("CARD_BRAND:VISA");
        assertThat(MerchantAdmissionPolicy.itemKey("BANK_CARD", "UNKNOWN")).isNull();
        assertThat(MerchantAdmissionPolicy.isItemKey("METHOD:BANK_CARD")).isFalse();
        assertThat(MerchantAdmissionPolicy.isItemKey("CARD_BRAND:ALL")).isFalse();
    }

    @Test
    void evaluatesHalfOpenAuthorizationWindowAtTheFinalCheckTime() {
        var grant = new MerchantAdmissionPolicy.Grant("ACTIVE", "APPLICATION", 1, EFFECTIVE, EXPIRY);
        assertThat(MerchantAdmissionPolicy.permits("ENFORCED", grant, false, EFFECTIVE.minusNanos(1))).isFalse();
        assertThat(MerchantAdmissionPolicy.permits("ENFORCED", grant, false, EFFECTIVE)).isTrue();
        assertThat(MerchantAdmissionPolicy.permits("ENFORCED", grant, false, EXPIRY.minusNanos(100_000))).isTrue();
        assertThat(MerchantAdmissionPolicy.permits("ENFORCED", grant, false, EXPIRY)).isFalse();
    }

    @Test
    void observationRequiresFrozenScopeAndCannotRestoreExplicitRestrictions() {
        var suspended = new MerchantAdmissionPolicy.Grant("SUSPENDED", "HISTORICAL_BASELINE", 1, EFFECTIVE, null);
        var terminated = new MerchantAdmissionPolicy.Grant("TERMINATED", "APPLICATION", 3, EFFECTIVE, null);
        assertThat(MerchantAdmissionPolicy.permits("LEGACY_OBSERVE", null, false, EFFECTIVE)).isFalse();
        assertThat(MerchantAdmissionPolicy.permits("LEGACY_OBSERVE", null, true, EFFECTIVE)).isTrue();
        assertThat(MerchantAdmissionPolicy.permits("LEGACY_OBSERVE", suspended, true, EFFECTIVE)).isFalse();
        assertThat(MerchantAdmissionPolicy.permits("LEGACY_OBSERVE", terminated, true, EFFECTIVE)).isFalse();
        assertThat(MerchantAdmissionPolicy.permits("ENFORCED", null, true, EFFECTIVE)).isFalse();
        assertThat(MerchantAdmissionPolicy.permits(null, null, true, EFFECTIVE)).isFalse();
    }

    @Test
    void unknownAuthorizationAndHoldFailClosed() {
        var invalid = new MerchantAdmissionPolicy.Grant("ACTIVE", "APPLICATION", 1, null, null);
        var active = new MerchantAdmissionPolicy.Grant("ACTIVE", "APPLICATION", 1, EFFECTIVE, null);
        assertThat(MerchantAdmissionPolicy.permits("ENFORCED", invalid, true, EFFECTIVE)).isFalse();
        assertThat(MerchantAdmissionPolicy.permits("HOLD", active, true, EFFECTIVE)).isFalse();
    }
}
