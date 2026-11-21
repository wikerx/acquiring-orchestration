package com.scott.payment.capability.governance;

import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.json.JsonUtils;
import org.springframework.cache.annotation.Cacheable;

/** 仅用于已生效版本的展示规则。规则版本本身不变，切换费率指针会自然切换缓存键。 */
public class PublishedMerchantFeeRules {
    private final MerchantPaymentReadinessFacts facts;

    public PublishedMerchantFeeRules(MerchantPaymentReadinessFacts facts) {
        this.facts = facts;
    }

    @Cacheable(cacheNames = PaymentCacheNames.MERCHANT_CAPABILITY_FEE_RULES, key = "#p0",
            unless = "#result == '[]'")
    public String byVersion(long versionId) {
        return JsonUtils.toJsonString(facts.feeRules(versionId));
    }
}
