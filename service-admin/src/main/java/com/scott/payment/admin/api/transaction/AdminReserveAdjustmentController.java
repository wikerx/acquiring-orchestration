package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminClearingApplicationService;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentSubmitRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminReserveAdjustmentController
 * @date : 2026-08-26 19:10
 * @email : scott_x@163.com
 * @description : Admin 保证金清分差额申请和双人复核入口；操作人只由应用层读取可信登录上下文。
 * @status : create
 */
@RestController
@RequestMapping("/admin/clearing/reserve-adjustments")
public class AdminReserveAdjustmentController {

    private final AdminClearingApplicationService applicationService;

    public AdminReserveAdjustmentController(AdminClearingApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 提交标签币种保证金差额申请，不直接产生保证金明细。
     *
     * @param request 保证金状态版本、方向、标签币种金额和原因
     * @return 新建或幂等回放的待复核申请
     */
    @PostMapping
    @RequiresPermission("clearing:reserve-adjustment:submit")
    @OperationLog(moduleName = "保证金清分", businessType = OperationTypeConstants.CREATE,
            operation = "提交保证金差额申请")
    public CommonResult<ReserveAdjustmentResponse> submit(
            @RequestBody ReserveAdjustmentSubmitRequest request) {
        return success(applicationService.submitReserveAdjustment(request));
    }

    /**
     * 复核保证金差额申请，批准后由清分服务同事务写事实和候选。
     *
     * @param adjustmentNo 保证金调整单号
     * @param request 决策、申请预期版本和复核意见
     * @return 复核状态和资金化动作身份
     */
    @PostMapping("/{adjustmentNo}/review")
    @RequiresPermission("clearing:reserve-adjustment:review")
    @OperationLog(moduleName = "保证金清分", businessType = OperationTypeConstants.AUDIT,
            operation = "复核保证金差额申请")
    public CommonResult<ReserveAdjustmentResponse> review(
            @PathVariable("adjustmentNo") String adjustmentNo,
            @RequestBody ReserveAdjustmentReviewRequest request) {
        return success(applicationService.reviewReserveAdjustment(adjustmentNo, request));
    }
}
