package com.scott.payment.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationConfigTests
 * @date : 2026-08-03 00:00
 * @email : scott_x@163.com
 * @description : 验证商户通知重试默认值、显式覆盖和启动边界门禁。
 * @status : create
 */
class MerchantNotificationConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MerchantNotificationConfig.class);

    @Test
    void shouldUseBoundedDefaultRetryCount() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MerchantNotificationProperties.class).getMaxRetryCount()).isEqualTo(10);
        });
    }

    @Test
    void shouldBindExplicitRetryCount() {
        contextRunner
                .withPropertyValues("payment.transaction.merchant-notification.max-retry-count=12")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MerchantNotificationProperties.class).getMaxRetryCount()).isEqualTo(12);
                });
    }

    @Test
    void shouldRejectRetryCountOutsideAllowedRange() {
        contextRunner
                .withPropertyValues("payment.transaction.merchant-notification.max-retry-count=101")
                .run(context -> assertThat(context).hasFailed());
    }
}
