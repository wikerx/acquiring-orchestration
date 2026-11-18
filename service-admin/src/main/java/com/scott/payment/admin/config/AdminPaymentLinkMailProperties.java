package com.scott.payment.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/** 支付邀请独立频控，不修改既有验证码和通用邮件限制。 */
@Data
@Component
@ConfigurationProperties(prefix = "payment-link.mail")
public class AdminPaymentLinkMailProperties {
    private int recipientCooldownSeconds = 60;
    private int merchantHourlyLimit = 100;
    private int actorHourlyLimit = 100;
    @PostConstruct public void validate() {
        if (recipientCooldownSeconds < 1 || merchantHourlyLimit < 1 || actorHourlyLimit < 1) {
            throw new IllegalStateException("payment-link.mail limits must be positive");
        }
    }
}
