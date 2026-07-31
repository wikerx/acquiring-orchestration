package com.scott.payment.component.redis.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Redis Key 动态维度摘要工具。
 *
 * <p>IP、Host、邮箱、卡号衍生值和长业务幂等键不得直接暴露在物理 Key 中，
 * 统一使用 SHA-256 摘要作为稳定匹配维度。</p>
 */
public final class RedisKeyDigest {

    private RedisKeyDigest() {
    }

    /**
     * 计算动态 Key 内容的 SHA-256 十六进制摘要。
     *
     * @param value 原始动态值
     * @return 64 位小写十六进制摘要
     */
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
