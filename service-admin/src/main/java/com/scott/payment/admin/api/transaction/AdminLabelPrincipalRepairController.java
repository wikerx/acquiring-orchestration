package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminLabelPrincipalRepairApplicationService;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.Response;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.ReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.SubmitRequest;
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
 * Admin 未结算历史标签本金修复入口，提交和复核使用独立权限并由服务端注入操作人。
 */
@RestController
@RequestMapping("/admin/clearing/principal-repairs")
public class AdminLabelPrincipalRepairController {

    private final AdminLabelPrincipalRepairApplicationService applicationService;

    public AdminLabelPrincipalRepairController(
            AdminLabelPrincipalRepairApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 以修复申请权限接收版本条件与原因，可信申请人由服务端会话注入。 */
    @PostMapping
    @RequiresPermission("clearing:principal-repair:submit")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.CREATE,
            operation = "提交历史标签本金修复")
    public CommonResult<Response> submit(@RequestBody SubmitRequest request) {
        return success(applicationService.submit(request));
    }

    /** 以复核权限接收决定及预期版本，可信复核人由服务端注入并交领域层验证双人规则。 */
    @PostMapping("/{repairNo}/review")
    @RequiresPermission("clearing:principal-repair:review")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.AUDIT,
            operation = "复核历史标签本金修复")
    public CommonResult<Response> review(@PathVariable("repairNo") String repairNo,
                                         @RequestBody ReviewRequest request) {
        return success(applicationService.review(repairNo, request));
    }
}
