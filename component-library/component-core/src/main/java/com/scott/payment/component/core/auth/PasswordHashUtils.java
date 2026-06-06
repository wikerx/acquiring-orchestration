package com.scott.payment.component.core.auth;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PasswordHashUtils
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 后台与商户管理系统登录密码哈希工具
 * @status : create
 */
public final class PasswordHashUtils {

    /**
     * PBKDF2 算法名称，JDK17 原生支持。
     */
    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /**
     * 密码派生迭代次数。
     */
    private static final int ITERATION_COUNT = 210_000;

    /**
     * 派生密钥长度，单位 bit。
     */
    private static final int KEY_LENGTH = 256;

    /**
     * 随机盐长度，单位 byte。
     */
    private static final int SALT_LENGTH = 16;

    /**
     * 安全随机数生成器。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHashUtils() {
    }

    /**
     * 生成随机密码盐。
     *
     * @return Base64Url 编码后的随机盐
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
    }

    /**
     * 使用 PBKDF2 计算密码哈希。
     *
     * @param rawPassword 登录明文密码
     * @param saltText    Base64Url 编码后的随机盐
     * @return Base64Url 编码后的密码哈希
     */
    public static String hashPassword(String rawPassword, String saltText) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "password is required");
        }
        if (saltText == null || saltText.isBlank()) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "password salt is required");
        }
        try {
            byte[] salt = Base64.getUrlDecoder().decode(saltText);
            PBEKeySpec keySpec = new PBEKeySpec(rawPassword.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
            byte[] hash = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec).getEncoded();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "password can not be hashed");
        }
    }

    /**
     * 校验登录密码是否匹配。
     *
     * @param rawPassword  登录明文密码
     * @param saltText     Base64Url 编码后的随机盐
     * @param expectedHash 数据库存储的密码哈希
     * @return true 表示密码正确
     */
    public static boolean matches(String rawPassword, String saltText, String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        String actualHash = hashPassword(rawPassword, saltText);
        return actualHash.equals(expectedHash);
    }
}
