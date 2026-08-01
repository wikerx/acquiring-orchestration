package com.scott.payment.payment.service;

import com.scott.payment.component.db.route.model.MerchantRouteProfile;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRouteProfileCacheService
 * @date : 2026-08-01 15:35
 * @email : scott_x@163.com
 * @description : 商户收单路由永久快照查询契约，在失效窗口内绕过 Redis 并固定读取主库
 * @status : create
 */
public interface MerchantRouteProfileCacheService {

    /**
     * 查询商户当前非敏感路由聚合快照。
     *
     * @param merchantId 商户号
     * @return 路由快照；无有效配置时返回包含空候选列表的快照
     */
    MerchantRouteProfile findRouteProfile(String merchantId);
}
