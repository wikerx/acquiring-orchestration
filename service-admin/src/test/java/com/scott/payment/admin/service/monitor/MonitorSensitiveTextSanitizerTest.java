package com.scott.payment.admin.service.monitor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 监控摘要凭据、支付卡号、URL 参数脱敏和长度限制测试。
 */
class MonitorSensitiveTextSanitizerTest {

    private final MonitorSensitiveTextSanitizer sanitizer = new MonitorSensitiveTextSanitizer();

    @Test
    void shouldRedactCredentialsPanAndCvvWithoutLeakingUrlParameters() {
        String sanitized = sanitizer.sanitize(
                "Authorization: Bearer-abc password=hunter2 card=4111 1111 1111 1111 "
                        + "callback=https://merchant.test/cb?token=plain&cvv=123", 512);

        assertThat(sanitized)
                .contains("Authorization=[REDACTED]", "password=[REDACTED]", "[REDACTED_PAN]")
                .contains("token=[REDACTED]", "cvv=[REDACTED]")
                .doesNotContain("Bearer-abc", "hunter2", "4111 1111 1111 1111", "plain", "123");
    }

    @Test
    void shouldTrimAndBoundOutput() {
        assertThat(sanitizer.sanitize("  abcdef  ", 4)).isEqualTo("abcd");
        assertThat(sanitizer.sanitize("   ", 4)).isNull();
    }
}
