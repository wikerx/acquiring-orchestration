package com.scott.payment.link.service;

import com.scott.payment.capability.MerchantAdmissionPolicy.Grant;
import com.scott.payment.capability.MerchantAdmissionReader;
import com.scott.payment.capability.MerchantAdmissionReader.FeeCoverage;
import com.scott.payment.capability.MerchantAdmissionReader.ItemAuthority;
import com.scott.payment.capability.MerchantAdmissionReader.MerchantAuthority;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.component.db.route.model.MerchantRouteProfile.RouteOption;
import com.scott.payment.link.model.PaymentLinkManagementModels.AllowedMethod;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentLinkMethodScopeTest {
    private static final String MERCHANT = "fixture-link-merchant";
    private static final String VISA = "CARD_BRAND:VISA";
    private static final String MASTERCARD = "CARD_BRAND:MASTERCARD";
    private static final String APPLE_PAY = "METHOD:APPLE_PAY";
    private final MerchantAdmissionReader authority = mock(MerchantAdmissionReader.class);
    private final PaymentLinkDatabaseCapabilities routes = mock(PaymentLinkDatabaseCapabilities.class);
    private final PaymentLinkMethodScope methodScope = new PaymentLinkMethodScope(routes, authority);

    @Test
    void aSharedMidAndFeeDoNotWidenApprovedCardBrands() {
        configure(Set.of(VISA), List.of(new FeeCoverage("PAYMENT", "BANK_CARD", "ALL")),
                option(1, "BANK_CARD", "USD", 10));

        assertThat(methodScope.enabledKeys(methodScope.snapshot(MERCHANT), "USD")).containsExactly(VISA);
        assertThat(methodScope.resolve(MERCHANT, "USD", List.of(VISA, MASTERCARD), false))
                .singleElement().satisfies(method -> assertThat(method.getBrands()).containsExactly("VISA"));
        assertUnavailable(MASTERCARD);
    }

    @Test
    void missingFeeAndAnEqualPriorityRouteConflictBothExcludeTheApprovedMethod() {
        var first = option(1, "BANK_CARD", "USD", 10);
        configure(Set.of(VISA), List.of(), first);
        assertUnavailable(VISA);
        assertThat(methodScope.resolve(MERCHANT, "USD", List.of(VISA), false)).isEmpty();

        configure(Set.of(VISA), cardFee(), first, option(2, "BANK_CARD", "USD", 10));
        assertUnavailable(VISA);
        assertThat(methodScope.resolve(MERCHANT, "USD", List.of(VISA), false)).isEmpty();
    }

    @Test
    void separatesCurrenciesAndUsesTheUniqueHighestPriorityCandidate() {
        var eur = option(2, "BANK_CARD", "EUR", 10);
        var usd = option(1, "BANK_CARD", "USD", 10);
        configure(Set.of(VISA), cardFee(), usd, eur);

        assertThat(methodScope.enabledKeys(methodScope.snapshot(MERCHANT), "USD")).containsExactly(VISA);
        assertThat(methodScope.enabledKeys(methodScope.snapshot(MERCHANT), "EUR")).containsExactly(VISA);
        assertThat(methodScope.enabledKeys(methodScope.snapshot(MERCHANT), "JPY")).isEmpty();

        var preferred = option(3, "BANK_CARD", "USD", 1);
        preferred.setCapabilitySupport3ds(1);
        configure(Set.of(VISA), cardFee(), usd, preferred);
        assertThat(methodScope.resolve(MERCHANT, "USD", List.of(VISA), true))
                .singleElement().satisfies(method -> {
                    assertThat(method.getThreeDsMode()).isEqualTo("AUTO");
                    assertThat(method.getChannelCode()).isNull();
                });
    }

    @Test
    void walletCanBeFrozenIndependentlyAndReopensOnlyWhileItsConfigurationIsEffective() {
        var wallet = option(4, "APPLE_PAY", "USD", 10);
        var fees = List.of(new FeeCoverage("PAYMENT", "APPLE_PAY", "ALL"));
        configure(Set.of(APPLE_PAY), fees, wallet, option(1, "BANK_CARD", "USD", 10));

        var frozen = PaymentLinkMethodScope.serialize(List.of(APPLE_PAY));
        var keys = PaymentLinkMethodScope.deserialize(frozen);
        assertThat(methodScope.resolve(MERCHANT, "USD", keys, true))
                .extracting(AllowedMethod::getPaymentMethod).containsExactly("APPLE_PAY");
        assertThat(methodScope.enabledKeys(methodScope.snapshot(MERCHANT), "USD"))
                .containsExactly(APPLE_PAY);

        wallet.setBindingExpireTime(LocalDateTime.now().minusSeconds(1));
        assertThat(methodScope.resolve(MERCHANT, "USD", keys, false)).isEmpty();
        assertUnavailable(APPLE_PAY);
    }

    private void configure(Set<String> approved, List<FeeCoverage> fees, RouteOption... candidates) {
        LocalDateTime effectiveAt = LocalDateTime.now().minusDays(1);
        var grants = approved.stream().collect(java.util.stream.Collectors.toMap(key -> key,
                key -> new ItemAuthority(new Grant("ACTIVE", "APPLICATION", 1L, effectiveAt, null), false)));
        when(authority.read(MERCHANT)).thenReturn(new MerchantAuthority("ENFORCED", Map.copyOf(grants)));
        when(authority.transactionFeeCoverage(MERCHANT)).thenReturn(fees);
        when(authority.enabledDirectory()).thenReturn(Set.of(VISA, MASTERCARD, APPLE_PAY));
        var profile = new MerchantRouteProfile();
        profile.setRouteOptions(List.of(candidates));
        when(routes.profile(MERCHANT)).thenReturn(profile);
    }

    private void assertUnavailable(String key) {
        assertThatThrownBy(() -> methodScope.resolve(MERCHANT, "USD", List.of(key), true))
                .isInstanceOfSatisfying(ServiceException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(ApiResultEnum.MERCHANT_PAYMENT_CONFIG_UNAVAILABLE.getCode()));
    }

    private static List<FeeCoverage> cardFee() {
        return List.of(new FeeCoverage("PAYMENT", "BANK_CARD", "VISA"));
    }

    private static RouteOption option(long midId, String paymentMethod, String currency, int priority) {
        var candidate = new RouteOption();
        candidate.setMidConfigId(midId);
        candidate.setCapabilityId(midId);
        candidate.setCapabilitySortOrder(priority);
        candidate.setBindingStatus(1);
        candidate.setMidStatus(1);
        candidate.setChannelStatus(1);
        candidate.setSupportAcquiring(1);
        candidate.setCapabilityStatus(1);
        candidate.setBusinessType("ACQUIRING");
        candidate.setChannelCode("SYNTHETIC");
        candidate.setPaymentMethodScope(paymentMethod);
        candidate.setTransactionTypeScope("PAYMENT");
        candidate.setCurrencyScope(currency);
        candidate.setSupportedCurrencies(List.of(currency));
        candidate.setCapabilityPaymentMethod(paymentMethod);
        candidate.setCapabilityTransactionType("PAYMENT");
        candidate.setCardBrandScope("VISA,MASTERCARD");
        candidate.setCapabilitySupportedCardBrands(List.of("VISA", "MASTERCARD"));
        return candidate;
    }
}
