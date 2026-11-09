package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminSettlementPrincipalCorrectionApplicationService;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.CommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.DecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.SubmitRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理端已入账历史标签本金差额调整入口；提交和复核使用独立最小权限。
 */
@RestController
@RequestMapping("/admin/settlement/principal-corrections")
public class AdminSettlementPrincipalCorrectionController {

    private final AdminSettlementPrincipalCorrectionApplicationService applicationService;

    public AdminSettlementPrincipalCorrectionController(
            AdminSettlementPrincipalCorrectionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 提交只冻结来源事实的 Maker 申请，不在该请求内直接执行资金调整。 */
    @PostMapping
    @RequiresPermission("settlement:principal-correction:submit")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.CREATE,
            operation = "提交历史标签本金差额调整")
    public CommonResult<CommandResponse> submit(@RequestBody SubmitRequest request,
                                                HttpServletRequest servletRequest) {
        return success(applicationService.submit(request, servletRequest));
    }

    /** 以独立审批权限批准待复核申请，结算服务最终执行异人校验和资金 CAS。 */
    @PostMapping("/{correctionNo}/approve")
    @RequiresPermission("settlement:principal-correction:approve")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.AUDIT,
            operation = "审批历史标签本金差额调整")
    public CommonResult<CommandResponse> approve(
            @PathVariable("correctionNo") String correctionNo,
            @RequestBody DecisionRequest request,
            HttpServletRequest servletRequest) {
        return success(applicationService.decide(correctionNo, "APPROVE", request, servletRequest));
    }

    /** 以独立拒绝权限终止待复核申请，原资金事实保持不变。 */
    @PostMapping("/{correctionNo}/reject")
    @RequiresPermission("settlement:principal-correction:reject")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.AUDIT,
            operation = "拒绝历史标签本金差额调整")
    public CommonResult<CommandResponse> reject(
            @PathVariable("correctionNo") String correctionNo,
            @RequestBody DecisionRequest request,
            HttpServletRequest servletRequest) {
        return success(applicationService.decide(correctionNo, "REJECT", request, servletRequest));
    }
}
