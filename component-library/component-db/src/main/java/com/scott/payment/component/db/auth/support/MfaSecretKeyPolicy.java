package com.scott.payment.component.db.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MfaSecretKeyPolicy
 * @date : 2026-10-25 00:00
 * @email : scott_x@163.com
 * @description : MFA 主密钥策略，位于认证数据支撑层；限制最低字节长度，拒绝公开默认值，并只允许隔离测试使用进程随机临时密钥。
 * @status : create
 */
final class MfaSecretKeyPolicy {

    /** MFA 主密钥最低 UTF-8 字节数；单位为字节，非敏感固定门槛。 */
    static final int MIN_SECRET_BYTES = 32;

    /** 轮换窗口最多保留的历史解密密钥数，避免配置错误导致每次解密无界尝试。 */
    static final int MAX_PREVIOUS_SECRETS = 2;

    /** 生成隔离测试进程临时主密钥所需的随机字节数。 */
    private static final int EPHEMERAL_SECRET_BYTES = 32;

    /** 临时测试密钥的密码学安全随机源。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private MfaSecretKeyPolicy() {
    }

    static String resolve(String configuredSecret,
                          boolean allowEphemeralTestSecret,
                          String[] activeProfiles) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            requireConfiguredSecret(configuredSecret, "payment.mfa.secret");
            return configuredSecret;
        }
        if (allowEphemeralTestSecret && hasOnlyTestProfile(activeProfiles)) {
            byte[] randomSecret = new byte[EPHEMERAL_SECRET_BYTES];
            SECURE_RANDOM.nextBytes(randomSecret);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(randomSecret);
        }
        throw new IllegalStateException("payment.mfa.secret must be configured with at least 32 bytes");
    }

    static void requireStrongSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("payment.mfa.secret must be configured with at least 32 bytes");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("payment.mfa.secret must be configured with at least 32 bytes");
        }
    }

    /**
     * 校验轮换窗口内的历史解密密钥，并返回去重后的不可变副本。
     *
     * @param configuredPreviousSecrets 逗号分隔配置转换得到的历史密钥数组
     * @param currentSecret 当前加密密钥
     * @return 最多两个历史解密密钥
     */
    static List<String> resolvePreviousSecrets(String[] configuredPreviousSecrets, String currentSecret) {
        if (configuredPreviousSecrets == null || configuredPreviousSecrets.length == 0) {
            return List.of();
        }
        List<String> resolved = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        unique.add(currentSecret);
        for (String previousSecret : configuredPreviousSecrets) {
            if (previousSecret == null || previousSecret.isBlank()) {
                continue;
            }
            requireConfiguredSecret(previousSecret, "payment.mfa.previous-secrets");
            if (!unique.add(previousSecret)) {
                throw new IllegalStateException("payment.mfa.previous-secrets contains a duplicate key");
            }
            resolved.add(previousSecret);
        }
        if (resolved.size() > MAX_PREVIOUS_SECRETS) {
            throw new IllegalStateException("payment.mfa.previous-secrets must contain at most 2 keys");
        }
        return List.copyOf(resolved);
    }

    private static void requireConfiguredSecret(String secret, String propertyName) {
        requireStrongSecret(secret);
        if (!secret.equals(secret.strip())) {
            throw new IllegalStateException(propertyName + " must not contain surrounding whitespace");
        }
    }

    private static boolean hasOnlyTestProfile(String[] activeProfiles) {
        if (activeProfiles == null || activeProfiles.length != 1) {
            return false;
        }
        String profile = activeProfiles[0];
        return profile != null && "test".equals(profile.trim().toLowerCase(Locale.ROOT));
    }
}
