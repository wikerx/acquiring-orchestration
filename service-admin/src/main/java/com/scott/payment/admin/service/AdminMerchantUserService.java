package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.merchant.AdminMerchantUserDetailDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserListDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserQueryRequest;
import com.scott.payment.component.core.model.PageResult;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantUserService
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : admin商户用户服务契约，位于 运营后台服务，声明该业务能力的输入、返回结果和异常边界，由实现类保持一致。
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
