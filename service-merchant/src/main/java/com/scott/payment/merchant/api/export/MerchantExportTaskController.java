package com.scott.payment.merchant.api.export;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.application.export.MerchantExportTaskApplicationService;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.RefundSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.FundLedgerSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.RetryRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SearchRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SettlementBatchSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SettlementReserveSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SettlementSummarySubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SettlementTransactionSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.TaskSummary;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.TransactionSubmitRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 商户异步导出与下载中心接口，只暴露当前认证账号和商户共同拥有的任务。
 */
@RestController
@RequestMapping("/merchant/operations/export-tasks")
public class MerchantExportTaskController {
    private final MerchantExportTaskApplicationService applicationService;

    /**
     * 创建商户异步导出接口。
     */
    public MerchantExportTaskController(MerchantExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 提交商户交易查询异步导出任务。
     */
    @PostMapping("/transaction-operations")
    @RequiresPermission("merchant:transaction:order:export")
    @OperationLog(moduleName = "商户交易查询", businessType = OperationTypeConstants.EXPORT,
            operation = "提交商户交易异步导出")
    public CommonResult<TaskSummary> submitTransactions(
            @Valid @RequestBody TransactionSubmitRequest request) {
        return success(applicationService.submitTransactions(request));
    }

    /**
     * 提交商户退款异步导出任务。
     */
    @PostMapping("/refunds")
    @RequiresPermission("merchant:transaction:refund:export")
    @OperationLog(moduleName = "退款管理", businessType = OperationTypeConstants.EXPORT,
            operation = "提交商户退款异步导出")
    public CommonResult<TaskSummary> submitRefunds(@Valid @RequestBody RefundSubmitRequest request) {
        return success(applicationService.submitRefunds(request));
    }

    /**
     * 提交商户结算批次异步导出任务。
     */
    @PostMapping("/settlement-batches")
    @RequiresPermission("merchant:settlement:batch:export")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.EXPORT,
            operation = "提交商户结算批次异步导出")
    public CommonResult<TaskSummary> submitSettlementBatches(
            @Valid @RequestBody SettlementBatchSubmitRequest request) {
        return success(applicationService.submitSettlementBatches(request));
    }

    /**
     * 提交指定结算批次的汇总异步导出任务。
     */
    @PostMapping("/settlement-summaries")
    @RequiresPermission("merchant:settlement:batch:summary:export")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.EXPORT,
            operation = "提交商户结算汇总异步导出")
    public CommonResult<TaskSummary> submitSettlementSummaries(
            @Valid @RequestBody SettlementSummarySubmitRequest request) {
        return success(applicationService.submitSettlementSummaries(request));
    }

    /**
     * 提交商户交易结算明细异步导出任务。
     */
    @PostMapping("/settlement-transactions")
    @RequiresPermission("merchant:settlement:transaction-item:export")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.EXPORT,
            operation = "提交商户交易结算明细异步导出")
    public CommonResult<TaskSummary> submitSettlementTransactions(
            @Valid @RequestBody SettlementTransactionSubmitRequest request) {
        return success(applicationService.submitSettlementTransactions(request));
    }

    /**
     * 提交商户保证金结算明细异步导出任务。
     */
    @PostMapping("/settlement-reserves")
    @RequiresPermission("merchant:settlement:reserve-item:export")
    @OperationLog(moduleName = "保证金结算", businessType = OperationTypeConstants.EXPORT,
            operation = "提交商户保证金结算明细异步导出")
    public CommonResult<TaskSummary> submitSettlementReserves(
            @Valid @RequestBody SettlementReserveSubmitRequest request) {
        return success(applicationService.submitSettlementReserves(request));
    }

    /**
     * 提交当前商户资金账户余额流水异步导出任务。
     */
    @PostMapping("/fund-ledgers")
    @RequiresPermission("merchant:fund:ledger:export")
    @OperationLog(moduleName = "资金账户", businessType = OperationTypeConstants.EXPORT,
            operation = "提交商户余额流水异步导出")
    public CommonResult<TaskSummary> submitFundLedgers(
            @Valid @RequestBody FundLedgerSubmitRequest request) {
        return success(applicationService.submitFundLedgers(request));
    }

    /**
     * 分页查询当前账号创建的商户导出任务。
     */
    @PostMapping("/search")
    @RequiresPermission("merchant:operations:export-task:list")
    public CommonResult<PageResult<TaskSummary>> search(@Valid @RequestBody(required = false) SearchRequest request) {
        return success(applicationService.search(request));
    }

    /**
     * 查询当前账号拥有的商户导出任务详情。
     */
    @GetMapping("/{taskNo}")
    @RequiresPermission("merchant:operations:export-task:detail")
    public CommonResult<TaskSummary> detail(@PathVariable("taskNo") String taskNo) {
        return success(applicationService.detail(taskNo));
    }

    /**
     * 使用预期版本重试当前账号的失败任务。
     */
    @PostMapping("/{taskNo}/retry")
    @RequiresPermission("merchant:operations:export-task:retry")
    @OperationLog(moduleName = "下载中心", businessType = OperationTypeConstants.UPDATE,
            operation = "重试商户导出任务")
    public CommonResult<TaskSummary> retry(@PathVariable("taskNo") String taskNo,
                                           @Valid @RequestBody RetryRequest request) {
        return success(applicationService.retry(taskNo, request));
    }

    /**
     * 下载当前账号拥有且未过期的商户导出文件。
     */
    @GetMapping("/{taskNo}/download")
    @RequiresPermission("merchant:operations:export-task:download")
    @OperationLog(moduleName = "下载中心", businessType = OperationTypeConstants.EXPORT,
            operation = "下载商户导出文件")
    public void download(@PathVariable("taskNo") String taskNo,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        applicationService.download(taskNo, request, response);
    }
}
