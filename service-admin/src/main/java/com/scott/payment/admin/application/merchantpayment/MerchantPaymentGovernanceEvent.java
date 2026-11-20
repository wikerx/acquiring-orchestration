package com.scott.payment.admin.application.merchantpayment;

import java.time.LocalDateTime;

/** 已登记的支付方式状态变更，供同事务保存可靠业务通知意图。 */
public record MerchantPaymentGovernanceEvent(String merchantId, String itemKey, Long applicationItemId,
                                             Integer roundNo, long reviewRecordId, Long authorizationRevision,
                                             String action, LocalDateTime occurredAt) {
}
