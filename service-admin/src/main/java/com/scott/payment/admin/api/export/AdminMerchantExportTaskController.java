package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MerchantIpWhitelistSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/** 管理端商户访问控制异步导出提交入口。 */
@RestController
@RequestMapping("/admin/merchant")
public class AdminMerchantExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /** 创建商户访问控制异步导出入口。 */
    public AdminMerchantExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 创建商户 IP 白名单异步导出任务。 */
    @PostMapping("/ip-whitelist/export-task")
    @RequiresPermission("merchant:ip-whitelist:export")
    @OperationLog(moduleName = "商户 IP 白名单", businessType = OperationTypeConstants.EXPORT,
            operation = "创建商户 IP 白名单导出任务")
    public CommonResult<TaskSummary> submitMerchantIpWhitelists(
            @Valid @RequestBody MerchantIpWhitelistSubmitRequest request) {
        return success(applicationService.submitMerchantIpWhitelists(request));
    }
}
