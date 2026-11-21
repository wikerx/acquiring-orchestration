package com.scott.payment.admin.application.merchantpayment;

import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.AuthorizationCoverage;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.FeeReference;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.KnownCapability;
import com.scott.payment.admin.service.impl.MerchantPaymentCapabilityReadinessService.CapabilityKey;
import com.scott.payment.admin.service.impl.MerchantPaymentCapabilityReadinessService.ReadinessResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantPaymentMethodReadinessTests {
    @Test
    void marksEvaluatedConfigurationsWithNoGapAsReady() {
        var visa = new CapabilityKey("PAYMENT", "BANK_CARD", "VISA", "USD");
        var mastercard = new CapabilityKey("PAYMENT", "BANK_CARD", "MASTERCARD", "USD");
        var fee = new FeeReference("11", "5", LocalDateTime.of(2026, 11, 20, 12, 0));
        var routes = new ReadinessResult(1, 0, List.of(visa, mastercard),
                List.of(visa), List.of(), List.of(), List.of(), List.of());
        var projection = new MerchantPaymentMethodReadiness.Evaluation(routes, fee).forMethod("CARD_BRAND:MASTERCARD");

        assertThat(projection.status()).isEqualTo("READY");
        assertThat(projection.reasons()).isEmpty();
        assertThat(projection.feeReference()).isEqualTo(fee);
    }

    @Test
    void doesNotTreatAnyKnownCoveredScopeAsUnconditionalExecution() {
        var usd = new CapabilityKey("PAYMENT", "BANK_CARD", "VISA", "USD");
        var eur = new CapabilityKey("PAYMENT", "BANK_CARD", "VISA", "EUR");
        var fee = new FeeReference("11", "1", LocalDateTime.of(2026, 11, 20, 12, 0));
        var result = new ReadinessResult(1, 0, List.of(usd, eur), List.of(usd), List.of(), List.of(), List.of(), List.of());
        var candidate = new MerchantPaymentMethodReadiness.Evaluation(result, fee).forMethod("CARD_BRAND:VISA");

        assertThat(candidate.status()).isEqualTo("CONDITIONAL");
        assertThat(candidate.reasons()).containsExactly("CHANNEL_ROUTE_CONFLICT");
        assertThat(candidate.feeReference()).isEqualTo(fee);
    }

    @Test
    void overviewSeparatesAuthorityConfigurationAndExecution() {
        var visa = new CapabilityKey("PAYMENT", "BANK_CARD", "VISA", "USD");
        var mastercard = new CapabilityKey("PAYMENT", "BANK_CARD", "MASTERCARD", "USD");
        var wallet = new CapabilityKey("PAYMENT", "APPLE_PAY", "ALL", "USD");
        var now = LocalDateTime.of(2026, 11, 20, 12, 0);
        var reference = new FeeReference("11", "1", now);
        var routes = new ReadinessResult(2, 0, List.of(visa, mastercard, wallet),
                List.of(), List.of(), List.of(), List.of(), List.of());
        var authority = new MerchantPaymentMethodReadiness.AuthorityFacts(List.of(
                new AuthorizationCoverage("CARD_BRAND:VISA", "Visa", "ACTIVE"),
                new AuthorizationCoverage("CARD_BRAND:MASTERCARD", "Mastercard", "NOT_AUTHORIZED"),
                new AuthorizationCoverage("METHOD:APPLE_PAY", "Apple Pay", "LEGACY_OBSERVED")),
                Set.of("CARD_BRAND:VISA", "CARD_BRAND:MASTERCARD", "METHOD:APPLE_PAY"));
        var evaluation = new MerchantPaymentMethodReadiness.Evaluation(routes, reference);

        var overview = evaluation.accessSummary(authority, false, now);

        assertThat(overview.authorizedCount()).isEqualTo(1);
        assertThat(overview.legacyCoverageCount()).isEqualTo(1);
        assertThat(overview.configurationReadyCount()).isEqualTo(2);
        assertThat(overview.executionReadyCount()).isEqualTo(1);
        assertThat(overview.capabilities()).containsExactly(new KnownCapability("BANK_CARD", "VISA", "USD"));
        assertThat(evaluation.accessSummary(authority, true, now).executionReadyCount()).isZero();
    }

    @Test
    void unknownAndMissingFeeRemainDistinct() {
        var unknown = new MerchantPaymentMethodReadiness.Evaluation(null, null).forMethod("METHOD:APPLE_PAY");
        var wallet = new CapabilityKey("PAYMENT", "APPLE_PAY", "ALL", "USD");
        var result = new ReadinessResult(1, 0, List.of(wallet), List.of(), List.of(wallet), List.of(), List.of(), List.of());
        var missingFee = new MerchantPaymentMethodReadiness.Evaluation(result, null).forMethod("METHOD:APPLE_PAY");

        assertThat(unknown.status()).isEqualTo("UNKNOWN");
        assertThat(missingFee.status()).isEqualTo("BLOCKED");
        assertThat(missingFee.reasons()).containsExactly("FEE_CAPABILITY_NOT_COVERED");
        assertThat(missingFee.feeReference()).isNull();
    }
}
