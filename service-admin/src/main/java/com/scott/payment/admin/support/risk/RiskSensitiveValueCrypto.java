package com.scott.payment.admin.support.risk;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
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
 * @classname : RiskSensitiveValueCrypto
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 风控管理端敏感名单值加解密组件，仅用于编辑授权回显完整值，不参与实时交易风控匹配。
 * @status : create
 */
@Component
public class RiskSensitiveValueCrypto {

    /**
     * 密码学安全随机数生成器，用于生成一次性 AES 密钥和 GCM IV。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    /**
     * {@code IV_LENGTH}常量，统一 {@code RiskSensitiveValueCrypto} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int IV_LENGTH = 12;
    /**
     * {@code TAG_LENGTH_BITS}常量，统一 {@code RiskSensitiveValueCrypto} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：位；格式：正整数；不允许为空；非敏感字段。
     * 取值范围：取值由算法协议或数值精度边界限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int TAG_LENGTH_BITS = 128;

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
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey(), "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
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
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey(), "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "风控敏感名单值解密失败");
        }
    }

    private byte[] secretKey() throws Exception {
        String seed = System.getProperty("payment.risk.secret", System.getenv().getOrDefault("PAYMENT_RISK_SECRET", "local-risk-secret-change-me"));
        return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
    }
}
