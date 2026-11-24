package com.scott.payment.link.service;

import com.scott.payment.capability.MerchantAdmissionPolicy.Grant;
import com.scott.payment.capability.MerchantAdmissionReader;
import com.scott.payment.capability.MerchantAdmissionReader.FeeCoverage;
import com.scott.payment.capability.MerchantAdmissionReader.ItemAuthority;
import com.scott.payment.capability.MerchantAdmissionReader.MerchantAuthority;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostedCheckoutNonCardScopeTest {
    private static final String MERCHANT = "synthetic-hosted-merchant";
    private final MerchantAdmissionReader admission = mock(MerchantAdmissionReader.class);
    private final PaymentLinkDatabaseCapabilities capabilities = mock(PaymentLinkDatabaseCapabilities.class);
    private final PaymentLinkMethodScope scope = new PaymentLinkMethodScope(capabilities, admission);

    @Test
    void selectsAuthorizedNonCardMethodsWithAUniquePaymentFee() {
        var methods = Map.of(
                "METHOD:APPLE_PAY", grant("ACTIVE"),
                "METHOD:PAYPAL", grant("ACTIVE"),
                "METHOD:GOOGLE_PAY", grant("ACTIVE"),
                "METHOD:CASH_APP_PAY", grant("SUSPENDED"),
                "METHOD:PYUSD", grant("ACTIVE"),
                "METHOD:UPI", grant("ACTIVE"),
                "METHOD:BTC_ON_CHAIN", grant("ACTIVE"),
                "CARD_BRAND:VISA", grant("ACTIVE"));
        when(admission.read(MERCHANT)).thenReturn(new MerchantAuthority("ENFORCED", methods));
        when(admission.enabledDirectory()).thenReturn(methods.keySet());
        when(admission.transactionFeeCoverage(MERCHANT)).thenReturn(List.of(
                fee("APPLE_PAY"), fee("PAYPAL"), fee("GOOGLE_PAY"), fee("CASH_APP_PAY"),
                fee("UPI"), fee("UPI"), fee("BTC_ON_CHAIN"), fee("BANK_CARD")));
        configureRoutes();

        assertThat(scope.hostedNonCardMethods(scope.snapshot(MERCHANT), null))
                .containsExactly("APPLE_PAY", "BTC_ON_CHAIN", "GOOGLE_PAY", "PAYPAL");
    }

    @Test
    void revalidatesFrozenChoicesWithoutAddingNewMethods() {
        var methods = Map.of("METHOD:APPLE_PAY", grant("ACTIVE"), "METHOD:PAYPAL", grant("ACTIVE"));
        when(admission.read(MERCHANT)).thenReturn(new MerchantAuthority("ENFORCED", methods));
        when(admission.enabledDirectory()).thenReturn(methods.keySet());
        when(admission.transactionFeeCoverage(MERCHANT)).thenReturn(List.of(fee("APPLE_PAY"), fee("PAYPAL")));
        configureRoutes();

        assertThat(scope.hostedNonCardMethods(scope.snapshot(MERCHANT), Set.of("APPLE_PAY")))
                .containsExactly("APPLE_PAY");
        when(admission.read(MERCHANT)).thenReturn(new MerchantAuthority(
                "ENFORCED", Map.of("METHOD:APPLE_PAY", grant("SUSPENDED"), "METHOD:PAYPAL", grant("ACTIVE"))));
        assertThat(scope.hostedNonCardMethods(scope.snapshot(MERCHANT), Set.of("APPLE_PAY"))).isEmpty();
    }

    @Test
    void displaysExplicitActiveApprovalForUnmigratedMerchantWithoutChangingLinkAdmission() {
        var methods = Map.of("METHOD:APPLE_PAY", grant("ACTIVE"), "METHOD:PAYPAL", grant("SUSPENDED"),
                "METHOD:UPI", new ItemAuthority(null, true));
        when(admission.read(MERCHANT)).thenReturn(new MerchantAuthority(null, methods));
        when(admission.enabledDirectory()).thenReturn(methods.keySet());
        when(admission.transactionFeeCoverage(MERCHANT)).thenReturn(List.of(fee("APPLE_PAY"), fee("PAYPAL"), fee("UPI")));
        configureRoutes();

        assertThat(scope.hostedNonCardMethods(scope.snapshot(MERCHANT), null)).containsExactly("APPLE_PAY");
        assertThat(scope.resolve(MERCHANT, "HKD", List.of("METHOD:APPLE_PAY"), false)).isEmpty();
    }

    private void configureRoutes() {
        var profile = new MerchantRouteProfile();
        profile.setRouteOptions(List.of());
        when(capabilities.profile(MERCHANT)).thenReturn(profile);
    }

    private static ItemAuthority grant(String status) {
        return new ItemAuthority(new Grant(status, "APPLICATION", 1L, LocalDateTime.now().minusDays(1), null), false);
    }

    private static FeeCoverage fee(String method) {
        return new FeeCoverage("PAYMENT", method, "ALL");
    }

}
