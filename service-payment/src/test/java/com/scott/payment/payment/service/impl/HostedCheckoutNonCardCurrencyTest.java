package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.reference.cache.CardBinCacheReader;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.component.db.route.model.MerchantRouteProfile.RouteOption;
import com.scott.payment.link.service.PaymentLinkMethodScope;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO;
import com.scott.payment.payment.service.MerchantRouteProfileCacheService;
import com.scott.payment.payment.service.PaymentExchangeRateService;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HostedCheckoutNonCardCurrencyTest {
    private static final String MERCHANT = "200045";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 15, 35);
    private final PaymentLinkMethodScope scope = mock(PaymentLinkMethodScope.class);
    private final PaymentExchangeRateService rates = mock(PaymentExchangeRateService.class);
    private final DefaultPaymentChannelRouteService routes = new DefaultPaymentChannelRouteService(null, null, null, null);
    private final PaymentCheckoutCardCapabilityService checkout = new PaymentCheckoutCardCapabilityService(
            mock(MerchantRouteProfileCacheService.class), mock(CardBinCacheReader.class), scope, routes, rates);

    @Test
    void cadShowsConfiguredWalletsThroughTheSameUsdEdcPathWhenRateIsValid() {
        configure(null, List.of("APPLE_PAY", "PAYPAL"),
                route(1, "APPLE_PAY", "USD"), route(2, "PAYPAL", "USD"));
        var rate = new PaymentExchangeRateDTO();
        rate.setRateId(1L);
        rate.setFinalRate(new BigDecimal("0.75"));
        when(rates.findTransactionRate("CAD", "USD", NOW)).thenReturn(Optional.of(rate));

        assertThat(checkout.resolveHostedNonCardMethods(MERCHANT, "CAD", "USA", null))
                .extracting(AllowedPaymentMethodDTO::getPaymentMethod).containsExactly("APPLE_PAY", "PAYPAL");
        verify(rates, times(1)).findTransactionRate("CAD", "USD", NOW);
    }

    @Test
    void hkdShowsConfiguredGooglePayThroughEdcWithoutASeparateHkdRoute() {
        configure(null, List.of("GOOGLE_PAY"), route(1, "GOOGLE_PAY", "USD"));
        var rate = new PaymentExchangeRateDTO();
        rate.setRateId(2L);
        rate.setFinalRate(new BigDecimal("0.128"));
        when(rates.findTransactionRate("HKD", "USD", NOW)).thenReturn(Optional.of(rate));

        assertThat(checkout.resolveHostedNonCardMethods(MERCHANT, "HKD", "USA", null))
                .extracting(AllowedPaymentMethodDTO::getPaymentMethod).containsExactly("GOOGLE_PAY");
        verify(rates).findTransactionRate("HKD", "USD", NOW);
    }

    @Test
    void missingEdcRateOrAmbiguousRouteHidesOptionAndStillAllowsHkdDirect() {
        configure(null, List.of("APPLE_PAY", "PAYPAL"),
                route(1, "APPLE_PAY", "USD"), route(2, "PAYPAL", "HKD"), route(3, "PAYPAL", "HKD"));
        when(rates.findTransactionRate("CAD", "USD", NOW)).thenReturn(Optional.empty());

        assertThat(checkout.resolveHostedNonCardMethods(MERCHANT, "CAD", "USA", null)).isEmpty();
        var frozen = Set.of("PAYPAL");
        configure(frozen, List.of("PAYPAL"),
                route(1, "APPLE_PAY", "HKD"), route(2, "PAYPAL", "HKD"), route(3, "PAYPAL", "USD"));
        assertThat(checkout.resolveHostedNonCardMethods(MERCHANT, "HKD", "USA", frozen)).singleElement()
                .satisfies(option -> {
                    assertThat(option.getPaymentMethod()).isEqualTo("PAYPAL");
                    assertThat(option.getChannelCode()).isNull();
                });
        verify(rates, times(1)).findTransactionRate("CAD", "USD", NOW);
    }

    @Test
    void billingCountryOutsideMidScopeCannotUseEvenACurrencyWithValidRates() {
        var option = route(1, "APPLE_PAY", "USD");
        option.setAllowedCountryScope("CAN");
        configure(null, List.of("APPLE_PAY"), option);

        assertThat(checkout.resolveHostedNonCardMethods(MERCHANT, "CAD", "USA", null)).isEmpty();
        verifyNoInteractions(rates);
    }

    private void configure(Set<String> frozen, List<String> methods, RouteOption... candidates) {
        var profile = new MerchantRouteProfile();
        profile.setRouteOptions(List.of(candidates));
        var snapshot = new PaymentLinkMethodScope.Snapshot(null, List.of(), Set.of(), profile, NOW);
        when(scope.snapshot(MERCHANT)).thenReturn(snapshot);
        when(scope.hostedNonCardMethods(snapshot, frozen)).thenReturn(methods);
    }

    private static RouteOption route(long id, String method, String currency) {
        var route = new RouteOption();
        route.setChannelId(id);
        route.setMidConfigId(id);
        route.setCapabilityId(id);
        route.setCapabilitySortOrder(10);
        route.setBindingStatus(1);
        route.setMidStatus(1);
        route.setChannelStatus(1);
        route.setSupportAcquiring(1);
        route.setCapabilityStatus(1);
        route.setBusinessType("ACQUIRING");
        route.setCapabilityBusinessType("ACQUIRING");
        route.setCapabilityPaymentMethod(method);
        route.setCapabilityTransactionType("PAYMENT");
        route.setSupportedCurrencies(List.of(currency));
        route.setDefaultTransactionCurrency(currency);
        return route;
    }
}
