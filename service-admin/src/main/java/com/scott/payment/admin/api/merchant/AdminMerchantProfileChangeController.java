package com.scott.payment.admin.api.merchant;

import com.scott.payment.admin.application.merchant.AdminMerchantProfileChangeApplicationService;
import com.scott.payment.admin.dto.merchant.AdminMerchantProfileChangeDTOs;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
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
 * @classname : AdminMerchantProfileChangeController
 * @date : 2026-09-21 10:15
 * @email : scott_x@163.com
 * @description : 管理端商户资料变更审核接口，负责权限校验和应用服务委托
 * @status : create
 */
@RestController
@RequestMapping("/admin/merchant-profile-changes")
public class AdminMerchantProfileChangeController {

    private final AdminMerchantProfileChangeApplicationService applicationService;

    /** 创建管理端商户资料变更审核接口。 */
    public AdminMerchantProfileChangeController(
            AdminMerchantProfileChangeApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 分页查询资料变更申请。 */
    @PostMapping("/search")
    @RequiresPermission("merchant:info:detail")
    public CommonResult<PageResult<AdminMerchantProfileChangeDTOs.ChangeRequest>> page(
            @RequestBody(required = false) AdminMerchantProfileChangeDTOs.Query query) {
        return success(applicationService.page(query));
    }

    /** 查询单笔资料变更申请详情。 */
    @GetMapping("/{requestNo}")
    @RequiresPermission("merchant:info:detail")
    public CommonResult<AdminMerchantProfileChangeDTOs.ChangeRequest> get(
            @PathVariable("requestNo") String requestNo) {
        return success(applicationService.get(requestNo));
    }

    /** 执行通过、补件或驳回决定。 */
    @PostMapping("/{requestNo}/review")
    @RequiresPermission("merchant:info:edit")
    @OperationLog(
            moduleName = "商户信息管理",
            businessType = OperationTypeConstants.UPDATE,
            operation = "审核商户资料变更",
            recordRequest = false,
            recordResponse = false
    )
    public CommonResult<AdminMerchantProfileChangeDTOs.ChangeRequest> review(
            @PathVariable("requestNo") String requestNo,
            @Valid @RequestBody AdminMerchantProfileChangeDTOs.ReviewRequest request) {
        return success(applicationService.review(requestNo, request));
    }
}
