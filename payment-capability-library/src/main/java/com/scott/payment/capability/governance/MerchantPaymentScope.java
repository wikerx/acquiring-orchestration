package com.scott.payment.capability.governance;

import java.util.Set;

/** 支付方式用例使用的商户数据范围，由各端从可信登录态构造。 */
public record MerchantPaymentScope(boolean allMerchants, Set<String> merchantIds) {
    public MerchantPaymentScope {
        merchantIds = Set.copyOf(merchantIds);
        if (allMerchants && !merchantIds.isEmpty()) {
            throw new IllegalArgumentException("all merchant scope must not carry merchant ids");
        }
    }

    public static MerchantPaymentScope all() {
        return new MerchantPaymentScope(true, Set.of());
    }

    public static MerchantPaymentScope limited(Set<String> merchantIds) {
        return new MerchantPaymentScope(false, merchantIds);
    }

    public boolean empty() {
        return !allMerchants && merchantIds.isEmpty();
    }
}
