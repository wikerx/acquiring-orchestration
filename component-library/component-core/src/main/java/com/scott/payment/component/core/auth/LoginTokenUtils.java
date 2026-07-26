package com.scott.payment.component.core.auth;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LoginTokenUtils
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统登录会话 token 工具
 * @status : create
 */
public final class LoginTokenUtils {

    /**
     * 登录 token 随机字节长度。
     */
    private static final int TOKEN_BYTES = 32;

    /**
     * 安全随机数生成器。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private LoginTokenUtils() {
    }

    /**
     * 生成一次性登录 token 明文。
     * <p>
     * token 明文只返回给前端，服务端数据库只保存 token_hash。
     *
     * @return Base64Url token 明文
     */
    public static String generateToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * 计算 token 哈希，数据库只保存该值。
     *
     * @param token 登录 token 明文
     * @return SHA-256 十六进制哈希
     */
    public static String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "token is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "token hash can not be calculated");
        }
    }
}
