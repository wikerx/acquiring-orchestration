package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RefundSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TransactionOrderSubmitRequest;
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

/**
 * 管理端交易主单与退款异步导出提交接口，保留原同步导出路径作为兼容入口。
 */
@RestController
@RequestMapping("/admin/transactions")
public class AdminTransactionExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /**
     * 创建交易域异步导出提交接口。
     */
    public AdminTransactionExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 冻结当前操作人的交易主单查询与数据范围并创建后台任务。
     */
    @PostMapping("/orders/export-task")
    @RequiresPermission("transaction:order:export")
    @OperationLog(moduleName = "交易主单管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建交易主单导出任务")
    public CommonResult<TaskSummary> submitOrders(
            @Valid @RequestBody TransactionOrderSubmitRequest request) {
        return success(applicationService.submitTransactionOrders(request));
    }

    /**
     * 冻结当前操作人的退款查询与数据范围并创建后台任务。
     */
    @PostMapping("/refunds/export-task")
    @RequiresPermission("transaction:refund:export")
    @OperationLog(moduleName = "退款管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建退款导出任务")
    public CommonResult<TaskSummary> submitRefunds(@Valid @RequestBody RefundSubmitRequest request) {
        return success(applicationService.submitRefunds(request));
    }
}
