package com.scott.payment.component.mq.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailDeliveryFailureSummaryTests
 * @date : 2026-08-02 23:59
 * @email : scott_x@163.com
 * @description : 验证邮件失败摘要只保留异常类型，不持久化邮箱、认证信息或基础设施地址
 * @status : create
 */
class EmailDeliveryFailureSummaryTests {

    /** 深层异常摘要不得包含任一异常消息中的敏感内容。 */
    @Test
    void shouldKeepOnlyRootExceptionType() {
        IllegalStateException exception = new IllegalStateException(
                "smtp authentication failed for recipient and credential",
                new IllegalArgumentException("mail endpoint and account are invalid"));

        String summary = EmailDeliveryFailureSummary.summarize(exception);

        assertThat(summary)
                .isEqualTo("IllegalArgumentException")
                .doesNotContain("recipient", "credential", "endpoint", "account");
    }
}
