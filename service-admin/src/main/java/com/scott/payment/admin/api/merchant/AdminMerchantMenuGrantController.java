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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant Menu Grant 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/merchant-menu-grants")
public class AdminMerchantMenuGrantController {

    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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
    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public CommonResult<Void> saveGrant(@PathVariable("merchantId") String merchantId,
                                        @Valid @RequestBody AdminMerchantMenuGrantSaveRequest request) {
        adminMerchantMenuGrantApplicationService.saveGrant(merchantId, request);
        return success();
    }
}
