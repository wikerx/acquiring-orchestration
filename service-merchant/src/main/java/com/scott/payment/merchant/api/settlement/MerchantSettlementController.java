package com.scott.payment.merchant.api.settlement;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.application.settlement.MerchantSettlementApplicationService;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchDetail;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchSummary;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ClearingDetail;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReconciliationRecord;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.SummaryLine;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionSettlement;
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
 * @classname : MerchantSettlementController
 * @date : 2026-09-01 22:35
 * @email : scott_x@163.com
 * @description : 商户结算账单、真实交易明细和保证金动作的查询及导出入口；只暴露当前商户数据，不提供任何结算命令。
 * @status : update
 */
@RestController
@RequestMapping("/merchant/settlements")
public class MerchantSettlementController {

    private final MerchantSettlementApplicationService applicationService;

    public MerchantSettlementController(MerchantSettlementApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询当前认证商户已入账或已冲正的结算批次。
     *
     * @param query 可空批次过滤和分页条件
     * @return 强制绑定当前 merchantId 的结算批次分页
     */
    @PostMapping("/search")
    @RequiresPermission("merchant:settlement:batch:list")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.QUERY, operation = "查询结算账单")
    public CommonResult<PageResult<BatchSummary>> search(@RequestBody(required = false) BatchQuery query) {
        return success(applicationService.searchBatches(query));
    }

    /**
     * 查询当前认证商户指定批次的净额、汇率和聚合明细。
     *
     * @param settlementBatchNo 正式结算批次号
     * @return 仅当前 merchantId 可见的批次详情
     */
    @GetMapping("/{settlementBatchNo}")
    @RequiresPermission("merchant:settlement:batch:detail")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.QUERY, operation = "查询结算账单详情")
    public CommonResult<BatchDetail> detail(@PathVariable("settlementBatchNo") String settlementBatchNo) {
        return success(applicationService.getBatch(settlementBatchNo));
    }

    /** 独立授权读取正式结算凭证数据，PDF 由浏览器生成。 */
    @GetMapping("/{settlementBatchNo}/voucher")
    @RequiresPermission("merchant:settlement:batch:voucher-download")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.EXPORT,
            operation = "下载结算凭证")
    public CommonResult<BatchDetail> voucher(@PathVariable("settlementBatchNo") String settlementBatchNo) {
        return success(applicationService.getVoucher(settlementBatchNo));
    }

    /** 分页读取当前商户指定正式批次的结算汇总。 */
    @GetMapping("/{settlementBatchNo}/summaries")
    @RequiresPermission("merchant:settlement:batch:summary:list")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.QUERY,
            operation = "查询结算汇总")
    public CommonResult<PageResult<SummaryLine>> summaries(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return success(applicationService.searchResultSummaries(settlementBatchNo, pageNo, pageSize));
    }

    /** 导出当前商户指定正式批次的全部结算汇总。 */
    @PostMapping("/{settlementBatchNo}/summaries/export")
    @RequiresPermission("merchant:settlement:batch:summary:export")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.EXPORT,
            operation = "导出结算汇总")
    public void exportSummaries(@PathVariable("settlementBatchNo") String settlementBatchNo,
                                HttpServletResponse response) {
        applicationService.exportResultSummaries(settlementBatchNo, response);
    }

    /**
     * 分页查询批次内可追溯到真实 transactionId 的结算财务行。
     *
     * @param query 可空交易明细过滤和分页条件
     * @return 强制绑定当前 merchantId 的交易结算明细分页
     */
    @PostMapping("/transaction-items/search")
    @RequiresPermission("merchant:settlement:transaction-item:list")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.QUERY, operation = "查询交易结算明细")
    public CommonResult<PageResult<TransactionSettlement>> searchTransactionItems(
            @RequestBody(required = false) TransactionItemQuery query) {
        return success(applicationService.searchTransactionItems(query));
    }

    /** 分页查询当前商户正式批次内一笔交易的结算财务组件。 */
    @GetMapping("/transaction-items/batches/{settlementBatchNo}/transactions/{transactionId}")
    @RequiresPermission("merchant:settlement:transaction-item:list")
    @OperationLog(moduleName = "交易结算", businessType = OperationTypeConstants.QUERY,
            operation = "查询交易结算组件")
    public CommonResult<PageResult<TransactionItem>> transactionComponents(
            @PathVariable("settlementBatchNo") String settlementBatchNo,
            @PathVariable("transactionId") String transactionId,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return success(applicationService.searchTransactionComponents(
                settlementBatchNo, transactionId, pageNo, pageSize));
    }

    /** 按真实交易号和交易时间查询当前认证商户的对账状态快照。 */
    @GetMapping("/reconciliation-records/transactions/{transactionId}")
    @RequiresPermission("merchant:reconciliation:record:detail")
    @OperationLog(moduleName = "交易对账", businessType = OperationTypeConstants.QUERY,
            operation = "按交易查询对账明细")
    public CommonResult<List<ReconciliationRecord>> reconciliationRecordsByTransaction(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime) {
        return success(applicationService.findReconciliationRecordsByTransaction(
                transactionId, transactionDateTime));
    }

    /** 按真实交易号和交易时间查询当前认证商户的清分详情。 */
    @GetMapping("/clearing-records/transactions/{transactionId}")
    @RequiresPermission("merchant:clearing:record:detail")
    @OperationLog(moduleName = "交易清分", businessType = OperationTypeConstants.QUERY,
            operation = "按交易查询清分明细")
    public CommonResult<ClearingDetail> clearingDetailByTransaction(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime) {
        return success(applicationService.findClearingDetailByTransaction(
                transactionId, transactionDateTime));
    }

    /**
     * 按真实交易号和交易时间查询当前商户完整的结算财务组件。
     */
    @GetMapping("/transaction-items/transactions/{transactionId}")
    @RequiresPermission("merchant:settlement:transaction-item:transaction-detail")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.QUERY,
            operation = "按交易查询结算明细")
    public CommonResult<PageResult<TransactionItem>> transactionItemsByTransaction(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return success(applicationService.searchTransactionItemsByTransaction(
                transactionId, transactionDateTime, pageNo, pageSize));
    }

    /**
     * 分页查询当前商户保证金结算动作及动作后的剩余责任。
     *
     * @param query 可空保证金明细过滤和分页条件
     * @return 强制绑定当前 merchantId 的保证金动作分页
     */
    @PostMapping("/reserve-items/search")
    @RequiresPermission("merchant:settlement:reserve-item:list")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.QUERY, operation = "查询保证金结算明细")
    public CommonResult<PageResult<ReserveItem>> searchReserveItems(
            @RequestBody(required = false) ReserveItemQuery query) {
        return success(applicationService.searchReserveItems(query));
    }

    /**
     * 按原支付交易号和交易时间查询当前商户完整的保证金动作。
     */
    @GetMapping("/reserve-items/transactions/{transactionId}")
    @RequiresPermission("merchant:settlement:reserve-item:transaction-detail")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.QUERY,
            operation = "按交易查询保证金明细")
    public CommonResult<PageResult<ReserveItem>> reserveItemsByTransaction(
            @PathVariable("transactionId") String transactionId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime transactionDateTime,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return success(applicationService.searchReserveItemsByTransaction(
                transactionId, transactionDateTime, pageNo, pageSize));
    }

    /**
     * 按当前查询条件导出商户可见的结算批次。
     *
     * @param query 可空批次过滤条件
     * @param response Excel 流式下载响应
     */
    @PostMapping("/export")
    @RequiresPermission("merchant:settlement:batch:export")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.EXPORT, operation = "导出结算账单")
    public void exportBatches(@RequestBody(required = false) BatchQuery query, HttpServletResponse response) {
        applicationService.exportBatches(query, response);
    }

    /**
     * 按当前查询条件导出真实交易结算明细。
     *
     * @param query 可空交易明细过滤条件
     * @param response Excel 流式下载响应
     */
    @PostMapping("/transaction-items/export")
    @RequiresPermission("merchant:settlement:transaction-item:export")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.EXPORT, operation = "导出交易结算明细")
    public void exportTransactionItems(@RequestBody(required = false) TransactionItemQuery query,
                                       HttpServletResponse response) {
        applicationService.exportTransactionItems(query, response);
    }

    /**
     * 按当前查询条件导出保证金动作明细。
     *
     * @param query 可空保证金明细过滤条件
     * @param response Excel 流式下载响应
     */
    @PostMapping("/reserve-items/export")
    @RequiresPermission("merchant:settlement:reserve-item:export")
    @OperationLog(moduleName = "结算账单", businessType = OperationTypeConstants.EXPORT, operation = "导出保证金结算明细")
    public void exportReserveItems(@RequestBody(required = false) ReserveItemQuery query,
                                   HttpServletResponse response) {
        applicationService.exportReserveItems(query, response);
    }
}
