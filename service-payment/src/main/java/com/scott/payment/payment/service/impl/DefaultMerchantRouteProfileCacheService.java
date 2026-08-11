package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.payment.service.MerchantRouteProfileCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantRouteProfileCacheService
 * @date : 2026-08-01 15:35
 * @email : scott_x@163.com
 * @description : 商户路由永久缓存安全门面，pending 或 Redis 状态未知时丢弃旧快照并查询主库
 * @status : create
 */
@Slf4j
@Service
public class DefaultMerchantRouteProfileCacheService implements MerchantRouteProfileCacheService {

    /** 路由快照缓存读取器。 */
    private final MerchantRouteProfileCacheReader cacheReader;

    /** 数据库变更至永久缓存删除完成之间的安全门禁。 */
    private final CacheInvalidationGuard invalidationGuard;

    /**
     * 创建商户路由缓存门面。
     *
     * @param cacheReader 路由快照缓存读取器
     * @param invalidationGuard 可靠失效门禁
     */
    public DefaultMerchantRouteProfileCacheService(MerchantRouteProfileCacheReader cacheReader,
                                                   CacheInvalidationGuard invalidationGuard) {
        this.cacheReader = cacheReader;
        this.invalidationGuard = invalidationGuard;
    }

    /**
     * 查询商户路由快照，并在缓存读取前后检查失效竞态。
     *
     * @param merchantId 商户号
     * @return 当前可用的非敏感路由快照
     */
    @Override
    public MerchantRouteProfile findRouteProfile(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        String normalizedMerchantId = merchantId.trim();
        if (mustBypassCache(normalizedMerchantId)) {
            return cacheReader.findFresh(normalizedMerchantId);
        }
        MerchantRouteProfile profile = cacheReader.findCached(normalizedMerchantId);
        if (mustBypassCache(normalizedMerchantId)) {
            return cacheReader.findFresh(normalizedMerchantId);
        }
        return isCurrentSchema(profile)
                ? profile
                : cacheReader.refreshCached(normalizedMerchantId);
    }

    /** 永久缓存只接受当前结构版本，旧结构在首次读取时从主库在线重建。 */
    private boolean isCurrentSchema(MerchantRouteProfile profile) {
        return profile != null
                && Integer.valueOf(MerchantRouteProfile.CURRENT_SCHEMA_VERSION).equals(profile.getSchemaVersion());
    }

    /** Redis 门禁查询失败时强制绕过共享快照，防止旧渠道配置继续参与交易。 */
    private boolean mustBypassCache(String merchantId) {
        try {
            return invalidationGuard.isPending(PaymentCacheNames.MERCHANT_ROUTE, merchantId);
        } catch (RuntimeException exception) {
            log.warn("event: MERCHANT_ROUTE_GUARD_CHECK_FAILED cacheName: {} exceptionType: {} reason: {}",
                    PaymentCacheNames.MERCHANT_ROUTE,
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
            return true;
        }
    }
}
