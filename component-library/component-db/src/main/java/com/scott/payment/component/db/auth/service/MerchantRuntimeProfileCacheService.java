package com.scott.payment.component.db.auth.service;

import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRuntimeProfileCacheService
 * @date : 2026-07-30 21:35
 * @email : scott_x@163.com
 * @description : 商户运行时最小资料缓存契约，为鉴权与交易入口提供受失效门禁保护的主库事实读取，并暴露正负缓存协同失效能力
 * @status : create
 */
public interface MerchantRuntimeProfileCacheService {

    /**
     * 按商户号查询未删除的最小运行时资料。
     *
     * @param merchantId 商户号
     * @return 商户运行时资料；不存在时返回 null
     */
    MerchantRuntimeProfile findRuntimeProfile(String merchantId);

    /**
     * 删除商户运行时正缓存与独立 miss marker。
     *
     * @param merchantId 商户号
     */
    void evictRuntimeProfile(String merchantId);

    /**
     * 删除商户 OpenAPI 聚合访问策略缓存，供 IP 白名单和接口开关变更后的可靠失效链调用。
     *
     * @param merchantId 商户号
     */
    void evictOpenApiAccessPolicy(String merchantId);
}
