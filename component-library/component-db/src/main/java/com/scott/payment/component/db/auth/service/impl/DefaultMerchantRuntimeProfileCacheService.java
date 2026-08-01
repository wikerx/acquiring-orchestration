package com.scott.payment.component.db.auth.service.impl;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.CacheMissMarkerStore;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantRuntimeProfileCacheService
 * @date : 2026-07-30 21:25
 * @email : scott_x@163.com
 * @description : 商户运行时资料安全缓存门面，协调正缓存、短 TTL miss marker、失效门禁与主库回源，避免陈旧资料或 Redis 故障绕过商户校验
 * @status : create
 */
@Slf4j
@Service
public class DefaultMerchantRuntimeProfileCacheService implements MerchantRuntimeProfileCacheService {

    /**
     * 商户运行时资料所属业务域。
     */
    private static final String MERCHANT_DOMAIN = "merchant";

    /**
     * 商户运行时资料 miss marker 业务用途。
     */
    private static final String RUNTIME_PROFILE_MISS_BUSINESS = "runtime-profile-miss";

    /**
     * miss marker 基础有效期。短 TTL 只用于抑制无效商户号穿透，不替代数据库事实源。
     */
    private static final Duration MISS_MARKER_TTL = Duration.ofSeconds(30);

    /**
     * miss marker TTL 抖动比例，减少大量无效商户号 marker 同时过期造成的回源尖峰。
     */
    private static final int MISS_MARKER_TTL_JITTER_PERCENT = 10;

    /**
     * 安全缓存失效门禁；pending 或状态未知时必须绕过缓存。
     */
    private final CacheInvalidationGuard invalidationGuard;

    /**
     * 可代理的正缓存与主库读取器。
     */
    private final MerchantRuntimeProfileCacheReader cacheReader;

    /**
     * 独立 miss marker 存储；未配置 Redis 时允许为空，此时禁用负缓存但保留主库查询。
     */
    private final CacheMissMarkerStore missMarkerStore;

    /**
     * 创建商户运行时资料缓存门面。
     *
     * @param invalidationGuard       安全缓存失效门禁
     * @param cacheReader             正缓存与主库读取器
     * @param missMarkerStoreProvider miss marker 存储提供器
     */
    public DefaultMerchantRuntimeProfileCacheService(
            CacheInvalidationGuard invalidationGuard,
            MerchantRuntimeProfileCacheReader cacheReader,
            ObjectProvider<CacheMissMarkerStore> missMarkerStoreProvider) {
        this.invalidationGuard = invalidationGuard;
        this.cacheReader = cacheReader;
        this.missMarkerStore = missMarkerStoreProvider.getIfAvailable();
    }

    /**
     * 查询商户最小运行时资料，数据库仍是最终事实源。
     *
     * @param merchantId 商户号
     * @return 商户运行时资料；不存在时返回 null
     */
    @Override
    public MerchantRuntimeProfile findRuntimeProfile(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        String normalizedMerchantId = merchantId.trim();
        if (mustBypassCache(normalizedMerchantId)) {
            return cacheReader.findFresh(normalizedMerchantId);
        }
        CacheMissMarkerStore.LookupStatus missStatus = lookupMissMarker(normalizedMerchantId);
        if (mustBypassCache(normalizedMerchantId)) {
            return cacheReader.findFresh(normalizedMerchantId);
        }
        if (missStatus == CacheMissMarkerStore.LookupStatus.PRESENT) {
            return null;
        }
        MerchantRuntimeProfile cached = cacheReader.findCached(normalizedMerchantId);
        if (mustBypassCache(normalizedMerchantId)) {
            return cacheReader.findFresh(normalizedMerchantId);
        }
        if (cached != null && !cached.hasCurrentCacheSchema()) {
            /*
             * 旧 Value 属于永久缓存，不能等待 TTL 自然淘汰。这里只删除当前商户 Key 并重新
             * 经过 @Cacheable 主库加载，避免批量扫描或在 Key 中引入无必要的版本片段。
             */
            cacheReader.evictLegacyValue(normalizedMerchantId);
            if (mustBypassCache(normalizedMerchantId)) {
                return cacheReader.findFresh(normalizedMerchantId);
            }
            cached = cacheReader.findCached(normalizedMerchantId);
            if (mustBypassCache(normalizedMerchantId)) {
                return cacheReader.findFresh(normalizedMerchantId);
            }
        }
        if (cached == null && missStatus == CacheMissMarkerStore.LookupStatus.ABSENT) {
            markConfirmedMissing(normalizedMerchantId);
        }
        return cached;
    }

    /**
     * 删除商户运行时正缓存和独立 miss marker。
     *
     * <p>本方法用于进程内主动失效；跨服务可靠失效由 Outbox 调用立即删除服务执行相同的双删除语义。
     * miss marker 删除失败会抛出异常，禁止在数据库已经新增商户后继续接受陈旧的“不存在”结论。</p>
     *
     * @param merchantId 商户号，允许包含首尾空白但不允许为空
     */
    @Override
    @CacheEvict(cacheNames = PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
            key = "#p0",
            condition = "T(org.springframework.util.StringUtils).hasText(#p0)")
    public void evictRuntimeProfile(String merchantId) {
        if (missMarkerStore != null) {
            missMarkerStore.evict(
                    MERCHANT_DOMAIN,
                    RUNTIME_PROFILE_MISS_BUSINESS,
                    merchantId.trim()
            );
        }
    }

    /**
     * 删除商户 OpenAPI 聚合访问策略缓存。
     *
     * @param merchantId 商户号
     */
    @Override
    @CacheEvict(cacheNames = PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
            key = "#p0",
            condition = "T(org.springframework.util.StringUtils).hasText(#p0)")
    public void evictOpenApiAccessPolicy(String merchantId) {
        // Spring Cache 负责删除物理缓存，本方法只表达跨模块失效语义。
    }

    /**
     * 查询商户运行时资料 miss marker。
     *
     * @param merchantId 已规范化商户号
     * @return marker 三态；未配置存储或读取异常时返回 UNAVAILABLE
     */
    private CacheMissMarkerStore.LookupStatus lookupMissMarker(String merchantId) {
        if (missMarkerStore == null) {
            return CacheMissMarkerStore.LookupStatus.UNAVAILABLE;
        }
        try {
            return missMarkerStore.lookup(
                    MERCHANT_DOMAIN,
                    RUNTIME_PROFILE_MISS_BUSINESS,
                    merchantId
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "event: MERCHANT_RUNTIME_PROFILE_MISS_MARKER_READ_FAILED "
                            + "exceptionType: {} reason: {}",
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return CacheMissMarkerStore.LookupStatus.UNAVAILABLE;
        }
    }

    /**
     * 在主库明确返回空记录后写入短 TTL marker。
     *
     * <p>写入失败只会失去穿透保护，不改变主库已确认的“不存在”结果，因此记录告警后返回 null；
     * Redis 读取状态为 UNAVAILABLE 时调用方不会进入本方法。</p>
     *
     * @param merchantId 已规范化商户号
     */
    private void markConfirmedMissing(String merchantId) {
        if (missMarkerStore == null) {
            return;
        }
        try {
            missMarkerStore.markMissing(
                    MERCHANT_DOMAIN,
                    RUNTIME_PROFILE_MISS_BUSINESS,
                    merchantId,
                    MISS_MARKER_TTL,
                    MISS_MARKER_TTL_JITTER_PERCENT
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "event: MERCHANT_RUNTIME_PROFILE_MISS_MARKER_WRITE_FAILED "
                            + "exceptionType: {} reason: {}",
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
        }
    }

    /**
     * 判断当前商户缓存是否必须绕过。
     *
     * @param merchantId 已规范化商户号
     * @return pending 或门禁状态无法确认时返回 true
     */
    private boolean mustBypassCache(String merchantId) {
        try {
            return invalidationGuard.isPending(
                    PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                    merchantId
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "event: SECURITY_CACHE_GUARD_CHECK_FAILED cacheName: {} "
                            + "exceptionType: {} reason: {}",
                    PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
            return true;
        }
    }
}
