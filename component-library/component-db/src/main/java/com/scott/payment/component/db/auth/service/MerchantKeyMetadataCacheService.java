package com.scott.payment.component.db.auth.service;

import com.scott.payment.component.db.auth.model.MerchantKeyMetadata;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantKeyMetadataCacheService
 * @date : 2026-08-01 15:05
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 密钥版本元数据缓存契约，统一处理永久快照读取、主库刷新和精确失效
 * @status : create
 */
public interface MerchantKeyMetadataCacheService {

    /**
     * 查询当前商户密钥版本元数据；缓存不可用或失效 pending 时回源主库。
     *
     * @param merchantId 商户号
     * @return 非敏感密钥元数据；尚未配置任何密钥时返回 null
     */
    MerchantKeyMetadata findKeyMetadata(String merchantId);

    /**
     * 从主库重建当前商户密钥版本元数据并覆盖永久缓存。
     *
     * @param merchantId 商户号
     * @return 主库最新元数据；尚未配置任何密钥时返回 null
     */
    MerchantKeyMetadata refreshKeyMetadata(String merchantId);

    /**
     * 精确删除当前商户密钥版本元数据缓存。
     *
     * @param merchantId 商户号
     */
    void evictKeyMetadata(String merchantId);
}
