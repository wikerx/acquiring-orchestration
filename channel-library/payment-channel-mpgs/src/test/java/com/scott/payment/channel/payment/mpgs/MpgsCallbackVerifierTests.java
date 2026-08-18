package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** MPGS Webhook notification secret verification tests. */
class MpgsCallbackVerifierTests {

    private static final String CONFIGURED_SECRET = "0123456789abcdef0123456789abcdef";

    private final MpgsCallbackVerifier verifier = new MpgsCallbackVerifier();

    @Test
    void shouldAcceptMatchingNotificationSecret() {
        assertThatCode(() -> verifier.verify(request(CONFIGURED_SECRET, CONFIGURED_SECRET)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMismatchedNotificationSecret() {
        assertThatThrownBy(() -> verifier.verify(request("different-notification-secret", CONFIGURED_SECRET)))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID));
    }

    @Test
    void shouldRejectMissingNotificationSecretHeader() {
        assertThatThrownBy(() -> verifier.verify(request(null, CONFIGURED_SECRET)))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.HEADER_MISSING));
    }

    @Test
    void shouldRejectMissingConfiguredNotificationSecret() {
        assertThatThrownBy(() -> verifier.verify(request(CONFIGURED_SECRET, null)))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.SECRET_MISSING));
    }

    private ChannelCallbackVerificationRequest request(String headerSecret, String configuredSecret) {
        Map<String, String> headers = headerSecret == null
                ? Map.of()
                : Map.of(MpgsCallbackVerifier.NOTIFICATION_SECRET_HEADER, headerSecret);
        return new ChannelCallbackVerificationRequest(
                "MPGS", "POST", "/channel/v1/callbacks/MPGS/3ds", headers, "{}",
                configuredSecret, Map.of(), 300_000L, System.currentTimeMillis());
    }
}
