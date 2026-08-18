package com.scott.payment.component.db.auth.service;

import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRuntimeProfileCacheService
 * @date : 2026-07-30 21:35
 * @email : scott_x@163.com
 * @description : 完整商户资料共享缓存契约，统一管理跨服务查询、写后刷新和删除失效行为
 * @status : create
 */
public interface MerchantRuntimeProfileCacheService {

    /**
     * 按商户号查询未删除的完整商户资料。
     *
     * @param merchantId 商户号
     * @return 商户运行时资料；不存在时返回 null
     */
    MerchantRuntimeProfile findRuntimeProfile(String merchantId);

    /**
     * 使用主库最新记录重建指定商户缓存。
     *
     * <p>管理端或商户端完成写事务后调用该方法，确保缓存内容不受只读库复制延迟影响。</p>
     *
     * @param merchantId 商户号
     * @return 主库最新商户资料；商户不存在时返回 null
     */
    MerchantRuntimeProfile refreshRuntimeProfile(String merchantId);

    /**
     * 将已经由主库写事务确认的商户资料写入共享缓存。
     *
     * @param profile 完整商户资料
     * @return 写入缓存的商户资料
     */
    MerchantRuntimeProfile putRuntimeProfile(MerchantRuntimeProfile profile);

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
