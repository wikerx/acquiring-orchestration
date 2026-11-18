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
