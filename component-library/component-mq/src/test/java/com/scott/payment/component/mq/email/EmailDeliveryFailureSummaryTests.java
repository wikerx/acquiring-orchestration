package com.scott.payment.component.mq.email;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailDeliveryFailureSummaryTests
 * @date : 2026-08-02 23:59
 * @email : scott_x@163.com
 * @description : 验证邮件失败摘要不持久化敏感异常消息，并将主密钥不匹配转换为可操作提示
 * @status : create
 */
@Slf4j
class EmailDeliveryFailureSummaryTests {

    /** 深层异常摘要不得包含任一异常消息中的敏感内容。 */
    @Test
    void shouldKeepOnlyRootExceptionType() {
        log.info("测试邮件失败摘要脱敏，关键输入: 包含敏感描述的嵌套异常");
        IllegalStateException exception = new IllegalStateException(
                "smtp authentication failed for recipient and credential",
                new IllegalArgumentException("mail endpoint and account are invalid"));

        String summary = EmailDeliveryFailureSummary.summarize(exception);

        assertThat(summary)
                .isEqualTo("IllegalArgumentException")
                .doesNotContain("recipient", "credential", "endpoint", "account");
        log.info("邮件失败摘要脱敏测试完成，结果: 仅保留根异常类型");
    }

    /** AES-GCM 认证标签校验失败时返回可操作提示，不向管理端暴露底层密码学异常。 */
    @Test
    void shouldTranslateEncryptionKeyMismatchIntoActionableMessage() {
        log.info("测试邮件主密钥不匹配提示，关键输入: AES-GCM 认证标签校验失败");
        IllegalStateException exception = new IllegalStateException(
                "email payload decrypt failed",
                new AEADBadTagException("Tag mismatch"));

        String summary = EmailDeliveryFailureSummary.summarize(exception);

        assertThat(summary)
                .isEqualTo("邮件加密数据无法解密，请确认邮件加密主密钥一致，或重新录入 SMTP 授权码")
                .doesNotContain("AEADBadTagException", "Tag mismatch");
        log.info("邮件主密钥不匹配提示测试完成，结果: 返回安全且可操作的业务提示");
    }
}
