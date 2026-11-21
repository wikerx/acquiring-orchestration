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

    /**
     * 为具备全商户可见权限的调用方创建访问范围。
     */
    public static MerchantPaymentScope all() {
        return new MerchantPaymentScope(true, Set.of());
    }

    /**
     * 以已核验的商户编号集合限定查询范围。
     */
    public static MerchantPaymentScope limited(Set<String> merchantIds) {
        return new MerchantPaymentScope(false, merchantIds);
    }

    /**
     * 创建无法访问任何商户记录的范围。
     */
    public boolean empty() {
        return !allMerchants && merchantIds.isEmpty();
    }
}
