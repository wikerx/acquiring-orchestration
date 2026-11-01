package com.scott.payment.admin.mq;

import com.scott.payment.admin.service.impl.AdminEmailDeliveryService;
import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时恢复 Admin 邮件超时占用并触发到期重投，不直接调用 SMTP
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "acquiring.email.delivery", name = "enabled", havingValue = "true")
public class AdminEmailDeliveryRecoveryScheduler {

    /**
     * Admin 邮件投递状态机服务。
     */
    private final AdminEmailDeliveryService deliveryService;
    /**
     * 调度开关与扫描间隔配置。
     */
    private final EmailDeliveryProperties properties;

    /**
     * 创建 Admin 邮件恢复调度器。
     */
    public AdminEmailDeliveryRecoveryScheduler(AdminEmailDeliveryService deliveryService,
                                               EmailDeliveryProperties properties) {
        this.deliveryService = deliveryService;
        this.properties = properties;
    }

    /**
     * 扫描并恢复超时或到期记录；异常只记录类型，等待下一轮恢复。
     */
    @Scheduled(fixedDelayString = "${acquiring.email.delivery.relay-interval-millis:30000}")
    public void recover() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            deliveryService.recoverAndRequeue();
        } catch (RuntimeException exception) {
            log.warn("邮件投递恢复任务执行失败, event: ADMIN_EMAIL_DELIVERY_RECOVERY_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
