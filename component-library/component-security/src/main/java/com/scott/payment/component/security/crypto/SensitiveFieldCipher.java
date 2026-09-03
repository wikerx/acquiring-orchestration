package com.scott.payment.component.security.crypto;

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
 * @classname : SensitiveFieldCipher
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : AES-GCM 字段级加密工具，密文同时具备机密性和完整性保护。
 * @status : create
 */
public final class SensitiveFieldCipher {

    /**
     * 版本，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String VERSION = "v1";
    /**
     * AES-GCM 随机 IV 字节数，每次加密必须重新生成。
     * <p>
     * 单位：字节；格式：正整数；不允许为空；非敏感字段。
     * 取值范围：取值由算法协议或输入长度保护边界限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int IV_BYTES = 12;
    /**
     * AES-GCM 认证标签位数，用于同时校验密文完整性。
     * <p>
     * 单位：位；格式：正整数；不允许为空；非敏感字段。
     * 取值范围：取值由算法协议或数值精度边界限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int TAG_BITS = 128;
    /**
     * 密码学安全随机数生成器，用于生成一次性 AES 密钥和 GCM IV。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SensitiveFieldCipher() {
    }

    /**
     * 使用版本化密文格式加密敏感字段，并为每次加密生成独立随机 IV。
     * @param plaintext 待加密敏感明文；空值或空白值返回 null
     * @param secret Base64 编码的 AES-256 密钥，禁止写入日志或持久化明文
     * @param aad 附加认证数据，用于将密文绑定到业务身份，可为空
     * @return 包含版本、IV 和认证标签的密文
     */
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

    /**
     * 校验密文版本和结构后解密敏感字段；非法密文直接失败，不返回部分明文。
     * @param envelope 版本化 AES-GCM 密文信封；空值或空白值返回 null
     * @param secret Base64 编码的 AES-256 密钥，必须与加密时一致
     * @param aad 附加认证数据，必须与加密时完全一致
     * @return 通过完整性校验的敏感字段明文
     */
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
