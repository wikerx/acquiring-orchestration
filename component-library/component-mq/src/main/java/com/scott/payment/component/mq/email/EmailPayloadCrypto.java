package com.scott.payment.component.mq.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailPayloadCrypto
 * @date : 2026-08-02 23:40
 * @email : scott_x@163.com
 * @description : 异步邮件投递正文加密组件，使用运行时密钥和随机 IV 保护 OTP、临时密码等敏感模板变量
 * @status : create
 */
@Component
public class EmailPayloadCrypto {

    /** AES-GCM 认证标签长度。 */
    private static final int TAG_BITS = 128;
    /** 每条密文独立生成的 GCM IV 长度。 */
    private static final int IV_LENGTH = 12;
    /** 密文分隔符。 */
    private static final String CIPHER_SEPARATOR = ".";
    /** 安全随机数生成器。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 由运行时配置派生的 AES 密钥；缺少配置时为空并在使用时拒绝。 */
    private final byte[] secretKey;

    /**
     * 创建邮件正文加密器。
     *
     * @param configuredSecret 配置中心或环境变量提供的邮件加密密钥，禁止在源码中设置真实默认值
     */
    public EmailPayloadCrypto(@Value("${payment.email.secret:}") String configuredSecret) {
        String secret = StringUtils.hasText(configuredSecret)
                ? configuredSecret
                : System.getenv("PAYMENT_EMAIL_SECRET");
        this.secretKey = deriveKey(secret);
    }

    /**
     * 加密渲染后的真实邮件正文。
     *
     * @param plainText 含敏感模板变量的真实投递正文
     * @return 随机 IV 与密文组成的持久化值
     */
    public String encrypt(String plainText) {
        requireKey();
        if (plainText == null) {
            throw new IllegalArgumentException("email payload can not be null");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv)
                    + CIPHER_SEPARATOR
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("email payload encrypt failed", exception);
        }
    }

    /**
     * 解密消费者即将发送的真实邮件正文。
     *
     * @param cipherText 数据库保存的随机 IV 与密文
     * @return 渲染后的真实邮件正文
     */
    public String decrypt(String cipherText) {
        requireKey();
        if (!StringUtils.hasText(cipherText)) {
            throw new IllegalArgumentException("email payload cipher can not be blank");
        }
        try {
            String[] parts = cipherText.split("\\.", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("email payload cipher format is invalid");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("email payload decrypt failed", exception);
        }
    }

    /** 校验运行时已提供邮件加密密钥。 */
    private void requireKey() {
        if (secretKey == null) {
            throw new IllegalStateException("email encryption secret is not configured");
        }
    }

    /** 通过 SHA-256 将运行时密钥派生为固定长度 AES 密钥。 */
    private byte[] deriveKey(String secret) {
        if (!StringUtils.hasText(secret)) {
            return null;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("email encryption key initialize failed", exception);
        }
    }
}
