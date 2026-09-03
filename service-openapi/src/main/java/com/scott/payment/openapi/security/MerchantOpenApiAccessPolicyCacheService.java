package com.scott.payment.openapi.security;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiAccessPolicyCacheService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 访问策略缓存服务。
 * @status : create
 */
@Service
public class MerchantOpenApiAccessPolicyCacheService {

    private static final Logger log =
            LoggerFactory.getLogger(MerchantOpenApiAccessPolicyCacheService.class);

    /**
     * 安全策略缓存失效门禁；存在待失效记录或门禁状态不可判定时强制回源主库。
     */
    private final CacheInvalidationGuard invalidationGuard;

    /**
     * 访问策略读取器，分别提供 Spring Cache 代理入口和主库直读入口。
     */
    private final MerchantOpenApiAccessPolicyCacheReader cacheReader;

    /**
     * 创建商户 OpenAPI 访问策略缓存服务。
     *
     * @param invalidationGuard 缓存失效门禁
     * @param cacheReader       访问策略读取器
     */
    public MerchantOpenApiAccessPolicyCacheService(CacheInvalidationGuard invalidationGuard,
                                                   MerchantOpenApiAccessPolicyCacheReader cacheReader) {
        this.invalidationGuard = invalidationGuard;
        this.cacheReader = cacheReader;
    }

    /**
     * 读取商户 IP 访问策略，缓存未启用状态可防止配置表穿透。
     *
     * @param merchantId 商户号
     * @return 商户访问策略
     */
    public MerchantOpenApiAccessPolicy findPolicy(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return new MerchantOpenApiAccessPolicy();
        }
        String normalizedMerchantId = merchantId.trim();
        if (mustBypassCache(normalizedMerchantId)) {
            return cacheReader.findFresh(normalizedMerchantId);
        }
        MerchantOpenApiAccessPolicy cached = cacheReader.findCached(normalizedMerchantId);
        return mustBypassCache(normalizedMerchantId)
                ? cacheReader.findFresh(normalizedMerchantId)
                : cached;
    }

    /**
     * 判断安全策略读取是否必须绕过缓存；失效待处理、门禁占用或门禁查询异常均直接回源主库。
     *
     * @param merchantId 商户号
     * @return true 表示禁止使用可能过期的缓存策略
     */
    private boolean mustBypassCache(String merchantId) {
        try {
            return invalidationGuard.isPending(
                    PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                    merchantId
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "event: SECURITY_CACHE_GUARD_CHECK_FAILED cacheName: {} exceptionType: {}",
                    PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                    exception.getClass().getSimpleName()
            );
            return true;
        }
    }
}
