package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysConfigDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 系统参数导出值安全策略，集中阻断隐藏、加密和敏感键值进入导出文件。
 */
@Component
public class SystemConfigExportValuePolicy {
    /** 导出文件中用于替代敏感原值的固定标记。 */
    public static final String REDACTED = "[REDACTED]";

    private static final Set<String> SENSITIVE_TOKENS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "credential",
            "privatekey", "private_key", "apikey", "api_key", "accesskey",
            "access_key", "signingkey", "signing_key", "encryptionkey",
            "encryption_key", "jwt", "mfa");

    /**
     * 返回允许写入导出文件的配置值；命中任一敏感条件时只返回脱敏标记。
     *
     * @param config 系统参数配置
     * @return 安全导出值
     */
    public String exportValue(SysConfigDTO config) {
        if (config == null || Integer.valueOf(1).equals(config.getEncrypted())
                || Integer.valueOf(0).equals(config.getVisible())
                || isSensitiveKey(config.getConfigKey())) {
            return REDACTED;
        }
        return config.getConfigValue();
    }

    /** 判断配置键是否包含凭证、签名或加密相关敏感语义。 */
    boolean isSensitiveKey(String configKey) {
        if (!StringUtils.hasText(configKey)) {
            return false;
        }
        String normalized = configKey.toLowerCase(Locale.ROOT).replace('-', '_');
        return SENSITIVE_TOKENS.stream().anyMatch(normalized::contains);
    }
}
