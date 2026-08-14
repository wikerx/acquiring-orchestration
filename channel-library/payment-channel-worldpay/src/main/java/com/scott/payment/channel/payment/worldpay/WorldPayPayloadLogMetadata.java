package com.scott.payment.channel.payment.worldpay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Worldpay channel payload metadata used by application logs.
 *
 * @param length UTF-8 payload length in bytes
 * @param digest irreversible SHA-256 digest
 */
record WorldPayPayloadLogMetadata(int length, String digest) {

    static WorldPayPayloadLogMetadata from(String payload) {
        byte[] bytes = (payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return new WorldPayPayloadLogMetadata(bytes.length, "sha256:" + HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException exception) {
            return new WorldPayPayloadLogMetadata(bytes.length, "sha256_unavailable");
        }
    }
}
