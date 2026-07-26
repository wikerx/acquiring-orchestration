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
import java.util.Base64;

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
     * 本地开发兜底主密钥，生产环境必须通过 PAYMENT_MFA_SECRET 或 payment.mfa.secret 显式配置。
     */
    private static final String LOCAL_FALLBACK_SECRET = "local-mfa-secret-change-me";

    /**
     * 安全随机数生成器。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private MfaSecretCrypto() {
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
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey(), KEY_ALGORITHM), new GCMParameterSpec(TAG_BITS, iv));
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
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey(), KEY_ALGORITHM), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "mfa secret cipher is invalid");
        }
    }

    /**
     * 完成 master Key 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 MfaSecretCrypto 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private static byte[] masterKey() {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(resolveMasterSecret().getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "mfa master key can not be derived");
        }
    }

    /**
     * 解析 resolve Master Secret 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 MfaSecretCrypto 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 解析或查询得到的业务值
     */
    private static String resolveMasterSecret() {
        String property = System.getProperty("payment.mfa.secret");
        if (property != null && !property.isBlank()) {
            return property;
        }
        String env = System.getenv("PAYMENT_MFA_SECRET");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return LOCAL_FALLBACK_SECRET;
    }
}
