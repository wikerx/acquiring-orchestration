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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantUserController
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : admin商户用户 HTTP 控制器，位于 运营后台服务，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 * @status : create
 */
@RestController
@RequestMapping("/admin/merchant-users")
public class AdminMerchantUserController {

    private final AdminMerchantUserApplicationService adminMerchantUserApplicationService;

    public AdminMerchantUserController(AdminMerchantUserApplicationService adminMerchantUserApplicationService) {
        this.adminMerchantUserApplicationService = adminMerchantUserApplicationService;
    }

    /**
     * 分页查询商户后台用户，敏感认证字段不得进入列表响应。
     *
     * @param request 商户号、账号、状态和分页条件
     * @return 商户用户分页结果
     */
    @GetMapping
    @RequiresPermission("admin:merchant:user:list")
    public CommonResult<PageResult<AdminMerchantUserListDTO>> pageMerchantUsers(@ModelAttribute AdminMerchantUserQueryRequest request) {
        return success(adminMerchantUserApplicationService.pageMerchantUsers(request));
    }

    /**
     * 查询指定商户用户详情，密码、盐值和令牌等认证材料不对管理端返回。
     *
     * @param accountId 商户用户账号主键
     * @return 商户用户详情
     */
    @GetMapping("/{accountId}")
    @RequiresPermission("admin:merchant:user:detail")
    public CommonResult<AdminMerchantUserDetailDTO> getMerchantUser(@PathVariable("accountId") Long accountId) {
        return success(adminMerchantUserApplicationService.getMerchantUser(accountId));
    }
}
