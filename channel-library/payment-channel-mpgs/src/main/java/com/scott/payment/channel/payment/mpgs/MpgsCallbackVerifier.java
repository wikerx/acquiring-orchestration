package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackVerifier;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/** MPGS Webhook 验证器，按官方 X-Notification-Secret 契约校验通知密钥。 */
public class MpgsCallbackVerifier implements PaymentChannelCallbackVerifier {

    public static final String NOTIFICATION_SECRET_HEADER = "X-Notification-Secret";
    public static final String NOTIFICATION_ID_HEADER = "X-Notification-ID";
    public static final String NOTIFICATION_ATTEMPT_HEADER = "X-Notification-Attempt";

    @Override
    public Set<String> channelCodes() {
        return Set.of(MpgsChannelCode.MPGS);
    }

    @Override
    public void verify(ChannelCallbackVerificationRequest request) {
        String actualSecret = request.header(NOTIFICATION_SECRET_HEADER);
        if (!hasText(actualSecret)) {
            throw failure(ChannelCallbackVerificationException.Reason.HEADER_MISSING,
                    "MPGS notification secret header is required");
        }
        if (!hasText(request.defaultSecret())) {
            throw failure(ChannelCallbackVerificationException.Reason.SECRET_MISSING,
                    "MPGS notification secret is not configured");
        }
        if (!MessageDigest.isEqual(
                request.defaultSecret().getBytes(StandardCharsets.UTF_8),
                actualSecret.getBytes(StandardCharsets.UTF_8))) {
            throw failure(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID,
                    "MPGS notification secret is invalid");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ChannelCallbackVerificationException failure(ChannelCallbackVerificationException.Reason reason,
                                                         String message) {
        return new ChannelCallbackVerificationException(reason, message);
    }
}
