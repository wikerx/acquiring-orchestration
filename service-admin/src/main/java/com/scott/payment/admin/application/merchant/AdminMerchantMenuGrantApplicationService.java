package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantQueryResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantSaveRequest;
import com.scott.payment.admin.service.AdminMerchantMenuGrantService;
import org.springframework.stereotype.Service;

/**
 * 管理端商户菜单授权应用服务，用于运营后台，编排可信登录上下文、权限、领域服务调用和响应模型组装。
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
