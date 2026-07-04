package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.merchant.AdminMerchantUserDetailDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserListDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserQueryRequest;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantUserService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 管理端商户用户只读查询服务。
 * @status : create
 */
public interface AdminMerchantUserService {

    /**
     * 分页查询商户系统账号。
     *
     * @param request 查询条件
     * @return 商户账号分页
     */
    PageResult<AdminMerchantUserListDTO> pageMerchantUsers(AdminMerchantUserQueryRequest request);

    /**
     * 查询商户系统账号详情。
     *
     * @param accountId 账号ID
     * @return 账号详情
     */
    AdminMerchantUserDetailDTO getMerchantUser(Long accountId);
}
