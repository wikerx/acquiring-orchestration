package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundAccountSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundDeductionSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundLedgerSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FundRechargeSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理端资金账户域异步导出提交接口，仅负责冻结浏览器条件并创建后台任务。
 */
@RestController
@RequestMapping("/admin/fund-accounts")
public class AdminFundExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /**
     * 创建资金账户域异步导出提交接口。
     *
     * @param applicationService 统一异步导出编排服务
     */
    public AdminFundExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 冻结资金账户筛选条件和可信操作人范围并创建任务。 */
    @PostMapping("/export-task")
    @RequiresPermission("fund:account:export")
    @OperationLog(moduleName = "资金账户", businessType = OperationTypeConstants.EXPORT,
            operation = "创建资金账户导出任务")
    public CommonResult<TaskSummary> submitAccounts(
            @Valid @RequestBody FundAccountSubmitRequest request) {
        return success(applicationService.submitFundAccounts(request));
    }

    /** 冻结指定账户的流水筛选条件并创建任务，账户归属由来源服务再次校验。 */
    @PostMapping("/{id}/ledgers/export-task")
    @RequiresPermission("fund:ledger:export")
    @OperationLog(moduleName = "资金账户", businessType = OperationTypeConstants.EXPORT,
            operation = "创建指定账户余额明细导出任务")
    public CommonResult<TaskSummary> submitAccountLedgers(
            @PathVariable("id") Long accountId,
            @Valid @RequestBody FundLedgerSubmitRequest request) {
        request.setAccountId(accountId);
        return success(applicationService.submitFundLedgers(request));
    }

    /** 冻结全局余额流水筛选条件并创建任务。 */
    @PostMapping("/ledgers/export-task")
    @RequiresPermission("fund:ledger:all:export")
    @OperationLog(moduleName = "余额明细", businessType = OperationTypeConstants.EXPORT,
            operation = "创建全局余额明细导出任务")
    public CommonResult<TaskSummary> submitAllLedgers(
            @Valid @RequestBody FundLedgerSubmitRequest request) {
        request.setAccountId(null);
        return success(applicationService.submitFundLedgers(request));
    }

    /** 冻结充值申请筛选条件并创建任务。 */
    @PostMapping("/recharges/export-task")
    @RequiresPermission("fund:recharge:export")
    @OperationLog(moduleName = "账户充值", businessType = OperationTypeConstants.EXPORT,
            operation = "创建充值申请导出任务")
    public CommonResult<TaskSummary> submitRecharges(
            @Valid @RequestBody FundRechargeSubmitRequest request) {
        return success(applicationService.submitFundRecharges(request));
    }

    /** 冻结扣减申请筛选条件并创建任务。 */
    @PostMapping("/deductions/export-task")
    @RequiresPermission("fund:deduction:export")
    @OperationLog(moduleName = "账户扣减", businessType = OperationTypeConstants.EXPORT,
            operation = "创建扣减申请导出任务")
    public CommonResult<TaskSummary> submitDeductions(
            @Valid @RequestBody FundDeductionSubmitRequest request) {
        return success(applicationService.submitFundDeductions(request));
    }
}
