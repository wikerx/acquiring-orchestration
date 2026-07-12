package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantQueryResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantSaveRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Merchant Menu Grant 服务契约，位于 service-admin 的服务契约层，用于定义调用契约和职责边界。
 * @status : create
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
