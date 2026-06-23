package com.scott.payment.admin.api.merchant;

import com.scott.payment.admin.application.merchant.AdminMerchantUserApplicationService;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserDetailDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserListDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserQueryRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理端商户用户只读查询接口。
 */
@RestController
@RequestMapping("/admin/merchant-users")
public class AdminMerchantUserController {

    private final AdminMerchantUserApplicationService adminMerchantUserApplicationService;

    public AdminMerchantUserController(AdminMerchantUserApplicationService adminMerchantUserApplicationService) {
        this.adminMerchantUserApplicationService = adminMerchantUserApplicationService;
    }

    @GetMapping
    @RequiresPermission("admin:merchant:user:list")
    public CommonResult<PageResult<AdminMerchantUserListDTO>> pageMerchantUsers(@ModelAttribute AdminMerchantUserQueryRequest request) {
        return success(adminMerchantUserApplicationService.pageMerchantUsers(request));
    }

    @GetMapping("/{accountId}")
    @RequiresPermission("admin:merchant:user:detail")
    public CommonResult<AdminMerchantUserDetailDTO> getMerchantUser(@PathVariable("accountId") Long accountId) {
        return success(adminMerchantUserApplicationService.getMerchantUser(accountId));
    }
}
