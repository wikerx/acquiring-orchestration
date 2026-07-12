package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantQueryResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantSaveRequest;
import com.scott.payment.admin.service.AdminMerchantMenuGrantService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant Menu Grant Application 服务契约，位于 service-admin 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminMerchantMenuGrantApplicationService {

    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminMerchantMenuGrantService adminMerchantMenuGrantService;

    /**
     * 创建商户菜单授权应用服务。
     *
     * @param adminMerchantMenuGrantService 商户菜单授权领域服务
     */
    public AdminMerchantMenuGrantApplicationService(AdminMerchantMenuGrantService adminMerchantMenuGrantService) {
        this.adminMerchantMenuGrantService = adminMerchantMenuGrantService;
    }

    /**
     * 查询商户菜单授权。
     *
     * @param merchantId 商户号
     * @return 授权信息
     */
    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public AdminMerchantMenuGrantQueryResponse queryGrant(String merchantId) {
        return adminMerchantMenuGrantService.queryGrant(merchantId);
    }

    /**
     * 保存商户菜单授权。
     *
     * @param merchantId 商户号
     * @param request    保存请求
     */
    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void saveGrant(String merchantId, AdminMerchantMenuGrantSaveRequest request) {
        adminMerchantMenuGrantService.saveGrant(merchantId, request);
    }
}
