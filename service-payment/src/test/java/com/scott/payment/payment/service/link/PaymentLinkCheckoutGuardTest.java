package com.scott.payment.payment.service.link;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.config.PaymentLinkProperties;
import com.scott.payment.payment.entity.PaymentLinkCheckoutBindingDO;
import com.scott.payment.link.entity.PaymentLinkDO;
import com.scott.payment.payment.mapper.PaymentLinkCheckoutBindingMapper;
import com.scott.payment.payment.mapper.PaymentLinkMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentLinkCheckoutGuardTest {
    private final PaymentLinkProperties p = new PaymentLinkProperties();
    private final PaymentLinkCheckoutBindingMapper bindings = mock(PaymentLinkCheckoutBindingMapper.class);
    private final PaymentLinkMapper links = mock(PaymentLinkMapper.class);
    private final PaymentLinkCheckoutGuard guard = new PaymentLinkCheckoutGuard(p, bindings, links);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 10, 0);

    private PaymentLinkDO linked() {
        p.setEnabled(true); p.setStorageReady(true);
        var b = new PaymentLinkCheckoutBindingDO(); b.setLinkId("link");
        when(bindings.selectOne(any())).thenReturn(b);
        var l = new PaymentLinkDO(); l.setLinkId("link"); l.setStatus("ACTIVE"); l.setExpireTime(now.plusHours(1));
        when(links.lock("link")).thenReturn(l); when(links.selectOne(any())).thenReturn(l); return l;
    }
    @Test void preMigrationDoesNotQueryNewTables() {
        guard.requireNewSubmissionAllowed("ordinary", now); verifyNoInteractions(bindings, links);
    }
    @Test void ordinaryCheckoutRemainsUsableAfterProvisioning() {
        p.setStorageReady(true); p.setEnabled(true);
        guard.requireNewSubmissionAllowed("ordinary", now); verifyNoInteractions(links);
    }
    @Test void rejectsDisabledAndExpiredLinks() {
        var l = linked(); l.setStatus("DISABLED");
        assertThatThrownBy(() -> guard.requireNewSubmissionAllowed("session", now)).isInstanceOf(ServiceException.class);
        l.setStatus("ACTIVE"); l.setExpireTime(now);
        assertThatThrownBy(() -> guard.requireNewSubmissionAllowed("session", now)).isInstanceOf(ServiceException.class);
    }
    @Test void featureContainmentAlsoProtectsAlreadyIssuedSessions() {
        linked(); p.setEnabled(false);
        assertThatThrownBy(() -> guard.requireNewSubmissionAllowed("session", now)).isInstanceOf(ServiceException.class);
        verify(links).lock("link");
    }
    @Test void activeLinkIsCheckedUnderSameLockAsDisable() {
        linked(); assertThatCode(() -> guard.requireNewSubmissionAllowed("session", now)).doesNotThrowAnyException();
        verify(links).lock("link");
    }
    @Test void readEligibilityUsesCurrentFactsWithoutLockingAndDoesNotTouchTablesBeforeMigration() {
        assertThat(guard.allowsNewSubmissionRead("ordinary", now)).isTrue(); verifyNoInteractions(bindings, links);
        var row = linked();
        assertThat(guard.allowsNewSubmissionRead("session", now)).isTrue();
        row.setStatus("DISABLED"); assertThat(guard.allowsNewSubmissionRead("session", now)).isFalse();
        verify(links, never()).lock(any());
    }


    @Test void longDeadlineRequiresFrozenOwnerFactsAndCannotBeExtendedBySourceFlag() {
        var link = linked(); link.setExpireTime(now.plusDays(30)); link.setCurrency("USD");
        link.setCurrencyExponent(2); link.setAmount(new java.math.BigDecimal("12.34")); link.setRequestFingerprint("frozen");
        when(links.lockMerchantOrder("merchant", "order")).thenReturn(link);
        var command = new com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO();
        command.setMerchantId("merchant"); command.setMerchantOrderNo("order"); command.setPaymentAction("PAYMENT");
        command.setMerchantRequestId("PAYMENT_LINK:link"); command.setExpireTime(link.getExpireTime());
        command.setCurrency("USD"); command.setCurrencyExponent(2); command.setAmount(new java.math.BigDecimal("12.34"));
        command.setRequestFingerprint("frozen");
        assertThatCode(() -> guard.requireOwnedDeadline(command, now)).doesNotThrowAnyException();
        command.setExpireTime(now.plusDays(31));
        assertThatThrownBy(() -> guard.requireOwnedDeadline(command, now)).isInstanceOf(ServiceException.class);
        command.setExpireTime(link.getExpireTime()); command.setAmount(new java.math.BigDecimal("0.01"));
        assertThatThrownBy(() -> guard.requireOwnedDeadline(command, now)).isInstanceOf(ServiceException.class);
        command.setAmount(link.getAmount()); p.setEnabled(false);
        assertThatThrownBy(() -> guard.requireOwnedDeadline(command, now)).isInstanceOf(ServiceException.class);
    }
}
