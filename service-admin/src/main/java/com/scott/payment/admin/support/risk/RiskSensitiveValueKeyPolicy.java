package com.scott.payment.admin.support.risk;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 风控敏感值主密钥策略。
 *
 * <p>生产和持久化开发环境必须显式配置独立强密钥；只有隔离 test profile 可显式启用进程随机临时密钥。</p>
 */
final class RiskSensitiveValueKeyPolicy {

    /**
     * 风控敏感值主密钥最低 UTF-8 字节数。
     */
    static final int MIN_SECRET_BYTES = 32;

    /**
     * 轮换期间允许保留的历史解密密钥上限。
     */
    static final int MAX_PREVIOUS_SECRETS = 2;

    /**
     * 隔离测试临时密钥随机字节数。
     */
    private static final int EPHEMERAL_SECRET_BYTES = 32;

    /**
     * 隔离测试临时密钥随机源。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 禁止实例化密钥策略工具类。
     */
    private RiskSensitiveValueKeyPolicy() {
    }

    /**
     * 解析当前加密主密钥，缺失配置时失败关闭。
     *
     * @param configuredSecret 显式配置的当前主密钥
     * @param allowEphemeralTestSecret 是否允许隔离测试生成临时密钥
     * @param activeProfiles 当前活动 profile
     * @return 当前加密主密钥
     */
    static String resolve(String configuredSecret,
                          boolean allowEphemeralTestSecret,
                          String[] activeProfiles) {
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            requireConfiguredSecret(configuredSecret, "payment.risk.secret");
            return configuredSecret;
        }
        if (allowEphemeralTestSecret && hasOnlyTestProfile(activeProfiles)) {
            byte[] randomSecret = new byte[EPHEMERAL_SECRET_BYTES];
            SECURE_RANDOM.nextBytes(randomSecret);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(randomSecret);
        }
        throw new IllegalStateException("payment.risk.secret must be configured with at least 32 bytes");
    }

    /**
     * 解析并校验轮换窗口内的历史解密密钥。
     *
     * @param configuredPreviousSecrets 配置的历史密钥
     * @param currentSecret 当前加密主密钥
     * @return 去重后的历史解密密钥
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
            requireConfiguredSecret(previousSecret, "payment.risk.previous-secrets");
            if (!unique.add(previousSecret)) {
                throw new IllegalStateException("payment.risk.previous-secrets contains a duplicate key");
            }
            resolved.add(previousSecret);
        }
        if (resolved.size() > MAX_PREVIOUS_SECRETS) {
            throw new IllegalStateException("payment.risk.previous-secrets must contain at most 2 keys");
        }
        return List.copyOf(resolved);
    }

    /**
     * 校验显式配置的密钥强度和首尾空白。
     *
     * @param secret 待校验密钥
     * @param propertyName 配置属性名
     */
    private static void requireConfiguredSecret(String secret, String propertyName) {
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(propertyName + " must be configured with at least 32 bytes");
        }
        if (!secret.equals(secret.strip())) {
            throw new IllegalStateException(propertyName + " must not contain surrounding whitespace");
        }
    }

    /**
     * 判断当前进程是否严格只启用了 test profile。
     *
     * @param activeProfiles 当前活动 profile
     * @return 仅启用 test profile 时返回 true
     */
    private static boolean hasOnlyTestProfile(String[] activeProfiles) {
        if (activeProfiles == null || activeProfiles.length != 1) {
            return false;
        }
        String profile = activeProfiles[0];
        return profile != null && "test".equals(profile.trim().toLowerCase(Locale.ROOT));
    }
}
