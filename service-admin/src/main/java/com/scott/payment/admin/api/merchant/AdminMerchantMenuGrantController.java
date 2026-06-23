package com.scott.payment.admin.api.merchant;

import com.scott.payment.admin.application.merchant.AdminMerchantMenuGrantApplicationService;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantQueryResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantSaveRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台商户菜单授权接口。
 */
@RestController
@RequestMapping("/admin/merchant-menu-grants")
public class AdminMerchantMenuGrantController {

    private final AdminMerchantMenuGrantApplicationService adminMerchantMenuGrantApplicationService;

    /**
     * 创建商户菜单授权接口。
     *
     * @param adminMerchantMenuGrantApplicationService 商户菜单授权应用服务
     */
    public AdminMerchantMenuGrantController(AdminMerchantMenuGrantApplicationService adminMerchantMenuGrantApplicationService) {
        this.adminMerchantMenuGrantApplicationService = adminMerchantMenuGrantApplicationService;
    }

    /**
     * 查询商户菜单授权。
     *
     * @param merchantId 商户号
     * @return 授权信息
     */
    @GetMapping("/{merchantId}")
    @RequiresPermission("merchant:menu-grant:list")
    public CommonResult<AdminMerchantMenuGrantQueryResponse> queryGrant(@PathVariable("merchantId") String merchantId) {
        return success(adminMerchantMenuGrantApplicationService.queryGrant(merchantId));
    }

    /**
     * 保存商户菜单授权。
     *
     * @param merchantId 商户号
     * @param request    授权保存请求
     * @return 空响应
     */
    @PostMapping("/{merchantId}")
    @RequiresPermission("merchant:menu-grant:save")
    @OperationLog(moduleName = "商户菜单授权", businessType = OperationTypeConstants.UPDATE,
            operation = "保存商户菜单授权", recordRequest = false, recordResponse = false)
    public CommonResult<Void> saveGrant(@PathVariable("merchantId") String merchantId,
                                        @Valid @RequestBody AdminMerchantMenuGrantSaveRequest request) {
        adminMerchantMenuGrantApplicationService.saveGrant(merchantId, request);
        return success();
    }
}
