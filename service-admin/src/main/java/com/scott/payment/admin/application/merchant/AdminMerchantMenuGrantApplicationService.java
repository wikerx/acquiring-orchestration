package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantQueryResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantSaveRequest;
import com.scott.payment.admin.service.AdminMerchantMenuGrantService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantApplicationService
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : admin商户菜单授权应用服务，位于 运营后台服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class AdminMerchantMenuGrantApplicationService {

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
    public AdminMerchantMenuGrantQueryResponse queryGrant(String merchantId) {
        return adminMerchantMenuGrantService.queryGrant(merchantId);
    }

    /**
     * 保存商户菜单授权。
     *
     * @param merchantId 商户号
     * @param request    保存请求
     */
    public void saveGrant(String merchantId, AdminMerchantMenuGrantSaveRequest request) {
        adminMerchantMenuGrantService.saveGrant(merchantId, request);
    }
}
