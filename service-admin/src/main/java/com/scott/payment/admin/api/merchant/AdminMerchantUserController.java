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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant User 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/merchant-users")
public class AdminMerchantUserController {

    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminMerchantUserApplicationService adminMerchantUserApplicationService;

    public AdminMerchantUserController(AdminMerchantUserApplicationService adminMerchantUserApplicationService) {
        this.adminMerchantUserApplicationService = adminMerchantUserApplicationService;
    }

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping
    @RequiresPermission("admin:merchant:user:list")
    public CommonResult<PageResult<AdminMerchantUserListDTO>> pageMerchantUsers(@ModelAttribute AdminMerchantUserQueryRequest request) {
        return success(adminMerchantUserApplicationService.pageMerchantUsers(request));
    }

    /**
     * 获取商户管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param accountId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/{accountId}")
    @RequiresPermission("admin:merchant:user:detail")
    public CommonResult<AdminMerchantUserDetailDTO> getMerchantUser(@PathVariable("accountId") Long accountId) {
        return success(adminMerchantUserApplicationService.getMerchantUser(accountId));
    }
}
