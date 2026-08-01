package com.scott.payment.component.db.auth.service.impl;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.auth.model.MerchantKeyMetadata;
import com.scott.payment.component.db.auth.service.MerchantKeyMetadataCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantKeyMetadataCacheService
 * @date : 2026-08-01 15:05
 * @email : scott_x@163.com
 * @description : 商户密钥元数据安全缓存门面，在失效 pending 或 Redis 状态未知时强制读取主库最新版本
 * @status : create
 */
@Slf4j
@Service
public class DefaultMerchantKeyMetadataCacheService implements MerchantKeyMetadataCacheService {

    /** 密钥元数据缓存加载器。 */
    private final MerchantKeyMetadataCacheReader cacheReader;

    /** 数据库变更至永久缓存删除完成之间的安全门禁。 */
    private final CacheInvalidationGuard invalidationGuard;

    /**
     * 创建商户密钥元数据缓存门面。
     *
     * @param cacheReader 元数据缓存加载器
     * @param invalidationGuard 安全缓存失效门禁
     */
    public DefaultMerchantKeyMetadataCacheService(MerchantKeyMetadataCacheReader cacheReader,
                                                  CacheInvalidationGuard invalidationGuard) {
        this.cacheReader = cacheReader;
        this.invalidationGuard = invalidationGuard;
    }

    /**
     * 查询当前密钥版本元数据；失效窗口内始终绕过 Redis。
     *
     * @param merchantId 商户号
     * @return 非敏感密钥元数据；未配置任何密钥时返回 null
     */
    @Override
    public MerchantKeyMetadata findKeyMetadata(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        String normalizedMerchantId = merchantId.trim();
        if (mustBypassCache(normalizedMerchantId)) {
            return cacheReader.findFresh(normalizedMerchantId);
        }
        MerchantKeyMetadata metadata = cacheReader.findCached(normalizedMerchantId);
        return mustBypassCache(normalizedMerchantId)
                ? cacheReader.findFresh(normalizedMerchantId)
                : metadata;
    }

    /**
     * 从主库刷新当前密钥版本元数据。
     *
     * @param merchantId 商户号
     * @return 最新元数据；未配置任何密钥时返回 null
     */
    @Override
    public MerchantKeyMetadata refreshKeyMetadata(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        MerchantKeyMetadata metadata = cacheReader.refresh(merchantId.trim());
        if (metadata == null) {
            evictKeyMetadata(merchantId);
        }
        return metadata;
    }

    /**
     * 精确删除当前商户密钥版本元数据缓存。
     *
     * @param merchantId 商户号
     */
    @Override
    @CacheEvict(cacheNames = PaymentCacheNames.MERCHANT_KEY_METADATA,
            key = "#p0.trim()",
            condition = "T(org.springframework.util.StringUtils).hasText(#p0)")
    public void evictKeyMetadata(String merchantId) {
        // Spring Cache 负责删除物理 Key，本方法只提供跨模块一致的失效入口。
    }

    /** Redis 门禁异常时按安全配置不可确认处理，强制回源主库。 */
    private boolean mustBypassCache(String merchantId) {
        try {
            return invalidationGuard.isPending(PaymentCacheNames.MERCHANT_KEY_METADATA, merchantId);
        } catch (RuntimeException exception) {
            log.warn("event: MERCHANT_KEY_METADATA_GUARD_CHECK_FAILED cacheName: {} exceptionType: {} reason: {}",
                    PaymentCacheNames.MERCHANT_KEY_METADATA,
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
            return true;
        }
    }
}
