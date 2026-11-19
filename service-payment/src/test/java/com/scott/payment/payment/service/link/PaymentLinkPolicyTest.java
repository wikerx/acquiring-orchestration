package com.scott.payment.payment.service.link;

import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.*;
import com.scott.payment.payment.config.PaymentLinkProperties;
import com.scott.payment.payment.service.impl.PaymentCheckoutCardCapabilityService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentLinkPolicyTest {
    @Test void newLinksUseProductAmountBoundsAndNeverRoundZeroMinorUnitCurrencies() {
        var iso = mock(IsoDictionaryService.class);
        when(iso.getCurrency("USD")).thenReturn(Optional.of(new IsoCurrencyInfo("USD", "840", "Dollar", "美元", 2, 100, new BigDecimal("0.01"), "$")));
        when(iso.getCurrency("JPY")).thenReturn(Optional.of(new IsoCurrencyInfo("JPY", "392", "Yen", "日元", 0, 1, BigDecimal.ONE, "¥")));
        when(iso.isCurrencyFractionValid(any(), anyString())).thenReturn(true);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var policy = new PaymentLinkPolicy(new PaymentLinkProperties(), mock(MerchantRuntimeProfileCacheService.class),
                    iso, mock(PaymentCheckoutCardCapabilityService.class), factory.getValidator(), new MockEnvironment());
            Create input = new Create(); input.setRequestId("amount-boundaries"); input.setCurrency("USD");
            for (String value : java.util.List.of("1", "1.00", "999999.99")) {
                input.setAmount(value); assertThat(policy.validate(input)).isEqualByComparingTo(value);
            }
            for (String value : java.util.List.of("0.99", "1000000", "999999.999", "1.000", "1e2", "-1")) {
                input.setAmount(value); assertThatThrownBy(() -> policy.validate(input)).as(value).isInstanceOf(ServiceException.class);
            }
            input.setCurrency("JPY"); input.setAmount("999999");
            assertThat(policy.validate(input)).isEqualByComparingTo("999999");
            for (String value : java.util.List.of("1.00", "100.01")) {
                input.setAmount(value); assertThatThrownBy(() -> policy.validate(input)).as(value).isInstanceOf(ServiceException.class);
            }
        }
    }

    @Test void createOptionsUseSystemCurrenciesAndMerchantSettlementDefault() {
        var iso = mock(IsoDictionaryService.class);
        var merchants = mock(MerchantRuntimeProfileCacheService.class);
        var capability = mock(PaymentCheckoutCardCapabilityService.class);
        var merchant = new com.scott.payment.component.db.auth.model.MerchantRuntimeProfile();
        merchant.setMerchantStatus(1); merchant.setActivationStatus("ACTIVE"); merchant.setSettlementCurrency("USD");
        when(merchants.findRuntimeProfile("m1")).thenReturn(merchant);
        when(iso.listCurrencies()).thenReturn(java.util.List.of(
                new IsoCurrencyInfo("JPY", "392", "Yen", "日元", 0, 1, BigDecimal.ONE, "¥"),
                new IsoCurrencyInfo("USD", "840", "Dollar", "美元", 2, 100, new BigDecimal("0.01"), "$")));
        when(capability.resolveAllowedMethods(eq("m1"), isNull(), anyString())).thenReturn(java.util.List.of());
        var config = new PaymentLinkProperties(); config.setEnabled(true); config.setStorageReady(true);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var policy = new PaymentLinkPolicy(config, merchants, iso, capability, factory.getValidator(), new MockEnvironment());
            Actor actor = new Actor(); actor.setAppCode("MERCHANT"); actor.setActorId("a1"); actor.setMerchantIds(Set.of("m1"));
            Options options = policy.options(actor, "m1");
            assertThat(options.getCurrencies()).containsExactly("JPY", "USD");
            assertThat(options.getDefaultCurrency()).isEqualTo("USD");
            assertThat(options.getCurrencyExponents()).containsEntry("JPY", 0).containsEntry("USD", 2);
            assertThat(options.getCardBrandsByCurrency()).isEmpty();
            merchant.setSettlementCurrency(null);
            assertThat(policy.options(actor, "m1").getDefaultCurrency()).isNull();
        }
    }

    @Test void optionsExposeWalletsForDisplayWithoutAuthorizingThemForLinkPayments() {
        var iso = mock(IsoDictionaryService.class);
        var merchants = mock(MerchantRuntimeProfileCacheService.class);
        var capability = mock(PaymentCheckoutCardCapabilityService.class);
        var merchant = new com.scott.payment.component.db.auth.model.MerchantRuntimeProfile();
        merchant.setMerchantStatus(1); merchant.setActivationStatus("ACTIVE");
        when(merchants.findRuntimeProfile("m1")).thenReturn(merchant);
        when(iso.listCurrencies()).thenReturn(java.util.List.of(
                new IsoCurrencyInfo("USD", "840", "Dollar", "美元", 2, 100, new BigDecimal("0.01"), "$")));
        var card = new com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO();
        card.setPaymentMethod("BANK_CARD"); card.setBrands(java.util.List.of("MASTERCARD", "VISA"));
        var wallet = new com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO();
        wallet.setPaymentMethod("PAYPAL");
        when(capability.resolveAllowedMethods("m1", null, "USD")).thenReturn(java.util.List.of(card, wallet));
        when(capability.resolveAllowedMethods(eq("m1"), anyList(), eq("USD"))).thenAnswer(invocation -> {
            java.util.List<com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> requested = invocation.getArgument(1);
            assertThat(requested).singleElement().satisfies(method -> assertThat(method.getPaymentMethod()).isEqualTo("BANK_CARD"));
            return java.util.List.of(card);
        });
        var config = new PaymentLinkProperties(); config.setEnabled(true); config.setStorageReady(true);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var policy = new PaymentLinkPolicy(config, merchants, iso, capability, factory.getValidator(), new MockEnvironment());
            Actor actor = new Actor(); actor.setAppCode("MERCHANT"); actor.setActorId("a1"); actor.setMerchantIds(Set.of("m1"));
            Options options = policy.options(actor, "m1");
            assertThat(options.getPaymentMethodsByCurrency()).containsEntry("USD", java.util.List.of("BANK_CARD", "PAYPAL"));
            assertThat(options.getCardBrandsByCurrency()).containsEntry("USD", java.util.List.of("MASTERCARD", "VISA"));
            assertThat(policy.methods("m1", "USD", java.util.List.of("VISA")))
                    .singleElement().satisfies(method -> assertThat(method.getPaymentMethod()).isEqualTo("BANK_CARD"));
        }
    }

    @Test void amountUsesCurrencyExponentWithoutRounding() {
        var iso = mock(IsoDictionaryService.class);
        when(iso.getCurrency("JPY")).thenReturn(Optional.of(new IsoCurrencyInfo("JPY", "392", "Yen", "日元", 0, 1, BigDecimal.ONE, "¥")));
        when(iso.isCurrencyFractionValid(new BigDecimal("100"), "JPY")).thenReturn(true);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var policy = new PaymentLinkPolicy(new PaymentLinkProperties(), mock(MerchantRuntimeProfileCacheService.class),
                    iso, mock(PaymentCheckoutCardCapabilityService.class), factory.getValidator(), new MockEnvironment());
            Create c = new Create(); c.setRequestId("request"); c.setLinkName("订单"); c.setAmount("100"); c.setCurrency("JPY");
            assertThat(policy.validate(c)).isEqualByComparingTo("100");
            c.setAmount("100.01"); assertThatThrownBy(() -> policy.validate(c)).isInstanceOf(ServiceException.class);
            c.setAmount("0"); assertThatThrownBy(() -> policy.validate(c)).isInstanceOf(ServiceException.class);
            c.setAmount("100"); c.setLinkName(null); c.setExpireMinutes(43200);
            assertThat(policy.validate(c)).isEqualByComparingTo("100");
            c.setExpireTime(java.time.LocalDateTime.now().plusDays(1));
            assertThatThrownBy(() -> policy.validate(c)).isInstanceOf(ServiceException.class);
            c.setExpireTime(null); c.setExpireMinutes(43201);
            assertThatThrownBy(() -> policy.validate(c)).isInstanceOf(ServiceException.class);
        }
    }
    @Test void merchantCannotPromoteItselfToAllMerchantScope() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var policy = new PaymentLinkPolicy(new PaymentLinkProperties(), mock(MerchantRuntimeProfileCacheService.class),
                    mock(IsoDictionaryService.class), mock(PaymentCheckoutCardCapabilityService.class), factory.getValidator(), new MockEnvironment());
            Actor actor = new Actor(); actor.setAppCode("MERCHANT"); actor.setActorId("actor");
            actor.setAllMerchants(true); actor.setMerchantIds(Set.of("one"));
            assertThatThrownBy(() -> policy.requireScope(actor, "two")).isInstanceOf(ServiceException.class);
            assertThatCode(() -> policy.requireScope(actor, "one")).doesNotThrowAnyException();
        }
    }
    @Test void existingLinkKeepsRemainingAvailableBrandWhileCreateRejectsAnUnsupportedSelection() {
        var capability = mock(PaymentCheckoutCardCapabilityService.class);
        var visa = new com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO();
        visa.setPaymentMethod("BANK_CARD"); visa.setBrands(java.util.List.of("VISA"));
        when(capability.resolveAllowedMethods(eq("m1"),anyList(),eq("USD"))).thenReturn(java.util.List.of(visa));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var policy = new PaymentLinkPolicy(new PaymentLinkProperties(),mock(MerchantRuntimeProfileCacheService.class),
                    mock(IsoDictionaryService.class),capability,factory.getValidator(),new MockEnvironment());
            assertThatThrownBy(() -> policy.methods("m1","USD",java.util.List.of("VISA","MASTERCARD"))).isInstanceOf(ServiceException.class);
            assertThat(policy.currentMethods("m1","USD",java.util.List.of("VISA","MASTERCARD")))
                    .singleElement().satisfies(method -> assertThat(method.getBrands()).containsExactly("VISA"));
        }
    }
}
