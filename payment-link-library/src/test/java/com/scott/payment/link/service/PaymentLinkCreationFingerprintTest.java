package com.scott.payment.link.service;

import com.scott.payment.link.entity.PaymentLinkDO;
import com.scott.payment.link.model.PaymentLinkManagementModels.Create;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PaymentLinkCreationFingerprintTest {
    final BigDecimal amount = new BigDecimal("10.00");
    final LocalDateTime created = LocalDateTime.of(2026, 11, 19, 12, 0, 0, 123_000_000);
    Create input(Integer minutes) {
        var input = new Create(); input.setRequestId("request"); input.setMerchantOrderNo("ORDER");
        input.setCurrency("USD"); input.setAmount("10.00"); input.setExpireMinutes(minutes); return input;
    }
    PaymentLinkDO row(String hash, int minutes) {
        var row = new PaymentLinkDO(); row.setRequestFingerprint(hash); row.setCreateTime(created);
        row.setExpireTime(created.plusMinutes(minutes)); return row;
    }
    /** 历史实现的固定输入形状，独立于新版本指纹函数。 */
    String legacy(Object expiry) {
        var fields = new TreeMap<String, Object>(); fields.put("order", "ORDER"); fields.put("name", "");
        fields.put("description", null); fields.put("amount", "10"); fields.put("currency", "USD");
        fields.put("expiry", expiry); fields.put("locale", "en-US"); fields.put("brands", List.of());
        fields.put("notify", null); fields.put("redirect", null);
        return PaymentLinkTokenService.hash(JsonUtils.toJsonString(fields));
    }
    @Test void defaultAndExplicitHourHaveSameFingerprintAndDeadline() {
        assertThat(PaymentLinkCreationFingerprint.canonical(input(null), amount))
                .isEqualTo(PaymentLinkCreationFingerprint.canonical(input(60), amount))
                .isNotEqualTo(PaymentLinkCreationFingerprint.canonical(input(1440), amount));
        assertThat(PaymentLinkExpiryPolicy.resolve(null, null, created)).isEqualTo(created.plusHours(1));
    }
    @Test void newFingerprintDoesNotCollideWithLegacyDefaultBug() {
        assertThat(PaymentLinkCreationFingerprint.canonical(input(1440), amount)).isNotEqualTo(legacy(1440));
    }
    @Test void explicitMethodScopeCannotReplayOnAStoredCardScopeOrExpandsAStoredWalletScope() {
        var first = input(null);
        first.setPaymentMethodKeys(List.of("METHOD:APPLE_PAY", "CARD_BRAND:VISA"));
        var same = input(60);
        same.setPaymentMethodKeys(List.of("CARD_BRAND:VISA", "METHOD:APPLE_PAY", "CARD_BRAND:VISA"));
        assertThat(PaymentLinkCreationFingerprint.canonical(first, amount))
                .isEqualTo(PaymentLinkCreationFingerprint.canonical(same, amount));
        var stored = row(PaymentLinkCreationFingerprint.canonical(first, amount), 60);
        stored.setAllowedMethodsJson(PaymentLinkMethodScope.serialize(first.getPaymentMethodKeys()));
        PaymentLinkCreationFingerprint.requireMatch(stored, same, amount);
        same.setPaymentMethodKeys(List.of("CARD_BRAND:VISA", "METHOD:GOOGLE_PAY"));
        assertThatThrownBy(() -> PaymentLinkCreationFingerprint.requireMatch(stored, same, amount))
                .isInstanceOf(ServiceException.class);
        var old = row(PaymentLinkCreationFingerprint.canonical(input(null), amount), 60);
        assertThatThrownBy(() -> PaymentLinkCreationFingerprint.requireMatch(old, first, amount))
                .isInstanceOf(ServiceException.class);
    }
    @Test void explicitAndLegacyCardSelectionsMustAgree() {
        var first = input(null);
        first.setPaymentMethodKeys(List.of("CARD_BRAND:VISA", "METHOD:APPLE_PAY"));
        first.setCardBrands(List.of("MASTERCARD"));
        assertThatThrownBy(() -> PaymentLinkCreationFingerprint.canonical(first, amount))
                .isInstanceOf(ServiceException.class);
    }
    @Test void legacyOmittedHourReplaysAsDefaultOrExplicitHourWithoutChangingHistory() {
        var old = row(legacy(1440), 60);
        PaymentLinkCreationFingerprint.requireMatch(old, input(null), amount);
        PaymentLinkCreationFingerprint.requireMatch(old, input(60), amount);
        assertThat(old.getRequestFingerprint()).isEqualTo(legacy(1440));
        assertThat(old.getExpireTime()).isEqualTo(created.plusHours(1));
        assertThatThrownBy(() -> PaymentLinkCreationFingerprint.requireMatch(old, input(1440), amount)).isInstanceOf(ServiceException.class);
    }
    @Test void legacyExplicitDayCannotReplayAsOmittedHour() {
        var old = row(legacy(1440), 1440);
        PaymentLinkCreationFingerprint.requireMatch(old, input(1440), amount);
        assertThatThrownBy(() -> PaymentLinkCreationFingerprint.requireMatch(old, input(null), amount)).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> PaymentLinkCreationFingerprint.requireMatch(old, input(60), amount)).isInstanceOf(ServiceException.class);
    }
    @Test void legacyExplicitHourAlsoAcceptsEquivalentDefault() {
        PaymentLinkCreationFingerprint.requireMatch(row(legacy(60), 60), input(null), amount);
    }
    @Test void unknownLegacyDeadlineCannotBeGuessedEvenIfOldHashMatches() {
        var old = row(legacy(1440), 60); old.setCreateTime(null);
        assertThatThrownBy(() -> PaymentLinkCreationFingerprint.requireMatch(old, input(null), amount)).isInstanceOf(ServiceException.class);
    }
    @Test void canonicalAbsoluteDeadlineUsesSameMillisecondPrecisionAsStorageAndToken() {
        var first = input(null); first.setExpireTime(created.plusDays(1).withNano(123_456_789));
        var retry = input(null); retry.setExpireTime(first.getExpireTime().withNano(123_000_000));
        assertThat(PaymentLinkCreationFingerprint.canonical(first, amount)).isEqualTo(PaymentLinkCreationFingerprint.canonical(retry, amount));
        retry.setExpireTime(retry.getExpireTime().plusNanos(1_000_000));
        assertThat(PaymentLinkCreationFingerprint.canonical(first, amount)).isNotEqualTo(PaymentLinkCreationFingerprint.canonical(retry, amount));
    }
    @Test void legacyAbsoluteDeadlineAcceptsProvableOriginalAndRejectsUnprovableChangedNanoseconds() {
        var input = input(null); input.setExpireTime(created.plusDays(1).withNano(123_456_789));
        var old = row(legacy(input.getExpireTime().toString()), 1440);
        PaymentLinkCreationFingerprint.requireMatch(old, input, amount);
        input.setExpireTime(input.getExpireTime().withNano(123_000_000));
        assertThatThrownBy(() -> PaymentLinkCreationFingerprint.requireMatch(old, input, amount)).isInstanceOf(ServiceException.class);
    }
    @Test void canonicalReplayKeepsFrozenExpiryEvenAfterLinkExpired() {
        var input = input(null); var old = row(PaymentLinkCreationFingerprint.canonical(input, amount), 60);
        old.setExpireTime(created.minusDays(1)); PaymentLinkCreationFingerprint.requireMatch(old, input, amount);
    }
    @Test void changedAmountDescriptionAndBrandsStillConflictForOldAndNewRecords() {
        for (String hash : List.of(legacy(1440), PaymentLinkCreationFingerprint.canonical(input(null), amount))) {
            var old = row(hash, 60);
            var changed = input(null); changed.setDescription("changed");
            assertThatThrownBy(() -> PaymentLinkCreationFingerprint.requireMatch(old, changed, amount)).isInstanceOf(ServiceException.class);
            assertThatThrownBy(() -> PaymentLinkCreationFingerprint.requireMatch(old, input(null), new BigDecimal("11.00"))).isInstanceOf(ServiceException.class);
        }
    }
}
