package com.scott.payment.merchant.service;

import com.scott.payment.merchant.dto.profile.MerchantProfileResponse;
import com.scott.payment.merchant.dto.profile.MerchantProfileUpdateRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileService
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 商户主体资料领域服务，统一当前商户查询、可编辑字段更新和共享缓存可靠失效边界
 * @status : create
 */
public interface MerchantProfileService {

    /**
     * 查询指定认证商户的主体资料。
     *
     * @param merchantId 认证上下文中的商户号
     * @return 合并共享缓存和主库敏感投影的商户资料
     */
    MerchantProfileResponse getProfile(String merchantId);

    /**
     * 更新指定认证商户允许自助维护的主体资料字段。
     *
     * @param merchantId 认证上下文中的商户号
     * @param request 允许商户维护的字段
     * @return 当前事务内从主库读取的最新商户资料
     */
    MerchantProfileResponse updateProfile(String merchantId, MerchantProfileUpdateRequest request);
}
