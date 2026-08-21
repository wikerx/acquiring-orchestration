package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantActiveFeeCachePolicy
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 商户生效费率缓存读取策略，在可靠失效窗口或 Redis 状态未知时绕过缓存并读取主库
 * @status : create
 */
@Slf4j
@Service
public class MerchantActiveFeeCachePolicy {

    /** 永久缓存失效门禁。 */
    private final CacheInvalidationGuard invalidationGuard;

    /**
     * 创建商户生效费率缓存读取策略。
     *
     * @param invalidationGuard 永久缓存失效门禁
     */
    public MerchantActiveFeeCachePolicy(CacheInvalidationGuard invalidationGuard) {
        this.invalidationGuard = invalidationGuard;
    }

    /**
     * 判断当前商户是否允许读取并回写生效费率缓存。
     *
     * <p>门禁查询失败时返回 {@code false}，使调用方法绕过缓存并从主库加载；不把 Redis
     * 基础设施故障放大为商户费率查询失败，也不会将不确定状态写入永久缓存。</p>
     *
     * @param merchantId 已认证商户号
     * @return 没有失效任务进行中且 Redis 明确返回正常状态时为 {@code true}
     */
    public boolean isCacheReadAllowed(String merchantId) {
        try {
            return !invalidationGuard.isPending(PaymentCacheNames.MERCHANT_ACTIVE_FEE, merchantId);
        } catch (RuntimeException exception) {
            log.warn(
                    "event: MERCHANT_ACTIVE_FEE_CACHE_GUARD_CHECK_FAILED exceptionType: {}",
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }
}
