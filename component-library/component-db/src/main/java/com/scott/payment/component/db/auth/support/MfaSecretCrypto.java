package com.scott.payment.component.db.auth.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MfaSecretCrypto
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : MFA 密钥加密支撑类，位于 component-db 认证支撑层；使用 AES-GCM 保存 TOTP 密钥，避免数据库出现 OTP 明文。
 * @status : create
 */
public final class MfaSecretCrypto {

    /**
     * AES-GCM 算法名称。
     */
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    /**
     * AES 算法名称。
     */
    private static final String KEY_ALGORITHM = "AES";

    /**
     * GCM IV 长度。
     */
    private static final int IV_BYTES = 12;

    /**
     * GCM 认证标签长度。
     */
    private static final int TAG_BITS = 128;

    /**
     * 密文版本前缀。
     */
    private static final String VERSION = "v1";

    /**
     * 安全随机数生成器。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 启动门禁注入的 AES 主密钥；未初始化时所有加解密均失败关闭。
     */
    private static volatile Keyring configuredKeyring;

    private MfaSecretCrypto() {
    }

    /**
     * 使用启动阶段已校验的主密钥初始化 MFA 加密组件。
     *
     * @param masterSecret 至少 32 字节的环境专属主密钥
     */
    public static synchronized void configure(String masterSecret) {
        configure(masterSecret, List.of());
    }

    /**
     * 初始化带轮换窗口的 MFA 密钥环。新密文只使用当前密钥，历史密钥仅用于解密存量密文。
     *
     * @param masterSecret 当前环境专属主密钥
     * @param previousSecrets 历史解密密钥，按最近到最旧排序，最多两个
     */
    public static synchronized void configure(String masterSecret, List<String> previousSecrets) {
        MfaSecretKeyPolicy.requireStrongSecret(masterSecret);
        List<String> validatedPreviousSecrets = MfaSecretKeyPolicy.resolvePreviousSecrets(
                previousSecrets == null ? null : previousSecrets.toArray(String[]::new), masterSecret);
        SecretKeySpec encryptionKey = toKey(masterSecret);
        List<SecretKeySpec> decryptionKeys = new ArrayList<>(validatedPreviousSecrets.size() + 1);
        decryptionKeys.add(encryptionKey);
        validatedPreviousSecrets.stream().map(MfaSecretCrypto::toKey).forEach(decryptionKeys::add);
        configuredKeyring = new Keyring(encryptionKey, List.copyOf(decryptionKeys));
    }

    /**
     * 加密 TOTP Base32 密钥。
     *
     * @param plaintext Base32 密钥明文
     * @return 带版本号的密文
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "mfa secret is required");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keyring().encryptionKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return VERSION + ":" + encoder.encodeToString(iv) + ":" + encoder.encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "mfa secret can not be encrypted");
        }
    }

    /**
     * 解密 TOTP Base32 密钥。
     *
     * @param cipherText 带版本号的密文
     * @return Base32 密钥明文
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "mfa secret cipher is required");
        }
        String[] parts = cipherText.split(":");
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "mfa secret cipher is invalid");
        }
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] iv = decoder.decode(parts[1]);
            byte[] ciphertext = decoder.decode(parts[2]);
            if (iv.length != IV_BYTES || ciphertext.length < TAG_BITS / Byte.SIZE) {
                throw new IllegalArgumentException("invalid mfa cipher structure");
            }
            for (SecretKeySpec decryptionKey : keyring().decryptionKeys()) {
                try {
                    Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
                    cipher.init(Cipher.DECRYPT_MODE, decryptionKey, new GCMParameterSpec(TAG_BITS, iv));
                    return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
                } catch (GeneralSecurityException ignored) {
                    // A v1 ciphertext has no key identifier, so the bounded rotation window is tried in order.
                }
            }
            throw new GeneralSecurityException("no configured mfa key can decrypt the cipher");
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "mfa secret cipher is invalid");
        }
    }

    private static Keyring keyring() {
        Keyring keyring = configuredKeyring;
        if (keyring == null) {
            throw new ServiceException(
                    ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "mfa secret crypto is not initialized");
        }
        return keyring;
    }

    private static SecretKeySpec toKey(String masterSecret) {
        return new SecretKeySpec(deriveKey(masterSecret), KEY_ALGORITHM);
    }

    private static byte[] deriveKey(String masterSecret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(masterSecret.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "mfa master key can not be derived");
        }
    }

    static synchronized void resetForTests() {
        configuredKeyring = null;
    }

    private record Keyring(SecretKeySpec encryptionKey, List<SecretKeySpec> decryptionKeys) {
    }
}
