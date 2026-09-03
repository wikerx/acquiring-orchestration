package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantQueryResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantSaveRequest;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantService
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : admin商户菜单授权服务契约，位于 运营后台服务，声明该业务能力的输入、返回结果和异常边界，由实现类保持一致。
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
