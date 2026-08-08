package com.scott.payment.component.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-GCM 字段级加密工具，密文同时具备机密性和完整性保护。 */
public final class SensitiveFieldCipher {

    private static final String VERSION = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SensitiveFieldCipher() {
    }

    public static String encrypt(String plaintext, String secret, String aad) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(secret), new GCMParameterSpec(TAG_BITS, iv));
            applyAad(cipher, aad);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return VERSION + "." + encoder.encodeToString(iv) + "." + encoder.encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("sensitive field encryption failed", exception);
        }
    }

    public static String decrypt(String envelope, String secret, String aad) {
        if (envelope == null || envelope.isBlank()) {
            return null;
        }
        String[] parts = envelope.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("sensitive field envelope is invalid");
        }
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] iv = decoder.decode(parts[1]);
            if (iv.length != IV_BYTES) {
                throw new IllegalArgumentException("sensitive field IV is invalid");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(secret), new GCMParameterSpec(TAG_BITS, iv));
            applyAad(cipher, aad);
            return new String(cipher.doFinal(decoder.decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("sensitive field decryption failed", exception);
        }
    }

    private static SecretKeySpec key(String secret) throws GeneralSecurityException {
        if (secret == null || secret.length() < 24) {
            throw new IllegalArgumentException("sensitive field encryption secret must contain at least 24 characters");
        }
        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    private static void applyAad(Cipher cipher, String aad) {
        if (aad != null && !aad.isBlank()) {
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        }
    }
}
