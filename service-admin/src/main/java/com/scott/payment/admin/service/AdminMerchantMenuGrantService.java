package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantQueryResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantSaveRequest;

/**
 * 管理后台商户菜单授权领域服务。
 */
public interface AdminMerchantMenuGrantService {

    /**
     * 查询指定商户的商户端菜单和权限授权信息。
     *
     * @param merchantId 商户号
     * @return 授权查询响应
     */
    AdminMerchantMenuGrantQueryResponse queryGrant(String merchantId);

    /**
     * 覆盖保存指定商户的商户端菜单和权限授权。
     *
     * @param merchantId 商户号
     * @param request    保存请求
     */
    void saveGrant(String merchantId, AdminMerchantMenuGrantSaveRequest request);
}
