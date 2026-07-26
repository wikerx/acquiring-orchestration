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
     * SECURE RANDOM 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    /**
     * IV LENGTH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int IV_LENGTH = 12;
    /**
     * TAG LENGTH BITS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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

    /**
     * 完成 secret Key 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 RiskSensitiveValueCrypto 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private byte[] secretKey() throws Exception {
        String seed = System.getProperty("payment.risk.secret", System.getenv().getOrDefault("PAYMENT_RISK_SECRET", "local-risk-secret-change-me"));
        return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
    }
}
