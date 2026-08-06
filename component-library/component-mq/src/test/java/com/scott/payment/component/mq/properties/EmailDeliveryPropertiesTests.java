package com.scott.payment.component.mq.properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailDeliveryPropertiesTests
 * @date : 2026-08-02 23:59
 * @email : scott_x@163.com
 * @description : 验证 Admin 与 Merchant 共享的邮件失败指数退避及上限规则
 * @status : create
 */
class EmailDeliveryPropertiesTests {

    /** 重试间隔按已失败次数翻倍，并在配置上限停止增长。 */
    @Test
    void shouldCalculateBoundedExponentialRetryDelay() {
        EmailDeliveryProperties properties = new EmailDeliveryProperties();
        properties.setRetryDelaySeconds(30L);
        properties.setMaxRetryDelaySeconds(100L);

        assertThat(properties.calculateRetryDelaySeconds(0)).isEqualTo(30L);
        assertThat(properties.calculateRetryDelaySeconds(1)).isEqualTo(60L);
        assertThat(properties.calculateRetryDelaySeconds(2)).isEqualTo(100L);
        assertThat(properties.calculateRetryDelaySeconds(Integer.MAX_VALUE)).isEqualTo(100L);
    }

    /** 恢复窗口必须覆盖 SMTP 连接、读取和写入超时预算。 */
    @Test
    void shouldRejectSmtpTimeoutBudgetThatCanOverlapRecovery() {
        EmailDeliveryProperties properties = new EmailDeliveryProperties();
        properties.setProcessingTimeoutSeconds(60L);

        assertThatThrownBy(() -> properties.validateSmtpTimeoutBudget(10_000, 30_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("email delivery processing timeout must exceed SMTP timeout budget");
    }
}
