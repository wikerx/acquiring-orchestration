package com.scott.payment.channel.payment.worldpay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayPayloadLogMetadata
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Worldpay channel payload metadata used by application logs.
 * @status : create
 *
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
