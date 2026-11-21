package com.scott.payment.capability.governance;

import org.springframework.context.annotation.Import;

/** 管理端和商户端显式装配支付方式用例。 */
@Import({MerchantPaymentRepository.class, MerchantPaymentGovernanceService.class,
        MerchantPaymentMethodReadiness.class, MerchantPaymentCapabilityReadinessService.class,
        MerchantPaymentReadinessFacts.class, PublishedMerchantFeeRules.class, MerchantPaymentCapabilityEventPublisher.class})
public class MerchantPaymentGovernanceConfiguration {
}
