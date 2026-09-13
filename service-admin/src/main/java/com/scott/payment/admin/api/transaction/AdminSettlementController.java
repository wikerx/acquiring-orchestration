package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminSettlementApplicationService;
import com.scott.payment.admin.application.transaction.AdminSettlementReportingApplicationService;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultSummaryLine;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementController
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : Admin 结算批次查询和入账前取消权限接口；Controller 不承载资金规则。
 * @status : create
 */
@RestController
@RequestMapping("/admin/settlement/batches")
public class AdminSettlementController {

    private final AdminSettlementApplicationService applicationService;
    private final AdminSettlementReportingApplicationService reportingApplicationService;

    public AdminSettlementController(AdminSettlementApplicationService applicationService,
                                     AdminSettlementReportingApplicationService reportingApplicationService) {
        this.applicationService = applicationService;
        this.reportingApplicationService = reportingApplicationService;
    }

    /**
     * 在当前 Admin 商户数据范围内分页查询正式结算批次。
     *
     * @param request 业务日期窗口和批次过滤条件
     * @return 正式结算批次标准分页
     */
    @PostMapping("/search")
    @RequiresPermission("settlement:batch:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算批次")
    public CommonResult<PageResult<BatchSummary>> search(@RequestBody BatchSearchRequest request) {
        return success(applicationService.search(request));
    }

    /** 在交易结算工作台分页查询 REGULAR 正式批次。 */
    @PostMapping("/transaction/search")
    @RequiresPermission("settlement:transaction-batch:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询交易正式结算批次")
    public CommonResult<PageResult<BatchSummary>> searchTransactionBatches(
            @RequestBody BatchSearchRequest request) {
        return success(applicationService.searchTransactionBatches(request));
    }

    /** 在保证金结算工作台分页查询释放和调整正式批次。 */
    @PostMapping("/reserve/search")
    @RequiresPermission("settlement:reserve-batch:list")
    @OperationLog(moduleName = "保证金结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询保证金正式结算批次")
    public CommonResult<PageResult<BatchSummary>> searchReserveBatches(
            @RequestBody BatchSearchRequest request) {
        return success(applicationService.searchReserveBatches(request));
    }

    /**
     * 在当前 Admin 商户数据范围内读取正式批次运营详情。
     *
     * @param settlementBatchNo 正式结算批次号
     * @return 批次、候选、汇率、结果、资金与投影详情
     */
    @GetMapping("/{settlementBatchNo}")
    @RequiresPermission("settlement:batch:detail")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算批次详情")
    public CommonResult<BatchDetailResponse> detail(
            @PathVariable("settlementBatchNo") String settlementBatchNo) {
        return success(applicationService.detail(settlementBatchNo));
    }

    /** 独立授权读取正式结算凭证的完整不可变汇总快照。 */
    @GetMapping("/{settlementBatchNo}/voucher")
    @RequiresPermission("settlement:batch:voucher-download")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.EXPORT,
            operation = "下载正式结算凭证")
    public CommonResult<BatchDetailResponse> voucher(
            @PathVariable("settlementBatchNo") String settlementBatchNo) {
        return success(applicationService.voucher(settlementBatchNo));
    }

    /** 分页读取正式批次结算汇总，避免详情一次加载全部聚合行。 */
    @GetMapping("/{settlementBatchNo}/summaries")
    @RequiresPermission("settlement:batch:summary:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算批次汇总")
    public CommonResult<PageResult<ResultSummaryLine>> summaries(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return success(reportingApplicationService.searchBatchResultSummaries(
                settlementBatchNo, pageNo, pageSize));
    }

    /** 导出正式批次全部结算汇总。 */
    @PostMapping("/{settlementBatchNo}/summaries/export")
    @RequiresPermission("settlement:batch:summary:export")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.EXPORT,
            operation = "导出结算批次汇总")
    public void exportSummaries(@PathVariable("settlementBatchNo") String settlementBatchNo,
                                HttpServletResponse response) {
        reportingApplicationService.exportBatchResultSummaries(settlementBatchNo, response);
    }

    /**
     * 注入可信操作人后取消尚未入账的正式批次。
     *
     * @param settlementBatchNo 待取消批次号
     * @param request 浏览器批次命令，不接受操作人字段
     * @param servletRequest 可信客户端环境来源
     * @return 取消状态和实际释放候选数
     */
    @PostMapping("/{settlementBatchNo}/cancel")
    @RequiresPermission("settlement:batch:cancel")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.UPDATE,
            operation = "取消未入账结算批次")
    public CommonResult<BatchCommandResponse> cancel(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @RequestBody BatchCommandRequest request,
            HttpServletRequest servletRequest) {
        return success(applicationService.cancel(settlementBatchNo, request, servletRequest));
    }

    /** 恢复仅因汇率锁定重试耗尽进入人工复核的正式批次。 */
    @PostMapping("/{settlementBatchNo}/retry")
    @RequiresPermission("settlement:batch:retry")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.UPDATE,
            operation = "重新处理汇率锁定失败批次")
    public CommonResult<BatchCommandResponse> retry(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @RequestBody BatchCommandRequest request,
            HttpServletRequest servletRequest) {
        return success(applicationService.retry(settlementBatchNo, request, servletRequest));
    }

}
