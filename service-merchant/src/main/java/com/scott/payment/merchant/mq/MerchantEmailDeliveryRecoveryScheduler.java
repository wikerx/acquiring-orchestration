package com.scott.payment.merchant.mq;

import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import com.scott.payment.merchant.service.impl.MerchantEmailDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantEmailDeliveryRecoveryScheduler
 * @date : 2026-08-02 23:40
 * @email : scott_x@163.com
 * @description : 定时恢复 Merchant 邮件超时占用并触发到期重投，不直接调用 SMTP
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "acquiring.email.delivery", name = "enabled", havingValue = "true")
public class MerchantEmailDeliveryRecoveryScheduler {

    /** Merchant 邮件投递状态机服务。 */
    private final MerchantEmailDeliveryService deliveryService;
    /** 调度开关与扫描间隔配置。 */
    private final EmailDeliveryProperties properties;

    /** 创建 Merchant 邮件恢复调度器。 */
    public MerchantEmailDeliveryRecoveryScheduler(MerchantEmailDeliveryService deliveryService,
                                                  EmailDeliveryProperties properties) {
        this.deliveryService = deliveryService;
        this.properties = properties;
    }

    /** 扫描并恢复超时或到期记录；异常只记录类型，等待下一轮恢复。 */
    @Scheduled(fixedDelayString = "${acquiring.email.delivery.relay-interval-millis:30000}")
    public void recover() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            deliveryService.recoverAndRequeue();
        } catch (RuntimeException exception) {
            log.warn("event: MERCHANT_EMAIL_DELIVERY_RECOVERY_FAILED exceptionType: {}",
                    exception.getClass().getSimpleName());
        }
    }
}
