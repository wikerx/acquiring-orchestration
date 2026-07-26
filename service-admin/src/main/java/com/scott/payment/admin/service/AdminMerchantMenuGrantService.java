package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantQueryResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantSaveRequest;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantService
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : AdminMerchantMenuGrantService 服务契约，用于声明业务能力、调用边界和返回结果约束，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
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
