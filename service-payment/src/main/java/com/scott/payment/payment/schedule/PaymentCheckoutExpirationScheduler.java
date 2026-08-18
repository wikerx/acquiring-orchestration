package com.scott.payment.payment.schedule;

import com.scott.payment.payment.config.PaymentCheckoutProperties;
import com.scott.payment.payment.service.PaymentCheckoutService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 定时关闭超过付款期限且尚未提交渠道的收银台订单。 */
@Slf4j
@Component
public class PaymentCheckoutExpirationScheduler {

    private final PaymentCheckoutService paymentCheckoutService;
    private final PaymentCheckoutProperties properties;

    public PaymentCheckoutExpirationScheduler(PaymentCheckoutService paymentCheckoutService,
                                              PaymentCheckoutProperties properties) {
        this.paymentCheckoutService = paymentCheckoutService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${payment.checkout.expiration-fixed-delay-ms:60000}")
    public void expireDueSessions() {
        if (!properties.isExpirationEnabled()) {
            return;
        }
        int expired = paymentCheckoutService.expireDue(LocalDateTime.now(), properties.getExpirationBatchSize());
        if (expired > 0) {
            log.info("event: PAYMENT_CHECKOUT_EXPIRE_DUE expiredCount: {}", expired);
        }
    }
}
