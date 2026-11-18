package com.scott.payment.payment.service.link;

import com.scott.payment.payment.config.PaymentLinkProperties;
import com.scott.payment.payment.entity.PaymentLinkDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PaymentLinkTokenServiceTest {
    private PaymentLinkProperties properties() {
        var p = new PaymentLinkProperties(); p.setEnabled(true); p.setStorageReady(true);
        p.setFrontendBaseUrl("https://pay.example.invalid"); p.setActiveKeyVersion("v1");
        p.setSigningKeys(Map.of("v1", "unit-test-only-signing-key-32-bytes-minimum",
                "v2", "second-unit-test-key-32-bytes-minimum"));
        return p;
    }
    private PaymentLinkDO link() {
        var l = new PaymentLinkDO(); l.setLinkId("11111111111111111111111111111111");
        l.setMerchantId("TEST_MERCHANT"); l.setExpireTime(LocalDateTime.of(2026, 11, 18, 10, 0));
        return l;
    }
    @Test void repeatsStableAddressWithoutPersistingRawToken() {
        var service = new PaymentLinkTokenService(properties()); var l = link();
        String token = service.token(l, "v1");
        assertThat(service.token(l, "v1")).isEqualTo(token);
        assertThat(service.url(l, "v1")).isEqualTo("https://pay.example.invalid/l/" + token);
        assertThat(service.matches(token, PaymentLinkTokenService.hash(token))).isTrue();
        assertThat(PaymentLinkTokenService.hash(token)).doesNotContain(token);
    }
    @Test void rejectsTamperingAndDoesNotInvalidateOldKeyOnRotation() {
        var service = new PaymentLinkTokenService(properties()); var l = link();
        String old = service.token(l, "v1");
        assertThat(service.matches(old + "0", PaymentLinkTokenService.hash(old))).isFalse();
        assertThat(service.token(l, "v2")).isNotEqualTo(old);
        assertThat(service.token(l, "v1")).isEqualTo(old);
        l.setMerchantId("ANOTHER_MERCHANT"); assertThat(service.token(l, "v1")).isNotEqualTo(old);
    }
    @Test void validatesProvisioningOnlyWhenFeatureEnabled() {
        var disabled = new PaymentLinkProperties(); assertThatCode(disabled::validate).doesNotThrowAnyException();
        disabled.setEnabled(true); assertThatThrownBy(disabled::validate).isInstanceOf(IllegalStateException.class);
        var valid = properties(); assertThatCode(valid::validate).doesNotThrowAnyException();
        valid.setFrontendBaseUrl("http://public.example.invalid");
        assertThatThrownBy(valid::validate).isInstanceOf(IllegalStateException.class);
        valid.setFrontendBaseUrl("http://localhost:5190"); assertThatCode(valid::validate).doesNotThrowAnyException();
    }
    @Test void signatureUsesFrozenTimeButNotMutableStatus() {
        var service = new PaymentLinkTokenService(properties()); var l = link(); String first = service.token(l, "v1");
        l.setStatus("DISABLED"); l.setVersion(5); assertThat(service.token(l, "v1")).isEqualTo(first);
        l.setExpireTime(l.getExpireTime().plusSeconds(1)); assertThat(service.token(l, "v1")).isNotEqualTo(first);
    }
}
