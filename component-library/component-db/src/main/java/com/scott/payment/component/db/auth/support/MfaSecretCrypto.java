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
     * 整理master密钥，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 解析resolvemastersecret，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 公共组件库 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @return 构造、转换或解析后的业务值
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
