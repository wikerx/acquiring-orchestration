package com.scott.payment.capability.governance;

import java.time.LocalDateTime;

/** 商户费用生效指针更新，由同事务监听者登记可靠配置变更。 */
public record MerchantPaymentFeeChangedEvent(String merchantId, Long versionId, LocalDateTime occurredAt) {
}
