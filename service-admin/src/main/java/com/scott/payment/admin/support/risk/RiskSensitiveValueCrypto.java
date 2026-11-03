package com.scott.payment.admin.support.risk;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 风控管理端敏感名单值加解密组件，仅用于编辑授权回显完整值，不参与实时交易风控匹配。
 */
@Component
public class RiskSensitiveValueCrypto {

    /**
     * 风控敏感值密钥兼容告警日志，不记录密钥或密文正文。
     */
    private static final Logger log = LoggerFactory.getLogger(RiskSensitiveValueCrypto.class);

    /**
     * 密码学安全随机数生成器，用于生成一次性 AES 密钥和 GCM IV。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * AES-GCM 推荐的初始化向量长度，单位字节。
     */
    private static final int IV_LENGTH = 12;

    /**
     * AES-GCM 认证标签长度，单位 bit。
     */
    private static final int TAG_LENGTH_BITS = 128;

    /**
     * 当前写入使用的 AES 密钥。
     */
    private final SecretKeySpec encryptionKey;

    /**
     * 当前密钥和轮换窗口内历史密钥组成的有界解密密钥集合。
     */
    private final List<SecretKeySpec> decryptionKeys;

    /**
     * 从进程外配置初始化风控敏感值密钥；缺失或弱密钥时阻止服务启动。
     *
     * @param environment 当前 Spring 环境
     */
    public RiskSensitiveValueCrypto(Environment environment) {
        String currentSecret = RiskSensitiveValueKeyPolicy.resolve(
                environment.getProperty("payment.risk.secret"),
                environment.getProperty("payment.risk.allow-ephemeral-test-secret", Boolean.class, false),
                environment.getActiveProfiles());
        this.encryptionKey = deriveKey(currentSecret);
        List<SecretKeySpec> keys = new ArrayList<>();
        keys.add(encryptionKey);
        RiskSensitiveValueKeyPolicy.resolvePreviousSecrets(
                        environment.getProperty("payment.risk.previous-secrets", String[].class, new String[0]),
                        currentSecret)
                .stream()
                .map(this::deriveKey)
                .forEach(keys::add);
        RiskSensitiveValueKeyPolicy.resolveLegacyDevDecryptionSecret(
                        environment.getProperty(
                                "payment.risk.allow-legacy-dev-key-decryption", Boolean.class, false),
                        environment.getActiveProfiles())
                .map(this::deriveKey)
                .ifPresent(legacyKey -> {
                    keys.add(legacyKey);
                    log.warn("已启用历史开发密钥只读解密兼容, event: risk_legacy_dev_key_decryption_enabled");
                });
        this.decryptionKeys = List.copyOf(keys);
    }

    /**
     * 加密敏感匹配值。
     *
     * @param plainText 完整敏感值，允许为空
     * @return AES-GCM 密文，空值返回空
     */
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "风控敏感名单值加密失败");
        }
    }

    /**
     * 解密敏感匹配值。
     *
     * @param cipherText AES-GCM 密文，允许为空
     * @return 解密后的完整值，空值返回空
     */
    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return null;
        }
        try {
            String[] parts = cipherText.split("\\.", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid cipher text");
            }
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            return decryptWithConfiguredKeys(iv, encrypted);
        } catch (Exception exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "风控敏感名单值解密失败");
        }
    }

    /**
     * 依次使用当前密钥和有界历史密钥解密，支持不中断服务的密钥轮换。
     *
     * @param iv GCM 初始化向量
     * @param encrypted 包含认证标签的密文
     * @return 解密后的敏感值
     * @throws Exception 所有配置密钥均无法通过认证时抛出
     */
    private String decryptWithConfiguredKeys(byte[] iv, byte[] encrypted) throws Exception {
        Exception lastFailure = null;
        for (SecretKeySpec key : decryptionKeys) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            } catch (Exception exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure == null ? new IllegalStateException("risk sensitive value key is unavailable") : lastFailure;
    }

    /**
     * 从高熵主密钥派生固定长度 AES 密钥。
     *
     * @param secret 已通过强度校验的主密钥
     * @return AES 密钥
     */
    private SecretKeySpec deriveKey(String secret) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(bytes, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("risk sensitive value key can not be derived", exception);
        }
    }
}
