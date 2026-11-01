package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.FrozenMerchantFeeVersionSnapshotDTO;

import java.time.LocalDateTime;

/**
 * Payment 动作费用冻结服务，选择商户当前 MERCHANT 生效版本，校验不可变缓存并生成动作级 hash；不计算费用、汇率或余额。
 */
public interface MerchantFeeVersionSnapshotService {

    /**
     * 在渠道调用前冻结定价时点生效的商户费用版本和不可变摘要。
     *
     * @param merchantId 需要冻结费用配置的商户号
     * @param pricingLockTime 首笔交易锁定的定价时间
     * @return 后续动作复用的费用版本快照
     */
    FrozenMerchantFeeVersionSnapshotDTO freezeActiveVersion(String merchantId,
                                                            LocalDateTime pricingLockTime);
}
