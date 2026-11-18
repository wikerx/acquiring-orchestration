package com.scott.payment.payment.service.link;

import com.scott.payment.component.core.exception.ServiceException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PaymentLinkExpiryPolicyTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 18, 10, 20, 30);

    @Test void relativeDefaultsAndBoundariesPreserveExactDeadline() {
        assertThat(PaymentLinkExpiryPolicy.resolve(null, null, now)).isEqualTo(now.plusHours(24));
        assertThat(PaymentLinkExpiryPolicy.resolve(15, null, now)).isEqualTo(now.plusMinutes(15));
        assertThat(PaymentLinkExpiryPolicy.resolve(4320, null, now)).isEqualTo(now.plusHours(72));
        assertThat(PaymentLinkExpiryPolicy.resolve(43200, null, now)).isEqualTo(now.plusDays(30));
        for (int minutes : new int[]{0, 14, 43201}) {
            assertThatThrownBy(() -> PaymentLinkExpiryPolicy.resolve(minutes, null, now)).isInstanceOf(ServiceException.class);
        }
    }
    @Test void customTimeSupportsSecondsAndRejectsPastTooShortTooLongOrConflictingInput() {
        LocalDateTime exact = now.plusDays(3).plusSeconds(17);
        assertThat(PaymentLinkExpiryPolicy.resolve(null, exact, now)).isEqualTo(exact);
        assertThat(PaymentLinkExpiryPolicy.resolve(null, now.plusDays(30), now)).isEqualTo(now.plusDays(30));
        for (LocalDateTime invalid : new LocalDateTime[]{now.minusSeconds(1), now.plusMinutes(15).minusSeconds(1), now.plusDays(30).plusSeconds(1)}) {
            assertThatThrownBy(() -> PaymentLinkExpiryPolicy.resolve(null, invalid, now)).isInstanceOf(ServiceException.class);
        }
        assertThatThrownBy(() -> PaymentLinkExpiryPolicy.resolve(1440, exact, now)).isInstanceOf(ServiceException.class);
    }

    @Test void subMillisecondInputIsCanonicalBeforeDatabaseAndStableShareSigning() {
        LocalDateTime requested = now.plusDays(1).withNano(999_999_999);
        assertThat(PaymentLinkExpiryPolicy.resolve(null, requested, now))
                .isEqualTo(requested.withNano(999_000_000));
    }
}
