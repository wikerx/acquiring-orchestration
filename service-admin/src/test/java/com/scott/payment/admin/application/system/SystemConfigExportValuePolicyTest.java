package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysConfigDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 系统参数导出值安全策略测试。 */
class SystemConfigExportValuePolicyTest {
    private final SystemConfigExportValuePolicy policy = new SystemConfigExportValuePolicy();

    @Test
    void shouldKeepOrdinaryVisiblePlainValue() {
        SysConfigDTO config = config("platform.gateway.base-url", "https://example.test", 1, 0);
        assertThat(policy.exportValue(config)).isEqualTo("https://example.test");
    }

    @Test
    void shouldRedactEncryptedHiddenAndSensitiveValues() {
        assertThat(policy.exportValue(config("platform.public.name", "cipher", 1, 1)))
                .isEqualTo(SystemConfigExportValuePolicy.REDACTED);
        assertThat(policy.exportValue(config("platform.internal.name", "hidden", 0, 0)))
                .isEqualTo(SystemConfigExportValuePolicy.REDACTED);
        assertThat(policy.exportValue(config("payment.signing-key", "secret-value", 1, 0)))
                .isEqualTo(SystemConfigExportValuePolicy.REDACTED);
        assertThat(policy.exportValue(config("security.jwt.token-ttl", "900", 1, 0)))
                .isEqualTo(SystemConfigExportValuePolicy.REDACTED);
    }

    private SysConfigDTO config(String key, String value, Integer visible, Integer encrypted) {
        SysConfigDTO config = new SysConfigDTO();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setVisible(visible);
        config.setEncrypted(encrypted);
        return config;
    }
}
