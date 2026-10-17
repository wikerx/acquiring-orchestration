package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.enums.ChannelCallbackScene;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsCallbackVerifierTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : MPGS Webhook notification secret verification tests.
 * @status : create
 */
class MpgsCallbackVerifierTests {

    private static final String CONFIGURED_SECRET = "0123456789abcdef0123456789abcdef";

    private final MpgsCallbackVerifier verifier = new MpgsCallbackVerifier();

    @Test
    void shouldAcceptMatchingNotificationSecret() {
        assertThatCode(() -> verifier.verify(request(CONFIGURED_SECRET), context(CONFIGURED_SECRET)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMismatchedNotificationSecret() {
        assertThatThrownBy(() -> verifier.verify(request("different-notification-secret"), context(CONFIGURED_SECRET)))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID));
    }

    @Test
    void shouldRejectMissingNotificationSecretHeader() {
        assertThatThrownBy(() -> verifier.verify(request(null), context(CONFIGURED_SECRET)))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.HEADER_MISSING));
    }

    @Test
    void shouldRejectMissingConfiguredNotificationSecret() {
        assertThatThrownBy(() -> verifier.verify(request(CONFIGURED_SECRET), context(null)))
                .isInstanceOfSatisfying(ChannelCallbackVerificationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(ChannelCallbackVerificationException.Reason.SECRET_MISSING));
    }

    private RawChannelCallbackRequest request(String headerSecret) {
        Map<String, List<String>> headers = headerSecret == null
                ? Map.of()
                : Map.of(MpgsCallbackVerifier.NOTIFICATION_SECRET_HEADER, List.of(headerSecret));
        return new RawChannelCallbackRequest(
                "MPGS", ChannelCallbackScene.THREE_DS, "POST", "/channel/v1/callbacks/MPGS/3ds",
                null, headers, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8), "127.0.0.1");
    }

    private ChannelCallbackVerificationContext context(String configuredSecret) {
        return new ChannelCallbackVerificationContext(
                configuredSecret, Map.of(), 300_000L, System.currentTimeMillis());
    }
}
