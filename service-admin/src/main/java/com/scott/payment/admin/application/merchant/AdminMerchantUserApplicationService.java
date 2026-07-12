package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantUserDetailDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserListDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserQueryRequest;
import com.scott.payment.admin.service.AdminMerchantUserService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantUserApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant User Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminMerchantUserApplicationService {

    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminMerchantUserService adminMerchantUserService;

    public AdminMerchantUserApplicationService(AdminMerchantUserService adminMerchantUserService) {
        this.adminMerchantUserService = adminMerchantUserService;
    }

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public PageResult<AdminMerchantUserListDTO> pageMerchantUsers(AdminMerchantUserQueryRequest request) {
        return adminMerchantUserService.pageMerchantUsers(request);
    }

    /**
     * 获取商户管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param accountId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */

    public AdminMerchantUserDetailDTO getMerchantUser(Long accountId) {
        return adminMerchantUserService.getMerchantUser(accountId);
    }
}
