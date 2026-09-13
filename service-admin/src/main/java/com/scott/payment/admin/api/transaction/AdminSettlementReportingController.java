package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminSettlementReportingApplicationService;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReconciliationRecord;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.TransactionSettlementSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSummary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReportingController
 * @date : 2026-09-01 22:50
 * @email : scott_x@163.com
 * @description : Admin 结算预审单、逐笔结果、保证金动作和净额入账的本地查询及导出入口；统一执行数据范围与导出审计。
 * @status : update
 */
@RestController
@RequestMapping("/admin/settlement")
public class AdminSettlementReportingController {

    private final AdminSettlementReportingApplicationService applicationService;

    public AdminSettlementReportingController(AdminSettlementReportingApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 按查询条件导出管理数据范围内的结算预审单。
     *
     * @param request 预审单查询条件
     * @param response Excel 响应流
     */
    @PostMapping("/review-orders/export")
    @RequiresPermission("settlement:review-order:export")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.EXPORT,
            operation = "导出结算预审单")
    public void exportReviews(@RequestBody ReviewSearchRequest request, HttpServletResponse response) {
        applicationService.exportReviews(request, response);
    }

    /**
     * 分页查询结算批次逐笔结果。
     *
     * @param request 批次、商户、真实交易、结果类型和分页条件
     * @return 结算结果明细分页
     */
    @PostMapping("/result-items/search")
    @RequiresPermission("settlement:result-item:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算结果明细")
    public CommonResult<PageResult<TransactionSettlementSummary>> searchResultItems(
            @RequestBody ResultItemSearchRequest request) {
        return success(applicationService.searchResultItems(request));
    }

    /** 分页查询正式批次内一笔交易的本金、费用、调整等不可变结算组件。 */
    @GetMapping("/result-items/batches/{settlementBatchNo}/transactions/{transactionId}")
    @RequiresPermission("settlement:result-item:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询交易结算组件")
    public CommonResult<PageResult<ResultItemSummary>> resultItemComponents(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @PathVariable("transactionId") String transactionId,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return success(applicationService.searchResultItemComponents(
                settlementBatchNo, transactionId, pageNo, pageSize));
    }

    /**
     * 按真实交易号和交易时间查询当前交易动作的对账状态快照。
     */
    @GetMapping("/reconciliation-records/transactions/{transactionId}")
    @RequiresPermission("reconciliation:record:detail")
    @OperationLog(moduleName = "交易对账", businessType = OperationTypeConstants.QUERY,
            operation = "按交易查询对账明细")
    public CommonResult<List<ReconciliationRecord>> reconciliationRecordsByTransaction(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime) {
        return success(applicationService.findReconciliationRecordsByTransaction(
                transactionId, transactionDateTime));
    }

    /**
     * 按真实交易号和交易时间查询当前交易的正式结算结果。
     *
     * @param transactionId 真实平台交易号
     * @param transactionDateTime 交易季度精确路由时间
     * @param pageNo 页码
     * @param pageSize 页大小
     * @return 当前交易结算结果分页
     */
    @GetMapping("/result-items/transactions/{transactionId}")
    @RequiresPermission("settlement:result-item:transaction-detail")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "按交易查询结算结果明细")
    public CommonResult<PageResult<ResultItemSummary>> resultItemsByTransaction(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return success(applicationService.searchResultItemsByTransaction(
                transactionId, transactionDateTime, pageNo, pageSize));
    }

    /**
     * 按查询条件导出结算批次逐笔结果。
     *
     * @param request 结算结果查询条件
     * @param response Excel 响应流
     */
    @PostMapping("/result-items/export")
    @RequiresPermission("settlement:result-item:export")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.EXPORT,
            operation = "导出结算结果明细")
    public void exportResultItems(@RequestBody ResultItemSearchRequest request, HttpServletResponse response) {
        applicationService.exportResultItems(request, response);
    }

    /**
     * 分页查询保证金不可变动作和责任余额。
     *
     * @param request 批次、商户、保证金、动作和分页条件
     * @return 保证金结算明细分页
     */
    @PostMapping("/reserve-items/search")
    @RequiresPermission("settlement:reserve-item:list")
    @OperationLog(moduleName = "保证金结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询保证金结算明细")
    public CommonResult<PageResult<ReserveItemSummary>> searchReserveItems(
            @RequestBody ReserveItemSearchRequest request) {
        return success(applicationService.searchReserveItems(request));
    }

    /**
     * 按原支付交易号和原支付时间查询完整保证金结算动作。
     *
     * @param transactionId 原支付真实平台交易号
     * @param transactionDateTime 原支付季度精确路由时间
     * @param pageNo 页码
     * @param pageSize 页大小
     * @return 当前原交易保证金结算动作分页
     */
    @GetMapping("/reserve-items/transactions/{transactionId}")
    @RequiresPermission("settlement:reserve-item:transaction-detail")
    @OperationLog(moduleName = "保证金结算", businessType = OperationTypeConstants.QUERY,
            operation = "按交易查询保证金结算明细")
    public CommonResult<PageResult<ReserveItemSummary>> reserveItemsByTransaction(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return success(applicationService.searchReserveItemsByTransaction(
                transactionId, transactionDateTime, pageNo, pageSize));
    }

    /**
     * 按查询条件导出保证金不可变动作。
     *
     * @param request 保证金结算明细查询条件
     * @param response Excel 响应流
     */
    @PostMapping("/reserve-items/export")
    @RequiresPermission("settlement:reserve-item:export")
    @OperationLog(moduleName = "保证金结算", businessType = OperationTypeConstants.EXPORT,
            operation = "导出保证金结算明细")
    public void exportReserveItems(@RequestBody ReserveItemSearchRequest request, HttpServletResponse response) {
        applicationService.exportReserveItems(request, response);
    }

    /**
     * 分页查询结算批次净额入账流水。
     *
     * @param request 批次、商户、账户、方向、币种和分页条件
     * @return 结算入账流水分页
     */
    @PostMapping("/postings/search")
    @RequiresPermission("settlement:posting:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算入账记录")
    public CommonResult<PageResult<PostingSummary>> searchPostings(@RequestBody PostingSearchRequest request) {
        return success(applicationService.searchPostings(request));
    }

    /**
     * 按查询条件导出结算净额入账流水。
     *
     * @param request 结算入账流水查询条件
     * @param response Excel 响应流
     */
    @PostMapping("/postings/export")
    @RequiresPermission("settlement:posting:export")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.EXPORT,
            operation = "导出结算入账记录")
    public void exportPostings(@RequestBody PostingSearchRequest request, HttpServletResponse response) {
        applicationService.exportPostings(request, response);
    }
}
